package kr.co.metadata.mcp.analytics


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Analytics service for crash reporting and app analytics
 * Supports both Google Analytics 4 (GA4) and Universal Analytics
 */
class GoogleAnalyticsService(
    private val measurementId: String,
    private val apiSecret: String? = null,
    private val debugMode: Boolean = false
) {
    private val logger = KotlinLogging.logger {}
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val GA4_ENDPOINT = "https://www.google-analytics.com/mp/collect"
        private const val GA4_DEBUG_ENDPOINT = "https://www.google-analytics.com/debug/mp/collect"
        private const val UNIVERSAL_ANALYTICS_ENDPOINT = "https://www.google-analytics.com/collect"
        
        // Event names for tracking
        const val EVENT_APP_START = "app_start"
        const val EVENT_APP_CRASH = "app_crash"
        const val EVENT_FIGMA_CONNECTION = "figma_connection"
        const val EVENT_MCP_REQUEST = "mcp_request"
        const val EVENT_ERROR = "app_error"
        const val EVENT_USER_ACTION = "user_action"
    }

    /**
     * Send event to Google Analytics 4
     */
    suspend fun sendEvent(
        eventName: String,
        parameters: Map<String, Any> = emptyMap(),
        userId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (debugMode) GA4_DEBUG_ENDPOINT else GA4_ENDPOINT
            
            val eventData = JSONObject().apply {
                put("client_id", generateClientId())
                put("events", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", eventName)
                        if (parameters.isNotEmpty()) {
                            put("params", JSONObject(parameters))
                        }
                    })
                })
                if (userId != null) {
                    put("user_id", userId)
                }
            }

            val request = Request.Builder()
                .url("$endpoint?measurement_id=$measurementId${apiSecret?.let { "&api_secret=$it" } ?: ""}")
                .post(eventData.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // Debug logging
            logger.info { "Sending analytics event to: $endpoint" }
            logger.info { "Event data: ${eventData}" }

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            
            if (success) {
                logger.info { "Analytics event sent successfully: $eventName" }
            } else {
                logger.warn { "Failed to send analytics event: ${response.code} - ${response.message}" }
                logger.warn { "Response body: ${response.body?.string()}" }
            }
            
            response.close()
            success
            
        } catch (e: Exception) {
            logger.error(e) { "Error sending analytics event: $eventName" }
            false
        }
    }

    /**
     * Send crash report with detailed information
     */
    suspend fun sendCrashReport(
        exception: Throwable,
        additionalInfo: Map<String, Any> = emptyMap(),
        userId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val crashInfo = mutableMapOf<String, Any>().apply {
            put("exception_type", exception.javaClass.simpleName)
            put("exception_message", exception.message ?: "Unknown error")
            put("stack_trace", getStackTrace(exception))
            putAll(additionalInfo)
        }

        return@withContext sendEvent(EVENT_APP_CRASH, crashInfo, userId)
    }

    /**
     * Send error report (non-crash errors)
     */
    suspend fun sendErrorReport(
        errorMessage: String,
        errorCode: String? = null,
        additionalInfo: Map<String, Any> = emptyMap(),
        userId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val errorInfo = mutableMapOf<String, Any>().apply {
            put("error_message", errorMessage)
            if (errorCode != null) put("error_code", errorCode)
            putAll(additionalInfo)
        }

        return@withContext sendEvent(EVENT_ERROR, errorInfo, userId)
    }

    /**
     * Track user actions
     */
    suspend fun trackUserAction(
        action: String,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        userId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val actionInfo = mutableMapOf<String, Any>().apply {
            put("action", action)
            if (category != null) put("category", category)
            if (label != null) put("label", label)
            if (value != null) put("value", value)
        }

        return@withContext sendEvent(EVENT_USER_ACTION, actionInfo, userId)
    }

    /**
     * Track Figma connection events
     */
    suspend fun trackFigmaConnection(
        connectionType: String,
        success: Boolean,
        errorMessage: String? = null,
        userId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val connectionInfo = mutableMapOf<String, Any>().apply {
            put("connection_type", connectionType)
            put("success", success)
            if (errorMessage != null) put("error_message", errorMessage)
        }

        return@withContext sendEvent(EVENT_FIGMA_CONNECTION, connectionInfo, userId)
    }

    /**
     * Track MCP request events
     */
    suspend fun trackMcpRequest(
        requestType: String,
        success: Boolean,
        duration: Long? = null,
        errorMessage: String? = null,
        userId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val requestInfo = mutableMapOf<String, Any>().apply {
            put("request_type", requestType)
            put("success", success)
            if (duration != null) put("duration_ms", duration)
            if (errorMessage != null) put("error_message", errorMessage)
        }

        return@withContext sendEvent(EVENT_MCP_REQUEST, requestInfo, userId)
    }

    /**
     * Track app start event
     */
    suspend fun trackAppStart(
        appVersion: String,
        osInfo: String,
        userId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val startInfo = mapOf(
            "app_version" to appVersion,
            "os_info" to osInfo
        )

        return@withContext sendEvent(EVENT_APP_START, startInfo, userId)
    }

    /**
     * Generate a unique client ID for analytics
     */
    private fun generateClientId(): String {
        return "desktop_${System.currentTimeMillis()}_${(Math.random() * 1000000).toInt()}"
    }

    /**
     * Get formatted stack trace
     */
    private fun getStackTrace(exception: Throwable): String {
        return exception.stackTraceToString()
    }

    /**
     * Close the HTTP client
     */
    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
} 