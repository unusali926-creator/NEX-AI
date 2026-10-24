# Nex AI - Project Architecture Map

## Overview
- **App Name:** Nex AI (JARVIS Capabilities)
- **Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android SDK 34
- **Repository:** unusali926-creator/NEX-AI
- **Backend AI:** Zero-Key Free Engine (Pollinations API integration)

## Core Files & Functions
1. `app/src/main/AndroidManifest.xml`
   - Permissions: Mic, Foreground Service Microphone, WakeLock, Notifications, Camera.
   - Declares `NexVoiceService` and `NexNotificationListenerService`.

2. `app/src/main/java/com/nexai/assistant/MainActivity.kt`
   - Master Core UI with Active/Inactive power switch.
   - Dynamic greeting (No Namaste: Good morning/afternoon/evening Sir).
   - Bottom-Center HUD interface for live voice feedback.
   - Gemini-style Drawer with 'New Chat' & conversation history.

3. `app/src/main/java/com/nexai/assistant/GeminiRepository.kt`
   - Proactive friendly advisor brain with multi-language support (Hindi, Hinglish, English, Urdu, Bengali, Punjabi).
   - Hardware action tags generator & "Kaun message kiya" notification intent router.

4. `app/src/main/java/com/nexai/assistant/VoiceManager.kt`
   - Android TextToSpeech engine with pending speech queue (instant "Hello Sir" playback).
   - 6 voice presets (JARVIS Deep, FRIDAY Female, Classic, Fast Crisp, Calm Soft, Cyber Metallic).
   - SpeechRecognizer with Hindi/English auto-matching.

5. `app/src/main/java/com/nexai/assistant/NexVoiceService.kt`
   - Foreground service running in background.
   - Listens for wake words ("Nex", "Hey Nex") to wake screen and open bottom-center HUD.

6. `app/src/main/java/com/nexai/assistant/NexNotificationListenerService.kt`
   - Notification reader for WhatsApp, SMS, Instagram.
   - Powers the "Kaun message kiya?" voice command.

7. `app/src/main/java/com/nexai/assistant/ChatHistoryManager.kt`
   - Multi-chat session persistence in JSON/SharedPreferences.
   - Separate conversational chat from device commands.

8. `app/src/main/java/com/nexai/assistant/DeviceManager.kt`
   - Controls Flashlight, Battery report, Volume Max/Mute, App launcher (WhatsApp, YouTube, Camera), Alarms, Dialpad.

9. `app/src/main/java/com/nexai/assistant/PreferencesManager.kt`
   - Local state manager for Master Core active state, languages, voices, and owner name.

10. `.github/workflows/build.yml`
    - Automated cloud compiler that outputs `NexAI-Debug-APK`.
