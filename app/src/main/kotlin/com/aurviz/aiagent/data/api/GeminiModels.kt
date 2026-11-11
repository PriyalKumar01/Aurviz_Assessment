package com.aurviz.aiagent.data.api

import com.google.gson.annotations.SerializedName

// Top-level Gemini Request
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("systemInstruction")
    val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiToolDeclaration>? = null,
    val generationConfig: GenerationConfig? = null
)

data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null
)

data class FunctionCall(
    val name: String,
    val args: Map<String, Any?>? = null
)

data class FunctionResponse(
    val name: String,
    val response: Map<String, Any?>
)

data class GeminiToolDeclaration(
    val functionDeclarations: List<FunctionDeclaration>
)

data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: SchemaObject? = null
)

data class SchemaObject(
    val type: String = "OBJECT",
    val properties: Map<String, SchemaProperty>,
    val required: List<String>? = null
)

data class SchemaProperty(
    val type: String, // STRING, INTEGER, BOOLEAN, ARRAY, OBJECT
    val description: String? = null,
    val enum: List<String>? = null
)

data class GenerationConfig(
    val temperature: Float = 0.2f,
    val topP: Float = 0.8f,
    val maxOutputTokens: Int = 2048
)

// Gemini Response Models
data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val usageMetadata: UsageMetadata? = null,
    val error: GeminiError? = null
)

data class Candidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)

data class GeminiError(
    val code: Int = 0,
    val message: String = "",
    val status: String = ""
)
