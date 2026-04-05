package me.miku.backup.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import me.miku.backup.config.ConfigManager
import me.miku.backup.service.DriveService
import me.miku.backup.util.ZipUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
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

    fun isBackupRunning(): Boolean = isTaskRunning

    suspend fun runBackup(manualSender: CommandSender? = null) = coroutineScope {
        if (isTaskRunning) {
            manualSender?.sendMessage("${ChatColor.RED}Một tác vụ sao lưu đang được thực hiện!")
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

            val finalMsg = ChatColor.translateAlternateColorCodes('&', 
                config.messageSuccess.replace("%duration%", String.format("%.2f", duration)).replace("%size%", fileSize))
            
            logger.info("Backup finished. Duration: ${duration}s. Size: $fileSize")
            manualSender?.sendMessage("${ChatColor.translateAlternateColorCodes('&', config.messagePrefix)}$finalMsg")

        } catch (e: CancellationException) {
            logger.warning("Backup task was cancelled.")
            manualSender?.sendMessage("${ChatColor.RED}Tác vụ sao lưu đã bị dừng.")
            if (backupFile?.exists() == true) {
                backupFile.delete()
            }
        } catch (e: Exception) {
            logger.severe("Backup failed: ${e.message}")
            manualSender?.sendMessage("${ChatColor.RED}Backup thất bại! Xem Console.")
        } finally {
            isTaskRunning = false
        }
    }
}
