package com.aurviz.aiagent.data.local.tasks

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskLogDao(private val dbProvider: () -> SQLiteDatabase) {

    private val _logsFlow = MutableStateFlow<List<TaskLogEntity>>(emptyList())
    val logsFlow: Flow<List<TaskLogEntity>> = _logsFlow.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _logsFlow.value = getAll()
    }

    fun insert(log: TaskLogEntity) {
        val db = dbProvider()
        val values = ContentValues().apply {
            put("id", log.id)
            put("task_id", log.taskId)
            put("task_title", log.taskTitle)
            put("status", log.status.name)
            put("summary_result", log.summaryResult)
            put("detailed_output", log.detailedOutput)
            put("executed_at", log.executedAt)
            put("duration_ms", log.durationMs)
        }
        db.insert(TABLE_NAME, null, values)
        refresh()
    }

    fun getAll(limit: Int = 100): List<TaskLogEntity> {
        val db = dbProvider()
        val list = mutableListOf<TaskLogEntity>()
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "executed_at DESC",
            limit.toString()
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val taskCol = it.getColumnIndexOrThrow("task_id")
            val titleCol = it.getColumnIndexOrThrow("task_title")
            val statusCol = it.getColumnIndexOrThrow("status")
            val sumCol = it.getColumnIndexOrThrow("summary_result")
            val detCol = it.getColumnIndexOrThrow("detailed_output")
            val exeCol = it.getColumnIndexOrThrow("executed_at")
            val durCol = it.getColumnIndexOrThrow("duration_ms")

            while (it.moveToNext()) {
                val status = try {
                    TaskExecutionStatus.valueOf(it.getString(statusCol))
                } catch (e: Exception) {
                    TaskExecutionStatus.SUCCESS
                }

                list.add(
                    TaskLogEntity(
                        id = it.getString(idCol),
                        taskId = it.getString(taskCol),
                        taskTitle = it.getString(titleCol),
                        status = status,
                        summaryResult = it.getString(sumCol),
                        detailedOutput = it.getString(detCol),
                        executedAt = it.getLong(exeCol),
                        durationMs = it.getLong(durCol)
                    )
                )
            }
        }
        return list
    }

    fun getLogsForTask(taskId: String): List<TaskLogEntity> {
        val db = dbProvider()
        val list = mutableListOf<TaskLogEntity>()
        val cursor = db.query(
            TABLE_NAME,
            null,
            "task_id = ?",
            arrayOf(taskId),
            null,
            null,
            "executed_at DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val taskCol = it.getColumnIndexOrThrow("task_id")
            val titleCol = it.getColumnIndexOrThrow("task_title")
            val statusCol = it.getColumnIndexOrThrow("status")
            val sumCol = it.getColumnIndexOrThrow("summary_result")
            val detCol = it.getColumnIndexOrThrow("detailed_output")
            val exeCol = it.getColumnIndexOrThrow("executed_at")
            val durCol = it.getColumnIndexOrThrow("duration_ms")

            while (it.moveToNext()) {
                val status = try {
                    TaskExecutionStatus.valueOf(it.getString(statusCol))
                } catch (e: Exception) {
                    TaskExecutionStatus.SUCCESS
                }

                list.add(
                    TaskLogEntity(
                        id = it.getString(idCol),
                        taskId = it.getString(taskCol),
                        taskTitle = it.getString(titleCol),
                        status = status,
                        summaryResult = it.getString(sumCol),
                        detailedOutput = it.getString(detCol),
                        executedAt = it.getLong(exeCol),
                        durationMs = it.getLong(durCol)
                    )
                )
            }
        }
        return list
    }

    companion object {
        const val TABLE_NAME = "task_execution_logs"
        const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                task_title TEXT NOT NULL,
                status TEXT NOT NULL,
                summary_result TEXT NOT NULL,
                detailed_output TEXT NOT NULL,
                executed_at INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0
            );
        """
    }
}
