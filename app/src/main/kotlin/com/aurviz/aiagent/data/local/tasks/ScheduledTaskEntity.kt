package com.aurviz.aiagent.data.local.tasks

import java.util.UUID

enum class ScheduleFrequency {
    DAILY,
    HOURLY,
    CUSTOM_INTERVAL,
    ONCE
}

data class ScheduledTaskEntity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val naturalLanguageInstruction: String,
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,
    val intervalMinutes: Long = 1440L, // 24 hours default
    val targetHour: Int = 9,          // 9 AM default
    val targetMinute: Int = 0,
    val nextExecutionTime: Long = System.currentTimeMillis(),
    val lastExecutedAt: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
