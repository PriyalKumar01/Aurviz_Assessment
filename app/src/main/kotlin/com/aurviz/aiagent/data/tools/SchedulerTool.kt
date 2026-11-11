package com.aurviz.aiagent.data.tools

import com.aurviz.aiagent.data.api.FunctionDeclaration
import com.aurviz.aiagent.data.api.SchemaObject
import com.aurviz.aiagent.data.api.SchemaProperty
import com.aurviz.aiagent.data.local.tasks.ScheduleFrequency
import com.aurviz.aiagent.data.local.tasks.ScheduledTaskEntity
import com.aurviz.aiagent.data.local.tasks.ScheduledTasksRepository
import com.aurviz.aiagent.data.scheduler.AgentWorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.regex.Pattern

class SchedulerTool(
    private val scheduledTasksRepository: ScheduledTasksRepository,
    private val agentWorkManager: AgentWorkManager
) : AgentTool {

    override val name: String = "schedule_task"
    override val description: String =
        "Schedules recurring or one-time background tasks using cron-like instructions (e.g. 'Every morning at 9 AM', 'Every 2 hours', 'Daily at 18:00')."

    override fun toFunctionDeclaration(): FunctionDeclaration {
        return FunctionDeclaration(
            name = name,
            description = description,
            parameters = SchemaObject(
                type = "OBJECT",
                properties = mapOf(
                    "title" to SchemaProperty(
                        type = "STRING",
                        description = "Short descriptive title for the scheduled task"
                    ),
                    "instruction" to SchemaProperty(
                        type = "STRING",
                        description = "The task instruction to execute autonomously when triggered"
                    ),
                    "cron_schedule" to SchemaProperty(
                        type = "STRING",
                        description = "Natural language schedule instruction, e.g. 'Every morning at 9 AM', 'Daily at 09:00', 'Every 2 hours'"
                    ),
                    "frequency" to SchemaProperty(
                        type = "STRING",
                        description = "Schedule frequency: 'DAILY', 'HOURLY', 'CUSTOM_INTERVAL'",
                        enum = listOf("DAILY", "HOURLY", "CUSTOM_INTERVAL")
                    )
                ),
                required = listOf("title", "instruction", "cron_schedule")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult = withContext(Dispatchers.IO) {
        val title = args["title"]?.toString()?.trim() ?: "Scheduled Agent Task"
        val instruction = args["instruction"]?.toString()?.trim() ?: ""
        val scheduleStr = args["cron_schedule"]?.toString()?.trim() ?: "Every morning at 9 AM"

        if (instruction.isBlank()) {
            return@withContext ToolExecutionResult.Failure("Task instruction cannot be empty.")
        }

        val parsedSchedule = parseNaturalLanguageSchedule(scheduleStr)

        val taskEntity = ScheduledTaskEntity(
            title = title,
            prompt = instruction,
            naturalLanguageInstruction = scheduleStr,
            frequency = parsedSchedule.frequency,
            intervalMinutes = parsedSchedule.intervalMinutes,
            targetHour = parsedSchedule.targetHour,
            targetMinute = parsedSchedule.targetMinute,
            nextExecutionTime = parsedSchedule.firstExecutionTime,
            isActive = true
        )

        // Save in Room/SQLite
        val savedTask = scheduledTasksRepository.saveTask(taskEntity)

        // Register with Android WorkManager & AlarmManager
        agentWorkManager.scheduleBackgroundTask(savedTask)

        val timeFormatted = String.format("%02d:%02d", parsedSchedule.targetHour, parsedSchedule.targetMinute)
        val summary = buildString {
            appendLine("Successfully scheduled recurring task:")
            appendLine("- Title: $title")
            appendLine("- Frequency: ${parsedSchedule.frequency.name} (at $timeFormatted)")
            appendLine("- WorkManager Job ID: ${savedTask.id}")
            appendLine("- Status: Active & Registered for background execution.")
        }

        ToolExecutionResult.Success(
            outputSummary = summary,
            structuredData = mapOf(
                "taskId" to savedTask.id,
                "title" to title,
                "frequency" to parsedSchedule.frequency.name,
                "nextExecutionTime" to parsedSchedule.firstExecutionTime
            )
        )
    }

    data class ParsedSchedule(
        val frequency: ScheduleFrequency,
        val intervalMinutes: Long,
        val targetHour: Int,
        val targetMinute: Int,
        val firstExecutionTime: Long
    )

    private fun parseNaturalLanguageSchedule(input: String): ParsedSchedule {
        val lower = input.lowercase()
        var frequency = ScheduleFrequency.DAILY
        var intervalMinutes = 1440L
        var targetHour = 9
        var targetMinute = 0

        // Parse hour patterns e.g. "9 AM", "9:30 AM", "18:00", "7 pm"
        val timeRegex = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
        val matcher = timeRegex.matcher(lower)

        if (matcher.find()) {
            val rawHour = matcher.group(1)?.toIntOrNull() ?: 9
            val rawMin = matcher.group(2)?.toIntOrNull() ?: 0
            val ampm = matcher.group(3)?.lowercase()

            targetHour = when (ampm) {
                "pm" -> if (rawHour < 12) rawHour + 12 else rawHour
                "am" -> if (rawHour == 12) 0 else rawHour
                else -> rawHour
            }
            targetMinute = rawMin
        }

        if (lower.contains("hour") || lower.contains("every 2 hours") || lower.contains("hourly")) {
            frequency = ScheduleFrequency.HOURLY
            intervalMinutes = 60L
        } else if (lower.contains("minute")) {
            frequency = ScheduleFrequency.CUSTOM_INTERVAL
            intervalMinutes = 15L // Minimum WorkManager periodic interval
        } else {
            frequency = ScheduleFrequency.DAILY
            intervalMinutes = 1440L
        }

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return ParsedSchedule(
            frequency = frequency,
            intervalMinutes = intervalMinutes,
            targetHour = targetHour,
            targetMinute = targetMinute,
            firstExecutionTime = next.timeInMillis
        )
    }
}
