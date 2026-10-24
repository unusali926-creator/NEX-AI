package com.nexai.assistant

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var geminiRepo: GeminiRepository
    private lateinit var deviceManager: DeviceManager
    private lateinit var voiceManager: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        geminiRepo = GeminiRepository(prefs)
        deviceManager = DeviceManager(this)

        setContent {
            NexTheme {
                MainJarvisScreen(
                    prefs = prefs,
                    onSendMessage = { query, updateHistory, onComplete ->
                        handleQuery(query, updateHistory, onComplete)
                    },
                    onStartVoice = { onVoiceRecognized ->
                        voiceManager = VoiceManager(
                            context = this,
                            onSpeechResult = onVoiceRecognized,
                            onError = { err -> Toast.makeText(this, err, Toast.LENGTH_SHORT).show() }
                        )
                        voiceManager.startListening()
                    }
                )
            }
        }
    }

    private fun handleQuery(
        query: String,
        history: List<ChatMessage>,
        onComplete: (String?, String?) -> Unit
    ) {
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            val result = geminiRepo.getResponse(history, query)
            result.onSuccess { rawReply ->
                // Check if reply contains an Action Tag to execute hardware
                val finalMessage = if (rawReply.startsWith("[ACTION:")) {
                    val actionTag = rawReply.substringBefore("]") + "]"
                    val actionResult = deviceManager.executeAction(actionTag)
                    actionResult ?: "Sir, action execute kiya gaya."
                } else {
                    rawReply
                }

                if (prefs.isTtsEnabled) {
                    if (::voiceManager.isInitialized) {
                        voiceManager.speak(finalMessage)
                    }
                }
                onComplete(finalMessage, null)
            }.onFailure { err ->
                onComplete(null, err.localizedMessage ?: "Systems busy, Sir.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceManager.isInitialized) {
            voiceManager.stop()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainJarvisScreen(
    prefs: PreferencesManager,
    onSendMessage: (String, List<ChatMessage>, (String?, String?) -> Unit) -> Unit,
    onStartVoice: ((String) -> Unit) -> Unit
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = Sender.NEX,
                text = "Systems online, Sir. Nex JARVIS is operational. Flashlight, Battery, Apps aur World Knowledge commands ready hain. Awaiting orders."
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoice { voiceQuery ->
                inputText = voiceQuery
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Corner Mini Arc Reactor (Pulsing & Glowing Widget)
                        MiniArcReactor(isGlowing = isThinking)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Nex JARVIS", color = TextWhite, fontSize = 18.sp)
                            Text("Systems Active", color = AccentCyan, fontSize = 11.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCardNavy),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AccentCyan)
                    }
                }
            )
        },
        containerColor = DarkBlueBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat history list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                state = listState
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (isThinking) {
                    item {
                        Text(
                            text = "JARVIS is computing protocol...",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepCardNavy)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = AccentCyan)
                }

                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Command JARVIS (e.g. Torch on, Battery...)", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    maxLines = 4
                )

                IconButton(
                    onClick = {
                        val prompt = inputText.trim()
                        if (prompt.isNotBlank() && !isThinking) {
                            messages.add(ChatMessage(sender = Sender.USER, text = prompt))
                            inputText = ""
                            isThinking = true
                            scope.launch { listState.animateScrollToItem(messages.size - 1) }

                            onSendMessage(prompt, messages) { reply, error ->
                                isThinking = false
                                if (reply != null) {
                                    messages.add(ChatMessage(sender = Sender.NEX, text = reply))
                                } else {
                                    messages.add(ChatMessage(sender = Sender.SYSTEM, text = "⚠️ $error"))
                                }
                                scope.launch { listState.animateScrollToItem(messages.size - 1) }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = AccentCyan)
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(prefs = prefs, onDismiss = { showSettings = false })
    }
}

// Mini Glowing Corner Arc Reactor Widget (Pulsing and Rotating Core)
@Composable
fun MiniArcReactor(isGlowing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorPulse")
    
    // Rotating ticks
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isGlowing) 2000 else 6000, easing = LinearEasing)
        ),
        label = "Rotation"
    )

    // Pulsing Core Size
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isGlowing) 400 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.minDimension / 2 - 2

            // Outer Glowing Cyan Circle
            drawCircle(
                color = Color(0xFF00F5D4),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner Rotating Segments
            rotate(rotation, center) {
                drawCircle(
                    color = Color(0xFF5BC0BE),
                    radius = outerRadius * 0.7f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // 3 Futuristic Arc Points
                drawLine(
                    color = Color(0xFF00F5D4),
                    start = center,
                    end = Offset(center.x, center.y - outerRadius * 0.7f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF00F5D4),
                    start = center,
                    end = Offset(center.x - outerRadius * 0.6f, center.y + outerRadius * 0.4f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF00F5D4),
                    start = center,
                    end = Offset(center.x + outerRadius * 0.6f, center.y + outerRadius * 0.4f),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Center Bright Pulsing Core
            drawCircle(
                color = Color.White,
                radius = (outerRadius * 0.28f) * pulseScale,
                center = center
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == Sender.USER
    val isSystem = message.sender == Sender.SYSTEM

    val bubbleColor = when {
        isUser -> AccentCyan
        isSystem -> Color(0xFF801515)
        else -> DeepCardNavy
    }

    val textColor = when {
        isUser -> Color.Black
        else -> TextWhite
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(14.dp)
        ) {
            Text(text = message.text, color = textColor, fontSize = 14.sp)
        }
    }
}

@Composable
fun SettingsDialog(prefs: PreferencesManager, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(prefs.apiKey) }
    var ttsEnabled by remember { mutableStateOf(prefs.isTtsEnabled) }
    var language by remember { mutableStateOf(prefs.preferredLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepCardNavy,
        title = { Text("JARVIS Protocols", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Gemini Key (Optional)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    )
                )
                Text(
                    "Note: Key is 100% Optional. JARVIS works free without any key.",
                    fontSize = 11.sp,
                    color = AccentCyan
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("JARVIS Voice Feedback", color = TextWhite)
                    Switch(
                        checked = ttsEnabled,
                        onCheckedChange = { ttsEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tone: $language", color = TextWhite)
                    Button(
                        onClick = {
                            language = when (language) {
                                "Hinglish" -> "Hindi"
                                "Hindi" -> "English"
                                else -> "Hinglish"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceAccent)
                    ) {
                        Text("Switch")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    prefs.apiKey = key.trim()
                    prefs.isTtsEnabled = ttsEnabled
                    prefs.preferredLanguage = language
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Save Protocols", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextWhite)
            }
        }
    )
}
