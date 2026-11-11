package com.aurviz.aiagent.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiApiResult {
    data class Success(
        val text: String?,
        val functionCalls: List<FunctionCall>,
        val latencyMs: Long
    ) : GeminiApiResult()

    data class Error(
        val message: String,
        val statusCode: Int = 0,
        val latencyMs: Long = 0
    ) : GeminiApiResult()
}

class GeminiApiClient(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { "gemini-1.5-flash" }
) {
    private val gson: Gson = GsonBuilder().create()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    suspend fun generateContent(
        userPrompt: String,
        systemInstructionText: String? = null,
        toolDeclarations: List<FunctionDeclaration>? = null,
        history: List<GeminiContent> = emptyList()
    ): GeminiApiResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        val model = modelProvider().trim()

        if (apiKey.isBlank()) {
            return@withContext GeminiApiResult.Error(
                message = "Gemini API key is not configured. Please add your key in Settings or local heuristic planning will be used.",
                statusCode = 401
            )
        }

        val contents = mutableListOf<GeminiContent>()
        contents.addAll(history)
        contents.add(
            GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(text = userPrompt))
            )
        )

        val systemInstruction = systemInstructionText?.let {
            GeminiContent(
                role = "system",
                parts = listOf(GeminiPart(text = it))
            )
        }

        val tools = toolDeclarations?.takeIf { it.isNotEmpty() }?.let {
            listOf(GeminiToolDeclaration(functionDeclarations = it))
        }

        val requestPayload = GeminiRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            tools = tools,
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        val jsonBody = gson.toJson(requestPayload)
        val url = "$BASE_URL/$model:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val responseBodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorObj = try {
                        gson.fromJson(responseBodyStr, GeminiResponse::class.java).error
                    } catch (e: Exception) {
                        null
                    }
                    val msg = errorObj?.message ?: "HTTP ${response.code}: ${response.message}"
                    Log.e(TAG, "Gemini API failed: $msg")
                    return@withContext GeminiApiResult.Error(
                        message = msg,
                        statusCode = response.code,
                        latencyMs = latency
                    )
                }

                val geminiResponse = gson.fromJson(responseBodyStr, GeminiResponse::class.java)
                val candidate = geminiResponse.candidates?.firstOrNull()
                val parts = candidate?.content?.parts ?: emptyList()

                val textParts = parts.mapNotNull { it.text }
                val functionCalls = parts.mapNotNull { it.functionCall }

                GeminiApiResult.Success(
                    text = if (textParts.isNotEmpty()) textParts.joinToString("\n") else null,
                    functionCalls = functionCalls,
                    latencyMs = latency
                )
            }
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Network connection error: ${e.message}", e)
            GeminiApiResult.Error(
                message = "Network failure while connecting to Gemini: ${e.localizedMessage}",
                statusCode = -1,
                latencyMs = latency
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            GeminiApiResult.Error(
                message = "Unexpected API error: ${e.localizedMessage}",
                statusCode = -2,
                latencyMs = latency
            )
        }
    }
}
