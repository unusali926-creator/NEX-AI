package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceManager(
    val context: Context,
    val prefs: PreferencesManager? = null,
    var onSpeechResult: ((String) -> Unit)? = null,
    var onError: ((String) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    private val pendingSpeechQueue = mutableListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        initTTS()
    }

    private fun initTTS() {
        mainHandler.post {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts?.language = Locale.ENGLISH
            setVoicePreset("JARVIS Deep")
            synchronized(pendingSpeechQueue) {
                for (text in pendingSpeechQueue) {
                    speak(text)
                }
                pendingSpeechQueue.clear()
            }
        }
    }

    fun speak(text: String) {
        mainHandler.post {
            if (!isTtsReady || tts == null) {
                synchronized(pendingSpeechQueue) {
                    pendingSpeechQueue.add(text)
                }
                return@post
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NEX_TTS_ID")
        }
    }

    fun setVoicePreset(preset: String) {
        when (preset) {
            "JARVIS Deep" -> {
                tts?.setPitch(0.75f)
                tts?.setSpeechRate(0.95f)
            }
            "FRIDAY Female" -> {
                tts?.setPitch(1.3f)
                tts?.setSpeechRate(1.05f)
            }
            "Classic" -> {
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)
            }
            "Fast Crisp" -> {
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.2f)
            }
            "Calm Soft" -> {
                tts?.setPitch(0.9f)
                tts?.setSpeechRate(0.85f)
            }
            "Cyber Metallic" -> {
                tts?.setPitch(0.6f)
                tts?.setSpeechRate(1.1f)
            }
            else -> {
                tts?.setPitch(0.8f)
                tts?.setSpeechRate(1.0f)
            }
        }
    }

    // MainActivity ke 0-parameter call ke liye
    fun startListening() {
        startListeningInternal(
            { result: String -> onSpeechResult?.invoke(result) },
            { err: String -> onError?.invoke(err) }
        )
    }

    // 1-parameter call ke liye
    fun startListening(onResult: (String) -> Unit) {
        startListeningInternal(onResult, onError)
    }

    // 2-parameters call ke liye
    fun startListening(
        onResult: (String) -> Unit,
        onErrorCallback: ((String) -> Unit)?
    ) {
        startListeningInternal(onResult, onErrorCallback)
    }

    private fun startListeningInternal(
        onResult: (String) -> Unit,
        onErrorCallback: ((String) -> Unit)?
    ) {
        mainHandler.post {
            try {
                this@VoiceManager.stopListening()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    val err = "Device par Speech Engine available nahi hai"
                    onErrorCallback?.invoke(err)
                    onError?.invoke(err)
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Aawaz detect nahi hui, dobara boliye..."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout. Boliye Sir..."
                            SpeechRecognizer.ERROR_AUDIO -> "Mic recording error"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission missing"
                            else -> "Mic code: $error"
                        }
                        onErrorCallback?.invoke(message)
                        onError?.invoke(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spokenText = matches[0]
                            onResult(spokenText)
                            onSpeechResult?.invoke(spokenText)
                        } else {
                            val err = "Aawaz detect nahi hui"
                            onErrorCallback?.invoke(err)
                            onError?.invoke(err)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                val crashErr = "Mic error: ${e.message}"
                onErrorCallback?.invoke(crashErr)
                onError?.invoke(crashErr)
            }
        }
    }

    fun stop() {
        stopListening()
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun destroy() {
        stopListening()
        mainHandler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
