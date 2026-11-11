package com.aurviz.aiagent.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aurviz.aiagent.data.local.tasks.ScheduleFrequency
import com.aurviz.aiagent.data.local.tasks.ScheduledTaskEntity
import java.util.concurrent.TimeUnit

class AgentWorkManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AgentWorkManager"
        const val ACTION_EXACT_ALARM = "com.aurviz.aiagent.EXACT_ALARM_TRIGGER"
    }

    fun scheduleBackgroundTask(task: ScheduledTaskEntity) {
        if (!task.isActive) {
            cancelTask(task.id)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(ScheduledTaskWorker.KEY_TASK_ID, task.id)
            .build()

        val intervalMinutes = when (task.frequency) {
            ScheduleFrequency.HOURLY -> 60L
            ScheduleFrequency.CUSTOM_INTERVAL -> task.intervalMinutes.coerceAtLeast(15L)
            ScheduleFrequency.DAILY -> 1440L
            ScheduleFrequency.ONCE -> 0L
        }

        if (task.frequency == ScheduleFrequency.ONCE || intervalMinutes < 15L) {
            // One-time or immediate
            val initialDelay = (task.nextExecutionTime - System.currentTimeMillis()).coerceAtLeast(0L)
            val oneTimeRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag(task.id)
                .build()

            workManager.enqueueUniqueWork(
                "work_${task.id}",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        } else {
            // Periodic background execution
            val periodicRequest = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(task.id)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "periodic_${task.id}",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        // Also schedule exact AlarmManager trigger to guarantee prompt wakeups across battery saver / Doze constraints
        scheduleExactAlarm(task)
        Log.i(TAG, "Task '${task.title}' successfully scheduled in WorkManager & AlarmManager.")
    }

    private fun scheduleExactAlarm(task: ScheduledTaskEntity) {
        if (task.nextExecutionTime <= System.currentTimeMillis()) return

        val intent = Intent(context, ExactAlarmReceiver::class.java).apply {
            action = ACTION_EXACT_ALARM
            putExtra(ScheduledTaskWorker.KEY_TASK_ID, task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    task.nextExecutionTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    task.nextExecutionTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission not granted, relying on WorkManager: ${e.message}")
        }
    }

    fun triggerTaskImmediately(taskId: String) {
        val inputData = Data.Builder()
            .putString(ScheduledTaskWorker.KEY_TASK_ID, taskId)
            .build()

        val immediateRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(immediateRequest)
        Log.i(TAG, "Triggered immediate execution for task: $taskId")
    }

    fun cancelTask(taskId: String) {
        workManager.cancelUniqueWork("work_$taskId")
        workManager.cancelUniqueWork("periodic_$taskId")

        val intent = Intent(context, ExactAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
        Log.i(TAG, "Cancelled scheduled task: $taskId")
    }
}
