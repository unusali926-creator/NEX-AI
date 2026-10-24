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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var geminiRepo: GeminiRepository
    private lateinit var deviceManager: DeviceManager
    private lateinit var chatHistoryManager: ChatHistoryManager
    private lateinit var voiceManager: VoiceManager
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        geminiRepo = GeminiRepository(prefs)
        deviceManager = DeviceManager(this)
        chatHistoryManager = ChatHistoryManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        setContent {
            NexTheme {
                MainMasterScreen(
                    prefs = prefs,
                    deviceManager = deviceManager,
                    chatHistoryManager = chatHistoryManager,
                    onVibrate = { triggerHaptic() },
                    onSendMessage = { query, history, onComplete ->
                        handleQuery(query, history, onComplete)
                    },
                    onStartVoice = { onVoiceRecognized ->
                        startVoiceInput(onVoiceRecognized)
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
                // Ignore haptics exception
            }
        }
    }

    private fun startVoiceInput(onVoiceRecognized: (String) -> Unit) {
        voiceManager = VoiceManager(
            context = this,
            prefs = prefs,
            onSpeechResult = onVoiceRecognized,
            onError = { err -> Toast.makeText(this, err, Toast.LENGTH_SHORT).show() }
        )
        voiceManager.startListening()
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
fun MainMasterScreen(
    prefs: PreferencesManager,
    deviceManager: DeviceManager,
    chatHistoryManager: ChatHistoryManager,
    onVibrate: () -> Unit,
    onSendMessage: (String, List<ChatMessage>, (String?, String?) -> Unit) -> Unit,
    onStartVoice: ((String) -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Dynamic Time-Based Greeting
    fun getDynamicGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeSalutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
        return "$timeSalutation, ${prefs.ownerName}! Nex AI online hai. Chit-chat aur voice messaging ke liye taiyar hoon. Aaj kya naya sikhna ya discuss karna chahte hain?"
    }

    var sessionsList by remember { mutableStateOf(chatHistoryManager.getAllSessions()) }
    var activeSession by remember {
        val activeId = chatHistoryManager.getActiveSessionId()
        val found = sessionsList.firstOrNull { it.id == activeId }
        mutableStateOf(found ?: chatHistoryManager.createNewSession(getDynamicGreeting()))
    }

    var currentTab by remember { mutableStateOf(0) } // 0 = Chat & Voice, 1 = Commander
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var isContinuousListening by remember { mutableStateOf(prefs.isContinuousModeEnabled) }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Command Center Log
    val commandLogs = remember {
        mutableStateListOf(
            "System initialized. Background protocols ready.",
            "Hardware Sentinel monitoring battery & torch.",
            "Say 'Torch on', 'Battery report', or 'Open WhatsApp'."
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoice { voiceQuery ->
                inputText = voiceQuery
            }
        }
    }

    // Continuous voice cycle runner
    fun triggerVoiceCycle() {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DeepCardNavy,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniArcReactor(isGlowing = false)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Nex AI History", color = TextWhite, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ➕ New Chat Button
                    Button(
                        onClick = {
                            onVibrate()
                            val newSess = chatHistoryManager.createNewSession(getDynamicGreeting())
                            activeSession = newSess
                            sessionsList = chatHistoryManager.getAllSessions()
                            scope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Chat", color = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Recent Conversations", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Saved Sessions List (Gemini Style)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(sessionsList) { session ->
                            val isSelected = session.id == activeSession.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SurfaceAccent else Color.Transparent)
                                    .clickable {
                                        onVibrate()
                                        activeSession = session
                                        chatHistoryManager.setActiveSessionId(session.id)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = if (isSelected) AccentCyan else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = session.title,
                                    color = if (isSelected) TextWhite else TextMuted,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = {
                                        onVibrate()
                                        chatHistoryManager.deleteSession(session.id)
                                        sessionsList = chatHistoryManager.getAllSessions()
                                        if (session.id == activeSession.id) {
                                            activeSession = sessionsList.firstOrNull() ?: chatHistoryManager.createNewSession(getDynamicGreeting())
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Divider(color = SurfaceAccent)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Drawer Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice Lock: ${if (prefs.isVoiceLockEnabled) "Active" else "Off"}", color = AccentCyan, fontSize = 11.sp)
                        IconButton(onClick = {
                            onVibrate()
                            showSettings = true
                            scope.launch { drawerState.close() }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AccentCyan)
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            onVibrate()
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Drawer", tint = AccentCyan)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiniArcReactor(isGlowing = isThinking || isContinuousListening)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Nex AI", color = TextWhite, fontSize = 18.sp)
                                Text(
                                    if (isContinuousListening) "Continuous Active" else "Sentinel Ready",
                                    color = AccentCyan,
                                    fontSize = 11.sp
                                )
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
                // Two Dedicated Tabs
                TabRow(
                    selectedTabIndex = currentTab,
                    containerColor = DeepCardNavy,
                    contentColor = AccentCyan
                ) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = {
                            onVibrate()
                            currentTab = 0
                        },
                        text = { Text("💬 Chat & Voice") },
                        icon = { Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = {
                            onVibrate()
                            currentTab = 1
                        },
                        text = { Text("⚡ Commander") },
                        icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (currentTab == 0) {
                    // TAB 1: PURE CHAT & VOICE (Commands nahi aayenge)
                    Column(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            state = listState
                        ) {
                            items(activeSession.messages) { msg ->
                                ChatBubble(message = msg)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            if (isThinking) {
                                item {
                                    Text(
                                        text = "Nex is formulating advice...",
                                        color = AccentCyan,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                                    )
                                }
                            }
                        }

                        // Chat Input Bar
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
                                    triggerVoiceCycle()
                                }
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = AccentCyan)
                            }

                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Chat with Nex AI...", color = TextMuted, fontSize = 13.sp) },
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
                                        activeSession.messages.add(ChatMessage(sender = Sender.USER, text = prompt))
                                        inputText = ""
                                        isThinking = true
                                        chatHistoryManager.saveSession(activeSession)
                                        scope.launch { listState.animateScrollToItem(activeSession.messages.size - 1) }

                                        onSendMessage(prompt, activeSession.messages) { reply, error ->
                                            isThinking = false
                                            if (reply != null) {
                                                activeSession.messages.add(ChatMessage(sender = Sender.NEX, text = reply))
                                                chatHistoryManager.saveSession(activeSession)

                                                // Continuous mode auto-trigger
                                                if (isContinuousListening) {
                                                    val lower = prompt.lowercase()
                                                    if (lower.contains("stop") || lower.contains("sleep") || lower.contains("bye") || lower.contains("band ho jao")) {
                                                        isContinuousListening = false
                                                    } else {
                                                        scope.launch {
                                                            delay(2800)
                                                            triggerVoiceCycle()
                                                        }
                                                    }
                                                }
                                            } else {
                                                activeSession.messages.add(ChatMessage(sender = Sender.SYSTEM, text = "⚠️ $error"))
                                            }
                                            scope.launch { listState.animateScrollToItem(activeSession.messages.size - 1) }
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = AccentCyan)
                            }
                        }
                    }
                } else {
                    // TAB 2: JARVIS COMMANDER (Direct hardware buttons + Command logs)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Hardware Controls Dashboard", color = AccentCyan, fontSize = 14.sp)

                        // Quick Hardware Action Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CommandActionCard(
                                title = "Torch Toggle",
                                icon = Icons.Default.FlashlightOn,
                                modifier = Modifier.weight(1f)
                            ) {
                                onVibrate()
                                val res = deviceManager.toggleTorch(true)
                                commandLogs.add(0, res)
                            }

                            CommandActionCard(
                                title = "Battery Check",
                                icon = Icons.Default.BatteryChargingFull,
                                modifier = Modifier.weight(1f)
                            ) {
                                onVibrate()
                                val res = deviceManager.getBatteryReport()
                                commandLogs.add(0, res)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CommandActionCard(
                                title = "Max Volume",
                                icon = Icons.Default.VolumeUp,
                                modifier = Modifier.weight(1f)
                            ) {
                                onVibrate()
                                val res = deviceManager.setMaxVolume()
                                commandLogs.add(0, res)
                            }

                            CommandActionCard(
                                title = "Camera Launch",
                                icon = Icons.Default.CameraAlt,
                                modifier = Modifier.weight(1f)
                            ) {
                                onVibrate()
                                val res = deviceManager.openCamera()
                                commandLogs.add(0, res)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CommandActionCard(
                                title = "WhatsApp",
                                icon = Icons.Default.Chat,
                                modifier = Modifier.weight(1f)
                            ) {
                                onVibrate()
                                val res = deviceManager.openApp("com.whatsapp", "WhatsApp")
                                commandLogs.add(0, res)
                            }

                            CommandActionCard(
                                title = "YouTube",
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.weight(1f)
                            ) {
                                onVibrate()
                                val res = deviceManager.openApp("com.google.android.youtube", "YouTube")
                                commandLogs.add(0, res)
                            }
                        }

                        // Background Sentinel Status Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepCardNavy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Off-Screen 'Nex' Sentinel", color = TextWhite, fontSize = 14.sp)
                                    Text("Screen off me 'Nex' sun kar wake karega", color = TextMuted, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = prefs.isBackgroundWakeEnabled,
                                    onCheckedChange = { enable ->
                                        onVibrate()
                                        prefs.isBackgroundWakeEnabled = enable
                                        val res = deviceManager.toggleBackgroundSentinel(enable)
                                        commandLogs.add(0, res)
                                    }
                                )
                            }
                        }

                        Text("Commander Terminal Logs", color = AccentCyan, fontSize = 13.sp)

                        // Logs List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DeepCardNavy)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            commandLogs.take(8).forEach { log ->
                                Text("⚡ $log", color = TextWhite, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        MasterSettingsDialog(
            prefs = prefs,
            onVibrate = onVibrate,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun CommandActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DeepCardNavy),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = AccentCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = TextWhite, fontSize = 13.sp)
        }
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

// Master Settings Panel
@Composable
fun MasterSettingsDialog(
    prefs: PreferencesManager,
    onVibrate: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(prefs.preferredLanguage) }
    var selectedVoice by remember { mutableStateOf(prefs.voiceStyle) }
    var selectedTone by remember { mutableStateOf(prefs.personalityTone) }
    var ttsEnabled by remember { mutableStateOf(prefs.isTtsEnabled) }
    var continuousEnabled by remember { mutableStateOf(prefs.isContinuousModeEnabled) }
    var voiceLockEnabled by remember { mutableStateOf(prefs.isVoiceLockEnabled) }

    val languages = listOf("Hindi", "Hinglish", "English", "Urdu", "Bengali", "Punjabi")
    val voices = listOf("JARVIS Deep", "FRIDAY Female", "Classic Nex", "Fast Crisp", "Calm Soft", "Cyber Metallic")
    val tones = listOf("Friendly Buddy", "JARVIS Tactical", "Short & Direct")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepCardNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nex AI Master Controls", color = TextWhite, fontSize = 18.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("v3.0 Ultra", color = AccentCyan, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Language Selector
                Text("Language", color = AccentCyan, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                // 6 Voices Selector
                Text("Voice Profile", color = AccentCyan, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(voices) { v ->
                        FilterChip(
                            selected = selectedVoice == v,
                            onClick = {
                                onVibrate()
                                selectedVoice = v
                            },
                            label = { Text(v) },
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

                // Personality
                Text("Personality Advisor", color = AccentCyan, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tones) { t ->
                        FilterChip(
                            selected = selectedTone == t,
                            onClick = {
                                onVibrate()
                                selectedTone = t
                            },
                            label = { Text(t) },
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

                // Continuous Listening Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Continuous Mode", color = TextWhite, fontSize = 14.sp)
                        Text("Bina ruke continuous baatein karein", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = continuousEnabled,
                        onCheckedChange = {
                            onVibrate()
                            continuousEnabled = it
                        }
                    )
                }

                // Voice Lock Security Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice Lock (Sir Only)", color = TextWhite, fontSize = 14.sp)
                        Text("Sirf Sir ki aawaz se command lega", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = voiceLockEnabled,
                        onCheckedChange = {
                            onVibrate()
                            voiceLockEnabled = it
                        }
                    )
                }

                // TTS Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spoken Audio Feedback", color = TextWhite)
                    Switch(
                        checked = ttsEnabled,
                        onCheckedChange = {
                            onVibrate()
                            ttsEnabled = it
                        }
                    )
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
                    prefs.isContinuousModeEnabled = continuousEnabled
                    prefs.isVoiceLockEnabled = voiceLockEnabled
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Apply Protocols", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextWhite)
            }
        }
    )
}
