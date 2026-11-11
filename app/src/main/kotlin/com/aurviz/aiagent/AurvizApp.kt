package com.aurviz.aiagent

import android.app.Application
import com.aurviz.aiagent.data.api.GeminiApiClient
import com.aurviz.aiagent.data.api.NetworkMonitor
import com.aurviz.aiagent.data.local.AppDatabase
import com.aurviz.aiagent.data.local.memory.MemoryRepository
import com.aurviz.aiagent.data.local.security.SecurePreferences
import com.aurviz.aiagent.data.local.tasks.ScheduledTasksRepository
import com.aurviz.aiagent.data.scheduler.AgentWorkManager
import com.aurviz.aiagent.data.tools.DeviceActionTool
import com.aurviz.aiagent.data.tools.EmailTool
import com.aurviz.aiagent.data.tools.MemoryTool
import com.aurviz.aiagent.data.tools.SchedulerTool
import com.aurviz.aiagent.data.tools.ToolRegistry
import com.aurviz.aiagent.data.tools.WebAutomationTool
import com.aurviz.aiagent.domain.AgentOrchestrator
import com.aurviz.aiagent.domain.MultiStepPlanner
import com.aurviz.aiagent.domain.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AurvizApp : Application() {

    lateinit var database: AppDatabase private set
    lateinit var securePreferences: SecurePreferences private set
    lateinit var memoryRepository: MemoryRepository private set
    lateinit var scheduledTasksRepository: ScheduledTasksRepository private set
    lateinit var agentWorkManager: AgentWorkManager private set
    lateinit var geminiApiClient: GeminiApiClient private set
    lateinit var toolRegistry: ToolRegistry private set
    lateinit var agentOrchestrator: AgentOrchestrator private set
    lateinit var voiceManager: VoiceManager private set
    lateinit var networkMonitor: NetworkMonitor private set
    lateinit var webAutomationTool: WebAutomationTool private set

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        securePreferences = SecurePreferences(this)
        _isDarkMode.value = securePreferences.isDarkMode

        memoryRepository = MemoryRepository(database.memoryDao)
        scheduledTasksRepository = ScheduledTasksRepository(database.scheduledTaskDao, database.taskLogDao)
        agentWorkManager = AgentWorkManager(this)
        networkMonitor = NetworkMonitor(this)

        geminiApiClient = GeminiApiClient(
            apiKeyProvider = { securePreferences.geminiApiKey },
            modelProvider = { securePreferences.preferredGeminiModel }
        )

        val emailTool = EmailTool(this, securePreferences)
        webAutomationTool = WebAutomationTool(this)
        val schedulerTool = SchedulerTool(scheduledTasksRepository, agentWorkManager)
        val memoryTool = MemoryTool(memoryRepository)
        val deviceActionTool = DeviceActionTool(this, securePreferences)

        toolRegistry = ToolRegistry(
            emailTool = emailTool,
            webAutomationTool = webAutomationTool,
            schedulerTool = schedulerTool,
            memoryTool = memoryTool,
            deviceActionTool = deviceActionTool
        )

        agentOrchestrator = AgentOrchestrator(
            geminiApiClient = geminiApiClient,
            toolRegistry = toolRegistry,
            memoryRepository = memoryRepository,
            multiStepPlanner = MultiStepPlanner(securePreferences),
            securePreferences = securePreferences
        )

        voiceManager = VoiceManager(this)
    }

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        securePreferences.isDarkMode = newMode
    }

    override fun onTerminate() {
        super.onTerminate()
        voiceManager.destroy()
    }
}
