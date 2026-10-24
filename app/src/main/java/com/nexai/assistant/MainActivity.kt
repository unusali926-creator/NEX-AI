package com.nexai.assistant

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var geminiRepo: GeminiRepository
    private lateinit var voiceManager: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        geminiRepo = GeminiRepository(prefs)

        setContent {
            NexTheme {
                MainChatScreen(
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
            result.onSuccess { reply ->
                if (prefs.isTtsEnabled) {
                    voiceManager.speak(reply)
                }
                onComplete(reply, null)
            }.onFailure { err ->
                onComplete(null, err.localizedMessage ?: "Unknown error occurred.")
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
fun MainChatScreen(
    prefs: PreferencesManager,
    onSendMessage: (String, List<ChatMessage>, (String?, String?) -> Unit) -> Unit,
    onStartVoice: ((String) -> Unit) -> Unit
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = Sender.NEX,
                text = "Namaste! I'm Nex, your AI buddy. Kaise hain aap? How can I help you today?"
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
                title = { Text("Nex AI", color = TextWhite, fontSize = 20.sp) },
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (isThinking) {
                    item {
                        Text(
                            text = "Nex is thinking...",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }

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
                    placeholder = { Text("Ask Nex in Hindi, English...", color = TextMuted) },
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
            Text(text = message.text, color = textColor, fontSize = 15.sp)
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
        title = { Text("Settings & Privacy", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Gemini API Key", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    )
                )
                Text(
                    "Your key stays strictly on this device. Get a free key at aistudio.google.com.",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spoken Voice Responses", color = TextWhite)
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
                    Text("Default Tone: $language", color = TextWhite)
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
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextWhite)
            }
        }
    )
}
