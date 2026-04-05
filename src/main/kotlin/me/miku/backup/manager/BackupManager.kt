package me.miku.backup.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import me.miku.backup.config.ConfigManager
import me.miku.backup.service.DriveService
import me.miku.backup.util.ZipUtils
import org.bukkit.Bukkit
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

    suspend fun runBackup(manual: Boolean = false) {
        val startTime = System.currentTimeMillis()
        logger.info("Starting backup...")

        try {
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
            val backupFile = File(backupFolder, backupFileName)

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
                ZipUtils.zipFolders(worldFiles, backupFile)
            }

            val fileSize = ZipUtils.formatSize(backupFile.length())
            val duration = (System.currentTimeMillis() - startTime) / 1000.0

            // 4. Upload to Google Drive
            if (config.driveEnabled) {
                withContext(Dispatchers.IO) {
                    logger.info("Uploading to Google Drive...")
                    val driveId = driveService.uploadFile(backupFile)
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

            logger.info("Backup finished. Duration: ${duration}s. Size: $fileSize")
            
            val successMsg = config.messageSuccess
                .replace("%duration%", String.format("%.2f", duration))
                .replace("%size%", fileSize)
            
            if (manual) {
                // Return success to the command sender
            }

        } catch (e: Exception) {
            logger.severe("Backup failed: ${e.message}")
            e.printStackTrace()
        }
    }
}
