package com.aurviz.aiagent.data.local.tasks

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScheduledTaskDao(private val dbProvider: () -> SQLiteDatabase) {

    private val _tasksFlow = MutableStateFlow<List<ScheduledTaskEntity>>(emptyList())
    val tasksFlow: Flow<List<ScheduledTaskEntity>> = _tasksFlow.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _tasksFlow.value = getAll()
    }

    fun insertOrUpdate(task: ScheduledTaskEntity) {
        val db = dbProvider()
        val values = ContentValues().apply {
            put("id", task.id)
            put("title", task.title)
            put("prompt", task.prompt)
            put("natural_instruction", task.naturalLanguageInstruction)
            put("frequency", task.frequency.name)
            put("interval_minutes", task.intervalMinutes)
            put("target_hour", task.targetHour)
            put("target_minute", task.targetMinute)
            put("next_execution_time", task.nextExecutionTime)
            put("last_executed_at", task.lastExecutedAt)
            put("is_active", if (task.isActive) 1 else 0)
            put("created_at", task.createdAt)
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refresh()
    }

    fun getAll(): List<ScheduledTaskEntity> {
        val db = dbProvider()
        val list = mutableListOf<ScheduledTaskEntity>()
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "next_execution_time ASC")
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val titleCol = it.getColumnIndexOrThrow("title")
            val promptCol = it.getColumnIndexOrThrow("prompt")
            val instCol = it.getColumnIndexOrThrow("natural_instruction")
            val freqCol = it.getColumnIndexOrThrow("frequency")
            val intCol = it.getColumnIndexOrThrow("interval_minutes")
            val hourCol = it.getColumnIndexOrThrow("target_hour")
            val minCol = it.getColumnIndexOrThrow("target_minute")
            val nextCol = it.getColumnIndexOrThrow("next_execution_time")
            val lastCol = it.getColumnIndexOrThrow("last_executed_at")
            val activeCol = it.getColumnIndexOrThrow("is_active")
            val createCol = it.getColumnIndexOrThrow("created_at")

            while (it.moveToNext()) {
                val freq = try {
                    ScheduleFrequency.valueOf(it.getString(freqCol))
                } catch (e: Exception) {
                    ScheduleFrequency.DAILY
                }

                list.add(
                    ScheduledTaskEntity(
                        id = it.getString(idCol),
                        title = it.getString(titleCol),
                        prompt = it.getString(promptCol),
                        naturalLanguageInstruction = it.getString(instCol),
                        frequency = freq,
                        intervalMinutes = it.getLong(intCol),
                        targetHour = it.getInt(hourCol),
                        targetMinute = it.getInt(minCol),
                        nextExecutionTime = it.getLong(nextCol),
                        lastExecutedAt = if (it.isNull(lastCol)) null else it.getLong(lastCol),
                        isActive = it.getInt(activeCol) == 1,
                        createdAt = it.getLong(createCol)
                    )
                )
            }
        }
        return list
    }

    fun getById(id: String): ScheduledTaskEntity? {
        val db = dbProvider()
        val cursor = db.query(TABLE_NAME, null, "id = ?", arrayOf(id), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                val freq = try {
                    ScheduleFrequency.valueOf(it.getString(it.getColumnIndexOrThrow("frequency")))
                } catch (e: Exception) {
                    ScheduleFrequency.DAILY
                }
                val lastCol = it.getColumnIndexOrThrow("last_executed_at")
                return ScheduledTaskEntity(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    prompt = it.getString(it.getColumnIndexOrThrow("prompt")),
                    naturalLanguageInstruction = it.getString(it.getColumnIndexOrThrow("natural_instruction")),
                    frequency = freq,
                    intervalMinutes = it.getLong(it.getColumnIndexOrThrow("interval_minutes")),
                    targetHour = it.getInt(it.getColumnIndexOrThrow("target_hour")),
                    targetMinute = it.getInt(it.getColumnIndexOrThrow("target_minute")),
                    nextExecutionTime = it.getLong(it.getColumnIndexOrThrow("next_execution_time")),
                    lastExecutedAt = if (it.isNull(lastCol)) null else it.getLong(lastCol),
                    isActive = it.getInt(it.getColumnIndexOrThrow("is_active")) == 1,
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return null
    }

    fun getActiveTasks(): List<ScheduledTaskEntity> {
        return getAll().filter { it.isActive }
    }

    fun updateExecutionTime(id: String, lastExecuted: Long, nextExecution: Long) {
        val db = dbProvider()
        val values = ContentValues().apply {
            put("last_executed_at", lastExecuted)
            put("next_execution_time", nextExecution)
        }
        db.update(TABLE_NAME, values, "id = ?", arrayOf(id))
        refresh()
    }

    fun toggleTaskActive(id: String, active: Boolean) {
        val db = dbProvider()
        val values = ContentValues().apply {
            put("is_active", if (active) 1 else 0)
        }
        db.update(TABLE_NAME, values, "id = ?", arrayOf(id))
        refresh()
    }

    fun delete(id: String): Boolean {
        val db = dbProvider()
        val rows = db.delete(TABLE_NAME, "id = ?", arrayOf(id))
        refresh()
        return rows > 0
    }

    companion object {
        const val TABLE_NAME = "scheduled_tasks"
        const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                prompt TEXT NOT NULL,
                natural_instruction TEXT NOT NULL,
                frequency TEXT NOT NULL,
                interval_minutes INTEGER NOT NULL DEFAULT 1440,
                target_hour INTEGER NOT NULL DEFAULT 9,
                target_minute INTEGER NOT NULL DEFAULT 0,
                next_execution_time INTEGER NOT NULL,
                last_executed_at INTEGER,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            );
        """
    }
}
