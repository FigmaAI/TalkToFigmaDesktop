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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.awt.SwingPanel
import mu.KotlinLogging
import kotlinx.coroutines.delay
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import java.io.File
import javax.swing.SwingUtilities

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
            width = 780.dp,
            height = 750.dp
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
            var currentVideoIndex by remember { mutableStateOf(0) }
            
            // 비디오 파일 경로 목록
            val videoTutorials = listOf(
                TutorialVideo(
                    title = "Step 1: Open MCP Configuration",
                    description = "Go to MCP Configuration menu from the tray and copy the MCP server addresses",
                    videoPath = "tutorials/tutorial01.mp4"
                ),
                TutorialVideo(
                    title = "Step 2: Configure IDE",
                    description = "In Cursor IDE, go to Settings → Tools & Integrations and paste the MCP server JSON configuration",
                    videoPath = "tutorials/tutorial02.mp4"
                ),
                TutorialVideo(
                    title = "Step 3: Start Services",
                    description = "Click \"Start Services\" in the tray menu and ensure Tools are enabled in MCP settings",
                    videoPath = "tutorials/tutorial03.mp4"
                ),
                TutorialVideo(
                    title = "Step 4: Run Figma Plugin",
                    description = "Find and run the Cursor Talk To Figma plugin in Figma",
                    videoPath = "tutorials/tutorial04.mp4"
                ),
                TutorialVideo(
                    title = "Step 5: Connect to Desktop",
                    description = "Toggle \"Use Localhost\" on and click Connect button to establish connection",
                    videoPath = "tutorials/tutorial05.mp4"
                )
            )
            
            val totalVideos = videoTutorials.size
            
            // JavaFX MediaPlayer state
            var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
            var jfxPanel by remember { mutableStateOf<JFXPanel?>(null) }
            
            // Initialize JavaFX when currentVideoIndex changes
            LaunchedEffect(currentVideoIndex) {
                val videoPath = videoTutorials[currentVideoIndex].videoPath
                val resourceUrl = javaClass.classLoader.getResource(videoPath)
                val darkTheme = isDarkTheme // Capture the theme value
                
                if (resourceUrl != null) {
                    Platform.runLater {
                        try {
                            // Dispose previous media player
                            mediaPlayer?.dispose()
                            
                            // Create new media and player
                            val media = Media(resourceUrl.toExternalForm())
                            val newMediaPlayer = MediaPlayer(media)
                            
                            newMediaPlayer.setOnEndOfMedia {
                                logger.info { "Video ${currentVideoIndex + 1} ended" }
                                if (currentVideoIndex < totalVideos - 1) {
                                    SwingUtilities.invokeLater {
                                        currentVideoIndex++
                                    }
                                }
                            }
                            
                            newMediaPlayer.setOnError {
                                logger.error { "MediaPlayer error: ${newMediaPlayer.error}" }
                            }
                            
                            // Create MediaView and Scene
                            val mediaView = MediaView(newMediaPlayer)
                            mediaView.isPreserveRatio = true
                            
                            val root = StackPane()
                            root.children.add(mediaView)
                            
                            // Set background color based on theme
                            val backgroundColor = if (darkTheme) "#2D2D2D" else "#FFFFFF"
                            root.style = "-fx-background-color: $backgroundColor;"
                            
                            val scene = Scene(root)
                            scene.fill = javafx.scene.paint.Paint.valueOf(backgroundColor)
                            jfxPanel?.scene = scene
                            
                            // Bind MediaView size to scene size
                            mediaView.fitWidthProperty().bind(scene.widthProperty())
                            mediaView.fitHeightProperty().bind(scene.heightProperty())
                            
                            mediaPlayer = newMediaPlayer
                            newMediaPlayer.play()
                            
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to load video: $videoPath" }
                        }
                    }
                } else {
                    logger.error { "Video resource not found: $videoPath" }
                }
            }
            
            // Clean up on dialog dismiss
            DisposableEffect(isVisible) {
                onDispose {
                    if (!isVisible) {
                        Platform.runLater {
                            mediaPlayer?.dispose()
                        }
                    }
                }
            }

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
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header with close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = videoTutorials[currentVideoIndex].title,
                                fontSize = 18.sp,
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

                        // Progress indicator with description
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { (currentVideoIndex + 1) / totalVideos.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                            )
                            Text(
                                text = videoTutorials[currentVideoIndex].description,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF424242)
                            )
                        }

                        // Video player card
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
                            // Video section with JavaFX MediaPlayer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                SwingPanel(
                                    background = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF2D2D2D) else androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.fillMaxSize(),
                                    factory = {
                                        JFXPanel().also { 
                                            jfxPanel = it
                                            // Initialize JavaFX Platform
                                            Platform.runLater {
                                                // Empty initialization
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Bottom action area (80dp height for all steps)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        ) {
                            // Figma Plugin link only for Step 4
                            if (currentVideoIndex == 3) {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Cursor Talk To Figma Plugin",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDarkTheme) Color.White else Color.Black
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
                                            Text("Open")
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
                            if (currentVideoIndex > 0) {
                                OutlinedButton(
                                    onClick = { 
                                        currentVideoIndex--
                                    },
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
                                repeat(totalVideos) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (index == currentVideoIndex) {
                                                    if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                                                } else {
                                                    Color.Gray.copy(alpha = 0.3f)
                                                },
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable { 
                                                currentVideoIndex = index
                                            }
                                    )
                                }
                            }

                            // Next/Finish button
                            Button(
                                onClick = {
                                    if (currentVideoIndex < totalVideos - 1) {
                                        currentVideoIndex++
                                    } else {
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                                )
                            ) {
                                Text(if (currentVideoIndex < totalVideos - 1) "Next" else "Finish")
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (currentVideoIndex < totalVideos - 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
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

data class TutorialVideo(
    val title: String,
    val description: String,
    val videoPath: String
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