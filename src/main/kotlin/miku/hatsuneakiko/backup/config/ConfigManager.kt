package miku.hatsuneakiko.backup.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {
    var mode: String = "CRON"
    var intervalMinutes: Long = 60
    var dailyTimes: List<String> = listOf("03:00")
    var cronExpression: String = "0 0 3 * * ?"

    var driveEnabled: Boolean = true
    var driveOverwrite: Boolean = false
    var serviceAccountPath: String = "service-account.json"
    var driveFolderId: String = ""
    var driveKeepCount: Int = 7

    var worlds: List<String> = listOf("world")
    var localPath: String = "backups"
    var localKeepCount: Int = 3
    var backupPrefix: String = "backup-"

    var messagePrefix: String = "&b[MikuBackup] &r"
    var messageSuccess: String = "&aBackup completed successfully in %duration%s. Size: %size%."
    var messageFailure: String = "&cBackup failed! Check console for details."
    var messageReloaded: String = "&aConfiguration reloaded."
    var messageScheduled: String = "&7Next backup scheduled for: &e%time%"
    var messageWarning: String = "&e[MikuBackup] &cServer will backup in 10 minutes! Expect some lag."

    init {
        loadConfig()
    }

    fun loadConfig() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config: FileConfiguration = plugin.config

        mode = config.getString("schedule.mode", "CRON") ?: "CRON"
        intervalMinutes = config.getLong("schedule.intervalMinutes", 60)
        dailyTimes = config.getStringList("schedule.dailyTimes")
        cronExpression = config.getString("schedule.cronExpression", "0 0 3 * * ?") ?: "0 0 3 * * ?"

        driveEnabled = config.getBoolean("google-drive.enabled", true)
        driveOverwrite = config.getBoolean("google-drive.overwrite-mode", false)
        serviceAccountPath = config.getString("google-drive.service-account-json", "service-account.json") ?: "service-account.json"
        driveFolderId = config.getString("google-drive.folder-id", "") ?: ""
        driveKeepCount = config.getInt("google-drive.keep-count", 7)

        worlds = config.getStringList("backup.worlds")
        localPath = config.getString("backup.local-path", "backups") ?: "backups"
        localKeepCount = config.getInt("backup.keep-count", 3)
        backupPrefix = config.getString("backup.prefix", "backup-") ?: "backup-"

        messagePrefix = config.getString("messages.prefix", "&b[MikuBackup] &r") ?: "&b[MikuBackup] &r"
        messageSuccess = config.getString("messages.success", "&aBackup completed successfully in %duration%s. Size: %size%.") ?: ""
        messageFailure = config.getString("messages.failure", "&cBackup failed! Check console for details.") ?: ""
        messageReloaded = config.getString("messages.reloaded", "&aConfiguration reloaded.") ?: ""
        messageScheduled = config.getString("messages.scheduled", "&7Next backup scheduled for: &e%time%") ?: ""
        messageWarning = config.getString("messages.warning", "&e[MikuBackup] &cServer will backup in 10 minutes! Expect some lag.") ?: ""
    }

    fun setOverwriteMode(enabled: Boolean) {
        this.driveOverwrite = enabled
        plugin.config.set("google-drive.overwrite-mode", enabled)
        plugin.saveConfig()
    }

    fun saveFolderId(folderId: String) {
        this.driveFolderId = folderId
        plugin.config.set("google-drive.folder-id", folderId)
        plugin.saveConfig()
    }
}
