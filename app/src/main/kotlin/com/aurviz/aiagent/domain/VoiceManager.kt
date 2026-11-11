package com.aurviz.aiagent.domain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    init {
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceManager)
            }
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _speechError.value = "Speech recognition is not available on this device."
            return
        }

        stopSpeaking()
        _speechError.value = null
        _transcribedText.value = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error starting listening: ${e.message}")
            _speechError.value = e.message
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w("VoiceManager", "Error stopping listening: ${e.message}")
        }
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    fun speak(text: String) {
        if (isTtsInitialized && text.isNotBlank()) {
            val clean = text.replace(Regex("[#*`_-]"), "")
            textToSpeech?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "AgentResponseUtterance")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
    }

    override fun onBeginningOfSpeech() {
        _isListening.value = true
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Normalize RMS dB (typical range -2dB to 10dB) into 0f..1f
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _rmsLevel.value = normalized
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    override fun onError(error: Int) {
        _isListening.value = false
        _rmsLevel.value = 0f
        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try again."
            SpeechRecognizer.ERROR_NETWORK -> "Network issue during speech recognition."
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            else -> "Speech recognition error ($error)"
        }
        _speechError.value = msg
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        _rmsLevel.value = 0f
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _transcribedText.value = text
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _transcribedText.value = text
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsInitialized = true
            }
        }
    }
}
