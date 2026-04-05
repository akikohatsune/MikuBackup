package me.miku.backup.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.miku.backup.config.ConfigManager
import me.miku.backup.manager.BackupManager
import me.miku.backup.scheduler.SchedulerService
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import java.time.format.DateTimeFormatter

class CommandHandler(
    private val scope: CoroutineScope,
    private val config: ConfigManager,
    private val backupManager: BackupManager,
    private val scheduler: SchedulerService
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("mikubackup.admin")) {
            sender.sendMessage("${ChatColor.RED}No permission.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "run" -> {
                sender.sendMessage("${color(config.messagePrefix)}&7Starting manual backup...".translate())
                scope.launch {
                    backupManager.runBackup(manual = true)
                    sender.sendMessage("${color(config.messagePrefix)}&aBackup process finished.".translate())
                }
            }
            "reload" -> {
                config.loadConfig()
                scheduler.start()
                sender.sendMessage("${color(config.messagePrefix)}${config.messageReloaded}".translate())
            }
            "next" -> {
                val next = scheduler.calculateNextRun()
                if (next != null) {
                    val timeStr = next.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    sender.sendMessage("${color(config.messagePrefix)}${config.messageScheduled.replace("%time%", timeStr)}".translate())
                } else {
                    sender.sendMessage("${ChatColor.RED}Failed to calculate next run.")
                }
            }
            else -> sendHelp(sender)
        }

        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.AQUA}--- MikuBackup Help ---")
        sender.sendMessage("${ChatColor.YELLOW}/backup run ${ChatColor.GRAY}- Start manual backup")
        sender.sendMessage("${ChatColor.YELLOW}/backup reload ${ChatColor.GRAY}- Reload configuration")
        sender.sendMessage("${ChatColor.YELLOW}/backup next ${ChatColor.GRAY}- Show next scheduled run time")
    }

    private fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)
    private fun String.translate(): String = ChatColor.translateAlternateColorCodes('&', this)
}
