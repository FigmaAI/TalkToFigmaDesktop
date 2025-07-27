package kr.co.metadata.mcp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.graphics.toComposeImageBitmap
import mu.KotlinLogging
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        val dialogState = rememberDialogState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 800.dp,
            height = 600.dp
        )
        
        DialogWindow(
            onCloseRequest = onDismiss,
            state = dialogState,
            title = "Getting Started Tutorial",
            alwaysOnTop = true,
            focusable = true
        ) {
            val isDarkTheme = isSystemInDarkTheme()
            val uriHandler = LocalUriHandler.current
            var currentStep by remember { mutableStateOf(0) }
            val totalSteps = 6

            val tutorialSteps = listOf(
                TutorialStep(
                    title = "Copy MCP Address",
                    description = "Go to MCP Configuration menu from the tray and copy the MCP server addresses",
                    image = "tutorials/Slide 4_3 - 1.png"
                ),
                TutorialStep(
                    title = "Register MCP Server in IDE",
                    description = "For example, in Cursor IDE,\n\nGo to Cursor Settings → Tools & Integrations\n\nClick New MCP Server",
                    image = "tutorials/Slide 4_3 - 2.png"
                ),
                TutorialStep(
                    title = "Paste the JSON Configuration",
                    description = "Paste the copied JSON into the MCP server setting",
                    image = "tutorials/Slide 4_3 - 3.png"
                ),
                TutorialStep(
                    title = "Start MCP Server and WebSocket",
                    description = "Click \"Start Services\" in the tray menu\nCheck that Tools are enabled in MCP settings",
                    image = "tutorials/Slide 4_3 - 4.png"
                ),
                TutorialStep(
                    title = "Run Figma Plugin",
                    description = "Find and run the Cursor Talk To Figma plugin in Figma",
                    image = "tutorials/Slide 4_3 - 5.png"
                ),
                TutorialStep(
                    title = "Connect to TalkToFigma Desktop",
                    description = "Toggle \"Use Localhost\" on\nClick Connect button to establish connection!",
                    image = "tutorials/Slide 4_3 - 6.png"
                )
            )

            MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Header with close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Getting Started - Step ${currentStep + 1} of $totalSteps",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkTheme) Color.White else Color(0xFF333333)
                            )
                            
                            // Close button
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tutorial",
                                    tint = if (isDarkTheme) Color.White else Color(0xFF333333)
                                )
                            }
                        }

                        // Progress indicator
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { (currentStep + 1) / totalSteps.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                            )
                            Text(
                                text = "Step ${currentStep + 1} of $totalSteps",
                                fontSize = 14.sp,
                                color = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFF666666)
                            )
                        }

                        // Main content card
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color.White
                            ),
                            border = CardDefaults.outlinedCardBorder(
                                enabled = true
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Image section
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(16.dp)
                                ) {
                                    val imagePainter = remember(tutorialSteps[currentStep].image) {
                                        try {
                                            val resource = this::class.java.classLoader.getResource(tutorialSteps[currentStep].image)
                                            if (resource != null) {
                                                val bufferedImage = javax.imageio.ImageIO.read(resource)
                                                androidx.compose.ui.graphics.painter.BitmapPainter(bufferedImage.toComposeImageBitmap())
                                            } else null
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    
                                    if (imagePainter != null) {
                                        Image(
                                            painter = imagePainter,
                                            contentDescription = "Tutorial step ${currentStep + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // Fallback if image not found
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Color.LightGray.copy(alpha = 0.2f),
                                                    RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Text(
                                                    text = "Image not available",
                                                    color = Color.Gray,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Content section
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Step title
                                    Text(
                                        text = tutorialSteps[currentStep].title,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDarkTheme) Color.White else Color(0xFF1F1F1F)
                                    )

                                    // Step description
                                    Text(
                                        text = tutorialSteps[currentStep].description,
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF424242)
                                    )

                                    // Special actions for specific steps
                                    when (currentStep) {
                                        0 -> {
                                            // Step 1: MCP Configuration - Reference to main dialog
                                            OutlinedCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.outlinedCardColors(
                                                    containerColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                                                ),
                                                border = CardDefaults.outlinedCardBorder(
                                                    enabled = true
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp)
                                                ) {
                                                    Text(
                                                        text = "💡 Tip:",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isDarkTheme) Color.White else Color.Black,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                    
                                                    Text(
                                                        text = "Use the 'MCP Configuration' menu item in the tray to get the JSON configuration that you'll need to paste in the next steps.",
                                                        fontSize = 12.sp,
                                                        color = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF424242),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                        4 -> {
                                            // Step 5: Figma Plugin Installation
                                            OutlinedCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.outlinedCardColors(
                                                    containerColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                                                ),
                                                border = CardDefaults.outlinedCardBorder(
                                                    enabled = true
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp)
                                                ) {
                                                    Text(
                                                        text = "Cursor Talk To Figma Plugin:",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isDarkTheme) Color.White else Color.Black,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                    
                                                    OutlinedButton(
                                                        onClick = {
                                                            try {
                                                                uriHandler.openUri("https://www.figma.com/community/plugin/1485687494525374295/cursor-talk-to-figma-mcp-plugin")
                                                                logger.info { "Opened Figma plugin page" }
                                                            } catch (e: Exception) {
                                                                logger.error(e) { "Failed to open Figma plugin page" }
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.Launch,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Open Plugin Page")
                                                    }
                                                }
                                            }
                                        }
                                        5 -> {
                                            // Step 6: Connection
                                            OutlinedCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.outlinedCardColors(
                                                    containerColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                                                ),
                                                border = CardDefaults.outlinedCardBorder(
                                                    enabled = true
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp)
                                                ) {
                                                    Text(
                                                        text = "💡 Important:",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isDarkTheme) Color.White else Color.Black,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                    
                                                    Text(
                                                        text = "Make sure TalkToFigma Desktop services are running before connecting. Check the tray icon menu to confirm.",
                                                        fontSize = 12.sp,
                                                        color = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF424242),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Navigation buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous button
                            if (currentStep > 0) {
                                OutlinedButton(
                                    onClick = { currentStep-- },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isDarkTheme) Color.White else Color(0xFF666666)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Previous")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            // Step indicator dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(totalSteps) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (index == currentStep) {
                                                    if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                                                } else {
                                                    Color.Gray.copy(alpha = 0.3f)
                                                },
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable { currentStep = index }
                                    )
                                }
                            }

                            // Next/Finish button
                            Button(
                                onClick = {
                                    if (currentStep < totalSteps - 1) {
                                        currentStep++
                                    } else {
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                                )
                            ) {
                                Text(if (currentStep < totalSteps - 1) "Next" else "Finish")
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (currentStep < totalSteps - 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class TutorialStep(
    val title: String,
    val description: String,
    val image: String
)

@Composable
fun SelectableText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            text = text,
            modifier = modifier,
            style = style
        )
    }
} 