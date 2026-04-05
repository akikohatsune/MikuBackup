package miku.hatsuneakiko.backup.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import miku.hatsuneakiko.backup.config.ConfigManager
import org.bukkit.plugin.java.JavaPlugin
import java.io.FileInputStream
import java.util.logging.Logger

class DriveService(
    private val plugin: JavaPlugin,
    private val config: ConfigManager,
    private val logger: Logger
) {
    private var drive: Drive? = null

    fun initialize() {
        if (!config.driveEnabled) return
        
        try {
            val jsonFile = java.io.File(plugin.dataFolder, config.serviceAccountPath)
            if (!jsonFile.exists()) {
                createServiceAccountTemplate(jsonFile)
                logger.warning("---------------------------------------------------")
                logger.warning("GOOGLE DRIVE SERVICE: service-account.json MISSING!")
                logger.warning("A template has been created at: ${jsonFile.name}")
                logger.warning("Please fill it with your Google Service Account data.")
                logger.warning("---------------------------------------------------")
                config.driveEnabled = false
                return
            }

            val credentials = GoogleCredentials.fromStream(FileInputStream(jsonFile))
                .createScoped(listOf(DriveScopes.DRIVE_FILE))

            drive = Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            ).setApplicationName("MikuBackup").build()
            
            logger.info("Google Drive Service initialized (Service Account).")

            // Auto-create folder if empty
            if (config.driveFolderId.isEmpty() || config.driveFolderId == "YOUR_DRIVE_FOLDER_ID") {
                createInitialFolder()
            }
        } catch (e: Exception) {
            logger.severe("Failed to initialize Google Drive Service: ${e.message}")
            config.driveEnabled = false
        }
    }

    private fun createServiceAccountTemplate(file: java.io.File) {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        
        val template = """
            {
              "type": "service_account",
              "project_id": "your-project-id",
              "private_key_id": "your-private-key-id",
              "private_key": "-----BEGIN PRIVATE KEY-----\nYOUR_PRIVATE_KEY_HERE\n-----END PRIVATE KEY-----\n",
              "client_email": "your-service-account@your-project-id.iam.gserviceaccount.com",
              "client_id": "your-client-id",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token",
              "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
              "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/your-service-account%40your-project-id.iam.gserviceaccount.com"
            }
        """.trimIndent()
        
        file.writeText(template)
    }

    private fun createInitialFolder() {
        val driveService = drive ?: return
        try {
            val serverName = plugin.server.name.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "Server" }
            val folderMetadata = File().apply {
                name = "Minecraft-$serverName"
                mimeType = "application/vnd.google-apps.folder"
            }
            
            val folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()
            
            val newId = folder.id
            config.saveFolderId(newId)
            logger.info("Auto-created Google Drive folder: Minecraft-$serverName (ID: $newId)")
        } catch (e: Exception) {
            logger.severe("Failed to auto-create Google Drive folder: ${e.message}")
        }
    }

    fun uploadFile(localFile: java.io.File, mimeType: String = "application/zip"): String? {
        val driveService = drive ?: return null
        
        return try {
            // Overwrite mode: find and delete old file with same prefix
            if (config.driveOverwrite && mimeType == "application/zip") {
                val safeFolderId = config.driveFolderId.replace("'", "\\'")
                val safePrefix = config.backupPrefix.replace("'", "\\'")
                val query = if (config.driveFolderId.isNotEmpty()) {
                    "'$safeFolderId' in parents and name contains '$safePrefix' and trashed = false"
                } else {
                    "name contains '$safePrefix' and trashed = false"
                }
                
                val result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .execute()
                
                result.files?.forEach { oldFile ->
                    driveService.files().delete(oldFile.id).execute()
                    logger.info("Overwrite mode: Deleted old file ${oldFile.name}")
                }
            }

            val fileMetadata = File().apply {
                name = localFile.name
                if (config.driveFolderId.isNotEmpty()) {
                    parents = listOf(config.driveFolderId)
                }
            }

            val mediaContent = FileContent(mimeType, localFile)
            val insert = driveService.files().create(fileMetadata, mediaContent)
            
            // Enable Resumable Upload
            val uploader = insert.mediaHttpUploader
            uploader.isDirectUploadEnabled = false
            uploader.chunkSize = MediaHttpUploader.MINIMUM_CHUNK_SIZE * 4 // 1MB chunks

            val uploadedFile = insert.setFields("id").execute()
            uploadedFile.id
        } catch (e: Exception) {
            logger.severe("Failed to upload file to Google Drive: ${e.message}")
            null
        }
    }

    fun cleanupOldBackups() {
        if (config.driveOverwrite) return // Only keep 1 file in overwrite mode
        
        val driveService = drive ?: return
        if (config.driveKeepCount <= 0) return

        try {
            val safeFolderId = config.driveFolderId.replace("'", "\\'")
            val safePrefix = config.backupPrefix.replace("'", "\\'")
            val query = if (config.driveFolderId.isNotEmpty()) {
                "'$safeFolderId' in parents and name contains '$safePrefix' and trashed = false"
            } else {
                "name contains '$safePrefix' and trashed = false"
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
