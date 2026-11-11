package com.aurviz.aiagent.domain

import android.util.Log
import com.aurviz.aiagent.data.api.FunctionCall
import com.aurviz.aiagent.data.api.GeminiApiClient
import com.aurviz.aiagent.data.api.GeminiApiResult
import com.aurviz.aiagent.data.local.memory.MemoryCategory
import com.aurviz.aiagent.data.local.memory.MemoryEntity
import com.aurviz.aiagent.data.local.memory.MemoryRepository
import com.aurviz.aiagent.data.local.security.SecurePreferences
import com.aurviz.aiagent.data.tools.ToolExecutionResult
import com.aurviz.aiagent.data.tools.ToolRegistry
import com.aurviz.aiagent.domain.models.AgentExecutionState
import com.aurviz.aiagent.domain.models.ExecutionPlan
import com.aurviz.aiagent.domain.models.PlanStep
import com.aurviz.aiagent.domain.models.PlanStepStatus
import com.aurviz.aiagent.domain.models.SensitiveAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AgentOrchestrator(
    private val geminiApiClient: GeminiApiClient,
    private val toolRegistry: ToolRegistry,
    private val memoryRepository: MemoryRepository,
    private val multiStepPlanner: MultiStepPlanner = MultiStepPlanner(),
    private val securePreferences: SecurePreferences? = null
) {

    private val _executionState = MutableStateFlow<AgentExecutionState>(AgentExecutionState.Idle)
    val executionState: StateFlow<AgentExecutionState> = _executionState.asStateFlow()

    private val _activePlan = MutableStateFlow<ExecutionPlan?>(null)
    val activePlan: StateFlow<ExecutionPlan?> = _activePlan.asStateFlow()

    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    suspend fun executeCommand(prompt: String): Unit = withContext(Dispatchers.IO) {
        val totalStartTime = System.currentTimeMillis()
        _executionState.value = AgentExecutionState.Thinking("Retrieving personal memories & context...")

        // Step 1: Memory Retrieval
        val relevantMemories = memoryRepository.getRelevantMemoriesForPrompt(prompt)
        val formattedMemoryContext = memoryRepository.formatMemoriesForContext(relevantMemories)

        _executionState.value = AgentExecutionState.Thinking("Reasoning and formulating execution plan...")

        // Build System Instruction with Persona & Memory
        val systemInstruction = buildString {
            appendLine("You are Aurviz Agent, an autonomous executive personal AI assistant.")
            appendLine("You understand natural language in English, Hindi, and Hinglish.")
            appendLine("You remember user preferences, key contacts, and routines, and execute real-world tasks via tools.")
            appendLine()
            appendLine(formattedMemoryContext)
            appendLine()
            appendLine("Crucial Operational Guidelines:")
            val currentUserName = securePreferences?.userName ?: "User"
            appendLine("1. Check personal memories before taking action (e.g. user name $currentUserName, contact Alice: alice.manager@aurviz.com).")
            appendLine("2. If user requests composing or sending an email, generate a complete, professional, and context-specific subject and body (never dummy placeholders).")
            appendLine("3. For recurring routines (e.g. 'Every morning at 9 AM'), call 'schedule_task' to set up the background WorkManager/AlarmManager schedule.")
            appendLine("4. When answering questions or confirming actions, be direct, friendly, and concise.")
        }

        // Try Gemini API first
        val geminiResult = geminiApiClient.generateContent(
            userPrompt = prompt,
            systemInstructionText = systemInstruction,
            toolDeclarations = toolRegistry.getAllFunctionDeclarations()
        )

        when (geminiResult) {
            is GeminiApiResult.Success -> {
                handleGeminiSuccess(prompt, geminiResult, totalStartTime)
            }
            is GeminiApiResult.Error -> {
                Log.w(TAG, "Gemini API unavailable (${geminiResult.message}), utilizing robust multi-step planner fallback.")
                handlePlannerFallback(prompt, formattedMemoryContext, totalStartTime)
            }
        }

        // Auto-extract and save any newly stated facts/preferences
        extractAndPersistImplicitMemories(prompt)
    }

    private suspend fun handleGeminiSuccess(
        prompt: String,
        result: GeminiApiResult.Success,
        totalStartTime: Long
    ) {
        if (result.functionCalls.isEmpty()) {
            // Pure conversational or memory explanation response
            val outcome = result.text ?: "Understood and processed."
            val totalLatency = System.currentTimeMillis() - totalStartTime
            val spoken = cleanForSpeech(outcome)
            _executionState.value = AgentExecutionState.Completed(
                outcomeSummary = outcome,
                spokenSummary = spoken,
                latencyMs = totalLatency
            )
            return
        }

        // Gemini generated tool calls -> translate into ExecutionPlan
        val steps = result.functionCalls.mapIndexed { index, fc ->
            PlanStep(
                stepNumber = index + 1,
                description = formatToolDescription(fc.name, fc.args),
                toolName = fc.name,
                args = fc.args ?: emptyMap()
            )
        }

        val plan = ExecutionPlan(
            originalPrompt = prompt,
            summaryGoal = "Execute AI Plan (${steps.size} step${if (steps.size > 1) "s" else ""})",
            steps = steps
        )
        _activePlan.value = plan
        executePlanSteps(plan, totalStartTime)
    }

    private fun formatToolDescription(toolName: String, args: Map<String, Any?>?): String {
        return when (toolName) {
            "send_email" -> {
                val action = args?.get("action")?.toString() ?: "send"
                if (action == "check_inbox") "Scan connected inbox for priority emails"
                else "Compose and dispatch email to ${args?.get("recipient") ?: "recipient"}"
            }
            "device_actions" -> {
                val action = args?.get("action")?.toString() ?: ""
                when (action) {
                    "call_phone" -> "Place phone call to ${args?.get("phone_number") ?: "number"}"
                    "send_whatsapp" -> "Dispatch WhatsApp message${if (args?.get("phone_number") != null && args["phone_number"].toString().isNotBlank()) " to ${args["phone_number"]}" else ""}"
                    "open_camera" -> "Launch device camera"
                    else -> "Execute device action"
                }
            }
            "schedule_task" -> "Register recurring task: ${args?.get("title") ?: "schedule"}"
            "web_automation" -> "Extract web briefing for query"
            "manage_memory" -> "Update personal memory layer"
            else -> "Execute tool '$toolName'"
        }
    }

    private suspend fun handlePlannerFallback(
        prompt: String,
        memoryContext: String,
        totalStartTime: Long
    ) {
        val plan = multiStepPlanner.createPlanForPrompt(prompt, memoryContext)
        _activePlan.value = plan
        executePlanSteps(plan, totalStartTime)
    }

    private suspend fun executePlanSteps(plan: ExecutionPlan, totalStartTime: Long) {
        val stepOutputs = mutableListOf<String>()

        for (i in plan.steps.indices) {
            val step = plan.steps[i]
            _executionState.value = AgentExecutionState.ExecutingPlan(plan, i)
            step.status = PlanStepStatus.RUNNING

            val tool = toolRegistry.getTool(step.toolName)
            if (tool == null) {
                step.status = PlanStepStatus.FAILED
                step.error = "Tool '${step.toolName}' is not registered."
                _executionState.value = AgentExecutionState.Error("Unknown tool: ${step.toolName}")
                return
            }

            // Check if this step requires explicit user confirmation
            if (tool.requiresConfirmation(step.args)) {
                val sensitiveAction = tool.getConfirmationDetails(step.args)
                if (sensitiveAction != null) {
                    step.status = PlanStepStatus.WAITING_CONFIRMATION

                    // Suspend and await user confirmation
                    val confirmed = awaitUserConfirmation(sensitiveAction)
                    if (!confirmed) {
                        step.status = PlanStepStatus.FAILED
                        step.error = "Action cancelled by user."
                        _executionState.value = AgentExecutionState.Completed(
                            outcomeSummary = "Action was explicitly cancelled by user.",
                            spokenSummary = "Email action was cancelled as requested.",
                            plan = plan,
                            latencyMs = System.currentTimeMillis() - totalStartTime
                        )
                        return
                    }
                }
            }

            // Execute the tool
            val stepStartTime = System.currentTimeMillis()
            val result = tool.execute(step.args)
            step.executionDurationMs = System.currentTimeMillis() - stepStartTime

            when (result) {
                is ToolExecutionResult.Success -> {
                    step.status = PlanStepStatus.COMPLETED
                    step.output = result.outputSummary
                    stepOutputs.add(result.outputSummary)

                    // If step was updating primary email, sync to securePreferences
                    if (step.toolName == "manage_memory" && step.args["key"] == "primary_email") {
                        val emailPattern = java.util.regex.Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
                        val matcher = emailPattern.matcher(step.args["content"]?.toString() ?: "")
                        if (matcher.find()) {
                            val extractedEmail = matcher.group(0)
                            if (extractedEmail != null) {
                                securePreferences?.userEmailAccount = extractedEmail
                            }
                        }
                    }
                }
                is ToolExecutionResult.Failure -> {
                    step.status = PlanStepStatus.FAILED
                    step.error = result.error
                    _executionState.value = AgentExecutionState.Error("Step failed: ${result.error}")
                    return
                }
                is ToolExecutionResult.NeedsUserConfirmation -> {
                    // Handled above
                }
            }
        }

        plan.isCompleted = true
        val totalDuration = System.currentTimeMillis() - totalStartTime
        plan.totalLatencyMs = totalDuration

        // Clean user-facing text for screen with polite English wrap-up
        val politeClosing = "\n\nTask completed successfully. Thank you for using Aurviz Agent! Is there anything else I can help you with today?"
        val primaryOutputs = if (stepOutputs.size > 1) {
            stepOutputs.filterNot { it.startsWith("Saved to personal memory", ignoreCase = true) }
        } else {
            stepOutputs
        }
        val cleanOutcome = (primaryOutputs.joinToString("\n\n").trim() + politeClosing).trim()

        // Clean, sweet conversational sentence for TTS (NO DEBUG LATENCY SPOKEN)
        val spoken = generateSpokenResponse(plan, primaryOutputs)

        _executionState.value = AgentExecutionState.Completed(
            outcomeSummary = cleanOutcome,
            spokenSummary = spoken,
            plan = plan,
            latencyMs = totalDuration
        )
    }

    private fun generateSpokenResponse(plan: ExecutionPlan, stepOutputs: List<String>): String {
        val toolsUsed = plan.steps.map { it.toolName }.distinct()
        val base = when {
            toolsUsed.contains("device_actions") -> {
                val devStep = plan.steps.firstOrNull { it.toolName == "device_actions" }
                when (devStep?.args?.get("action")?.toString()) {
                    "call_phone" -> "Phone call is being placed."
                    "send_whatsapp" -> "WhatsApp message has been prepared."
                    "open_camera" -> "Camera is opened."
                    "open_app" -> "Application opened successfully."
                    else -> "Device action executed."
                }
            }
            toolsUsed.contains("send_email") && toolsUsed.contains("schedule_task") ->
                "I've configured your recurring schedule and prepared the summary."
            toolsUsed.contains("send_email") -> {
                val emailStep = plan.steps.firstOrNull { it.toolName == "send_email" }
                val recipient = emailStep?.args?.get("recipient")?.toString() ?: "recipient"
                "Your email to $recipient is ready."
            }
            toolsUsed.contains("schedule_task") ->
                "Your recurring task has been scheduled successfully."
            toolsUsed.contains("web_automation") ->
                "I have browsed the website and prepared your briefing."
            toolsUsed.contains("manage_memory") ->
                "I have updated your personal memory."
            stepOutputs.isNotEmpty() ->
                cleanForSpeech(stepOutputs.first())
            else -> "Task completed successfully."
        }
        return "$base Thank you for using Aurviz Agent!"
    }

    private fun cleanForSpeech(raw: String): String {
        return raw.lines().firstOrNull { it.isNotBlank() }
            ?.replace(Regex("[#*_`\\[\\](){}]"), "")
            ?.replace(Regex("Task Execution Completed.*", RegexOption.IGNORE_CASE), "")
            ?.take(180)
            ?: "Understood and processed."
    }

    private suspend fun awaitUserConfirmation(action: SensitiveAction): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            _executionState.value = AgentExecutionState.AwaitingConfirmation(
                action = action,
                onConfirm = {
                    if (continuation.isActive) continuation.resume(true)
                },
                onCancel = {
                    if (continuation.isActive) continuation.resume(false)
                }
            )
        }

    /**
     * Autonomous background execution for WorkManager or Alarm triggers.
     */
    suspend fun executeAutonomousBackgroundPrompt(prompt: String): String = withContext(Dispatchers.IO) {
        val memories = memoryRepository.getRelevantMemoriesForPrompt(prompt)
        val memContext = memoryRepository.formatMemoriesForContext(memories)
        val plan = multiStepPlanner.createPlanForPrompt(prompt, memContext)

        val outputs = mutableListOf<String>()
        for (step in plan.steps) {
            val tool = toolRegistry.getTool(step.toolName) ?: continue
            val result = tool.execute(step.args)
            if (result is ToolExecutionResult.Success) {
                outputs.add(result.outputSummary)
            }
        }

        if (outputs.isEmpty()) {
            "Autonomous routine executed for: '$prompt'."
        } else {
            outputs.joinToString("\n")
        }
    }

    private suspend fun extractAndPersistImplicitMemories(prompt: String) {
        val lower = prompt.lowercase()

        // 1. Explicit Remember Pattern
        val rememberRegex = Regex("^(remember that|remember|please remember|yaad rakhna ki|yaad rakho ki)\\s*", RegexOption.IGNORE_CASE)
        if (rememberRegex.containsMatchIn(lower)) {
            val fact = prompt.replace(rememberRegex, "").trim()
            if (fact.isNotBlank()) {
                memoryRepository.saveMemory(
                    key = "user_memory_${System.currentTimeMillis() % 10000}",
                    content = fact,
                    category = if (lower.contains("prefer") || lower.contains("pasand")) MemoryCategory.PREFERENCE else MemoryCategory.FACT,
                    importance = 4
                )
            }
        }

        // 2. Auto-extract Email addresses mentioned in conversation
        val emailMatcher = java.util.regex.Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b").matcher(prompt)
        while (emailMatcher.find()) {
            val email = emailMatcher.group(0)
            if (email != null && !email.contains("example.com")) {
                memoryRepository.saveMemory(
                    key = "contact_email_${email.substringBefore("@").replace(".", "_")}",
                    content = "Email Contact: $email",
                    category = MemoryCategory.CONTACT,
                    importance = 3
                )
            }
        }

        // 3. Auto-extract Phone numbers mentioned in conversation
        val phoneMatcher = java.util.regex.Pattern.compile("(?:\\+?91[\\-\\s]?)?[6-9]\\d{9}").matcher(prompt)
        while (phoneMatcher.find()) {
            val phone = phoneMatcher.group(0)
            if (phone != null) {
                memoryRepository.saveMemory(
                    key = "contact_phone_${phone.takeLast(6)}",
                    content = "Phone Contact: $phone",
                    category = MemoryCategory.CONTACT,
                    importance = 3
                )
            }
        }
    }

    fun resetState() {
        _executionState.value = AgentExecutionState.Idle
        _activePlan.value = null
    }
}
