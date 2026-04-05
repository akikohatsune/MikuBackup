package miku.hatsuneakiko.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import miku.hatsuneakiko.backup.command.CommandHandler
import miku.hatsuneakiko.backup.config.ConfigManager
import miku.hatsuneakiko.backup.manager.BackupManager
import miku.hatsuneakiko.backup.scheduler.SchedulerService
import miku.hatsuneakiko.backup.service.DriveService
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class MikuBackup : JavaPlugin() {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    lateinit var configManager: ConfigManager
    lateinit var driveService: DriveService
    lateinit var backupManager: BackupManager
    lateinit var schedulerService: SchedulerService

    override fun onEnable() {
        // Miku Lyrics - Loading line by line
        val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        val lyrics = listOf(
            "<aqua>why you gotta go</aqua>",
            "<aqua>but don't even spare that much</aqua>",
            "<aqua>why you don't tell me</aqua>",
            "<aqua>have you ever needed it when you leave</aqua>",
            "<aqua>I can't see your heart when you move your act</aqua>",
            "<aqua>and I never thought you could come back</aqua>",
            "<aqua>the dot rain me like dot</aqua>",
            "<aqua>eyes locked</aqua>",
            "<aqua>like rain</aqua>",
            "<aqua>What won't change me like dot</aqua>",
            "<aqua>like rain</aqua>",
            "<aqua>like you</aqua>",
            "<aqua>The dot stains me like drop</aqua>",
            "<aqua>eyes locked</aqua>",
            "<aqua>like rain</aqua>",
            "<aqua>What won't change me like rain</aqua>",
            "<aqua>like rain me red light</aqua>",
            "<aqua>she apart</aqua>",
            ""
        )

        lyrics.forEach { line ->
            server.consoleSender.sendMessage(mm.deserialize(line))
            try { Thread.sleep(100) } catch (e: InterruptedException) { }
        }

        // Initialize Config
        configManager = ConfigManager(this)

        // Warn about legacy OAuth
        checkForLegacyOAuth()

        // Initialize Services
        driveService = DriveService(this, configManager, logger)
        backupManager = BackupManager(this, configManager, driveService, logger)
        
        // Initialize Scheduler
        schedulerService = SchedulerService(pluginScope, configManager, backupManager, logger)
        
        // Finalize Initialization (Async)
        pluginScope.launch {
            driveService.initialize()
            schedulerService.start()
        }

        // Register Command
        val handler = CommandHandler(this, pluginScope, configManager, driveService, backupManager, schedulerService)
        getCommand("backup")?.let {
            it.setExecutor(handler)
            it.tabCompleter = handler
        }

        logger.info("MikuBackup enabled successfully.")
    }

    private fun checkForLegacyOAuth() {
        val legacySecrets = File(dataFolder, "client_secrets.json")
        val legacyTokens = File(dataFolder, "tokens")
        if (legacySecrets.exists() || legacyTokens.exists()) {
            logger.warning("Lưu ý: Miku đã phát hiện các file OAuth cũ (client_secrets.json hoặc thư mục tokens).")
            logger.warning("Phiên bản này sử dụng Service Account. Bạn có thể xóa các file/thư mục này.")
        }
    }

    override fun onDisable() {
        schedulerService.stop()
        pluginScope.cancel()
        logger.info("MikuBackup disabled.")
    }
}
