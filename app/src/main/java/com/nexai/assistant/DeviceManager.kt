package com.nexai.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import java.util.Locale

class DeviceManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    // Dynamic Any-App Search & Launch (Duniya ka koi bhi app kholne ke liye)
    fun launchAnyAppByName(targetName: String): String {
        return try {
            val pm = context.packageManager
            val cleanTarget = targetName.lowercase(Locale.ROOT).trim()

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val appList = pm.queryIntentActivities(mainIntent, 0)

            var matchedPkg: String? = null
            var matchedLabel: String? = null

            for (resolveInfo in appList) {
                val label = resolveInfo.loadLabel(pm).toString().lowercase(Locale.ROOT)
                val pkg = resolveInfo.activityInfo.packageName

                if (label == cleanTarget || label.contains(cleanTarget) || cleanTarget.contains(label)) {
                    matchedPkg = pkg
                    matchedLabel = resolveInfo.loadLabel(pm).toString()
                    break
                }
            }

            if (matchedPkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(matchedPkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    return "Sir, $matchedLabel launch kar diya gaya hai."
                }
            }
            "Sir, '$targetName' is device me install nahi mila."
        } catch (e: Exception) {
            "App open karne me dikkat: ${e.localizedMessage}"
        }
    }

    // Call Pick-Up Engine
    fun acceptIncomingCall(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    @Suppress("DEPRECATION")
                    telecomManager?.acceptRingingCall()
                    "Sir, call pick up kar li hai."
                } else {
                    "Sir, call pick up karne ke liye Answer Phone permission allow karni hogi."
                }
            } else {
                "Sir, call answer feature Android 8.0+ par supported hai."
            }
        } catch (e: Exception) {
            "Call pick karne me samasya: ${e.localizedMessage}"
        }
    }

    // Call Reject / Cut Engine
    fun endOrRejectCall(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager?.endCall()
                    "Sir, call disconnect kar di gayi hai."
                } else {
                    "Sir, permission required to disconnect call."
                }
            } else {
                "Sir, call reject feature supported nahi hai."
            }
        } catch (e: Exception) {
            "Call disconnect karne me samasya: ${e.localizedMessage}"
        }
    }

    fun toggleTorch(enable: Boolean): String {
        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                ?: return "Sir, camera flash hardware detect nahi hua."
            cameraManager.setTorchMode(cameraId, enable)
            if (enable) "Sir, Flashlight on kar di gayi hai." else "Sir, Flashlight off kar di gayi hai."
        } catch (e: Exception) {
            "Sir, Flashlight error: ${e.localizedMessage}"
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

            val chargeText = if (isCharging) "Charging active" else "Discharging"
            "Sir, battery $batteryPct% hai ($chargeText). All power levels optimal."
        } catch (e: Exception) {
            "Sir, battery check nahi ho saki."
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
            "Volume adjust error."
        }
    }

    fun muteVolume(): String {
        return try {
            audioManager?.let {
                it.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                "Sir, volume silent kar diya gaya hai."
            } ?: "Audio manager uplabdh nahi hai."
        } catch (e: Exception) {
            "Volume mute error."
        }
    }

    fun searchYouTube(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Sir, YouTube par '$query' play/search kar diya hai."
        } catch (e: Exception) {
            launchAnyAppByName("YouTube")
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
            "Settings launch error."
        }
    }

    fun openDialer(): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Sir, dialpad launch kar diya hai."
        } catch (e: Exception) {
            "Dialer launch error."
        }
    }

    fun toggleBackgroundSentinel(enable: Boolean): String {
        return try {
            val serviceIntent = Intent(context, NexVoiceService::class.java)
            if (enable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                "Sir, Background Sentinel active hai."
            } else {
                context.stopService(serviceIntent)
                "Sir, Background Sentinel disabled."
            }
        } catch (e: Exception) {
            "Sentinel error: ${e.localizedMessage}"
        }
    }

    fun executeAction(actionTag: String): String? {
        val trimmed = actionTag.trim()
        val upper = trimmed.uppercase(Locale.ROOT)
        return when {
            upper.startsWith("[ACTION:CALL_ACCEPT]") -> acceptIncomingCall()
            upper.startsWith("[ACTION:CALL_REJECT]") -> endOrRejectCall()
            upper.startsWith("[ACTION:OPEN_APP:") -> {
                val appName = trimmed.substringAfter(":").substringBefore("]")
                launchAnyAppByName(appName)
            }
            upper.startsWith("[ACTION:TORCH_ON]") -> toggleTorch(true)
            upper.startsWith("[ACTION:TORCH_OFF]") -> toggleTorch(false)
            upper.startsWith("[ACTION:BATTERY]") -> getBatteryReport()
            upper.startsWith("[ACTION:VOLUME_MAX]") -> setMaxVolume()
            upper.startsWith("[ACTION:VOLUME_MUTE]") -> muteVolume()
            upper.startsWith("[ACTION:SETTINGS_WIFI]") -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
            upper.startsWith("[ACTION:SETTINGS_BLUETOOTH]") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            upper.startsWith("[ACTION:SETTINGS_MAIN]") -> openSettings(Settings.ACTION_SETTINGS, "Main")
            upper.startsWith("[ACTION:DIALPAD]") -> openDialer()
            upper.startsWith("[ACTION:SENTINEL_ON]") -> toggleBackgroundSentinel(true)
            upper.startsWith("[ACTION:SENTINEL_OFF]") -> toggleBackgroundSentinel(false)
            upper.startsWith("[ACTION:SEARCH_YOUTUBE:") -> {
                val query = trimmed.substringAfter(":").substringBefore("]")
                searchYouTube(query)
            }
            else -> null
        }
    }
}
