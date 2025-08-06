package kr.co.metadata.mcp.analytics

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging

/**
 * Configuration manager for Google Analytics settings
 */
class AnalyticsConfig {
    private val logger = KotlinLogging.logger {}
    private val config: Config = ConfigFactory.load("analytics.properties")

    val measurementId: String = config.getString("analytics.measurement.id")
    val apiSecret: String? = if (config.hasPath("analytics.api.secret")) {
        config.getString("analytics.api.secret")
    } else null
    
    val debugMode: Boolean = config.getBoolean("analytics.debug.mode")
    val crashReportingEnabled: Boolean = config.getBoolean("analytics.crash.reporting.enabled")
    val userTrackingEnabled: Boolean = config.getBoolean("analytics.user.tracking.enabled")
    val figmaTrackingEnabled: Boolean = config.getBoolean("analytics.figma.tracking.enabled")
    val mcpTrackingEnabled: Boolean = config.getBoolean("analytics.mcp.tracking.enabled")
    
    val userId: String? = if (config.hasPath("analytics.user.id")) {
        val id = config.getString("analytics.user.id")
        if (id.isNotBlank()) id else null
    } else null

    // Custom dimensions
    val customDimensions = mapOf(
        "app_version" to config.getInt("analytics.custom.dimension.app_version"),
        "os_info" to config.getInt("analytics.custom.dimension.os_info"),
        "error_type" to config.getInt("analytics.custom.dimension.error_type")
    )

    init {
        logger.info { "Analytics configuration loaded" }
        logger.debug { "Measurement ID: $measurementId" }
        logger.debug { "Debug mode: $debugMode" }
        logger.debug { "Crash reporting enabled: $crashReportingEnabled" }
        logger.debug { "User tracking enabled: $userTrackingEnabled" }
        logger.debug { "Figma tracking enabled: $figmaTrackingEnabled" }
        logger.debug { "MCP tracking enabled: $mcpTrackingEnabled" }
        
        if (measurementId == "G-XXXXXXXXXX") {
            logger.warn { "Please configure your Google Analytics Measurement ID in analytics.properties" }
        }
    }

    /**
     * Check if analytics is properly configured
     */
    fun isConfigured(): Boolean {
        return measurementId.isNotBlank() && 
               measurementId != "G-XXXXXXXXXX" &&
               apiSecret != null && 
               apiSecret.isNotBlank()
    }

    /**
     * Get OS information string
     */
    fun getOsInfo(): String {
        val os = System.getProperty("os.name")
        val osVersion = System.getProperty("os.version")
        val arch = System.getProperty("os.arch")
        return "$os $osVersion ($arch)"
    }
} 