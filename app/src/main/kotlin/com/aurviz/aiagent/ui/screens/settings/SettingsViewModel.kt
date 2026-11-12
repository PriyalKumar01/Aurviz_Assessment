package com.aurviz.aiagent.ui.screens.settings

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.aiagent.AurvizApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PerformanceMetrics(
    val allocatedMemoryMb: Long = 0L,
    val maxMemoryMb: Long = 0L,
    val freeMemoryMb: Long = 0L,
    val memoryUsagePercent: Int = 0,
    val averageGeminiLatencyMs: Long = 480L,
    val averageTaskExecutionLatencyMs: Long = 180L,
    val isNetworkOnline: Boolean = true
)

data class PermissionItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val permissionKey: String
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AurvizApp
    private val prefs = app.securePreferences
    private val networkMonitor = app.networkMonitor

    private val _apiKey = MutableStateFlow(prefs.geminiApiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _userName = MutableStateFlow(prefs.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(prefs.userEmailAccount)
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userPhone = MutableStateFlow(prefs.userPhoneNumber)
    val userPhone: StateFlow<String> = _userPhone.asStateFlow()

    private val _geminiModel = MutableStateFlow(prefs.preferredGeminiModel)
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(prefs.isVoiceTtsEnabled)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _requireConfirmation = MutableStateFlow(prefs.requireEmailConfirmation)
    val requireConfirmation: StateFlow<Boolean> = _requireConfirmation.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissions: StateFlow<List<PermissionItem>> = _permissions.asStateFlow()

    init {
        refreshPerformanceMetrics()
        refreshPermissions()
    }

    fun onApiKeyChanged(key: String) {
        _apiKey.value = key
        prefs.geminiApiKey = key
    }

    fun onUserNameChanged(name: String) {
        _userName.value = name
        prefs.userName = name
    }

    fun onUserEmailChanged(email: String) {
        _userEmail.value = email
        prefs.userEmailAccount = email
    }

    fun onUserPhoneChanged(phone: String) {
        _userPhone.value = phone
        prefs.userPhoneNumber = phone
    }

    fun saveUserProfile(name: String, email: String, phone: String) {
        onUserNameChanged(name)
        onUserEmailChanged(email)
        onUserPhoneChanged(phone)
        viewModelScope.launch {
            val memoryRepo = app.memoryRepository
            if (name.isNotBlank()) {
                memoryRepo.saveMemory("user_name", "User's preferred name is $name", com.aurviz.aiagent.data.local.memory.MemoryCategory.PREFERENCE, 5)
            }
            if (email.isNotBlank()) {
                memoryRepo.saveMemory("primary_email", "Primary email address is $email", com.aurviz.aiagent.data.local.memory.MemoryCategory.CONTACT, 5)
            }
            if (phone.isNotBlank()) {
                memoryRepo.saveMemory("phone_number", "User phone number is $phone", com.aurviz.aiagent.data.local.memory.MemoryCategory.CONTACT, 5)
            }
        }
    }

    fun resetUserProfile() {
        saveUserProfile("User", "user.aurviz@example.com", "")
    }

    fun onGeminiModelChanged(model: String) {
        _geminiModel.value = model
        prefs.preferredGeminiModel = model
    }

    fun onTtsToggled(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        prefs.isVoiceTtsEnabled = enabled
    }

    fun onRequireConfirmationToggled(required: Boolean) {
        _requireConfirmation.value = required
        prefs.requireEmailConfirmation = required
    }

    fun refreshPermissions() {
        val context = app.applicationContext
        val list = mutableListOf<PermissionItem>()

        // 1. Microphone
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        list.add(PermissionItem("Microphone (Audio)", "Required for voice commands & speech-to-text", audioGranted, Manifest.permission.RECORD_AUDIO))

        // 2. Notifications
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        list.add(PermissionItem("System Notifications", "Required for scheduled cron routines & alerts", notifGranted, "POST_NOTIFICATIONS"))

        // 3. Phone Calling
        val callGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        list.add(PermissionItem("Phone Call Dispatch", "Required for direct telephone dialing", callGranted, Manifest.permission.CALL_PHONE))

        // 4. Camera
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        list.add(PermissionItem("Device Camera", "Required to launch photo capture on voice command", cameraGranted, Manifest.permission.CAMERA))

        // 5. Contacts (WhatsApp & Calls)
        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        list.add(PermissionItem("Contacts Access", "Required to look up contact names for direct WhatsApp messaging and calls", contactsGranted, Manifest.permission.READ_CONTACTS))

        // 6. Exact Alarms
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: true
        } else true
        list.add(PermissionItem("Exact Alarm Manager", "Required for time-critical recurring background schedules", alarmGranted, "SCHEDULE_EXACT_ALARM"))

        _permissions.value = list
    }

    fun refreshPerformanceMetrics() {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        val usagePercent = if (maxMemory > 0) ((usedMemory * 100) / maxMemory).toInt() else 0

        _performanceMetrics.value = PerformanceMetrics(
            allocatedMemoryMb = usedMemory,
            maxMemoryMb = maxMemory,
            freeMemoryMb = freeMemory,
            memoryUsagePercent = usagePercent,
            isNetworkOnline = networkMonitor.isCurrentlyConnected()
        )
    }
}
