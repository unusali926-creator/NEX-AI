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
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            updateTtsLanguage()
            isTtsReady = true
        }
    }

    private fun getSelectedLocale(): Locale {
        return when (prefs.preferredLanguage) {
            "Hindi" -> Locale("hi", "IN")
            "English" -> Locale("en", "US")
            "Urdu" -> Locale("ur", "PK")
            "Bengali" -> Locale("bn", "IN")
            "Punjabi" -> Locale("pa", "IN")
            else -> Locale("hi", "IN") // Hinglish default
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
                tts?.setPitch(0.72f) // Deep robotic bass
                tts?.setSpeechRate(1.05f)
            }
            "FRIDAY Female" -> {
                tts?.setPitch(1.25f) // Crisp smooth female pitch
                tts?.setSpeechRate(1.0f)
            }
            "Classic Nex" -> {
                tts?.setPitch(1.0f) // Balanced natural voice
                tts?.setSpeechRate(1.0f)
            }
            "Fast Crisp" -> {
                tts?.setPitch(1.05f)
                tts?.setSpeechRate(1.25f) // Fast response speed
            }
            "Calm Soft" -> {
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(0.85f) // Relaxed & gentle
            }
            "Cyber Metallic" -> {
                tts?.setPitch(0.58f) // Heavy metallic tone
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
                            SpeechRecognizer.ERROR_NO_MATCH -> "Kuch sunai nahi diya, kripya mic daba kar saaf aur thoda tez boliye."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Samay samapt. Mic dabate hi bolna shuru karein."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording me dikkat aayi."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet slow hai, connection check karein."
                            else -> "Kripya dobara boliye."
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onSpeechResult(matches[0])
                        } else {
                            onError("Kuch sunai nahi diya, dobara boliye.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val targetLocale = getSelectedLocale()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale.toString())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale.toString())
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("Mic chalu karne me dikkat aayi: ${e.localizedMessage}")
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            updateTtsLanguage()
            applyVoicePreset()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NexTTS")
        }
    }

    fun stop() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // Cleanup
        }
    }
}
