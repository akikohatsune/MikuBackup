package miku.hatsuneakiko.backup.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import miku.hatsuneakiko.backup.config.ConfigManager
import miku.hatsuneakiko.backup.service.DriveService
import miku.hatsuneakiko.backup.util.ZipUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class BackupManager(
    private val plugin: JavaPlugin,
    private val config: ConfigManager,
    private val driveService: DriveService,
    private val logger: Logger
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
    private var isTaskRunning = false
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    fun isBackupRunning(): Boolean = isTaskRunning

    suspend fun testBackup(sender: CommandSender) = withContext(Dispatchers.IO) {
        if (isTaskRunning) {
            sender.sendMessage(Component.text("Một tác vụ sao lưu đang được thực hiện!", NamedTextColor.RED))
            return@withContext
        }

        if (!config.driveEnabled) {
            sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&cGoogle Drive hiện đang bị TẮT trong config.yml!")))
            return@withContext
        }

        sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&7Đang kiểm tra kết nối Google Drive...")))

        try {
            val testFile = File(plugin.dataFolder, "test-connection.txt")
            testFile.writeText("MikuBackup Connection Test\nTimestamp: ${Date()}\nServer: ${Bukkit.getServer().name}")

            val driveId = driveService.uploadFile(testFile, "text/plain")
            
            if (driveId != null) {
                sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&aKết nối thành công! File ID: &e$driveId")))
                logger.info("Test upload successful. File ID: $driveId")
            } else {
                sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&cKết nối thất bại! Hãy kiểm tra thông tin Service Account hoặc ID Folder.")))
            }
            
            if (testFile.exists()) {
                testFile.delete()
            }
        } catch (e: Exception) {
            sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&cCó lỗi xảy ra: ${e.message}")))
            logger.severe("Test backup failed: ${e.message}")
        }
    }

    suspend fun runBackup(manualSender: CommandSender? = null) = coroutineScope {
        if (isTaskRunning) {
            manualSender?.sendMessage(Component.text("Một tác vụ sao lưu đang được thực hiện!", NamedTextColor.RED))
            return@coroutineScope
        }
        
        isTaskRunning = true
        val startTime = System.currentTimeMillis()
        var backupFile: File? = null

        try {
            logger.info("Starting backup...")

            // 1. Force save worlds on main thread
            val future = CompletableFuture<Unit>()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                for (worldName in config.worlds) {
                    val world = Bukkit.getWorld(worldName)
                    if (world != null) {
                        logger.info("Saving world: $worldName")
                        world.save()
                    }
                }
                future.complete(Unit)
            })
            future.await()

            // 2. Prepare backup folder
            val backupFolder = File(plugin.server.worldContainer, config.localPath)
            val containerPath = plugin.server.worldContainer.canonicalPath
            val backupPath = backupFolder.canonicalPath
            if (!backupPath.startsWith(containerPath + File.separator) && backupPath != containerPath) {
                throw SecurityException("Path traversal detected in backup local-path configuration!")
            }
            
            if (!backupFolder.exists()) {
                backupFolder.mkdirs()
            }

            val timestamp = dateFormat.format(Date())
            val backupFileName = "${config.backupPrefix}$timestamp.zip"
            val currentBackupFile = File(backupFolder, backupFileName)
            backupFile = currentBackupFile

            // 3. Zip files in IO thread
            withContext(Dispatchers.IO) {
                val worldFiles = config.worlds.mapNotNull { 
                    val world = Bukkit.getWorld(it)
                    world?.worldFolder
                }
                
                if (worldFiles.isEmpty()) {
                    throw IllegalStateException("No worlds found to backup!")
                }

                logger.info("Zipping worlds...")
                ZipUtils.zipFolders(worldFiles, currentBackupFile)
            }

            val fileSize = ZipUtils.formatSize(currentBackupFile.length())
            val duration = (System.currentTimeMillis() - startTime) / 1000.0

            // 4. Upload to Google Drive
            if (config.driveEnabled) {
                withContext(Dispatchers.IO) {
                    logger.info("Uploading to Google Drive...")
                    val driveId = driveService.uploadFile(currentBackupFile)
                    if (driveId != null) {
                        logger.info("Successfully uploaded to Google Drive. File ID: $driveId")
                        driveService.cleanupOldBackups()
                    } else {
                        logger.severe("Failed to upload to Google Drive!")
                    }
                }
            }

            // 5. Cleanup local backups
            withContext(Dispatchers.IO) {
                val localBackups = backupFolder.listFiles { file -> 
                    file.name.startsWith(config.backupPrefix) && file.name.endsWith(".zip") 
                }?.sortedByDescending { it.lastModified() } ?: emptyList<File>()

                if (localBackups.size > config.localKeepCount) {
                    for (i in config.localKeepCount until localBackups.size) {
                        val toDelete = localBackups[i]
                        if (toDelete.delete()) {
                            logger.info("Deleted old local backup: ${toDelete.name}")
                        }
                    }
                }
            }

            val finalMsg = legacy.deserialize(config.messageSuccess.replace("%duration%", String.format("%.2f", duration)).replace("%size%", fileSize))
            
            logger.info("Backup finished. Duration: ${duration}s. Size: $fileSize")
            
            val broadcastMsg = legacy.deserialize(config.messagePrefix).append(finalMsg)
            Bukkit.broadcast(broadcastMsg)
            
            if (manualSender != null && manualSender != Bukkit.getConsoleSender()) {
                manualSender.sendMessage(broadcastMsg)
            }

        } catch (e: CancellationException) {
            logger.warning("Backup task was cancelled.")
            manualSender?.sendMessage(Component.text("Tác vụ sao lưu đã bị dừng.", NamedTextColor.RED))
            if (backupFile?.exists() == true) {
                backupFile.delete()
            }
        } catch (e: Exception) {
            logger.severe("Backup failed: ${e.message}")
            manualSender?.sendMessage(Component.text("Backup thất bại! Xem Console.", NamedTextColor.RED))
        } finally {
            isTaskRunning = false
        }
    }
}
