package kr.co.metadata.mcp

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import kr.co.metadata.mcp.server.WebSocketServer
import kr.co.metadata.mcp.server.McpServer
import mu.KotlinLogging
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.ServerSocket
import javax.imageio.ImageIO
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import kr.co.metadata.mcp.ui.TutorialDialog

import kotlinx.coroutines.launch
import java.io.File
import java.util.prefs.Preferences


private val logger = KotlinLogging.logger {}

/**
 * Check if a port is available
 */
fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { true }
    } catch (e: IOException) {
        false
    }
}

/**
 * Kill process using the specified port with enhanced reliability
 */
fun killProcessUsingPort(port: Int): Boolean {
    return try {
        logger.info { "🔍 Searching for processes using port $port..." }
        val os = System.getProperty("os.name").lowercase()
        var processesKilled = false
        
        if (os.contains("win")) {
            // Windows - Enhanced process finding and killing
            val findCmd = "netstat -ano | findstr :$port"
            val findProcess = ProcessBuilder("cmd", "/c", findCmd).start()
            val exitCode = findProcess.waitFor()
            val output = findProcess.inputStream.bufferedReader().readText()
            
            if (exitCode == 0 && output.isNotEmpty()) {
                val lines = output.trim().split("\n")
                val pids = mutableSetOf<String>()
                
                for (line in lines) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val pid = parts.last()
                        pids.add(pid)
                    }
                }
                
                for (pid in pids) {
                    logger.info { "💀 Killing Windows process $pid using port $port" }
                    val killCmd = "taskkill /PID $pid /F"
                    val killProcess = ProcessBuilder("cmd", "/c", killCmd).start()
                    if (killProcess.waitFor() == 0) {
                        processesKilled = true
                        logger.info { "✅ Successfully killed process $pid" }
                    } else {
                        logger.warn { "⚠️  Failed to kill process $pid" }
                    }
                }
            } else {
                logger.info { "ℹ️  No processes found using port $port" }
            }
        } else {
            // macOS/Linux - Enhanced process finding and killing
            val findCmd = arrayOf("lsof", "-ti:$port")
            val findProcess = ProcessBuilder(*findCmd).start()
            val exitCode = findProcess.waitFor()
            val output = findProcess.inputStream.bufferedReader().readText().trim()
            
            if (exitCode == 0 && output.isNotEmpty()) {
                val pids = output.split("\n").filter { it.isNotEmpty() }
                
                for (pid in pids) {
                    logger.info { "💀 Killing Unix process $pid using port $port" }
                    val killProcess = ProcessBuilder("kill", "-9", pid).start()
                    if (killProcess.waitFor() == 0) {
                        processesKilled = true
                        logger.info { "✅ Successfully killed process $pid" }
                    } else {
                        logger.warn { "⚠️  Failed to kill process $pid" }
                    }
                }
            } else {
                logger.info { "ℹ️  No processes found using port $port" }
            }
        }
        
        if (processesKilled) {
            logger.info { "🎯 Process killing completed for port $port" }
            // Give extra time for OS to clean up
            Thread.sleep(1500)
        }
        
        true
    } catch (e: Exception) {
        logger.error(e) { "❌ Failed to kill process using port $port: ${e.message}" }
        false
    }
}

/**
 * Ensure port is available with enhanced retry logic and process cleanup
 */
fun ensurePortAvailable(port: Int): Boolean {
    logger.info { "🔍 Checking availability of port $port..." }
    
    if (isPortAvailable(port)) {
        logger.info { "✅ Port $port is already available" }
        return true
    }
    
    logger.info { "⚠️  Port $port is in use, attempting cleanup..." }
    
    // Try to kill processes using the port
    if (!killProcessUsingPort(port)) {
        logger.error { "❌ Failed to kill processes using port $port" }
        return false
    }
    
    // Enhanced retry logic with progressive delays
    val maxAttempts = 10
    for (attempt in 1..maxAttempts) {
        val waitTime = when {
            attempt <= 3 -> 1000L   // First 3 attempts: 1 second
            attempt <= 6 -> 2000L   // Next 3 attempts: 2 seconds
            else -> 3000L           // Final attempts: 3 seconds
        }
        
        logger.info { "⏳ Waiting ${waitTime/1000}s for port $port to be released (attempt $attempt/$maxAttempts)..." }
        Thread.sleep(waitTime)
        
        if (isPortAvailable(port)) {
            logger.info { "🎉 Port $port successfully released after ${attempt * waitTime/1000} seconds total" }
            return true
        }
        
        if (attempt == maxAttempts) {
            logger.error { "❌ Port $port could not be released after $maxAttempts attempts (${maxAttempts * 2} seconds)" }
        } else {
            logger.debug { "🔄 Port $port still in use, continuing to wait..." }
        }
    }
    
    // Final attempt - try killing processes again
    logger.warn { "🔄 Making final attempt to kill processes on port $port..." }
    if (killProcessUsingPort(port)) {
        Thread.sleep(3000) // Give it one more chance
        if (isPortAvailable(port)) {
            logger.info { "🎉 Port $port finally released after aggressive cleanup" }
            return true
        }
    }
    
    logger.error { "💥 Port $port remains unavailable despite all cleanup attempts" }
    return false
}

/**
 * Kill all servers running on ports 3055 and 3056
 */
fun killAllServers(): Boolean {
    return try {
        logger.info { "Killing all servers on ports 3055 and 3056..." }
        
        var success = true
        
        // Kill WebSocket server on port 3055
        if (!killProcessUsingPort(3055)) {
            logger.warn { "Failed to kill process on port 3055" }
            success = false
        }
        
        // Kill MCP server on port 3056
        if (!killProcessUsingPort(3056)) {
            logger.warn { "Failed to kill process on port 3056" }
            success = false
        }
        
        // Give some time for ports to be released
        Thread.sleep(2000)
        logger.info { "All servers kill operation completed" }
        
        success
    } catch (e: Exception) {
        logger.error(e) { "Error killing all servers" }
        false
    }
}



@Composable
fun rememberSizedTrayIconPainter(path: String, width: Int, height: Int): BitmapPainter {
    val image = remember(path, width, height) {
        val resource = Thread.currentThread().contextClassLoader.getResource(path)
        val originalImage = ImageIO.read(resource)
        val scaledAwtImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = bufferedImage.createGraphics()
        g2d.drawImage(scaledAwtImage, 0, 0, null)
        g2d.dispose()
        bufferedImage.toComposeImageBitmap()
    }
    return BitmapPainter(image)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpConfigurationDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        DialogWindow(
            onCloseRequest = onDismiss,
            state = rememberDialogState(
                position = WindowPosition.Aligned(Alignment.Center),
                width = 600.dp,
                height = 400.dp
            ),
            title = "MCP Configuration",
            alwaysOnTop = true,
            focusable = true
        ) {
            val clipboardManager = LocalClipboardManager.current
            val mcpConfig = """
{
  "mcpServers": {
    "Figma": {
      "url": "http://127.0.0.1:3845/sse"
    },
    "TalkToFigmaDesktop": {
      "url": "http://127.0.0.1:3056/sse"
    }
  }
}
""".trimIndent()

            val isDarkTheme = isSystemInDarkTheme()

            MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkTheme) Color(0xFF2B2B2B) else Color(0xFFF5F5F5)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title
                        Text(
                            text = "MCP Configuration",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF1F1F1F)
                        )
                        
                        // Instructions
                        Text(
                            text = "Copy this configuration to your Cursor settings:",
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFF666666)
                        )
                        
                        // Config content in a card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = mcpConfig,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = if (isDarkTheme) Color.White else Color.Black
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }
                        
                        // Buttons row
                        Row(
                            modifier = Modifier.align(Alignment.Start),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Copy button
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(mcpConfig))
                                    logger.info { "MCP configuration copied to clipboard" }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) Color(0xFF007ACC) else Color(0xFF0078D4)
                                )
                            ) {
                                Text(
                                    text = "Copy to Clipboard",
                                    color = Color.White
                                )
                            }
                            
                            // Close button (outlined style)
                            OutlinedButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isDarkTheme) Color.White else Color(0xFF666666)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    if (isDarkTheme) Color(0xFF666666) else Color(0xFFCCCCCC)
                                )
                            ) {
                                Text(
                                    text = "Close",
                                    color = if (isDarkTheme) Color.White else Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogViewerDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        DialogWindow(
            onCloseRequest = onDismiss,
            state = rememberDialogState(
                position = WindowPosition.Aligned(Alignment.Center),
                width = 800.dp,
                height = 500.dp
            ),
            title = "Application Logs",
            alwaysOnTop = true,
            focusable = true
        ) {
            val isDarkTheme = isSystemInDarkTheme()
            val clipboardManager = LocalClipboardManager.current
            var logContent by remember { mutableStateOf("") }
            var clearTimestamp by remember { mutableStateOf<Long?>(null) }
            
            // Function to filter logs based on clear timestamp
            fun filterLogsAfterClear(fullLogContent: String): String {
                if (clearTimestamp == null) return fullLogContent
                
                val clearTime = clearTimestamp!!
                val lines = fullLogContent.split("\n")
                val filteredLines = mutableListOf<String>()
                
                for (line in lines) {
                    if (line.trim().isEmpty()) continue
                    
                    // Parse timestamp from log line (format: HH:mm:ss.SSS)
                    val timeMatch = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})").find(line)
                    if (timeMatch != null) {
                        try {
                            val timeStr = timeMatch.groupValues[1]
                            val timeParts = timeStr.split(":", ".")
                            if (timeParts.size == 4) {
                                val hour = timeParts[0].toInt()
                                val minute = timeParts[1].toInt()
                                val second = timeParts[2].toInt()
                                val millis = timeParts[3].toInt()
                                
                                // Convert to milliseconds since midnight
                                val logTimeMillis = (hour * 3600 + minute * 60 + second) * 1000L + millis
                                
                                // Convert clear timestamp to milliseconds since midnight
                                val clearCal = java.util.Calendar.getInstance()
                                clearCal.timeInMillis = clearTime
                                val clearTimeMillis = (clearCal.get(java.util.Calendar.HOUR_OF_DAY) * 3600 +
                                                     clearCal.get(java.util.Calendar.MINUTE) * 60 +
                                                     clearCal.get(java.util.Calendar.SECOND)) * 1000L +
                                                     clearCal.get(java.util.Calendar.MILLISECOND)
                                
                                if (logTimeMillis >= clearTimeMillis) {
                                    filteredLines.add(line)
                                }
                            } else {
                                // If we can't parse timestamp, include the line (might be continuation)
                                filteredLines.add(line)
                            }
                        } catch (e: Exception) {
                            // If parsing fails, include the line
                            filteredLines.add(line)
                        }
                    } else {
                        // Line without timestamp (continuation line), include if we have previous lines
                        if (filteredLines.isNotEmpty()) {
                            filteredLines.add(line)
                        }
                    }
                }
                
                return if (filteredLines.isEmpty()) {
                    "Logs cleared. Showing new logs from ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(clearTime))}..."
                } else {
                    filteredLines.joinToString("\n")
                }
            }
            
            // Function to read log file
            fun refreshLogs() {
                try {
                    val logFile = java.io.File(System.getProperty("java.io.tmpdir"), "TalkToFigmaDesktop.log")
                    if (logFile.exists()) {
                        val fullContent = logFile.readText()
                        logContent = filterLogsAfterClear(fullContent)
                    } else {
                        logContent = "Log file not found. Start the servers to generate logs."
                    }
                } catch (e: Exception) {
                    logContent = "Error reading log file: ${e.message}"
                    logger.error(e) { "Failed to read log file" }
                }
            }
            
            // Function to clear displayed logs (not the file)
            fun clearLogs() {
                try {
                    clearTimestamp = System.currentTimeMillis()
                    val clearTimeStr = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
                    logContent = "Logs cleared at $clearTimeStr. Showing new logs from this point..."
                    logger.info { "Log display cleared by user at $clearTimeStr - log file preserved" }
                } catch (e: Exception) {
                    logContent = "Error clearing log display: ${e.message}"
                    logger.error(e) { "Failed to clear log display" }
                }
            }
            
            // Function to copy logs to clipboard
            fun copyLogs() {
                try {
                    if (logContent.isNotEmpty() && logContent != "Log file not found. Start the servers to generate logs.") {
                        clipboardManager.setText(AnnotatedString(logContent))
                        logger.info { "Log content copied to clipboard" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to copy log content to clipboard" }
                }
            }
            
            // Load logs when dialog opens
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    refreshLogs()
                }
            }
            
            MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkTheme) Color(0xFF2B2B2B) else Color(0xFFF5F5F5)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header with title and icon controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Application Logs",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF1F1F1F)
                            )
                            
                            // Icon buttons row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Refresh button
                                IconButton(
                                    onClick = { refreshLogs() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = if (isDarkTheme) Color.White else Color(0xFF333333)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh logs"
                                    )
                                }
                                
                                // Clear logs button
                                IconButton(
                                    onClick = { clearLogs() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = if (isDarkTheme) Color.White else Color(0xFF333333)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear all logs"
                                    )
                                }
                                
                                // Copy logs button
                                IconButton(
                                    onClick = { copyLogs() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = if (isDarkTheme) Color.White else Color(0xFF333333)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy logs to clipboard"
                                    )
                                }
                            }
                        }
                        
                        // Log content
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
                            )
                        ) {
                            SelectionContainer {
                                Text(
                                    text = logContent,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = if (isDarkTheme) Color.White else Color.Black
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                        
                        // Bottom action buttons
                        Row(
                            modifier = Modifier.align(Alignment.Start),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Report Issue button (Primary)
                            Button(
                                onClick = {
                                    try {
                                        // Prepare issue content with log data
                                        val issueTitle = "Bug Report from Cursor Talk to Figma desktop"
                                        val issueBody = """
## Bug Description
Please describe the issue you're experiencing:

## Environment
- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}
- Java: ${System.getProperty("java.version")}
- App Version: 1.0.0

## Logs
Please paste relevant logs here:
```

```

## Steps to Reproduce
Please describe how to reproduce this issue:

1. 
2. 
3. 

## Expected Behavior
What did you expect to happen?

## Actual Behavior
What actually happened?
                                        """.trimIndent()
                                        
                                        // URL encode the content
                                        val encodedTitle = java.net.URLEncoder.encode(issueTitle, "UTF-8")
                                        val encodedBody = java.net.URLEncoder.encode(issueBody, "UTF-8")
                                        val issueUrl = "https://github.com/FigmaAI/TalkToFigmaDesktop/issues/new?title=$encodedTitle&body=$encodedBody"
                                        
                                        // Open in default browser
                                        val desktop = java.awt.Desktop.getDesktop()
                                        if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                                            desktop.browse(java.net.URI(issueUrl))
                                            logger.info { "Opened GitHub issue page with log content" }
                                        }
                                    } catch (e: Exception) {
                                        logger.error(e) { "Failed to open GitHub issue page" }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) Color(0xFF007ACC) else Color(0xFF0078D4)
                                )
                            ) {
                                Text(
                                    text = "Report an Issue",
                                    color = Color.White
                                )
                            }
                            
                            // Close button (outlined style)
                            OutlinedButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isDarkTheme) Color.White else Color(0xFF666666)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    if (isDarkTheme) Color(0xFF666666) else Color(0xFFCCCCCC)
                                )
                            ) {
                                Text(
                                    text = "Close",
                                    color = if (isDarkTheme) Color.White else Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



fun main() {
    application {
        var websocketServerRunning by remember { mutableStateOf(false) }
        var mcpServerRunning by remember { mutableStateOf(false) }
        var showMcpConfigDialog by remember { mutableStateOf(false) }
        var showLogViewerDialog by remember { mutableStateOf(false) }
        var showTutorialDialog by remember { mutableStateOf(false) }
        val trayState = rememberTrayState()
        val scope = rememberCoroutineScope()
        
        // Check if it's first time launch and show tutorial
        LaunchedEffect(Unit) {
            logger.info { "Cursor Talk to Figma desktop application started" }
            
            try {
                val prefs = Preferences.userNodeForPackage(this@application::class.java)
                val hasShownTutorial = prefs.getBoolean("tutorial_shown", false)
                
                if (!hasShownTutorial) {
                    logger.info { "First time launch detected - showing tutorial" }
                    showTutorialDialog = true
                    prefs.putBoolean("tutorial_shown", true)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to check tutorial preferences" }
            }
        }
        
        // Initialize server components
        var webSocketServer by remember { mutableStateOf<WebSocketServer?>(null) }
        var mcpServer by remember { mutableStateOf<McpServer?>(null) }

        // Use different icons based on server status
        val anyServerRunning = websocketServerRunning || mcpServerRunning
        val trayIconPath = if (anyServerRunning) "active_image.png" else "tray_icon.png"

        Tray(
            icon = rememberSizedTrayIconPainter(trayIconPath, 128, 128),
            state = trayState,
                            tooltip = "Cursor Talk to Figma desktop",
            menu = {
                // Status
                val wsStatus = if (websocketServerRunning) "Running" else "Stopped"
                val mcpStatus = if (mcpServerRunning) "Running" else "Stopped"
                Item("WebSocket Server: $wsStatus", enabled = false, onClick = { })
                Item("MCP Server: $mcpStatus", enabled = false, onClick = { })
                
                Separator()
                
                // MCP Configuration
                Item("MCP Configuration", onClick = {
                    showMcpConfigDialog = true
                })
                
                // View Logs
                Item("View Logs", onClick = {
                    showLogViewerDialog = true
                })

                // Tutorial
                Item("Tutorial", onClick = {
                    showTutorialDialog = true
                })
                
                Separator()
                
                // Main Controls
                val allRunning = websocketServerRunning && mcpServerRunning
                val allStopped = !websocketServerRunning && !mcpServerRunning
                
                if (allStopped) {
                    Item("Start All Services", onClick = {
                        scope.launch {
                            try {
                                logger.info { "🚀 Starting all services..." }
                                
                                // Start WebSocket server first
                                val wsPort = 3055
                                logger.info { "🔧 Preparing WebSocket server on port $wsPort..." }
                                
                                if (ensurePortAvailable(wsPort)) {
                                    logger.info { "🚀 Creating WebSocket server..." }
                                    val newWebSocketServer = WebSocketServer(wsPort)
                                    webSocketServer = newWebSocketServer
                                    newWebSocketServer.start()
                                    websocketServerRunning = true
                                    logger.info { "✅ WebSocket server started successfully on port $wsPort" }
                                    
                                    // Start MCP server with port cleanup
                                    val mcpPort = 3056
                                    logger.info { "🔧 Preparing MCP server on port $mcpPort..." }
                                    
                                    if (ensurePortAvailable(mcpPort)) {
                                        logger.info { "🚀 Creating MCP server..." }
                                        mcpServer = McpServer()
                                        mcpServer?.start()
                                        mcpServerRunning = true
                                        logger.info { "✅ MCP server started successfully on http://localhost:$mcpPort/sse" }
                                        logger.info { "🎉 All services started successfully!" }
                                    } else {
                                        logger.error { "❌ Failed to make port $mcpPort available - cannot start MCP server" }
                                        // Clean up WebSocket server if MCP server fails
                                        webSocketServer?.stop()
                                        websocketServerRunning = false
                                        webSocketServer = null
                                    }
                                } else {
                                    logger.error { "❌ Failed to make port $wsPort available - cannot start WebSocket server" }
                                }
                            } catch (e: Exception) {
                                logger.error(e) { "💥 Failed to start all services: ${e.message}" }
                                // Clean up any partially started services
                                try {
                                    mcpServer?.stop()
                                    webSocketServer?.stop()
                                } catch (cleanupEx: Exception) {
                                    logger.error(cleanupEx) { "Error during cleanup: ${cleanupEx.message}" }
                                }
                                websocketServerRunning = false
                                mcpServerRunning = false
                                webSocketServer = null
                                mcpServer = null
                            }
                        }
                    })
                } else if (allRunning) {
                    Item("Stop All Services", onClick = {
                        scope.launch {
                            try {
                                mcpServer?.stop()
                                mcpServerRunning = false
                                webSocketServer?.stop()
                                websocketServerRunning = false
                                webSocketServer = null
                                mcpServer = null
                                logger.info { "All services stopped" }
                            } catch (e: Exception) {
                                logger.error(e) { "Failed to stop all services" }
                            }
                        }
                    })
                } else {
                    // Mixed state - show individual controls
                    if (!websocketServerRunning) {
                        Item("Start WebSocket Server", onClick = {
                            scope.launch {
                                try {
                                    val port = 3055
                                    logger.info { "🔧 Preparing WebSocket server on port $port..." }
                                    if (ensurePortAvailable(port)) {
                                        logger.info { "🚀 Creating WebSocket server..." }
                                        val newWebSocketServer = WebSocketServer(port)
                                        webSocketServer = newWebSocketServer
                                        newWebSocketServer.start()
                                        websocketServerRunning = true
                                        logger.info { "✅ WebSocket server started successfully on port $port" }
                                    } else {
                                        logger.error { "❌ Failed to make port $port available for WebSocket server" }
                                    }
                                } catch (e: Exception) {
                                    logger.error(e) { "💥 Failed to start WebSocket server: ${e.message}" }
                                    websocketServerRunning = false
                                    webSocketServer = null
                                }
                            }
                        })
                    } else {
                        Item("Stop WebSocket Server", onClick = {
                            try {
                                webSocketServer?.stop()
                                websocketServerRunning = false
                                webSocketServer = null
                                logger.info { "WebSocket server stopped" }
                            } catch (e: Exception) {
                                logger.error(e) { "Failed to stop WebSocket server" }
                            }
                        })
                    }
                    
                    if (!mcpServerRunning) {
                        Item("Start MCP Server", onClick = {
                            scope.launch {
                                try {
                                    val mcpPort = 3056
                                    logger.info { "🔧 Preparing MCP server on port $mcpPort..." }
                                    
                                    if (ensurePortAvailable(mcpPort)) {
                                        logger.info { "🚀 Creating MCP server..." }
                                        mcpServer = McpServer()
                                        mcpServer?.start()
                                        mcpServerRunning = true
                                        logger.info { "✅ MCP server started successfully on http://localhost:$mcpPort/sse" }
                                    } else {
                                        logger.error { "❌ Failed to make port $mcpPort available for MCP server" }
                                    }
                                } catch (e: Exception) {
                                    logger.error(e) { "💥 Failed to start MCP server: ${e.message}" }
                                    mcpServerRunning = false
                                    mcpServer = null
                                }
                            }
                        })
                    } else {
                        Item("Stop MCP Server", onClick = {
                            try {
                                mcpServer?.stop()
                                mcpServerRunning = false
                                logger.info { "MCP server stopped" }
                            } catch (e: Exception) {
                                logger.error(e) { "Failed to stop MCP server" }
                            }
                        })
                    }
                }
                
                Separator()
                
                // Emergency action
                Item("Kill All Servers", onClick = {
                    try {
                        logger.info { "User requested to kill all servers" }
                        if (killAllServers()) {
                            websocketServerRunning = false
                            mcpServerRunning = false
                            webSocketServer = null
                            mcpServer = null
                            logger.info { "All servers killed and state reset" }
                        } else {
                            logger.error { "Failed to kill all servers" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error during kill all servers operation" }
                    }
                })
                
                Separator()
                
                Item("Exit", onClick = {
                    try {
                        if (mcpServerRunning) {
                            mcpServer?.stop()
                        }
                        if (websocketServerRunning) {
                            webSocketServer?.stop()
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error stopping servers during exit" }
                    }
                    exitApplication()
                })
            }
        )
        
        // MCP Configuration Dialog
        McpConfigurationDialog(
            isVisible = showMcpConfigDialog,
            onDismiss = { showMcpConfigDialog = false }
        )
        
        // Log Viewer Dialog
        LogViewerDialog(
            isVisible = showLogViewerDialog,
            onDismiss = { showLogViewerDialog = false }
        )

        // Tutorial Dialog
        TutorialDialog(
            isVisible = showTutorialDialog,
            onDismiss = { showTutorialDialog = false }
        )
    }
} 