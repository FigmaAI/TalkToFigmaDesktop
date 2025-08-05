package kr.co.metadata.mcp.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.lang.Thread.UncaughtExceptionHandler

/**
 * Global crash handler that captures uncaught exceptions and reports them to Google Analytics
 */
class CrashHandler(
    private val analyticsService: GoogleAnalyticsService,
    private val appVersion: String,
    private val userId: String? = null
) : UncaughtExceptionHandler {
    
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(Dispatchers.IO)
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        // Set this as the default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(this)
        logger.info { "Crash handler initialized" }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        logger.error(throwable) { "Uncaught exception in thread: ${thread.name}" }
        
        // Collect system information
        val systemInfo = collectSystemInfo()
        
        // Send crash report to Google Analytics
        scope.launch {
            try {
                val additionalInfo = mutableMapOf<String, Any>().apply {
                    put("thread_name", thread.name)
                    put("thread_id", thread.threadId())
                    put("app_version", appVersion)
                    putAll(systemInfo)
                }
                
                val success = analyticsService.sendCrashReport(
                    exception = throwable,
                    additionalInfo = additionalInfo,
                    userId = userId
                )
                
                if (success) {
                    logger.info { "Crash report sent to Google Analytics successfully" }
                } else {
                    logger.warn { "Failed to send crash report to Google Analytics" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error sending crash report to Google Analytics" }
            }
        }
        
        // Call the default handler if it exists
        defaultHandler?.uncaughtException(thread, throwable)
    }

    /**
     * Collect system information for crash reporting
     */
    private fun collectSystemInfo(): Map<String, Any> {
        return try {
            val runtime = Runtime.getRuntime()
            val os = System.getProperty("os.name")
            val osVersion = System.getProperty("os.version")
            val javaVersion = System.getProperty("java.version")
            val javaVendor = System.getProperty("java.vendor")
            
            mapOf(
                "os_name" to (os ?: "Unknown"),
                "os_version" to (osVersion ?: "Unknown"),
                "java_version" to (javaVersion ?: "Unknown"),
                "java_vendor" to (javaVendor ?: "Unknown"),
                "total_memory_mb" to (runtime.totalMemory() / 1024 / 1024),
                "free_memory_mb" to (runtime.freeMemory() / 1024 / 1024),
                "max_memory_mb" to (runtime.maxMemory() / 1024 / 1024),
                "available_processors" to runtime.availableProcessors(),
                "timestamp" to System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to collect system information" }
            emptyMap()
        }
    }

    /**
     * Manually report an exception (for caught exceptions)
     */
    fun reportException(exception: Throwable, context: String? = null) {
        logger.error(exception) { "Reporting exception: $context" }
        
        scope.launch {
            try {
                val additionalInfo = mutableMapOf<String, Any>().apply {
                    put("app_version", appVersion)
                    put("reported_manually", true)
                    if (context != null) put("context", context)
                    putAll(collectSystemInfo())
                }
                
                analyticsService.sendCrashReport(
                    exception = exception,
                    additionalInfo = additionalInfo,
                    userId = userId
                )
            } catch (e: Exception) {
                logger.error(e) { "Error reporting exception to Google Analytics" }
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        analyticsService.close()
        logger.info { "Crash handler cleaned up" }
    }
} 