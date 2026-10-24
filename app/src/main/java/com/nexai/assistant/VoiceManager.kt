package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupTtsEngine()
            isTtsReady = true

            // Agar pehle se koi aawaz queue me thi (jaise Hello Sir), to turant bolo
            val queued = pendingSpeech
            if (queued != null) {
                speak(queued)
                pendingSpeech = null
            }
        } else {
            onError("TTS engine initialize nahi ho saka.")
        }
    }

    private fun setupTtsEngine() {
        // Step 1: Pehle check karo phone me Hindi hai ya nahi
        var result = tts?.setLanguage(Locale("hi", "IN"))
        
        // Step 2: Agar phone me Hindi nahi hai, to chup mat raho! Turant English (India ya US) set karo
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts?.setLanguage(Locale("en", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
        }
        tts?.setPitch(0.85f) // JARVIS style slightly deep pitch
        tts?.setSpeechRate(1.0f)
    }

    fun ensureAudibleVolume() {
        try {
            audioManager?.let {
                val currentVol = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol < (maxVol * 0.5f).toInt()) {
                    it.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75f).toInt(), AudioManager.FLAG_SHOW_UI)
                }
            }
        } catch (e: Exception) {
            // Ignore volume adjustments
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Phone me Speech Recognition available nahi hai.")
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
                            SpeechRecognizer.ERROR_NO_MATCH -> "Kuch sunai nahi diya, kripya thoda tez aur saaf boliye."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Samay samapt. Mic dabate hi bolna shuru karein."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording me dikkat."
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

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("Mic start error: ${e.localizedMessage}")
        }
    }

    fun speak(text: String) {
        ensureAudibleVolume()
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NexTTS_Alert")
        } else {
            pendingSpeech = text
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
            // Ignore cleanup
        }
    }
}
