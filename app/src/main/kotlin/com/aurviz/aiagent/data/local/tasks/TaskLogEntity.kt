package com.aurviz.aiagent.data.local.tasks

import java.util.UUID

enum class TaskExecutionStatus {
    SUCCESS,
    FAILED,
    IN_PROGRESS,
    CANCELLED
}

data class TaskLogEntity(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val taskTitle: String,
    val status: TaskExecutionStatus,
    val summaryResult: String,
    val detailedOutput: String,
    val executedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)
