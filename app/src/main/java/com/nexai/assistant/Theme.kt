package com.nexai.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBlueBackground = Color(0xFF0B132B)
val DeepCardNavy = Color(0xFF1C2541)
val SurfaceAccent = Color(0xFF3A506B)
val AccentCyan = Color(0xFF5BC0BE)
val TextWhite = Color(0xFFF0F4F8)
val TextMuted = Color(0xFF8D99AE)

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    background = DarkBlueBackground,
    surface = DeepCardNavy,
    onPrimary = Color.Black,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun NexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
