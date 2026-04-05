package me.miku.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.miku.backup.command.CommandHandler
import me.miku.backup.config.ConfigManager
import me.miku.backup.manager.BackupManager
import me.miku.backup.scheduler.SchedulerService
import me.miku.backup.service.DriveService
import org.bukkit.plugin.java.JavaPlugin

class MikuBackup : JavaPlugin() {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    lateinit var configManager: ConfigManager
    lateinit var driveService: DriveService
    lateinit var backupManager: BackupManager
    lateinit var schedulerService: SchedulerService

    override fun onEnable() {
        // Initialize Config
        configManager = ConfigManager(this)

        // Initialize Services
        driveService = DriveService(configManager, logger, dataFolder)
        backupManager = BackupManager(this, configManager, driveService, logger)
        
        // Initialize Scheduler
        schedulerService = SchedulerService(pluginScope, configManager, backupManager, logger)
        schedulerService.start()

        // Register Command
        getCommand("backup")?.setExecutor(CommandHandler(pluginScope, configManager, backupManager, schedulerService))

        logger.info("MikuBackup enabled successfully.")
    }

    override fun onDisable() {
        schedulerService.stop()
        pluginScope.cancel()
        logger.info("MikuBackup disabled.")
    }
}
