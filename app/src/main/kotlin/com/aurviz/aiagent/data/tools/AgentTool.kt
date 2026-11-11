package com.aurviz.aiagent.data.tools

import com.aurviz.aiagent.data.api.FunctionDeclaration
import com.aurviz.aiagent.domain.models.SensitiveAction

sealed class ToolExecutionResult {
    data class Success(
        val outputSummary: String,
        val structuredData: Map<String, Any?> = emptyMap(),
        val liveViewPayload: Any? = null
    ) : ToolExecutionResult()

    data class NeedsUserConfirmation(
        val action: SensitiveAction
    ) : ToolExecutionResult()

    data class Failure(
        val error: String,
        val recoverable: Boolean = true
    ) : ToolExecutionResult()
}

interface AgentTool {
    val name: String
    val description: String

    fun toFunctionDeclaration(): FunctionDeclaration

    fun requiresConfirmation(args: Map<String, Any?>): Boolean = false

    fun getConfirmationDetails(args: Map<String, Any?>): SensitiveAction? = null

    suspend fun execute(args: Map<String, Any?>): ToolExecutionResult
}
