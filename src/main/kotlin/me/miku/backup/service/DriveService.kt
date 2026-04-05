package me.miku.backup.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import me.miku.backup.auth.GoogleAuthManager
import me.miku.backup.config.ConfigManager
import java.util.logging.Logger

class DriveService(
    private val config: ConfigManager,
    private val authManager: GoogleAuthManager,
    private val logger: Logger
) {
    private var drive: Drive? = null

    fun initialize() {
        if (!config.driveEnabled || !authManager.isReady) return
        
        try {
            val credential = authManager.getCredential() ?: return

            drive = Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("MikuBackup").build()
            
            logger.info("Google Drive Service initialized (OAuth 2.0).")
        } catch (e: Exception) {
            logger.severe("Failed to initialize Google Drive Service: ${e.message}")
        }
    }

    fun uploadFile(localFile: java.io.File): String? {
        val driveService = drive ?: return null
        
        return try {
            // Nếu ở chế độ Overwrite, tìm file cũ có cùng prefix và xóa đi
            if (config.driveOverwrite) {
                val query = if (config.driveFolderId.isNotEmpty()) {
                    "'${config.driveFolderId}' in parents and name contains '${config.backupPrefix}' and trashed = false"
                } else {
                    "name contains '${config.backupPrefix}' and trashed = false"
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

            val fileMetadata = File()
            fileMetadata.name = localFile.name
            if (config.driveFolderId.isNotEmpty()) {
                fileMetadata.parents = listOf(config.driveFolderId)
            }

            val mediaContent = FileContent("application/zip", localFile)
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
        if (config.driveOverwrite) return // Không dọn dẹp nếu đang ở chế độ lưu đè (vì chỉ có 1 file)
        
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
