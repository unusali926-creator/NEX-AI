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
import android.speech.tts.UtteranceProgressListener
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
    var isSleeping = false

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
            
            // Professional British JARVIS Voice setup (Null-safe check)
            val ukLocale = Locale.UK
            val langAvailable = tts?.isLanguageAvailable(ukLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (langAvailable >= TextToSpeech.LANG_AVAILABLE) {
                tts?.language = ukLocale
            } else {
                tts?.language = Locale.ENGLISH
            }

            // JARVIS Signature Cadence (Dheemi, serious aur crisp tone)
            tts?.setPitch(0.85f)
            tts?.setSpeechRate(1.02f)

            // Echo & Mic Clash Protection
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    this@VoiceManager.stopListening()
                }

                override fun onDone(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
            })

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
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_VOICE_OUT")
        }
    }

    fun startListening() {
        if (isSleeping) return
        startListeningInternal(
            { result: String -> onSpeechResult?.invoke(result) },
            { err: String -> onError?.invoke(err) }
        )
    }

    fun startListening(onResult: (String) -> Unit) {
        if (isSleeping) return
        startListeningInternal(onResult, onError)
    }

    fun startListening(
        onResult: (String) -> Unit,
        onErrorCallback: ((String) -> Unit)?
    ) {
        if (isSleeping) return
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
                    val err = "Speech recognizer unavailable"
                    onErrorCallback?.invoke(err)
                    onError?.invoke(err)
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-GB", "en-US", "hi-IN"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    
                    // Fast timing: 1 second silence hote hi process karega
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            val err = "Mic error code: $error"
                            onErrorCallback?.invoke(err)
                            onError?.invoke(err)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spokenText = matches[0]
                            onResult(spokenText)
                            onSpeechResult?.invoke(spokenText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                onErrorCallback?.invoke("Crash: ${e.message}")
            }
        }
    }

    fun enterSleepMode() {
        isSleeping = true
        stopListening()
        speak("Entering sleep mode. Core systems powered down, Sir.")
    }

    fun wakeUp() {
        isSleeping = false
        speak("Systems restored. Ready for commands, Sir.")
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
