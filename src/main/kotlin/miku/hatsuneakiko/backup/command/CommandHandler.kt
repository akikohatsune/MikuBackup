package miku.hatsuneakiko.backup.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import miku.hatsuneakiko.backup.config.ConfigManager
import miku.hatsuneakiko.backup.manager.BackupManager
import miku.hatsuneakiko.backup.scheduler.SchedulerService
import miku.hatsuneakiko.backup.service.DriveService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
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
    private val driveService: DriveService,
    private val backupManager: BackupManager,
    private val scheduler: SchedulerService
) : CommandExecutor, TabCompleter {

    private var currentManualJob: Job? = null
    private val mm = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("mikubackup.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "mode" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("Sử dụng: /backup mode <overwrite|daily>", NamedTextColor.RED))
                    return true
                }
                when (args[1].lowercase()) {
                    "overwrite" -> {
                        config.setOverwriteMode(true)
                        sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&aĐã chuyển sang chế độ &eGHI ĐÈ &afile cũ.")))
                    }
                    "daily" -> {
                        config.setOverwriteMode(false)
                        sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&aĐã chuyển sang chế độ &eLƯU MỚI &amỗi lần sao lưu.")))
                    }
                    else -> sender.sendMessage(Component.text("Chế độ không hợp lệ!", NamedTextColor.RED))
                }
            }
            "ver" -> {
                sender.sendMessage(legacy.deserialize("&b&l&m-------&r &3&lMikuBackup &b&l&m-------"))
                sender.sendMessage(legacy.deserialize("&7» &fPhiên bản: &b${plugin.description.version}"))
                sender.sendMessage(legacy.deserialize("&7» &fTác giả: &e${plugin.description.authors.joinToString(", ")}"))
                sender.sendMessage(legacy.deserialize("&7» &fTrạng thái Drive: " + (if (config.driveEnabled) "&aĐang hoạt động" else "&cCưa bật")))
                sender.sendMessage(legacy.deserialize("&7» &fChế độ lưu: " + (if (config.driveOverwrite) "&eGhi đè (Overwrite)" else "&6Mỗi ngày (Daily)")))
                sender.sendMessage(legacy.deserialize("&b&l&m-----------------------"))
            }
            "run" -> {
                if (backupManager.isBackupRunning()) {
                    sender.sendMessage(Component.text("Một tác vụ sao lưu đang được thực hiện!", NamedTextColor.RED))
                    return true
                }
                sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize("&7Starting manual backup...")))
                currentManualJob = scope.launch {
                    backupManager.runBackup(manualSender = sender)
                }
            }
            "stop" -> {
                if (!backupManager.isBackupRunning()) {
                    sender.sendMessage(Component.text("Không có tác vụ sao lưu nào đang chạy.", NamedTextColor.YELLOW))
                } else {
                    currentManualJob?.cancel()
                    scheduler.stop()
                    sender.sendMessage(Component.text("Đã dừng tác vụ sao lưu và Scheduler.", NamedTextColor.RED))
                    sender.sendMessage(Component.text("Dùng /backup reload để bật lại Scheduler.", NamedTextColor.GRAY))
                }
            }
            "reload" -> {
                config.loadConfig()
                driveService.initialize()
                scheduler.start()
                sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize(config.messageReloaded)))
            }
            "next" -> {
                val next = scheduler.calculateNextRun()
                if (next != null) {
                    val timeStr = next.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    sender.sendMessage(legacy.deserialize(config.messagePrefix).append(legacy.deserialize(config.messageScheduled.replace("%time%", timeStr))))
                } else {
                    sender.sendMessage(Component.text("Failed to calculate next run.", NamedTextColor.RED))
                }
            }
            "test" -> {
                scope.launch {
                    backupManager.testBackup(sender)
                }
            }
            else -> sendHelp(sender)
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (!sender.hasPermission("mikubackup.admin")) return emptyList()
        
        if (args.size == 1) {
            return listOf("run", "stop", "reload", "next", "ver", "mode", "test").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].lowercase() == "mode") {
            return listOf("overwrite", "daily").filter { it.startsWith(args[1].lowercase()) }
        }
        return emptyList()
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("--- MikuBackup Help ---", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/backup test ", NamedTextColor.YELLOW).append(Component.text("- Kiểm tra kết nối Google Drive", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/backup run ", NamedTextColor.YELLOW).append(Component.text("- Chạy sao lưu ngay lập tức", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/backup stop ", NamedTextColor.YELLOW).append(Component.text("- Dừng tác vụ đang chạy & Scheduler", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/backup reload ", NamedTextColor.YELLOW).append(Component.text("- Reload configuration & Bật Scheduler", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/backup next ", NamedTextColor.YELLOW).append(Component.text("- Xem lịch chạy tiếp theo", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/backup mode <overwrite|daily> ", NamedTextColor.YELLOW).append(Component.text("- Chọn chế độ lưu trữ", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/backup ver ", NamedTextColor.YELLOW).append(Component.text("- Xem thông tin phiên bản", NamedTextColor.GRAY)))
    }
}
