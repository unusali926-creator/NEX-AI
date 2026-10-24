package com.nexai.assistant

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var geminiRepo: GeminiRepository
    private lateinit var deviceManager: DeviceManager
    private lateinit var voiceManager: VoiceManager
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        geminiRepo = GeminiRepository(prefs)
        deviceManager = DeviceManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        setContent {
            NexTheme {
                MainNexAiScreen(
                    prefs = prefs,
                    onVibrate = { triggerHaptic() },
                    onSendMessage = { query, updateHistory, onComplete ->
                        handleQuery(query, updateHistory, onComplete)
                    },
                    onStartVoice = { onVoiceRecognized ->
                        voiceManager = VoiceManager(
                            context = this,
                            prefs = prefs,
                            onSpeechResult = onVoiceRecognized,
                            onError = { err -> Toast.makeText(this, err, Toast.LENGTH_SHORT).show() }
                        )
                        voiceManager.startListening()
                    }
                )
            }
        }
    }

    private fun triggerHaptic() {
        if (prefs.isHapticEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35)
                }
            } catch (e: Exception) {
                // Ignore haptic failure
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
                    actionResult ?: "Sir, action execute kar diya gaya hai."
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
fun MainNexAiScreen(
    prefs: PreferencesManager,
    onVibrate: () -> Unit,
    onSendMessage: (String, List<ChatMessage>, (String?, String?) -> Unit) -> Unit,
    onStartVoice: ((String) -> Unit) -> Unit
) {
    // Dynamic Time-Based Greeting (No Namaste)
    val greetingText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeSalutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
        "$timeSalutation, Sir! Nex AI online hai. JARVIS protocols aur world knowledge ready hai. Boliye, kya command hai?"
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = Sender.NEX,
                text = greetingText
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
                        MiniArcReactor(isGlowing = isThinking)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Nex AI", color = TextWhite, fontSize = 18.sp)
                            Text("JARVIS Core Active", color = AccentCyan, fontSize = 11.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCardNavy),
                actions = {
                    IconButton(onClick = {
                        onVibrate()
                        showSettings = true
                    }) {
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
                            text = "Nex is processing...",
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
                        onVibrate()
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = AccentCyan)
                }

                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Command Nex AI...", color = TextMuted, fontSize = 13.sp) },
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
                            onVibrate()
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
        AdvancedGeminiSettingsDialog(
            prefs = prefs,
            onVibrate = onVibrate,
            onClearHistory = {
                messages.clear()
                messages.add(ChatMessage(sender = Sender.NEX, text = "History cleared, Sir. Nex AI ready for new instructions."))
            },
            onDismiss = { showSettings = false }
        )
    }
}

// Mini Glowing Corner Arc Reactor Widget
@Composable
fun MiniArcReactor(isGlowing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcPulse")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isGlowing) 2000 else 6000, easing = LinearEasing)
        ),
        label = "Rotation"
    )

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

            drawCircle(
                color = Color(0xFF00F5D4),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            rotate(rotation, center) {
                drawCircle(
                    color = Color(0xFF5BC0BE),
                    radius = outerRadius * 0.7f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
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

// Gemini / ChatGPT Level Advanced Settings Screen
@Composable
fun AdvancedGeminiSettingsDialog(
    prefs: PreferencesManager,
    onVibrate: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(prefs.preferredLanguage) }
    var selectedVoice by remember { mutableStateOf(prefs.voiceStyle) }
    var selectedTone by remember { mutableStateOf(prefs.personalityTone) }
    var ttsEnabled by remember { mutableStateOf(prefs.isTtsEnabled) }
    var hapticEnabled by remember { mutableStateOf(prefs.isHapticEnabled) }

    val languages = listOf("Hindi", "Hinglish", "English", "Urdu", "Bengali", "Punjabi")
    val voices = listOf("JARVIS Deep", "FRIDAY Female", "Classic Nex", "Fast Crisp", "Calm Soft", "Cyber Metallic")
    val tones = listOf("JARVIS Tactical", "Friendly Buddy", "Short & Direct")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepCardNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nex AI Settings", color = TextWhite, fontSize = 20.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("v2.5 Pro", color = AccentCyan, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Language Selector
                Text("Response Language", color = AccentCyan, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(languages) { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = {
                                onVibrate()
                                selectedLanguage = lang
                            },
                            label = { Text(lang) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = SurfaceAccent,
                                labelColor = TextWhite
                            )
                        )
                    }
                }

                Divider(color = SurfaceAccent)

                // 2. 6 AI Voices Selector
                Text("AI Voice Profile (6 Styles)", color = AccentCyan, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(voices) { voice ->
                        FilterChip(
                            selected = selectedVoice == voice,
                            onClick = {
                                onVibrate()
                                selectedVoice = voice
                            },
                            label = { Text(voice) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = SurfaceAccent,
                                labelColor = TextWhite
                            )
                        )
                    }
                }

                Divider(color = SurfaceAccent)

                // 3. AI Personality Tone
                Text("Personality Protocol", color = AccentCyan, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tones) { tone ->
                        FilterChip(
                            selected = selectedTone == tone,
                            onClick = {
                                onVibrate()
                                selectedTone = tone
                            },
                            label = { Text(tone) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = SurfaceAccent,
                                labelColor = TextWhite
                            )
                        )
                    }
                }

                Divider(color = SurfaceAccent)

                // 4. Voice and Haptics Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Voice Spoken Feedback", color = TextWhite)
                    Switch(
                        checked = ttsEnabled,
                        onCheckedChange = {
                            onVibrate()
                            ttsEnabled = it
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Touch Feedback", color = TextWhite)
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = {
                            onVibrate()
                            hapticEnabled = it
                        }
                    )
                }

                Divider(color = SurfaceAccent)

                // 5. Clear Chat History Button
                Button(
                    onClick = {
                        onVibrate()
                        onClearHistory()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1D2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Chat", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Chat History", color = Color.White)
                }

                // 6. Zero API Key Shield Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkBlueBackground),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🛡️ Zero API Key Mode: 100% Free Lifetime. Data is encrypted & never stored.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onVibrate()
                    prefs.preferredLanguage = selectedLanguage
                    prefs.voiceStyle = selectedVoice
                    prefs.personalityTone = selectedTone
                    prefs.isTtsEnabled = ttsEnabled
                    prefs.isHapticEnabled = hapticEnabled
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Apply Protocols", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextWhite)
            }
        }
    )
}
