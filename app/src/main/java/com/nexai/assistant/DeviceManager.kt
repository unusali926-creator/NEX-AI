package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore

class DeviceManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun toggleBackgroundSentinel(enabled: Boolean = true): Boolean {
        try {
            val intent = Intent(context, NexVoiceService::class.java)
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return enabled
    }

    fun toggleBackgroundSentinel(): Boolean = toggleBackgroundSentinel(true)

    fun executeAction(actionTag: String?): String? {
        if (actionTag == null) return null actionTag == "[ACTION:SLEEP_MODE]" -> {
                toggleBackgroundSentinel(false)
                "Core systems powered down. Entering sleep mode, Sir."
            }

        return when {
            actionTag == "[ACTION:FLASHLIGHT_ON]" -> {
                setFlashlight(true)
                "Flashlight activated, Sir."
            }
            actionTag == "[ACTION:FLASHLIGHT_OFF]" -> {
                setFlashlight(false)
                "Flashlight deactivated, Sir."
            }
            actionTag == "[ACTION:BATTERY]" -> {
                val level = getBatteryPercentage()
                "Sir, battery is currently at $level percent."
            }
            actionTag == "[ACTION:OPEN_WHATSAPP]" -> {
                launchApp("com.whatsapp")
                "Opening WhatsApp, Sir."
            }
            actionTag == "[ACTION:OPEN_YOUTUBE]" -> {
                launchApp("com.google.android.youtube")
                "Opening YouTube, Sir."
            }
            actionTag == "[ACTION:OPEN_CAMERA]" -> {
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
