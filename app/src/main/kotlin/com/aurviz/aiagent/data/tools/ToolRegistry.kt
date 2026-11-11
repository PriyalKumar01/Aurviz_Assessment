package com.aurviz.aiagent.data.tools

import com.aurviz.aiagent.data.api.FunctionDeclaration

class ToolRegistry(
    val emailTool: EmailTool,
    val webAutomationTool: WebAutomationTool,
    val schedulerTool: SchedulerTool,
    val memoryTool: MemoryTool,
    val deviceActionTool: DeviceActionTool
) {
    private val toolMap: Map<String, AgentTool> = listOf(
        emailTool,
        webAutomationTool,
        schedulerTool,
        memoryTool,
        deviceActionTool
    ).associateBy { it.name }

    fun getAllTools(): List<AgentTool> = toolMap.values.toList()

    fun getTool(name: String): AgentTool? = toolMap[name]

    fun getAllFunctionDeclarations(): List<FunctionDeclaration> {
        return toolMap.values.map { it.toFunctionDeclaration() }
    }
}
