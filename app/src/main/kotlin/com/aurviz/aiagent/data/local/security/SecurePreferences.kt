package com.aurviz.aiagent.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences failed, falling back to standard prefs: ${e.message}")
        context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "User") ?: "User"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()

    var userPhoneNumber: String
        get() = prefs.getString(KEY_USER_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PHONE, value.trim()).apply()

    var userEmailAccount: String
        get() = prefs.getString(KEY_USER_EMAIL, "user.aurviz@example.com") ?: "user.aurviz@example.com"
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value.trim()).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value).apply()

    var isVoiceTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_TTS_ENABLED, value).apply()

    var requireEmailConfirmation: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_EMAIL_CONFIRMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_EMAIL_CONFIRMATION, value).apply()

    var preferredGeminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value.trim()).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    companion object {
        private const val TAG = "SecurePreferences"
        private const val SECURE_PREFS_FILE = "aurviz_secure_prefs"
        private const val FALLBACK_PREFS_FILE = "aurviz_agent_prefs"

        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_EMAIL = "user_email_account"
        private const val KEY_VOICE_TTS_ENABLED = "voice_tts_enabled"
        private const val KEY_REQUIRE_EMAIL_CONFIRMATION = "require_email_confirmation"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
    }
}
