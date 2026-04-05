package me.miku.backup.service

import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import me.miku.backup.config.ConfigManager
import java.io.FileInputStream
import java.nio.file.Path
import java.util.logging.Logger

class DriveService(
    private val config: ConfigManager,
    private val logger: Logger,
    private val dataFolder: java.io.File
) {
    private var drive: Drive? = null

    init {
        initialize()
    }

    fun initialize() {
        if (!config.driveEnabled) return
        
        try {
            val jsonFile = java.io.File(dataFolder, config.serviceAccountJson)
            if (!jsonFile.exists()) {
                logger.warning("Google Drive Service Account JSON not found at ${jsonFile.absolutePath}")
                return
            }

            val credentials = GoogleCredentials.fromStream(FileInputStream(jsonFile))
                .createScoped(listOf(DriveScopes.DRIVE_FILE))

            drive = Drive.Builder(
                com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            ).setApplicationName("MikuBackup").build()
            
            logger.info("Google Drive Service initialized.")
        } catch (e: Exception) {
            logger.severe("Failed to initialize Google Drive Service: ${e.message}")
            e.printStackTrace()
        }
    }

    fun uploadFile(localFile: java.io.File): String? {
        val driveService = drive ?: return null
        
        return try {
            val fileMetadata = File()
            fileMetadata.name = localFile.name
            if (config.driveFolderId.isNotEmpty()) {
                fileMetadata.parents = listOf(config.driveFolderId)
            }

            val mediaContent = FileContent("application/zip", localFile)
            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            
            uploadedFile.id
        } catch (e: Exception) {
            logger.severe("Failed to upload file to Google Drive: ${e.message}")
            null
        }
    }

    fun cleanupOldBackups() {
        val driveService = drive ?: return
        if (config.driveKeepCount <= 0) return

        try {
            val query = if (config.driveFolderId.isNotEmpty()) {
                "'${config.driveFolderId}' in parents and name contains '${config.backupPrefix}' and trashed = false"
            } else {
                "name contains '${config.backupPrefix}' and trashed = false"
            }

            val result = driveService.files().list()
                .setQ(query)
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, createdTime)")
                .execute()

            val files = result.files ?: return
            if (files.size > config.driveKeepCount) {
                for (i in config.driveKeepCount until files.size) {
                    val file = files[i]
                    driveService.files().delete(file.id).execute()
                    logger.info("Deleted old remote backup: ${file.name}")
                }
            }
        } catch (e: Exception) {
            logger.severe("Failed to cleanup old backups on Google Drive: ${e.message}")
        }
    }
}
