package me.miku.backup.auth

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.DriveScopes
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files

class GoogleAuthManager(private val plugin: JavaPlugin) {
    private val JSON_FACTORY = GsonFactory.getDefaultInstance()
    private val SCOPES = listOf(DriveScopes.DRIVE_FILE)
    private val REDIRECT_URI = "http://localhost"
    
    private var flow: GoogleAuthorizationCodeFlow? = null
    var isReady = false
        private set

    init {
        reload()
    }

    fun reload() {
        try {
            val secretsFile = File(plugin.dataFolder, "client_secrets.json")
            
            // 1. Tạo template nếu chưa có
            if (!secretsFile.exists()) {
                createTemplate(secretsFile)
                isReady = false
                return
            }

            // 2. Kiểm tra xem người dùng đã sửa template chưa
            val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, 
                InputStreamReader(Files.newInputStream(secretsFile.toPath())))
            
            val clientId = clientSecrets.details.clientId
            if (clientId == null || clientId.contains("YOUR_CLIENT_ID")) {
                plugin.logger.warning("Vui lòng điền Client ID thật vào client_secrets.json!")
                isReady = false
                return
            }

            // 3. Khởi tạo Flow (Cho phép cập nhật nóng)
            val tokensDir = File(plugin.dataFolder, "tokens")
            flow = GoogleAuthorizationCodeFlow.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    clientSecrets,
                    SCOPES
                )
                .setDataStoreFactory(FileDataStoreFactory(tokensDir))
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build()

            isReady = true
            plugin.logger.info("Google Auth Manager đã sẵn sàng!")
        } catch (e: Exception) {
            plugin.logger.severe("Lỗi khi nạp Google Auth: ${e.message}")
            isReady = false
        }
    }

    private fun createTemplate(file: File) {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        val template = """
            {
              "installed": {
                "client_id": "YOUR_CLIENT_ID_HERE.apps.googleusercontent.com",
                "client_secret": "YOUR_CLIENT_SECRET_HERE",
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
                "redirect_uris": ["http://localhost"]
              }
            }
        """.trimIndent()
        file.writeText(template)
        plugin.logger.info("Đã tạo file client_secrets.json template.")
    }

    fun getAuthUrl(): String? = flow?.newAuthorizationUrl()?.setRedirectUri(REDIRECT_URI)?.build()

    suspend fun storeCode(code: String) {
        val response = flow?.newTokenRequest(code)?.setRedirectUri(REDIRECT_URI)?.execute()
        flow?.createAndStoreCredential(response, "user")
    }

    fun getCredential(): Credential? = flow?.loadCredential("user")

    suspend fun isAuthorized(): Boolean {
        if (!isReady) return false
        val credential = getCredential() ?: return false
        return credential.refreshToken != null || (credential.expiresInSeconds ?: 0L) > 60
    }
}
