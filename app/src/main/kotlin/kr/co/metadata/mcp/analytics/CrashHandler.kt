package kr.co.metadata.mcp.analytics

import mu.KotlinLogging

/**
 * Simple crash handler - will be implemented later
 * For now, just basic logging
 */
class CrashHandler {
    private val logger = KotlinLogging.logger {}
    
    init {
        logger.info { "Crash handler initialized (basic logging only)" }
    }
    
    /**
     * Handle uncaught exceptions - basic implementation
     */
    fun handleException(thread: Thread, exception: Throwable) {
        logger.error(exception) { "Uncaught exception in thread ${thread.name}" }
        // TODO: Send crash event to GA4 later
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        logger.info { "Crash handler cleaned up" }
    }
}