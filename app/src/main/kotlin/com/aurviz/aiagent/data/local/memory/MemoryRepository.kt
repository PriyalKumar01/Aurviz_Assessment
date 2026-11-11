package com.aurviz.aiagent.data.local.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<MemoryEntity>> = memoryDao.memoriesFlow

    suspend fun saveMemory(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.PREFERENCE,
        importance: Int = 3
    ): MemoryEntity = withContext(Dispatchers.IO) {
        val memory = MemoryEntity(
            key = key.trim(),
            content = content.trim(),
            category = category,
            importance = importance
        )
        memoryDao.insert(memory)
        memory
    }

    suspend fun getAllMemories(): List<MemoryEntity> = withContext(Dispatchers.IO) {
        memoryDao.getAll()
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            memoryDao.getAll()
        } else {
            memoryDao.search(query.trim())
        }
    }

    suspend fun deleteMemory(id: String): Boolean = withContext(Dispatchers.IO) {
        memoryDao.delete(id)
    }

    suspend fun clearAllMemories(): Unit = withContext(Dispatchers.IO) {
        memoryDao.deleteAll()
    }

    /**
     * Retrieve relevant memories given a natural language prompt.
     * Uses tokenization, key matching, category relevance, and recency/importance scoring.
     */
    suspend fun getRelevantMemoriesForPrompt(prompt: String, maxLimit: Int = 5): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            val all = memoryDao.getAll()
            if (all.isEmpty()) return@withContext emptyList()

            val normalizedPrompt = prompt.lowercase()
            val tokens = normalizedPrompt
                .split(Regex("[^a-zA-Z0-9_@.-]+"))
                .filter { it.length > 2 }
                .toSet()

            val scored = all.map { memory ->
                var score = 0.0

                // Importance base weight (1..5 -> 1.0..5.0)
                score += memory.importance.toDouble()

                // Key exact match in prompt
                val memKey = memory.key.lowercase()
                if (normalizedPrompt.contains(memKey)) {
                    score += 10.0
                }

                // Content token overlap
                val contentTokens = memory.content.lowercase()
                    .split(Regex("[^a-zA-Z0-9_@.-]+"))
                    .filter { it.length > 2 }

                val tokenMatches = contentTokens.count { token -> tokens.contains(token) }
                score += (tokenMatches * 3.0)

                // Category boosts
                if (normalizedPrompt.contains("email") || normalizedPrompt.contains("mail")) {
                    if (memory.category == MemoryCategory.CONTACT || memory.key.contains("email", true)) {
                        score += 8.0
                    }
                }
                if (normalizedPrompt.contains("schedule") || normalizedPrompt.contains("morning") || normalizedPrompt.contains("remind")) {
                    if (memory.category == MemoryCategory.TASK || memory.category == MemoryCategory.PREFERENCE) {
                        score += 5.0
                    }
                }

                // Recency bonus: slightly favor recently accessed/created memories
                val ageHours = (System.currentTimeMillis() - memory.lastAccessedAt) / (1000 * 60 * 60)
                if (ageHours < 24) {
                    score += 2.0
                }

                Pair(memory, score)
            }

            // Filter to items with relevant score or fallback to top important if prompt is generic
            val topMemories = scored
                .filter { it.second >= 3.0 }
                .sortedByDescending { it.second }
                .take(maxLimit)
                .map {
                    memoryDao.updateLastAccessed(it.first.id)
                    it.first
                }

            topMemories
        }

    /**
     * Formats retrieved memories into a structured context string to be injected into LLM prompt.
     */
    fun formatMemoriesForContext(memories: List<MemoryEntity>): String {
        if (memories.isEmpty()) return "No specific prior personal memories found."
        return buildString {
            appendLine("=== USER PERSONAL MEMORY LAYER ===")
            memories.forEach { mem ->
                appendLine("- [${mem.category.name}] ${mem.key}: ${mem.content}")
            }
            appendLine("===================================")
        }
    }
}
