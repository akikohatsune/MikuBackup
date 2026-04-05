package me.miku.backup.scheduler

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kotlinx.coroutines.*
import me.miku.backup.config.ConfigManager
import me.miku.backup.manager.BackupManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class SchedulerService(
    private val scope: CoroutineScope,
    private val config: ConfigManager,
    private val backupManager: BackupManager,
    private val logger: Logger
) {
    private var job: Job? = null
    private val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
    private val parser = CronParser(cronDefinition)

    fun start() {
        stop()
        job = scope.launch {
            while (isActive) {
                val nextRun = calculateNextRun() ?: break
                val delayMs = Duration.between(ZonedDateTime.now(), nextRun).toMillis()

                if (delayMs > 0) {
                    logger.info("Next backup scheduled for: ${nextRun.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                    delay(delayMs)
                }

                if (isActive) {
                    backupManager.runBackup()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun calculateNextRun(): ZonedDateTime? {
        return try {
            when (config.mode.uppercase()) {
                "FIXED_INTERVAL" -> {
                    ZonedDateTime.now().plusMinutes(config.intervalMinutes)
                }
                "DAILY_TIME" -> {
                    val now = LocalTime.now()
                    val times = config.dailyTimes.map { LocalTime.parse(it) }.sorted()
                    val nextTime = times.firstOrNull { it.isAfter(now) } ?: times.first()
                    
                    var nextRun = ZonedDateTime.now().with(nextTime).withSecond(0).withNano(0)
                    if (nextRun.isBefore(ZonedDateTime.now())) {
                        nextRun = nextRun.plusDays(1)
                    }
                    nextRun
                }
                "CRON" -> {
                    val cron = parser.parse(config.cronExpression)
                    val executionTime = ExecutionTime.forCron(cron)
                    executionTime.nextExecution(ZonedDateTime.now()).orElse(null)
                }
                else -> {
                    logger.severe("Invalid schedule mode: ${config.mode}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.severe("Failed to calculate next run: ${e.message}")
            null
        }
    }
}
