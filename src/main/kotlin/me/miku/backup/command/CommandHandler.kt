package me.miku.backup.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.miku.backup.auth.GoogleAuthManager
import me.miku.backup.config.ConfigManager
import me.miku.backup.manager.BackupManager
import me.miku.backup.scheduler.SchedulerService
import me.miku.backup.service.DriveService
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import java.time.format.DateTimeFormatter

class CommandHandler(
    private val plugin: JavaPlugin,
    private val scope: CoroutineScope,
    private val config: ConfigManager,
    private val authManager: GoogleAuthManager,
    private val driveService: DriveService,
    private val backupManager: BackupManager,
    private val scheduler: SchedulerService
) : CommandExecutor, TabCompleter {

    private var currentManualJob: Job? = null

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
            "ver" -> {
                sender.sendMessage("${ChatColor.AQUA}${ChatColor.BOLD}MikuBackup Information")
                sender.sendMessage("${ChatColor.GRAY}Phiên bản: ${ChatColor.WHITE}${plugin.description.version}")
                sender.sendMessage("${ChatColor.GRAY}Tác giả: ${ChatColor.WHITE}${plugin.description.authors.joinToString(", ")}")
                sender.sendMessage("${ChatColor.GRAY}API Version: ${ChatColor.WHITE}${plugin.description.apiVersion}")
                sender.sendMessage("${ChatColor.AQUA}---")
            }
            "run" -> {
                if (backupManager.isBackupRunning()) {
                    sender.sendMessage("${ChatColor.RED}Một tác vụ sao lưu đang được thực hiện!")
                    return true
                }
                sender.sendMessage("${color(config.messagePrefix)}&7Starting manual backup...".translate())
                currentManualJob = scope.launch {
                    backupManager.runBackup(manualSender = sender)
                }
            }
            "stop" -> {
                if (!backupManager.isBackupRunning()) {
                    sender.sendMessage("${ChatColor.YELLOW}Không có tác vụ sao lưu nào đang chạy.")
                } else {
                    currentManualJob?.cancel("Manual stop")
                    scheduler.stop()
                    sender.sendMessage("${ChatColor.RED}Đã dừng tác vụ sao lưu và Scheduler.")
                    sender.sendMessage("${ChatColor.GRAY}Dùng /backup reload để bật lại Scheduler.")
                }
            }
            "reload" -> {
                config.loadConfig()
                authManager.reload()
                driveService.initialize()
                scheduler.start()
                sender.sendMessage("${color(config.messagePrefix)}${config.messageReloaded}".translate())
                if (!authManager.isReady) {
                    sender.sendMessage("${ChatColor.YELLOW}Cảnh báo: client_secrets.json chưa hợp lệ!")
                }
            }
            "login" -> {
                if (!authManager.isReady) {
                    sender.sendMessage("${ChatColor.RED}Lỗi: Bạn chưa cấu hình client_secrets.json! Hãy sửa file và dùng /backup reload.")
                    return true
                }
                val url = authManager.getAuthUrl()
                if (url != null) {
                    sender.sendMessage("${ChatColor.AQUA}Truy cập link sau để lấy mã xác thực:")
                    sender.sendMessage("${ChatColor.YELLOW}$url")
                    sender.sendMessage("${ChatColor.GRAY}Sau khi xác nhận, copy mã code trên URL và dùng /backup verify <mã>")
                }
            }
            "verify" -> {
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}Sử dụng: /backup verify <mã-code>")
                    return true
                }
                scope.launch {
                    try {
                        authManager.storeCode(args[1])
                        driveService.initialize()
                        sender.sendMessage("${ChatColor.GREEN}Liên kết thành công với Google Drive!")
                    } catch (e: Exception) {
                        sender.sendMessage("${ChatColor.RED}Xác thực thất bại: ${e.message}")
                    }
                }
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

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (!sender.hasPermission("mikubackup.admin")) return emptyList()
        
        if (args.size == 1) {
            return listOf("login", "verify", "run", "stop", "reload", "next", "ver").filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.AQUA}--- MikuBackup Help ---")
        sender.sendMessage("${ChatColor.YELLOW}/backup login ${ChatColor.GRAY}- Lấy link liên kết Google")
        sender.sendMessage("${ChatColor.YELLOW}/backup verify <code> ${ChatColor.GRAY}- Xác nhận mã code")
        sender.sendMessage("${ChatColor.YELLOW}/backup run ${ChatColor.GRAY}- Start manual backup")
        sender.sendMessage("${ChatColor.YELLOW}/backup stop ${ChatColor.GRAY}- Dừng tác vụ đang chạy & Scheduler")
        sender.sendMessage("${ChatColor.YELLOW}/backup reload ${ChatColor.GRAY}- Reload configuration & Bật Scheduler")
        sender.sendMessage("${ChatColor.YELLOW}/backup next ${ChatColor.GRAY}- Show next scheduled run time")
        sender.sendMessage("${ChatColor.YELLOW}/backup ver ${ChatColor.GRAY}- Xem thông tin phiên bản")
    }

    private fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)
    private fun String.translate(): String = ChatColor.translateAlternateColorCodes('&', this)
}
