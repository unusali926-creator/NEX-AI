package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val onSpeechResult: (String) -> Unit,
    private val onError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeech: String? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            updateTtsLanguage()
            isTtsReady = true

            val textToSpeak = pendingSpeech
            if (textToSpeak != null) {
                speak(textToSpeak)
                pendingSpeech = null
            }
        }
    }

    private fun getSelectedLocale(): Locale {
        return when (prefs.preferredLanguage) {
            "Hindi" -> Locale("hi", "IN")
            "English" -> Locale("en", "US")
            "Urdu" -> Locale("ur", "PK")
            "Bengali" -> Locale("bn", "IN")
            "Punjabi" -> Locale("pa", "IN")
            else -> Locale("hi", "IN")
        }
    }

    private fun updateTtsLanguage() {
        val targetLocale = getSelectedLocale()
        val result = tts?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.language = Locale.ENGLISH
        }
    }

    private fun applyVoicePreset() {
        when (prefs.voiceStyle) {
            "JARVIS Deep" -> {
                tts?.setPitch(0.72f)
                tts?.setSpeechRate(1.05f)
            }
            "FRIDAY Female" -> {
                tts?.setPitch(1.25f)
                tts?.setSpeechRate(1.0f)
            }
            "Classic Nex" -> {
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)
            }
            "Fast Crisp" -> {
                tts?.setPitch(1.05f)
                tts?.setSpeechRate(1.25f)
            }
            "Calm Soft" -> {
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(0.85f)
            }
            "Cyber Metallic" -> {
                tts?.setPitch(0.58f)
                tts?.setSpeechRate(1.15f)
            }
            else -> {
                tts?.setPitch(0.75f)
                tts?.setSpeechRate(1.05f)
            }
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Google Speech Recognition engine is device me available nahi hai.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Kuch sunai nahi diya, kripya saaf boliye."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Samay samapt. Kripya mic dabakar turant boliye."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording me dikkat aayi."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet check karein."
                            else -> "Kripya dobara boliye."
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onSpeechResult(matches[0])
                        } else {
                            onError("Kuch sunai nahi diya.")
                        }
                    }

                    override fun onPartialResults(partialResults:
