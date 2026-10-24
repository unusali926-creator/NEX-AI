package com.nexai.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

    private var activeVoiceQueryHandler: ((String) -> Unit)? = null

    var isBottomHudActive by mutableStateOf(false)
    var hudText by mutableStateOf("Listening to Sir...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        geminiRepo = GeminiRepository(prefs)
        deviceManager = DeviceManager(this)
        chatHistoryManager = ChatHistoryManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        // Pre-initialize HD Studio Voice Engine immediately
        voiceManager = VoiceManager(
            context = this,
            prefs = prefs,
            onSpeechResult = { spokenText ->
                activeVoiceQueryHandler?.invoke(spokenText)
            },
            onError = { err ->
                hudText = err
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
            }
        )

        handleWakeUpIntent(intent)

        setContent {
            NexTheme {
                MainVoiceCockpitScreen(
                    prefs = prefs,
                    voiceManager = voiceManager,
                    chatHistoryManager = chatHistoryManager,
                    isBottomHudActive = isBottomHudActive,
                    hudText = hudText,
                    onCloseHud = { isBottomHudActive = false },
                    onVibrate = { triggerHaptic() },
                    onToggleMasterActive = { makeActive ->
                        executeToggleCore(makeActive)
                    },
                    onOpenNotificationSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onSendMessage = { query, history, onComplete ->
                        handleQuery(query, history, onComplete)
                    },
                    onBindVoiceHandler = { handler ->
                        activeVoiceQueryHandler = handler
                    },
                    onStartVoiceListening = {
                        startListeningSafe()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeUpIntent(intent)
    }

    private fun handleWakeUpIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("AUTO_OPEN_BOTTOM_HUD", false) == true) {
            triggerBottomHudCycle()
        }
    }

    private fun executeToggleCore(makeActive: Boolean) {
        prefs.isMasterActive = makeActive
        triggerHaptic()

        if (makeActive) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                deviceManager.toggleBackgroundSentinel(true)
            }
            voiceManager.speak("Hello Sir! Nex is online and listening.")
            Toast.makeText(this, "NEX: ACTIVE (Voice Only)", Toast.LENGTH_SHORT).show()
        } else {
            deviceManager.toggleBackgroundSentinel(false)
            voiceManager.speak("Nex entering standby, Sir.")
            Toast.makeText(this, "NEX: STANDBY", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerBottomHudCycle() {
        isBottomHudActive = true
        hudText = "Listening to Sir..."
        startListeningSafe()
    }

    fun startListeningSafe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            hudText = "Listening to Sir..."
            isBottomHudActive = true
            voiceManager.startListening()
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
                // Ignore
            }
        }
    }

    fun handleQuery(
        query: String,
        history: List<ChatMessage>,
        onComplete: (String?, String?) -> Unit
    ) {
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            val result = geminiRepo.getResponse(history, query)
            result.onSuccess { rawReply ->
                val finalMessage = when {
                    rawReply.startsWith("[ACTION:DEACTIVATE_CORE]") -> {
                        executeToggleCore(false)
                        "Deactivating systems, Sir."
                    }
                    rawReply.startsWith("[ACTION:") -> {
                        val actionTag = rawReply.substringBefore("]") + "]"
                        val actionResult = deviceManager.executeAction(actionTag)
                        actionResult ?: "Command executed, Sir."
                    }
                    else -> rawReply
                }

                hudText = finalMessage
                if (prefs.isTtsEnabled) {
                    voiceManager.speak(finalMessage)
                }
                onComplete(finalMessage, null)
            }.onFailure { err ->
                onComplete(null, err.localizedMessage ?: "Systems busy, Sir.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainVoiceCockpitScreen(
    prefs: PreferencesManager,
    voiceManager: VoiceManager,
    chatHistoryManager: ChatHistoryManager,
    isBottomHudActive: Boolean,
    hudText: String,
    onCloseHud: () -> Unit,
    onVibrate: () -> Unit,
    onToggleMasterActive: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendMessage: (String, List<ChatMessage>, (String?, String?) -> Unit) -> Unit,
    onBindVoiceHandler: (((String) -> Unit)) -> Unit,
    onStartVoiceListening: () -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isMasterActive by remember { mutableStateOf(prefs.isMasterActive) }

    fun getDynamicGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeSalutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
        return "$timeSalutation, ${prefs.ownerName}! Nex Voice Cockpit ready. Tap center Arc button to speak or command."
    }

    var sessionsList by remember { mutableStateOf(chatHistoryManager.getAllSessions()) }
    var activeSession by remember {
        val activeId = chatHistoryManager.getActiveSessionId()
        val found = sessionsList.firstOrNull { it.id == activeId }
        mutableStateOf(found ?: chatHistoryManager.createNewSession(getDynamicGreeting()))
    }

    var isThinking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun submitVoiceQuery(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isNotBlank() && !isThinking) {
            onVibrate()
            activeSession.messages.add(ChatMessage(sender = Sender.USER, text = cleanPrompt))
            isThinking = true
            chatHistoryManager.saveSession(activeSession)
            scope.launch { listState.animateScrollToItem(activeSession.messages.size - 1) }

            onSendMessage(cleanPrompt, activeSession.messages) { reply, error ->
                isThinking = false
                if (reply != null) {
                    activeSession.messages.add(ChatMessage(sender = Sender.NEX, text = reply))
                    chatHistoryManager.saveSession(activeSession)
                } else {
                    activeSession.messages.add(ChatMessage(sender = Sender.SYSTEM, text = "⚠️ $error"))
                }
                scope.launch { listState.animateScrollToItem(activeSession.messages.size - 1) }
            }
        }
    }

    LaunchedEffect(Unit) {
        onBindVoiceHandler { recognizedSpeech ->
            submitVoiceQuery(recognizedSpeech)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isMasterActive = true
            onToggleMasterActive(true)
            onStartVoiceListening()
        } else {
            Toast.makeText(context, "Microphone permission is required for Voice Commands", Toast.LENGTH_SHORT).show()
        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigCircularArcReactor(isGlowing = isMasterActive, sizeDp = 40)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Nex Voice Sessions", color = TextWhite, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                        Icon(Icons.Default.Add, contentDescription = "New Voice Session", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Voice Session", color = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Session Transcripts", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

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
                                Icon(Icons.Default.Chat, contentDescription = null, tint = if (isSelected) AccentCyan else TextMuted, modifier = Modifier.size(16.dp))
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

                    Button(
                        onClick = {
                            onVibrate()
                            showSettings = true
                            scope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = AccentCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cockpit Settings & Voice", color = TextWhite)
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    onVibrate()
                                    scope.launch { drawerState.open() }
                                }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Drawer", tint = AccentCyan)
                                }
                                IconButton(onClick = {
                                    onVibrate()
                                    val newSess = chatHistoryManager.createNewSession(getDynamicGreeting())
                                    activeSession = newSess
                                    sessionsList = chatHistoryManager.getAllSessions()
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "New Session", tint = AccentCyan)
                                }
                            }
                        },
                        title = {
                            Text("Nex Voice Cockpit", color = TextWhite, fontSize = 18.sp)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCardNavy),
                        actions = {}
                    )
                },
                containerColor = DarkBlueBackground
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // CENTER GIANT VOICE ARC REACTOR CORE BUTTON
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    onVibrate()
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        if (!isMasterActive) {
                                            isMasterActive = true
                                            onToggleMasterActive(true)
                                        }
                                        onStartVoiceListening()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            BigCircularArcReactor(isGlowing = isMasterActive || isThinking, sizeDp = 110)

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (isThinking) "NEX COMPUTING..." else if (isMasterActive) "TAP TO SPEAK COMMAND" else "TAP CORE TO ACTIVATE",
                                color = if (isMasterActive) AccentCyan else TextMuted,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Notification Access Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clickable { onOpenNotificationSettings() },
                        colors = CardDefaults.cardColors(containerColor = SurfaceAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Voice Notification Sync ('Kaun message kiya?')",
                                color = TextWhite,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Live Voice Transcripts List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        state = listState
                    ) {
                        items(activeSession.messages) { msg ->
                            ChatBubble(message = msg)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // BOTTOM 100% VOICE COCKPIT BAR (TEXT FIELD COMPLETELY REMOVED!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepCardNavy)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(30.dp))
                                .background(AccentCyan)
                                .clickable {
                                    onVibrate()
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        onStartVoiceListening()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = Color.Black, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("HOLD OR TAP TO TALK", color = Color.Black, fontSize = 14.sp)
                        }
                    }
                }
            }

            // FLOATING BOTTOM-CENTER HUD INTERFACE (During Live Speech)
            AnimatedVisibility(
                visible = isBottomHudActive,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 70.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = DeepCardNavy),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NEX LIVE HUD", color = AccentCyan, fontSize = 11.sp)
                            IconButton(onClick = onCloseHud, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        BigCircularArcReactor(isGlowing = true, sizeDp = 44)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = hudText,
                            color = TextWhite,
                            fontSize = 14.sp,
                            maxLines = 3
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        MasterSettingsDialog(
            prefs = prefs,
            voiceManager = voiceManager,
            onVibrate = onVibrate,
            onDismiss = { showSettings = false }
        )
    }
}

// Bada Gol (Circular) Glowing Arc Reactor Button
@Composable
fun BigCircularArcReactor(isGlowing: Boolean, sizeDp: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcPulse")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isGlowing) 2200 else 6500, easing = LinearEasing)
        ),
        label = "Rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isGlowing) 400 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.minDimension / 2 - 2

            drawCircle(
                color = if (isGlowing) Color(0xFF00F5D4) else Color(0xFF3A506B),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            rotate(rotation, center) {
                drawCircle(
                    color = if (isGlowing) Color(0xFF5BC0BE) else Color(0xFF1C2541),
                    radius = outerRadius * 0.72f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawLine(
                    color = if (isGlowing) Color(0xFF00F5D4) else Color(0xFF3A506B),
                    start = center,
                    end = Offset(center.x, center.y - outerRadius * 0.72f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = if (isGlowing) Color(0xFF00F5D4) else Color(0xFF3A506B),
                    start = center,
                    end = Offset(center.x - outerRadius * 0.62f, center.y + outerRadius * 0.42f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = if (isGlowing) Color(0xFF00F5D4) else Color(0xFF3A506B),
                    start = center,
                    end = Offset(center.x + outerRadius * 0.62f, center.y + outerRadius * 0.42f),
                    strokeWidth = 2.dp.toPx()
                )
            }

            drawCircle(
                color = if (isGlowing) Color.White else Color(0xFF5BC0BE),
                radius = (outerRadius * 0.30f) * pulseScale,
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
fun MasterSettingsDialog(
    prefs: PreferencesManager,
    voiceManager: VoiceManager,
    onVibrate: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(prefs.preferredLanguage) }
    var selectedVoice by remember { mutableStateOf(prefs.voiceStyle) }
    var selectedTone by remember { mutableStateOf(prefs.personalityTone) }
    var ttsEnabled by remember { mutableStateOf(prefs.isTtsEnabled) }

    val languages = listOf("Hindi", "Hinglish", "English", "Urdu", "Bengali", "Punjabi")
    val voices = listOf("JARVIS Deep", "FRIDAY Female", "Classic Nex", "Fast Crisp", "Calm Soft", "Cyber Metallic")
    val tones = listOf("Friendly Buddy", "JARVIS Tactical", "Short & Direct")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepCardNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nex Settings & Voices", color = TextWhite, fontSize = 18.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("v4.5 Voice", color = AccentCyan, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Voice Profiles (Live Sample Audio)", color = AccentCyan, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(voices) { v ->
                        FilterChip(
                            selected = selectedVoice == v,
                            onClick = {
                                onVibrate()
                                selectedVoice = v
                                prefs.voiceStyle = v
                                voiceManager.speak("Online, Sir. This is $v profile.")
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

                Text("Response Language", color = AccentCyan, fontSize = 13.sp)
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
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextWhite)
            }
        }
    )
}
