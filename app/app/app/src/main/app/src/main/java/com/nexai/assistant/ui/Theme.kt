package com.nexai.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepNavy = Color(0xFF0A1128)
val SurfaceNavy = Color(0xFF1C2541)
val AccentCyan = Color(0xFF48CAE4)
val BubbleUser = Color(0xFF0077B6)
val BubbleAI = Color(0xFF1E2A4A)
val TextLight = Color(0xFFF1F5F9)
val TextMuted = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = DeepNavy,
    background = DeepNavy,
    surface = SurfaceNavy,
    onBackground = TextLight,
    onSurface = TextLight
)

@Composable
fun NexAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
