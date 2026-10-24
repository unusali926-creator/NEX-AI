package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

class DeviceManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isTorchOn = false

    fun toggleTorch(enable: Boolean): String {
        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return "Sir, camera flash hardware detect nahi hua."
            cameraManager.setTorchMode(cameraId, enable)
            isTorchOn = enable
            if (enable) "Sir, Flashlight on kar di gayi hai." else "Sir, Flashlight band kar di gayi hai."
        } catch (e: Exception) {
            "Sir, Flashlight control karne me dikkat aayi: ${e.localizedMessage}"
        }
    }

    fun getBatteryReport(): String {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val chargeText = if (isCharging) "Charging ho rahi hai" else "Battery discharge ho rahi hai"
            "Sir, battery level $batteryPct% hai aur $chargeText. All systems normal."
        } catch (e: Exception) {
            "Sir, battery status check karne me dikkat aayi."
        }
    }

    fun setMaxVolume(): String {
        return try {
            audioManager?.let {
                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                it.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                "Sir, media volume 100% full kar diya gaya hai."
            } ?: "Audio manager uplabdh nahi hai."
        } catch (e: Exception) {
            "Volume adjust karne me dikkat aayi."
        }
    }

    fun muteVolume(): String {
        return try {
            audioManager?.let {
                it.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                "Sir, volume mute kar diya gaya hai."
            } ?: "Audio manager uplabdh nahi hai."
        } catch (e: Exception) {
            "Volume mute karne me dikkat aayi."
        }
    }

    fun openApp(packageName: String, appName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Sir, $appName open kiya ja raha hai."
            } else {
                "Sir, $appName is phone me install nahi mila."
            }
        } catch (e: Exception) {
            "Sir, $appName launch nahi ho paya."
        }
    }

    fun openCamera(): String {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Sir, camera open kar diya hai."
        } catch (e: Exception) {
            "Sir, camera launch karne me dikkat aayi."
        }
    }

    fun openSettings(action: String, name: String): String {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Sir, $name settings open kar di gayi hai."
        } catch (e: Exception) {
            "Sir, settings launch nahi ho saki."
        }
    }

    fun openDialer(): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Sir, dialpad open kar diya hai."
        } catch (e: Exception) {
            "Sir, phone dialer open nahi hua."
        }
    }

    fun setAlarm(hour: Int, minute: Int, message: String = "Nex JARVIS Alarm"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Sir, alarm $hour:$minute par set kar diya gaya hai."
        } catch (e: Exception) {
            "Sir, alarm set nahi ho paya: ${e.localizedMessage}"
        }
    }

    fun executeAction(actionTag: String): String? {
        val trimmed = actionTag.trim().uppercase()
        return when {
            trimmed.startsWith("[ACTION:TORCH_ON]") -> toggleTorch(true)
            trimmed.startsWith("[ACTION:TORCH_OFF]") -> toggleTorch(false)
            trimmed.startsWith("[ACTION:BATTERY]") -> getBatteryReport()
            trimmed.startsWith("[ACTION:VOLUME_MAX]") -> setMaxVolume()
            trimmed.startsWith("[ACTION:VOLUME_MUTE]") -> muteVolume()
            trimmed.startsWith("[ACTION:APP_WHATSAPP]") -> openApp("com.whatsapp", "WhatsApp")
            trimmed.startsWith("[ACTION:APP_YOUTUBE]") -> openApp("com.google.android.youtube", "YouTube")
            trimmed.startsWith("[ACTION:APP_INSTAGRAM]") -> openApp("com.instagram.android", "Instagram")
            trimmed.startsWith("[ACTION:APP_CAMERA]") -> openCamera()
            trimmed.startsWith("[ACTION:SETTINGS_WIFI]") -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
            trimmed.startsWith("[ACTION:SETTINGS_BLUETOOTH]") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            trimmed.startsWith("[ACTION:SETTINGS_MAIN]") -> openSettings(Settings.ACTION_SETTINGS, "Main")
            trimmed.startsWith("[ACTION:DIALPAD]") -> openDialer()
            else -> null
        }
    }
}
