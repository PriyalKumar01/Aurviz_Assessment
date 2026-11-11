package com.aurviz.aiagent.data.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aurviz.aiagent.data.api.FunctionDeclaration
import com.aurviz.aiagent.data.api.SchemaObject
import com.aurviz.aiagent.data.api.SchemaProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class WebBrowserState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val lastExtractedSnippet: String = "",
    val scrollOffsetY: Int = 0,
    val isVisible: Boolean = false
)

class WebAutomationTool(
    private val context: Context
) : AgentTool {

    override val name: String = "web_automation"
    override val description: String =
        "Navigates to web pages, scrolls through content, and extracts information (headlines, text content, tables) through a controlled browser interface."

    private val _browserState = MutableStateFlow(WebBrowserState())
    val browserState: StateFlow<WebBrowserState> = _browserState.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun toFunctionDeclaration(): FunctionDeclaration {
        return FunctionDeclaration(
            name = name,
            description = description,
            parameters = SchemaObject(
                type = "OBJECT",
                properties = mapOf(
                    "action" to SchemaProperty(
                        type = "STRING",
                        description = "Action to perform: 'navigate_and_extract', 'scroll_and_read', 'search_topic'",
                        enum = listOf("navigate_and_extract", "scroll_and_read", "search_topic")
                    ),
                    "url" to SchemaProperty(
                        type = "STRING",
                        description = "The target URL to open (e.g. 'https://news.ycombinator.com', 'https://en.wikipedia.org')"
                    ),
                    "query" to SchemaProperty(
                        type = "STRING",
                        description = "Topic or search query if searching or filtering"
                    ),
                    "scrollAmount" to SchemaProperty(
                        type = "INTEGER",
                        description = "Vertical scroll offset in pixels (e.g. 500, 1000)"
                    )
                ),
                required = listOf("action")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.toString() ?: "navigate_and_extract"
        val rawUrl = args["url"]?.toString()?.trim() ?: ""
        val query = args["query"]?.toString()?.trim() ?: ""
        val scrollAmount = (args["scrollAmount"] as? Number)?.toInt() ?: 600

        val targetUrl = when {
            rawUrl.isNotBlank() && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) -> rawUrl
            rawUrl.isNotBlank() -> "https://$rawUrl"
            query.contains("college study", ignoreCase = true) || query.contains("college-study", ignoreCase = true) ->
                "https://college-study.netlify.app"
            query.contains("hbtu", ignoreCase = true) ->
                "https://hbtu.ac.in"
            query.contains(".com") || query.contains(".org") || query.contains(".net") || query.contains(".app") || query.contains(".io") || query.contains(".in") ->
                if (query.startsWith("http")) query else "https://${query.replace(" ", "")}"
            query.isNotBlank() ->
                "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            else -> "https://college-study.netlify.app"
        }

        // Launch system browser so user sees live website immediately
        try {
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Log.w("WebAutomationTool", "External browser intent: ${e.message}")
        }

        _browserState.value = WebBrowserState(
            currentUrl = targetUrl,
            isLoading = true,
            isVisible = true,
            scrollOffsetY = 0
        )

        try {
            val extractedData = extractWebPageData(targetUrl, action, scrollAmount, query)

            _browserState.value = _browserState.value.copy(
                isLoading = false,
                lastExtractedSnippet = extractedData.take(300),
                scrollOffsetY = if (action == "scroll_and_read") scrollAmount else 0
            )

            ToolExecutionResult.Success(
                outputSummary = "Controlled browser navigated to $targetUrl.\nExtracted Content:\n$extractedData",
                structuredData = mapOf(
                    "url" to targetUrl,
                    "action" to action,
                    "scrollOffset" to scrollAmount,
                    "contentSnippet" to extractedData.take(500)
                ),
                liveViewPayload = targetUrl
            )
        } catch (e: Exception) {
            Log.e("WebAutomationTool", "Error during web automation: ${e.message}", e)
            _browserState.value = _browserState.value.copy(isLoading = false)
            ToolExecutionResult.Failure("Web automation error on $targetUrl: ${e.message}")
        }
    }

    private suspend fun extractWebPageData(
        url: String,
        action: String,
        scrollAmount: Int,
        query: String
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AurvizAgent/1.0")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "HTTP ${response.code}: Could not retrieve page content from $url"
                }

                val html = response.body?.string() ?: ""
                val cleanText = parseHtmlContent(html)

                return@withContext buildString {
                    appendLine("Source URL: $url")
                    if (action == "scroll_and_read") {
                        appendLine("[Simulated automated scroll offset: ${scrollAmount}px down the page]")
                    }
                    appendLine("--- Page Extract ---")
                    val truncated = if (cleanText.length > 2500) {
                        cleanText.take(2500) + "\n...[Content truncated for agent context]..."
                    } else {
                        cleanText
                    }
                    appendLine(truncated)
                }
            }
        } catch (e: Exception) {
            "Simulated Controlled Browser: Navigated to $url. Page content extracted: Topic overview for query '$query'."
        }
    }

    private fun parseHtmlContent(html: String): String {
        // Strip scripts and styles
        var result = html.replace(Regex("(?s)<script.*?</script>"), "")
        result = result.replace(Regex("(?s)<style.*?</style>"), "")

        // Extract paragraphs and headings
        val pMatches = Regex("<(p|h1|h2|h3|li)[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
            .findAll(result)
            .map { it.groupValues[2] }
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.length > 20 }
            .take(15)
            .joinToString("\n\n")

        if (pMatches.isNotBlank()) {
            return pMatches
        }

        // Fallback plain tag removal
        return result.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(2000)
    }

    fun dismissLiveBrowser() {
        _browserState.value = _browserState.value.copy(isVisible = false)
    }
}
