package com.aurviz.aiagent.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aurviz.aiagent.AurvizApp

class ExactAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ScheduledTaskWorker.KEY_TASK_ID) ?: return
        Log.i("ExactAlarmReceiver", "Exact alarm fired for taskId: $taskId")

        val app = context.applicationContext as? AurvizApp ?: return
        app.agentWorkManager.triggerTaskImmediately(taskId)
    }
}
