package com.aurviz.aiagent.domain.models

sealed class AgentExecutionState {
    object Idle : AgentExecutionState()

    data class Listening(
        val rmsDb: Float = 0f,
        val partialTranscription: String = ""
    ) : AgentExecutionState()

    data class Thinking(
        val statusMessage: String = "Reasoning and consulting memory layer..."
    ) : AgentExecutionState()

    data class ExecutingPlan(
        val plan: ExecutionPlan,
        val currentStepIndex: Int
    ) : AgentExecutionState()

    data class AwaitingConfirmation(
        val action: SensitiveAction,
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit
    ) : AgentExecutionState()

    data class Completed(
        val outcomeSummary: String,
        val spokenSummary: String = outcomeSummary,
        val plan: ExecutionPlan? = null,
        val latencyMs: Long = 0L
    ) : AgentExecutionState()

    data class Error(
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : AgentExecutionState()
}
