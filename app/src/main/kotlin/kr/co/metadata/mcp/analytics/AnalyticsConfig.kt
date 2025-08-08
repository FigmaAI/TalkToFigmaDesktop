package kr.co.metadata.mcp.analytics

import mu.KotlinLogging

/**
 * Simple Analytics configuration - just check environment variables
 */
class AnalyticsConfig {
    private val logger = KotlinLogging.logger {}
    
    val measurementId: String? = System.getenv("GOOGLE_ANALYTICS_ID")
    val apiSecret: String? = System.getenv("GOOGLE_ANALYTICS_API_SECRET")
    
    init {
        logger.info { "Analytics configuration loaded from environment" }
        if (isConfigured()) {
            logger.info { "✅ GA4 properly configured" }
        } else {
            logger.warn { "⚠️ GA4 not configured - check GOOGLE_ANALYTICS_ID and GOOGLE_ANALYTICS_API_SECRET in .envrc" }
        }
    }
    
    /**
     * Check if analytics is properly configured
     */
    fun isConfigured(): Boolean {
        return !measurementId.isNullOrBlank() && !apiSecret.isNullOrBlank()
    }
    
    /**
     * Get basic system info for debugging
     */
    fun getSystemInfo(): String {
        val os = System.getProperty("os.name")
        val osVersion = System.getProperty("os.version")
        val arch = System.getProperty("os.arch")
        return "$os $osVersion ($arch)"
    }
}