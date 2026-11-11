package com.aurviz.aiagent.data.local.memory

import java.util.UUID

enum class MemoryCategory {
    PREFERENCE,
    FACT,
    CONTACT,
    INTERACTION,
    TASK
}

data class MemoryEntity(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val content: String,
    val category: MemoryCategory = MemoryCategory.PREFERENCE,
    val importance: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)
