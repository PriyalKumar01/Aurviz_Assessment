package com.aurviz.aiagent.data.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurviz.aiagent.AurvizApp
import com.aurviz.aiagent.data.local.tasks.TaskExecutionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduledTaskWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val app = applicationContext as? AurvizApp ?: return@withContext Result.failure()

        val taskRepo = app.scheduledTasksRepository
        val orchestrator = app.agentOrchestrator
        val notificationHelper = NotificationHelper(applicationContext)

        val task = taskRepo.getTaskById(taskId)
        if (task == null || !task.isActive) {
            Log.w(TAG, "Task $taskId not found or inactive.")
            return@withContext Result.success()
        }

        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Executing background scheduled task: ${task.title} ('${task.prompt}')")

        try {
            // Run autonomous agent execution on the scheduled prompt
            val outcome = orchestrator.executeAutonomousBackgroundPrompt(task.prompt)
            val duration = System.currentTimeMillis() - startTime

            taskRepo.recordExecutionLog(
                taskId = task.id,
                taskTitle = task.title,
                status = TaskExecutionStatus.SUCCESS,
                summary = outcome.take(300),
                detailedOutput = outcome,
                durationMs = duration
            )

            notificationHelper.showTaskExecutionNotification(
                notificationId = task.id.hashCode(),
                title = "AI Agent: ${task.title}",
                message = outcome.take(200),
                isSuccess = true
            )

            Result.success()
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Background task execution failed: ${e.message}", e)

            taskRepo.recordExecutionLog(
                taskId = task.id,
                taskTitle = task.title,
                status = TaskExecutionStatus.FAILED,
                summary = "Execution failed: ${e.message}",
                detailedOutput = e.stackTraceToString(),
                durationMs = duration
            )

            notificationHelper.showTaskExecutionNotification(
                notificationId = task.id.hashCode(),
                title = "AI Agent Failed: ${task.title}",
                message = "Error: ${e.localizedMessage}",
                isSuccess = false
            )

            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val KEY_TASK_ID = "task_id"
    }
}
