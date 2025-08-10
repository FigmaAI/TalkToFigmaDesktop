package kr.co.metadata.mcp.analytics

import mu.KotlinLogging
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.Properties

/**
 * Basic GA4 Measurement Protocol implementation
 */
class GoogleAnalyticsService {
    private val logger = KotlinLogging.logger {}
    
    // Use AnalyticsConfig for centralized configuration management
    private val analyticsConfig = AnalyticsConfig()
    private val measurementId: String? = analyticsConfig.measurementId
    private val apiSecret: String? = analyticsConfig.apiSecret
    
    // Basic device/environment info
    private val osName = System.getProperty("os.name")
    private val osVersion = System.getProperty("os.version") 
    private val osArch = System.getProperty("os.arch")
    private val javaVersion = System.getProperty("java.version")
    private val userLocale = Locale.getDefault().toString()
    
    // Generate persistent client ID for this app instance
    private val clientId = UUID.randomUUID().toString()
    
    // Generate persistent session ID for this app session
    private val sessionId = UUID.randomUUID().toString()
    
    // Get app version dynamically
    private val appVersion = getAppVersion()
    
    // Get system timezone
    private val timeZone = TimeZone.getDefault().id
    
    init {
        logger.info { "GA4 Analytics Service initialized" }
        logger.debug { "Measurement ID: $measurementId" }
        logger.debug { "Client ID: $clientId" }
        logger.debug { "Session ID: $sessionId" }
        logger.debug { "OS: $osName $osVersion ($osArch)" }
        logger.debug { "App Version: $appVersion" }
        logger.debug { "Timezone: $timeZone" }
    }
    
    /**
     * Send page_view event (gtag.js standard)
     */
    fun sendPageView(pageTitle: String, pageLocation: String, pagePath: String? = null): Boolean {
        return sendEvent("page_view", mapOf(
            "page_title" to pageTitle,
            "page_location" to pageLocation,
            "page_path" to (pagePath ?: pageLocation)
        ))
    }
    
    /**
     * Send app_start event (gtag.js standard for apps)
     */
    fun sendAppStart(): Boolean {
        return sendEvent("app_start", mapOf(
            "app_version" to appVersion,
            "platform" to "desktop"
        ))
    }
    
    /**
     * Send user_engagement event (gtag.js standard)
     */
    fun sendUserEngagement(engagementTimeMs: Long = 1000): Boolean {
        return sendEvent("user_engagement", mapOf(
            "engagement_time_msec" to engagementTimeMs
        ))
    }
    
    /**
     * Send server action event (custom)
     */
    fun sendServerAction(action: String, serverType: String, port: Int? = null, duration: Long? = null): Boolean {
        val params = mutableMapOf<String, Any>(
            "action" to action,
            "server_type" to serverType
        )
        port?.let { params["port"] = it }
        duration?.let { params["startup_time_ms"] = it }
        
        return sendEvent("server_action", params)
    }
    
    /**
     * Send user action event (custom)
     */
    fun sendUserAction(action: String, category: String, label: String? = null, value: Int? = null): Boolean {
        val params = mutableMapOf<String, Any>(
            "action" to action,
            "category" to category
        )
        label?.let { params["label"] = it }
        value?.let { params["value"] = it }
        
        return sendEvent("user_action", params)
    }
    
    /**
     * Send first_open event for new users (GA4 standard)
     */
    fun sendFirstOpen(): Boolean {
        return sendEvent("first_open", mapOf(
            "platform" to "desktop"
        ))
    }
    
    /**
     * Send any event to GA4 with custom parameters
     */
    private fun sendEvent(eventName: String, customParams: Map<String, Any> = emptyMap()): Boolean {
        if (!isConfigured()) {
            logger.warn { "GA4 not configured - skipping $eventName event" }
            return false
        }
        
        return try {
            val url = URL("https://www.google-analytics.com/mp/collect" +
                    "?measurement_id=$measurementId&api_secret=$apiSecret")
            
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            // Base parameters  
            val params = mutableMapOf<String, Any>(
                "session_id" to sessionId,
                "app_version" to appVersion,
                "os_name" to osName,
                "os_version" to osVersion,
                "os_arch" to osArch,
                "java_version" to javaVersion,
                "user_locale" to userLocale,
                "timezone" to timeZone,
                "engagement_time_msec" to 1
                
                
            )
            
            // Add custom parameters
            params.putAll(customParams)
            
            val paramsJson = params.entries.joinToString(",\n") { (key, value) ->
                if (value is String) "    \"$key\": \"$value\""
                else "    \"$key\": $value"
            }
            
            val body = """
            {
              "client_id": "$clientId",
              "events": [
                {
                  "name": "$eventName",
                  "params": {
$paramsJson
                  }
                }
              ]
            }
            """.trimIndent()
            
            logger.debug { "Sending $eventName event to GA4" }
            logger.debug { "Request body: $body" }
            
            conn.outputStream.use { it.write(body.toByteArray()) }
            
            val responseCode = conn.responseCode
            logger.info { "GA4 Response Code: $responseCode" }
            
            if (responseCode in 200..299) {
                logger.info { "✅ $eventName event sent successfully" }
                true
            } else {
                logger.warn { "❌ Failed to send $eventName event: $responseCode" }
                false
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error sending $eventName event" }
            false
        }
    }
    
    /**
     * Check if GA4 is properly configured
     */
    private fun isConfigured(): Boolean {
        return !measurementId.isNullOrBlank() && !apiSecret.isNullOrBlank()
    }
    
    /**
     * Get app version from version.properties
     */
    private fun getAppVersion(): String {
        return try {
            val properties = Properties()
            val versionStream = javaClass.getResourceAsStream("/version.properties")
            versionStream?.use { stream ->
                properties.load(stream)
                properties.getProperty("version", "unknown")
            } ?: "unknown"
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load app version from version.properties" }
            "unknown"
        }
    }
}