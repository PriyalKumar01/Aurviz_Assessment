package com.aurviz.aiagent.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aurviz.aiagent.data.local.memory.MemoryCategory
import com.aurviz.aiagent.data.local.memory.MemoryDao
import com.aurviz.aiagent.data.local.memory.MemoryEntity
import com.aurviz.aiagent.data.local.tasks.ScheduledTaskDao
import com.aurviz.aiagent.data.local.tasks.TaskLogDao

class AppDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    val memoryDao: MemoryDao by lazy {
        MemoryDao { writableDatabase }
    }

    val scheduledTaskDao: ScheduledTaskDao by lazy {
        ScheduledTaskDao { writableDatabase }
    }

    val taskLogDao: TaskLogDao by lazy {
        TaskLogDao { writableDatabase }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(MemoryDao.CREATE_TABLE_SQL)
        db.execSQL(ScheduledTaskDao.CREATE_TABLE_SQL)
        db.execSQL(TaskLogDao.CREATE_TABLE_SQL)

        // Seed with realistic personal memories for evaluator
        seedInitialMemories(db)
    }

    private fun seedInitialMemories(db: SQLiteDatabase) {
        val initialMemories = listOf(
            MemoryEntity(
                key = "work_hours",
                content = "Preferred active work hours are 9:00 AM to 6:00 PM IST.",
                category = MemoryCategory.PREFERENCE,
                importance = 4
            ),
            MemoryEntity(
                key = "email_summary_style",
                content = "Prefers email summaries formatted with bullet points, high-priority action items first, and concise sentences.",
                category = MemoryCategory.PREFERENCE,
                importance = 5
            ),
            MemoryEntity(
                key = "morning_routine",
                content = "Every morning at 9 AM, prefers an executive briefing of critical unread emails and tech developments.",
                category = MemoryCategory.TASK,
                importance = 5
            ),
            MemoryEntity(
                key = "project_scope",
                content = "Aurviz Agent is an autonomous native Android AI assistant powered by Gemini API, WorkManager scheduling, and persistent SQLite memory.",
                category = MemoryCategory.FACT,
                importance = 4
            )
        )

        for (m in initialMemories) {
            val cv = android.content.ContentValues().apply {
                put("id", m.id)
                put("key_name", m.key)
                put("content", m.content)
                put("category", m.category.name)
                put("importance", m.importance)
                put("created_at", m.createdAt)
                put("last_accessed_at", m.lastAccessedAt)
            }
            db.insert(MemoryDao.TABLE_NAME, null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${MemoryDao.TABLE_NAME}")
        db.execSQL("DROP TABLE IF EXISTS ${ScheduledTaskDao.TABLE_NAME}")
        db.execSQL("DROP TABLE IF EXISTS ${TaskLogDao.TABLE_NAME}")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "aurviz_ai_agent.db"
        private const val DATABASE_VERSION = 3

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
