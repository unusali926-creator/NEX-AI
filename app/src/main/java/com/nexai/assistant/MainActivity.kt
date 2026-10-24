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
import androidx.compose.material.icons.filled.PowerSettingsNew
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
    var hudSpeakingState by mutableStateOf(false)
    var hudText by mutableStateOf("Listening to Sir...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        geminiRepo = GeminiRepository(prefs)
        deviceManager = DeviceManager(this)
        chatHistoryManager = ChatHistoryManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        // Pre-initialize Voice Engine immediately
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
                MainMasterScreen(
                    prefs = prefs,
                    chatHistoryManager = chatHistoryManager,
                    isBottomHudActive = isBottomHudActive,
                    hudSpeakingState = hudSpeakingState,
                    hudText = hudText,
                    onCloseHud = { isBottomHudActive = false },
                    onVibrate = { triggerHaptic() },
                    onTestAudio = {
                        voiceManager.speak("Testing audio systems. Hello Sir, Nex speaker is fully operational!")
                        Toast.makeText(this, "Testing Audio Speaker...", Toast.LENGTH_SHORT).show()
                    },
                    onToggleMasterActive = { makeActive ->
                        toggleMasterCore(makeActive)
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

    private fun toggleMasterCore(makeActive: Boolean) {
        prefs.isMasterActive = makeActive
        deviceManager.toggleBackgroundSentinel(makeActive)
        triggerHaptic()

        if (makeActive) {
            voiceManager.speak("Hello Sir! Nex is online and listening.")
            Toast.makeText(this, "Nex AI: ACTIVE", Toast.LENGTH_SHORT).show()
        } else {
            voiceManager.speak("Nex entering standby mode, Sir.")
            Toast.makeText(this, "Nex AI: INACTIVE", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerBottomHudCycle() {
        isBottomHudActive = true
        hudSpeakingState = false
        hudText = "Listening to ${prefs.ownerName}..."
        startListeningSafe()
    }

    fun startListeningSafe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
                // Ignore haptics
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
                        toggleMasterCore(false)
                        "Nex deactivated, Sir. Call me anytime."
                    }
                    rawReply.startsWith("[ACTION:") -> {
                        val actionTag = rawReply.substringBefore("]") + "]"
                        val actionResult = deviceManager.executeAction(actionTag)
                        actionResult ?: "Action executed, Sir."
                    }
                    else -> rawReply
                }

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
fun MainMasterScreen(
    prefs: PreferencesManager,
    chatHistoryManager: ChatHistoryManager,
    isBottomHudActive: Boolean,
    hudSpeakingState: Boolean,
    hudText: String,
    onCloseHud: () -> Unit,
    onVibrate: () -> Unit,
    onTestAudio: () -> Unit,
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
        return "$timeSalutation, ${prefs.ownerName}! Nex AI online hai. Mic dabakar boliye ya type karein. Aaj kya guidance chahiye?"
    }

    var sessionsList by remember { mutableStateOf(chatHistoryManager.getAllSessions()) }
    var activeSession by remember {
        val activeId = chatHistoryManager.getActiveSessionId()
        val found = sessionsList.firstOrNull { it.id == activeId }
        mutableStateOf(found ?: chatHistoryManager.createNewSession(getDynamicGreeting()))
    }

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun submitMessage(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isNotBlank() && !isThinking) {
            onVibrate()
            activeSession.messages.add(ChatMessage(sender = Sender.USER, text = cleanPrompt))
            inputText = ""
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
            submitMessage(recognizedSpeech)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceListening()
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
                        MiniArcReactor(isGlowing = isMasterActive)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Nex AI History", color = TextWhite, fontSize = 18.sp)
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
                        Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Chat", color = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Saved Conversations", color = TextMuted, fontSize = 12.sp)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isMasterActive) "Core: ACTIVE" else "Core: STANDBY", color = AccentCyan, fontSize = 11.sp)
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                                MiniArcReactor(isGlowing = isMasterActive)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Nex AI", color = TextWhite, fontSize = 18.sp)
                                    Text(
                                        if (isMasterActive) "ACTIVE COMPANION" else "STANDBY",
                                        color = if (isMasterActive) Color(0xFF00F5D4) else TextMuted,
                                        fontSize = 10.sp
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
                    // MASTER ACTIVE / INACTIVE POWER CORE BAR WITH TEST VOICE BUTTON
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMasterActive) Color(0xFF0E3A42) else DeepCardNavy
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            isMasterActive = !isMasterActive
                                            onToggleMasterActive(isMasterActive)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isMasterActive) AccentCyan else SurfaceAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PowerSettingsNew,
                                            contentDescription = "Core Toggle",
                                            tint = if (isMasterActive) Color.Black else TextWhite
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            if (isMasterActive) "NEX: ACTIVE" else "NEX: INACTIVE",
                                            color = TextWhite,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            if (isMasterActive) "Online & Listening" else "Tap switch to Activate",
                                            color = if (isMasterActive) AccentCyan else TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = isMasterActive,
                                    onCheckedChange = { active ->
                                        isMasterActive = active
                                        onToggleMasterActive(active)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Direct Test Audio Button
                            Button(
                                onClick = onTestAudio,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔊 Test Audio Speaker (Tap to verify sound)", color = TextWhite, fontSize = 12.sp)
                            }
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
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Allow Notification Access (to ask 'Kaun message kiya?')",
                                color = TextWhite,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Pure Chat List
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
                                    text = "Nex AI is thinking & speaking...",
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
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    onStartVoiceListening()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = AccentCyan)
                        }

                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask Nex (or ask 'Kaun message kiya?')...", color = TextMuted, fontSize = 12.sp) },
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
                                submitMessage(inputText)
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = AccentCyan)
                        }
                    }
                }
            }

            // FLOATING BOTTOM-CENTER HUD INTERFACE
            AnimatedVisibility(
                visible = isBottomHudActive,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
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
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NEX HUD CORE", color = AccentCyan, fontSize = 11.sp)
                            IconButton(onClick = onCloseHud, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        MiniArcReactor(isGlowing = true)

                        Spacer(modifier = Modifier.height(12.dp))

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
            onVibrate = onVibrate,
            onDismiss = { showSettings = false }
        )
    }
}

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

    val languages = listOf("Hindi", "Hinglish", "English", "Urdu", "Bengali", "Punjabi")
    val voices = listOf("JARVIS Deep", "FRIDAY Female", "Classic Nex", "Fast Crisp", "Calm Soft", "Cyber Metallic")
    val tones = listOf("Friendly Buddy", "JARVIS Tactical", "Short & Direct")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepCardNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nex AI Controls", color = TextWhite, fontSize = 18.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("v3.8 Final", color = AccentCyan, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                    Text("Voice Spoken Responses", color = TextWhite)
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
                Text("Apply", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextWhite)
            }
        }
    )
}
