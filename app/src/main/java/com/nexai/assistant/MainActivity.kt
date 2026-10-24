package com.nexai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var geminiRepository: GeminiRepository
    private lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            deviceManager = DeviceManager(this)
            geminiRepository = GeminiRepository()
            
            voiceManager = VoiceManager(
                context = this,
                onSpeechResult = { userSpokenText ->
                    processUserCommand(userSpokenText)
                },
                onError = { errorMessage ->
                    voiceManager.speak("Sir, aawaz detect nahi hui. Dobara boliye.")
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            NexJarvisScreen(
                onStartListening = {
                    try {
                        voiceManager.startListening()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onStopAndPowerOff = {
                    try {
                        voiceManager.enterSleepMode()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onWakeUp = {
                    try {
                        voiceManager.wakeUp()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }
    }

    private fun processUserCommand(command: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val responseResult = geminiRepository.getResponse(command)
                val replyText = responseResult.getOrDefault("Sir, response me issue aaya.")

                // Check action tags (Flashlight, battery, etc)
                val regex = "\\[ACTION:[A-Z_]+\\]".toRegex()
                val match = regex.find(replyText)
                val actionTag = match?.value
                val cleanReply = replyText.replace(regex, "").trim()

                if (actionTag != null) {
                    val actionResult = deviceManager.executeAction(actionTag)
                    if (actionResult != null) {
                        voiceManager.speak(actionResult)
                        return@launch
                    }
                }

                voiceManager.speak(cleanReply)
            } catch (e: Exception) {
                voiceManager.speak("Sir, satellite connection interrupted.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            voiceManager.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun NexJarvisScreen(
    onStartListening: () -> Unit,
    onStopAndPowerOff: () -> Unit,
    onWakeUp: () -> Unit
) {
    var isPowerOn by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("TAP CENTER ARC-REACTOR TO TALK") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Stark HUD Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "J.A.R.V.I.S.",
                color = Color(0xFF00E5FF),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                text = "NEX CORE SYSTEM v2.0",
                color = Color(0xFF80E5FF),
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
        }

        // Center Arc-Reactor Mic Button (Cyan Glow)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(if (isPowerOn) Color(0xFF0C1929) else Color(0xFF140D0D))
                .border(4.dp, if (isPowerOn) Color(0xFF00E5FF) else Color(0xFFFF1744), CircleShape)
                .clickable {
                    if (isPowerOn) {
                        statusText = "LISTENING... SPEAK NOW SIR"
                        onStartListening()
                    } else {
                        statusText = "SYSTEM IS OFF. PRESS POWER ON FIRST"
                    }
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPowerOn) "NEX" else "OFFLINE",
                    color = if (isPowerOn) Color.White else Color(0xFFFF5252),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isPowerOn) "ONLINE (TAP)" else "SLEEP",
                    color = if (isPowerOn) Color(0xFF00E5FF) else Color.Gray,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        // Status Feedback
        Text(
            text = statusText,
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )

        // Bottom Controls: POWER ON & RED POWER OFF
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Power ON Button (Safe, No-Crash)
            Button(
                onClick = {
                    isPowerOn = true
                    statusText = "SYSTEM ONLINE, SIR"
                    onWakeUp()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "POWER ON", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            // RED POWER OFF Button (Safe Standby)
            Button(
                onClick = {
                    isPowerOn = false
                    statusText = "SYSTEM POWERED DOWN (SLEEP)"
                    onStopAndPowerOff()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "POWER OFF", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
