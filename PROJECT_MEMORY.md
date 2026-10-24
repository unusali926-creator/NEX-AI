# 🤖 NEX AI — Project Architecture & Developer Memory

> **Note for any AI Assistant:** This is the master blueprint and current state of **Nex AI**. Read this file to immediately understand the full codebase, file dependencies, and architecture without needing prior conversation context.

---

## 📌 Project Overview
- **App Name:** Nex AI (JARVIS-Class Mobile Voice Assistant)
- **Repository:** `unusali926-creator/NEX-AI`
- **Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android SDK 34
- **Build System:** Automated Cloud CI/CD via GitHub Actions (`build.yml`)
- **Primary Design:** Iron Man Arc-Reactor Cyber HUD UI with Voice-First Interaction

---

## 🧠 AI Brain & Networking (`GeminiRepository.kt`)
- **Location:** `app/src/main/java/com/nexai/assistant/GeminiRepository.kt`
- **Constructor:** `GeminiRepository(prefs: PreferencesManager? = null, groqApiKey: String = "")`
- **Network Engine:** Uses native `java.net.HttpURLConnection` (Zero third-party library dependency to avoid build errors).
- **Dual Engine Architecture:**
  1. **Primary Engine:** Groq Cloud LLaMA 3.3 70B (ultra-fast sub-second latency, Iron Man speed).
  2. **Backup Engine:** Pollinations Text API (Zero-Key free fallback).
- **Output Return Type:** `suspend fun getResponse(chatHistory: List<ChatMessage>, userMessage: String): Result<String>` (and 1-arg overload `getResponse(userMessage)`).
- **Supported Action Tags:**
  - `[ACTION:FLASHLIGHT_ON]` / `[ACTION:FLASHLIGHT_OFF]`
  - `[ACTION:BATTERY]`
  - `[ACTION:OPEN_WHATSAPP]` / `[ACTION:OPEN_YOUTUBE]` / `[ACTION:OPEN_CAMERA]`

---

## 🎙️ Voice & Audio Engine (`VoiceManager.kt`)
- **Location:** `app/src/main/java/com/nexai/assistant/VoiceManager.kt`
- **Constructor:** `VoiceManager(context: Context, prefs: PreferencesManager? = null, onSpeechResult: ((String) -> Unit)? = null, onError: ((String) -> Unit)? = null)`
- **Speech Engine:** Android `SpeechRecognizer` running on **Main UI Thread (`Looper.getMainLooper()`)** to prevent mic freezing.
- **Language Auto-Matching:** Multi-language support (`hi-IN`, `en-IN`, `en-US`) for Hindi, English, and Hinglish.
- **Relaxed Timeouts:** 4000ms speech input window + 3000ms silence detection to eliminate false "No speech detect" errors.
- **Text-to-Speech (TTS):** 6 Voice presets (`JARVIS Deep`, `FRIDAY Female`, `Classic`, `Fast Crisp`, `Calm Soft`, `Cyber Metallic`).
- **Core Methods:**
  - `startListening()` (0-param overload for Compose UI triggers)
  - `startListening(onResult)` and `startListening(onResult, onErrorCallback)`
  - `stop()` and `stopListening()`
  - `speak(text: String)`

---

## ⚙️ Device Automation & Hardware (`DeviceManager.kt`)
- **Location:** `app/src/main/java/com/nexai/assistant/DeviceManager.kt`
- **Sentinel Control:** `toggleBackgroundSentinel(enabled: Boolean = true)` starts/stops `NexVoiceService`.
- **Hardware Features:**
  - Flashlight on/off (`CameraManager.setTorchMode`)
  - Battery capacity percentage (`BatteryManager.BATTERY_PROPERTY_CAPACITY`)
  - App Launcher (WhatsApp, YouTube, Camera intents)
  - *Note:* Automatic max-volume jump has been safely disabled.

---

## 🎨 Visual Identity & Android Manifest
- **App Icon:** `app/src/main/res/drawable/ic_nex_logo.xml` (Futuristic Arc-Reactor Neon Cyan Vector Graphic).
- **Manifest:** `app/src/main/AndroidManifest.xml`
  - Icon: `@drawable/ic_nex_logo`
  - Theme: `@android:style/Theme.Material.Light.NoActionBar`
  - Permissions: `RECORD_AUDIO`, `INTERNET`, `CAMERA`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`.

---

## 🛠️ CI/CD Workflow (`.github/workflows/build.yml`)
- Automated GitHub Actions compiler running on `ubuntu-latest`.
- Java 17 Temurin setup.
- Auto-generates missing `gradlew` wrapper automatically via `gradle wrapper`.
- Comprehensive diagnostic log extractor for immediate debugging.
- Artifact output: `NexAI-Debug-APK`.

---

## 🔮 Future Upgrade Roadmap
1. [ ] Wake-word sensitivity tuning for "Nex" / "Hey Nex".
2. [ ] Notification listener ("Kaun message kiya" WhatsApp reader integration).
3. [ ] Real-time internet search tool calling (live weather, sports, news).
