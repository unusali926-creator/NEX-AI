package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Crash-free safe toggle (No risky Android 14 background crash)
    fun toggleBackgroundSentinel(enabled: Boolean = true): Boolean {
        return enabled
    }

    fun toggleBackgroundSentinel(): Boolean = true

    fun executeAction(actionTag: String?): String? {
        if (actionTag == null) return null

        return when (actionTag) {
            "[ACTION:SLEEP_MODE]" -> {
                "Powering down. Entering sleep mode, Sir."
            }
            "[ACTION:READ_NOTIFICATION]" -> {
                NexNotificationListenerService.getLatestNotificationReport()
            }
            "[ACTION:TIME]" -> {
                val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                "Sir, abhi theek $currentTime hue hain."
            }
            "[ACTION:FLASHLIGHT_ON]" -> {
                setFlashlight(true)
                "Flashlight activated, Sir."
            }
            "[ACTION:FLASHLIGHT_OFF]" -> {
                setFlashlight(false)
                "Flashlight deactivated, Sir."
            }
            "[ACTION:BATTERY]" -> {
                val level = getBatteryPercentage()
                "Sir, battery is currently at $level percent."
            }
            "[ACTION:OPEN_WHATSAPP]" -> {
                launchApp("com.whatsapp")
                "Opening WhatsApp, Sir."
            }
            "[ACTION:OPEN_YOUTUBE]" -> {
                launchApp("com.google.android.youtube")
                "Opening YouTube, Sir."
            }
            "[ACTION:OPEN_CAMERA]" -> {
                openCamera()
                "Camera active, Sir."
            }
            else -> null
        }
    }

    private fun setFlashlight(status: Boolean) {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, status)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBatteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
