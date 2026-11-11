package com.aurviz.aiagent.domain.models

import java.util.UUID

enum class PlanStepStatus {
    PENDING,
    RUNNING,
    WAITING_CONFIRMATION,
    COMPLETED,
    FAILED
}

data class PlanStep(
    val id: String = UUID.randomUUID().toString(),
    val stepNumber: Int,
    val description: String,
    val toolName: String,
    val args: Map<String, Any?> = emptyMap(),
    var status: PlanStepStatus = PlanStepStatus.PENDING,
    var output: String? = null,
    var error: String? = null,
    var executionDurationMs: Long = 0L
)

data class ExecutionPlan(
    val id: String = UUID.randomUUID().toString(),
    val originalPrompt: String,
    val summaryGoal: String,
    val steps: List<PlanStep>,
    var isCompleted: Boolean = false,
    var totalLatencyMs: Long = 0L
)
