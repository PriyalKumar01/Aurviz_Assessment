package com.aurviz.aiagent.data.local.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemoryDao(private val dbProvider: () -> SQLiteDatabase) {

    private val _memoriesFlow = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val memoriesFlow: Flow<List<MemoryEntity>> = _memoriesFlow.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _memoriesFlow.value = getAll()
    }

    fun insert(memory: MemoryEntity) {
        val db = dbProvider()
        val values = ContentValues().apply {
            put("id", memory.id)
            put("key_name", memory.key)
            put("content", memory.content)
            put("category", memory.category.name)
            put("importance", memory.importance)
            put("created_at", memory.createdAt)
            put("last_accessed_at", memory.lastAccessedAt)
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refresh()
    }

    fun getAll(): List<MemoryEntity> {
        val db = dbProvider()
        val list = mutableListOf<MemoryEntity>()
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "importance DESC, created_at DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val keyCol = it.getColumnIndexOrThrow("key_name")
            val contentCol = it.getColumnIndexOrThrow("content")
            val catCol = it.getColumnIndexOrThrow("category")
            val impCol = it.getColumnIndexOrThrow("importance")
            val createCol = it.getColumnIndexOrThrow("created_at")
            val accessCol = it.getColumnIndexOrThrow("last_accessed_at")

            while (it.moveToNext()) {
                val categoryStr = it.getString(catCol)
                val category = try {
                    MemoryCategory.valueOf(categoryStr)
                } catch (e: Exception) {
                    MemoryCategory.PREFERENCE
                }

                list.add(
                    MemoryEntity(
                        id = it.getString(idCol),
                        key = it.getString(keyCol),
                        content = it.getString(contentCol),
                        category = category,
                        importance = it.getInt(impCol),
                        createdAt = it.getLong(createCol),
                        lastAccessedAt = it.getLong(accessCol)
                    )
                )
            }
        }
        return list
    }

    fun getById(id: String): MemoryEntity? {
        val db = dbProvider()
        val cursor = db.query(
            TABLE_NAME,
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val idCol = it.getColumnIndexOrThrow("id")
                val keyCol = it.getColumnIndexOrThrow("key_name")
                val contentCol = it.getColumnIndexOrThrow("content")
                val catCol = it.getColumnIndexOrThrow("category")
                val impCol = it.getColumnIndexOrThrow("importance")
                val createCol = it.getColumnIndexOrThrow("created_at")
                val accessCol = it.getColumnIndexOrThrow("last_accessed_at")

                val category = try {
                    MemoryCategory.valueOf(it.getString(catCol))
                } catch (e: Exception) {
                    MemoryCategory.PREFERENCE
                }

                return MemoryEntity(
                    id = it.getString(idCol),
                    key = it.getString(keyCol),
                    content = it.getString(contentCol),
                    category = category,
                    importance = it.getInt(impCol),
                    createdAt = it.getLong(createCol),
                    lastAccessedAt = it.getLong(accessCol)
                )
            }
        }
        return null
    }

    fun search(query: String): List<MemoryEntity> {
        val db = dbProvider()
        val list = mutableListOf<MemoryEntity>()
        val wildQuery = "%$query%"
        val cursor = db.query(
            TABLE_NAME,
            null,
            "key_name LIKE ? OR content LIKE ? OR category LIKE ?",
            arrayOf(wildQuery, wildQuery, wildQuery),
            null,
            null,
            "importance DESC, created_at DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val keyCol = it.getColumnIndexOrThrow("key_name")
            val contentCol = it.getColumnIndexOrThrow("content")
            val catCol = it.getColumnIndexOrThrow("category")
            val impCol = it.getColumnIndexOrThrow("importance")
            val createCol = it.getColumnIndexOrThrow("created_at")
            val accessCol = it.getColumnIndexOrThrow("last_accessed_at")

            while (it.moveToNext()) {
                val category = try {
                    MemoryCategory.valueOf(it.getString(catCol))
                } catch (e: Exception) {
                    MemoryCategory.PREFERENCE
                }

                list.add(
                    MemoryEntity(
                        id = it.getString(idCol),
                        key = it.getString(keyCol),
                        content = it.getString(contentCol),
                        category = category,
                        importance = it.getInt(impCol),
                        createdAt = it.getLong(createCol),
                        lastAccessedAt = it.getLong(accessCol)
                    )
                )
            }
        }
        return list
    }

    fun updateLastAccessed(id: String) {
        val db = dbProvider()
        val values = ContentValues().apply {
            put("last_accessed_at", System.currentTimeMillis())
        }
        db.update(TABLE_NAME, values, "id = ?", arrayOf(id))
    }

    fun delete(id: String): Boolean {
        val db = dbProvider()
        val rows = db.delete(TABLE_NAME, "id = ?", arrayOf(id))
        refresh()
        return rows > 0
    }

    fun deleteAll() {
        val db = dbProvider()
        db.delete(TABLE_NAME, null, null)
        refresh()
    }

    companion object {
        const val TABLE_NAME = "memories"
        const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id TEXT PRIMARY KEY,
                key_name TEXT NOT NULL,
                content TEXT NOT NULL,
                category TEXT NOT NULL,
                importance INTEGER NOT NULL DEFAULT 3,
                created_at INTEGER NOT NULL,
                last_accessed_at INTEGER NOT NULL
            );
        """
    }
}
