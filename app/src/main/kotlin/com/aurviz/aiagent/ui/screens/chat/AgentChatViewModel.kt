package com.aurviz.aiagent.ui.screens.chat

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.aiagent.AurvizApp
import com.aurviz.aiagent.data.tools.WebBrowserState
import com.aurviz.aiagent.domain.models.AgentExecutionState
import com.aurviz.aiagent.domain.models.ExecutionPlan
import com.aurviz.aiagent.ui.components.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage>
)

class AgentChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AurvizApp
    private val orchestrator = app.agentOrchestrator
    private val voiceManager = app.voiceManager
    private val webAutomationTool = app.webAutomationTool
    private val securePreferences = app.securePreferences

    val executionState: StateFlow<AgentExecutionState> = orchestrator.executionState
    val activePlan: StateFlow<ExecutionPlan?> = orchestrator.activePlan
    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val rmsLevel: StateFlow<Float> = voiceManager.rmsLevel
    val browserState: StateFlow<WebBrowserState> = webAutomationTool.browserState

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Hi ${securePreferences.userName.split(" ").firstOrNull() ?: "there"}! I'm your Aurviz Assistant. How can I help you today?",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    init {
        // Collect voice transcriptions
        viewModelScope.launch {
            voiceManager.transcribedText.collect { text ->
                if (text.isNotBlank()) {
                    _inputText.value = text
                }
            }
        }

        // Collect completed responses to speak aloud if TTS is enabled
        viewModelScope.launch {
            orchestrator.executionState.collect { state ->
                if (state is AgentExecutionState.Completed) {
                    addMessage(state.outcomeSummary, isUser = false)
                    // CRITICAL FIX: Only speak natural conversational summary (never speak debug latencies!)
                    if (securePreferences.isVoiceTtsEnabled) {
                        voiceManager.speak(state.spokenSummary)
                    }
                } else if (state is AgentExecutionState.Error) {
                    addMessage("Error: ${state.errorMessage}", isUser = false)
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun submitCommand(prompt: String? = null, isVoice: Boolean = false) {
        val finalPrompt = (prompt ?: _inputText.value).trim()
        if (finalPrompt.isBlank()) return

        addMessage(finalPrompt, isUser = true, isVoice = isVoice)
        _inputText.value = ""

        viewModelScope.launch {
            orchestrator.executeCommand(finalPrompt)
        }
    }

    fun toggleVoiceListening() {
        if (isListening.value) {
            voiceManager.stopListening()
            if (_inputText.value.isNotBlank()) {
                submitCommand(isVoice = true)
            }
        } else {
            voiceManager.startListening { transcribed ->
                _inputText.value = transcribed
                submitCommand(transcribed, isVoice = true)
            }
        }
    }

    fun dismissBrowserSheet() {
        webAutomationTool.dismissLiveBrowser()
    }

    fun startNewChat() {
        val current = _messages.value
        if (current.any { it.isUser }) {
            val firstUserMsg = current.firstOrNull { it.isUser }?.text?.take(35) ?: "Conversation"
            val session = ChatSession(
                title = firstUserMsg,
                timestamp = System.currentTimeMillis(),
                messages = current
            )
            _chatSessions.value = listOf(session) + _chatSessions.value
        }
        _messages.value = listOf(
            ChatMessage(
                text = "Hi ${securePreferences.userName.split(" ").firstOrNull() ?: "there"}! I'm your Aurviz Assistant. How can I help you today?",
                isUser = false
            )
        )
    }

    fun restoreSession(session: ChatSession) {
        _messages.value = session.messages
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Aurviz Message", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun setInputForEdit(text: String) {
        _inputText.value = text
    }

    fun addMessage(text: String, isUser: Boolean, isVoice: Boolean = false) {
        val newMsg = ChatMessage(text = text, isUser = isUser, isVoice = isVoice)
        _messages.value = _messages.value + newMsg
    }
}
