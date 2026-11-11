package com.aurviz.aiagent.data.local.tasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

class ScheduledTasksRepository(
    private val scheduledTaskDao: ScheduledTaskDao,
    private val taskLogDao: TaskLogDao
) {
    val tasksFlow: Flow<List<ScheduledTaskEntity>> = scheduledTaskDao.tasksFlow
    val logsFlow: Flow<List<TaskLogEntity>> = taskLogDao.logsFlow

    suspend fun saveTask(task: ScheduledTaskEntity): ScheduledTaskEntity = withContext(Dispatchers.IO) {
        scheduledTaskDao.insertOrUpdate(task)
        task
    }

    suspend fun getTaskById(id: String): ScheduledTaskEntity? = withContext(Dispatchers.IO) {
        scheduledTaskDao.getById(id)
    }

    suspend fun getAllTasks(): List<ScheduledTaskEntity> = withContext(Dispatchers.IO) {
        scheduledTaskDao.getAll()
    }

    suspend fun getActiveTasks(): List<ScheduledTaskEntity> = withContext(Dispatchers.IO) {
        scheduledTaskDao.getActiveTasks()
    }

    suspend fun toggleTaskActive(id: String, active: Boolean) = withContext(Dispatchers.IO) {
        scheduledTaskDao.toggleTaskActive(id, active)
    }

    suspend fun deleteTask(id: String): Boolean = withContext(Dispatchers.IO) {
        scheduledTaskDao.delete(id)
    }

    suspend fun recordExecutionLog(
        taskId: String,
        taskTitle: String,
        status: TaskExecutionStatus,
        summary: String,
        detailedOutput: String,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        val log = TaskLogEntity(
            taskId = taskId,
            taskTitle = taskTitle,
            status = status,
            summaryResult = summary,
            detailedOutput = detailedOutput,
            durationMs = durationMs
        )
        taskLogDao.insert(log)

        // Calculate next execution time
        val task = scheduledTaskDao.getById(taskId)
        if (task != null) {
            val nextTime = calculateNextExecutionTime(task)
            scheduledTaskDao.updateExecutionTime(taskId, System.currentTimeMillis(), nextTime)
        }
    }

    suspend fun getLogsForTask(taskId: String): List<TaskLogEntity> = withContext(Dispatchers.IO) {
        taskLogDao.getLogsForTask(taskId)
    }

    suspend fun getAllLogs(): List<TaskLogEntity> = withContext(Dispatchers.IO) {
        taskLogDao.getAll()
    }

    /**
     * Calculates the next execution timestamp based on schedule frequency and target hour/minute.
     */
    fun calculateNextExecutionTime(task: ScheduledTaskEntity): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance()

        when (task.frequency) {
            ScheduleFrequency.DAILY -> {
                next.set(Calendar.HOUR_OF_DAY, task.targetHour)
                next.set(Calendar.MINUTE, task.targetMinute)
                next.set(Calendar.SECOND, 0)
                next.set(Calendar.MILLISECOND, 0)

                // If scheduled time has already passed today, advance to tomorrow
                if (next.timeInMillis <= now.timeInMillis) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            ScheduleFrequency.HOURLY -> {
                next.add(Calendar.HOUR_OF_DAY, 1)
            }
            ScheduleFrequency.CUSTOM_INTERVAL -> {
                val intervalMs = (task.intervalMinutes.coerceAtLeast(15L)) * 60 * 1000L
                next.timeInMillis = now.timeInMillis + intervalMs
            }
            ScheduleFrequency.ONCE -> {
                return task.nextExecutionTime
            }
        }
        return next.timeInMillis
    }
}
