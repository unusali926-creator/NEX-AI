package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.MediaStore

class DeviceManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun executeAction(actionTag: String?): String? {
        if (actionTag == null) return null

        return when (actionTag) {
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
            "[ACTION:VOLUME_MAX]" -> {
                setMaxVolume()
                "Audio levels at maximum, Sir."
            }
            "[ACTION:VOLUME_MUTE]" -> {
                muteVolume()
                "Audio output muted, Sir."
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

    private fun setMaxVolume() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
    }

    private fun muteVolume() {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
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
