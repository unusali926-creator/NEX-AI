package com.nexai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

class NexVoiceService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    companion object {
        private const val CHANNEL_ID = "NexVoiceSentinelChannel"
        private const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        startWakeWordListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isListening) {
            startWakeWordListening()
        }
        return START_STICKY // Android agar service kill kare toh khud wapas start kare
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nex AI Sentinel Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nex AI listens for wake-word 'Hey Nex' in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nex AI Core Active")
            .setContentText("Listening for 'Hey Nex' command...")
            .setSmallIcon(R.drawable.ic_nex_logo)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startWakeWordListening() {
        mainHandler.post {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.destroy()
                }

                if (!SpeechRecognizer.isRecognitionAvailable(this)) return@post

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "hi-IN", "en-IN"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        // Agar user chup raha ya timeout hua, toh chupchap wapas restart karega
                        restartListeningDelay()
                    }

                    override fun onResults(results: Bundle?) {
                        handleSpeechResults(results)
                        restartListeningDelay()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        handleSpeechResults(partialResults)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                restartListeningDelay()
            }
        }
    }

    private fun handleSpeechResults(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (text in matches) {
            val lower = text.lowercase(Locale.ROOT)
            // Wake Word Detection ("nex", "hey nex", "hello nex")
            if (lower.contains("nex") || lower.contains("hey nex") || lower.contains("hello nex")) {
                triggerAppWakeUp()
                break
            }
        }
    }

    private fun triggerAppWakeUp() {
        try {
            // App ko screen par layega
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("WAKE_TRIGGERED", true)
            }
            startActivity(openAppIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restartListeningDelay() {
        mainHandler.postDelayed({
            if (speechRecognizer != null) {
                startWakeWordListening()
            }
        }, 1000) // 1 second ka gap dekar loop restart
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        mainHandler.post {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
