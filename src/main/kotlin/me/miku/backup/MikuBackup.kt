package me.miku.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.miku.backup.auth.GoogleAuthManager
import me.miku.backup.command.CommandHandler
import me.miku.backup.config.ConfigManager
import me.miku.backup.manager.BackupManager
import me.miku.backup.scheduler.SchedulerService
import me.miku.backup.service.DriveService
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin

class MikuBackup : JavaPlugin() {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    lateinit var configManager: ConfigManager
    lateinit var authManager: GoogleAuthManager
    lateinit var driveService: DriveService
    lateinit var backupManager: BackupManager
    lateinit var schedulerService: SchedulerService

    override fun onEnable() {
        // Miku Console Logo
        val mikuLogo = """
            ${ChatColor.AQUA}/＾7_
            ${ChatColor.AQUA}.　　　 　 　 　 　 ,'　/ /
            ${ChatColor.AQUA}　　　　　 　 　 　|　　/／ﾍ
            ${ChatColor.AQUA}　　　　　 　 　 　|　 /　 ／
            ${ChatColor.AQUA}　　　　　　　三　| 　 ／
            ${ChatColor.AQUA}　　　　 　 　-‐￢　 { 　 ミ　rへ ＿＿　 __
            ${ChatColor.AQUA}　＼ 　／ 　三　 L ｣　 ミ／: :　: : : : : Y: :ヽ
            ${ChatColor.AQUA}　 　 Ｘ　 ／　　 ..|　|　／: :.／ l: :.lヽ: : :ヽ: : :ヽ
            ${ChatColor.AQUA}　　/ 　/ .　　　　|　| //: :/ノ　 l: :　｀ヽヽ: ::',: : :'.,
            ${ChatColor.AQUA}　 ,′ ,＼＼　　 .|　|/ l: :/ ○　丶l ○ l: : :ﾊ: : : :',
            ${ChatColor.AQUA}　{　　 　 ＼＼　＿/: :l: :l＠　､_,､_, ＠ l: :/: :l: : : :.',
            ${ChatColor.AQUA}　―　 --　 ＼/:::::::＼ヽl.＼ 　ゝ._）　 /:/／ l: : : :..i
            ${ChatColor.AQUA}　　‐ ―　. 　 {:::::::::::::::::`Ｔ７  ＼‐‐‐＜　　　　ｌ: : : :. l
            ${ChatColor.AQUA}　　　 　　／ ／r──‐┼-/l／l／/ __ヽ　　l: : : : : l
            ${ChatColor.AQUA}　　 ヽ ／ ／ミ/: : : : : : | /:/: l: : : :Y:::::::l　　l: : : : : :.l
            ${ChatColor.AQUA}　　　　　　　　,': : : : : :.／ /: /: : : : l:::::::::l.　 l: : : : : : l
            ${ChatColor.AQUA}          >> Backup with Love by Miku <<          
        """.trimIndent()
        server.consoleSender.sendMessage(mikuLogo)

        // Initialize Config
        configManager = ConfigManager(this)

        // Initialize Auth
        authManager = GoogleAuthManager(this)

        // Initialize Services
        driveService = DriveService(configManager, authManager, logger)
        backupManager = BackupManager(this, configManager, driveService, logger)
        
        // Initialize Scheduler
        schedulerService = SchedulerService(pluginScope, configManager, backupManager, logger)
        
        // Finalize Initialization
        pluginScope.launch {
            if (authManager.isAuthorized()) {
                driveService.initialize()
                logger.info("Google Drive đã được liên kết thành công.")
            } else {
                logger.warning("CẢNH BÁO: Drive chưa được xác thực. Hãy dùng /backup login.")
            }
            schedulerService.start()
        }

        // Register Command
        val handler = CommandHandler(this, pluginScope, configManager, authManager, driveService, backupManager, schedulerService)
        getCommand("backup")?.let {
            it.setExecutor(handler)
            it.tabCompleter = handler
        }

        logger.info("MikuBackup enabled successfully.")
    }

    override fun onDisable() {
        schedulerService.stop()
        pluginScope.cancel()
        logger.info("MikuBackup disabled.")
    }
}
