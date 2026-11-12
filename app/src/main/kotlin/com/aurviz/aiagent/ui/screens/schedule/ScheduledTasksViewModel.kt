package com.aurviz.aiagent.ui.screens.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.aiagent.AurvizApp
import com.aurviz.aiagent.data.local.tasks.ScheduledTaskEntity
import com.aurviz.aiagent.data.local.tasks.TaskLogEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduledTasksViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AurvizApp
    private val scheduledTasksRepository = app.scheduledTasksRepository
    private val agentWorkManager = app.agentWorkManager

    val tasks: StateFlow<List<ScheduledTaskEntity>> = scheduledTasksRepository.tasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<TaskLogEntity>> = scheduledTasksRepository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleTaskActive(taskId: String, active: Boolean) {
        viewModelScope.launch {
            scheduledTasksRepository.toggleTaskActive(taskId, active)
            val task = scheduledTasksRepository.getTaskById(taskId)
            if (task != null) {
                if (active) {
                    agentWorkManager.scheduleBackgroundTask(task)
                } else {
                    agentWorkManager.cancelTask(taskId)
                }
            }
        }
    }

    fun triggerTaskImmediately(taskId: String) {
        agentWorkManager.triggerTaskImmediately(taskId)
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            agentWorkManager.cancelTask(taskId)
            scheduledTasksRepository.deleteTask(taskId)
        }
    }
}
