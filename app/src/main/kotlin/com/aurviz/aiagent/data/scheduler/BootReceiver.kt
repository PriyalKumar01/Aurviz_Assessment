package com.aurviz.aiagent.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aurviz.aiagent.AurvizApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("BootReceiver", "Device reboot detected. Rescheduling active AI agent tasks...")
            val app = context.applicationContext as? AurvizApp ?: return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val activeTasks = app.scheduledTasksRepository.getActiveTasks()
                    for (task in activeTasks) {
                        app.agentWorkManager.scheduleBackgroundTask(task)
                        Log.i("BootReceiver", "Rescheduled task: ${task.title}")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule tasks: ${e.message}", e)
                }
            }
        }
    }
}
