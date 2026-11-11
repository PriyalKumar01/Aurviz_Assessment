package com.aurviz.aiagent.data.tools

import com.aurviz.aiagent.data.api.FunctionDeclaration
import com.aurviz.aiagent.data.api.SchemaObject
import com.aurviz.aiagent.data.api.SchemaProperty
import com.aurviz.aiagent.data.local.memory.MemoryCategory
import com.aurviz.aiagent.data.local.memory.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryTool(
    private val memoryRepository: MemoryRepository
) : AgentTool {

    override val name: String = "manage_memory"
    override val description: String =
        "Persists user preferences, important facts, or contacts to the permanent memory layer, or retrieves stored memories."

    override fun toFunctionDeclaration(): FunctionDeclaration {
        return FunctionDeclaration(
            name = name,
            description = description,
            parameters = SchemaObject(
                type = "OBJECT",
                properties = mapOf(
                    "action" to SchemaProperty(
                        type = "STRING",
                        description = "Action to take: 'save', 'retrieve', 'delete', 'greeting'",
                        enum = listOf("save", "retrieve", "delete", "greeting")
                    ),
                    "key" to SchemaProperty(
                        type = "STRING",
                        description = "Short identifier for the memory (e.g. 'user_work_email', 'presentation_preference')"
                    ),
                    "content" to SchemaProperty(
                        type = "STRING",
                        description = "The fact, preference, or detail to remember"
                    ),
                    "category" to SchemaProperty(
                        type = "STRING",
                        description = "Category: 'PREFERENCE', 'FACT', 'CONTACT', 'TASK'",
                        enum = listOf("PREFERENCE", "FACT", "CONTACT", "TASK")
                    ),
                    "importance" to SchemaProperty(
                        type = "INTEGER",
                        description = "Importance level from 1 to 5 (5 is most critical)"
                    )
                ),
                required = listOf("action")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.toString() ?: "save"
        val key = args["key"]?.toString()?.trim() ?: "general_note"
        val content = args["content"]?.toString()?.trim() ?: ""
        val categoryStr = args["category"]?.toString() ?: "PREFERENCE"
        val importance = (args["importance"] as? Number)?.toInt() ?: 3

        val category = try {
            MemoryCategory.valueOf(categoryStr)
        } catch (e: Exception) {
            MemoryCategory.PREFERENCE
        }

        when (action) {
            "greeting" -> {
                ToolExecutionResult.Success(
                    outputSummary = "Hello! I am your Aurviz Personal AI Agent. I can help you compose and send emails, manage your personal memory, automate web tasks, and schedule recurring routines. What would you like to do?",
                    structuredData = mapOf("type" to "greeting")
                )
            }
            "save" -> {
                if (content.isBlank()) {
                    return@withContext ToolExecutionResult.Failure("Cannot save empty memory content.")
                }
                val saved = memoryRepository.saveMemory(
                    key = key,
                    content = content,
                    category = category,
                    importance = importance
                )
                val userFriendlySummary = if (key == "primary_email" || content.contains("@")) {
                    "Your primary email has been updated: $content"
                } else if (category == MemoryCategory.PREFERENCE) {
                    "Noted preference: \"$content\" saved to memory."
                } else {
                    "Saved to personal memory: $content"
                }

                ToolExecutionResult.Success(
                    outputSummary = userFriendlySummary,
                    structuredData = mapOf("memoryId" to saved.id, "key" to saved.key)
                )
            }
            "retrieve" -> {
                val query = key.ifBlank { content }
                val results = memoryRepository.searchMemories(query)
                val summary = if (results.isEmpty()) {
                    "I searched your personal memory for '$query' but didn't find any saved facts yet. You can ask me to remember details anytime!"
                } else {
                    "Found ${results.size} memory item(s):\n" + results.joinToString("\n") { "• ${it.content}" }
                }
                ToolExecutionResult.Success(
                    outputSummary = summary,
                    structuredData = mapOf("count" to results.size)
                )
            }
            "delete" -> {
                val targetKey = key.ifBlank { content }
                val matches = memoryRepository.searchMemories(targetKey)
                var deletedCount = 0
                for (m in matches) {
                    if (memoryRepository.deleteMemory(m.id)) {
                        deletedCount++
                    }
                }
                ToolExecutionResult.Success(
                    outputSummary = "Deleted $deletedCount memory item(s) matching '$targetKey'.",
                    structuredData = mapOf("deletedCount" to deletedCount)
                )
            }
            else -> ToolExecutionResult.Failure("Unknown memory action: $action")
        }
    }
}
