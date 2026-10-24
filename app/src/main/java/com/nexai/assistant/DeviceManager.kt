package com.nexai.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import java.util.Locale

class DeviceManager(private val context: Context) {

    private val TAG = "NexDeviceManager"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var cameraId: String? = null

    init {
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Camera init error: ${e.message}")
        }
    }

    // =========================================================================
    // 📲 1. DYNAMIC ANY-APP LAUNCHER (Phone ka koi bhi app scan karke kholna)
    // =========================================================================
    fun launchAnyApp(rawAppName: String): String {
        // Voice text me se extra shabd hatana (e.g., "calculator kholo" -> "calculator")
        val cleanName = rawAppName.lowercase(Locale.ROOT)
            .replace(Regex("(kholo|open|chalao|start|app|karo|bhai|please)"), "")
            .trim()

        if (cleanName.isEmpty()) {
            return "Sir, kis app ko open karna hai uska naam batayein."
        }

        val pm: PackageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Phone me installed sabhi launchable apps ki list
        val installedApps: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
        var matchedPackage: String? = null
        var matchedAppName: String? = null

        // 1st Priority: Exact App Name match (Label se jaise "Calculator", "Instagram")
        for (info in installedApps) {
            val label = info.loadLabel(pm).toString().lowercase(Locale.ROOT)
            if (label == cleanName || label.contains(cleanName) || cleanName.contains(label)) {
                matchedPackage = info.activityInfo.packageName
                matchedAppName = info.loadLabel(pm).toString()
                break
            }
        }

        // 2nd Priority: Package name fallback (jaise agar user "insta" ya "whatsapp" bole)
        if (matchedPackage == null) {
            for (info in installedApps) {
                val pkgName = info.activityInfo.packageName.lowercase(Locale.ROOT)
                if (pkgName.contains(cleanName)) {
                    matchedPackage = info.activityInfo.packageName
                    matchedAppName = info.loadLabel(pm).toString()
                    break
                }
            }
        }

        // App ko launch karna
        return if (matchedPackage != null) {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    "Opening $matchedAppName, Sir."
                } else {
                    "App open karne me dikkat aayi, Sir."
                }
            } catch (e: Exception) {
                "Error launching app: ${e.message}"
            }
        } else {
            "Sir, phone me '$cleanName' app nahi mila."
        }
    }


    // =========================================================================
    // 🔦 2. HARDWARE TOGGLES (Torch, Volume, Battery, Wi-Fi, BT)
    // =========================================================================

    // --- TORCH ON / OFF ---
    fun toggleTorch(state: Boolean): String {
        return try {
            cameraId?.let { id ->
                cameraManager.setTorchMode(id, state)
                if (state) "Flashlight turned ON, Sir." else "Flashlight turned OFF, Sir."
            } ?: "Flashlight hardware nahi mila."
        } catch (e: CameraAccessException) {
            "Camera permission ya hardware error: ${e.message}"
        } catch (e: Exception) {
            "Unable to toggle flashlight."
        }
    }

    // --- VOLUME CONTROLLER (Max / Mute / Silent) ---
    fun setVolumeMax(): String {
        return try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
            "Media volume maximum par set kar diya hai, Sir."
        } catch (e: Exception) {
            "Volume change nahi ho paya."
        }
    }

    fun setVolumeMute(): String {
        return try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            "Device ko Silent aur Mute kar diya hai, Sir."
        } catch (e: Exception) {
            "Mute karne me error aaya."
        }
    }

    // --- BATTERY REPORT ---
    fun getBatteryReport(): String {
        return try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else 0

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL

            val chargeState = if (isCharging) "charger connected hai" else "battery mode par hai"
            "Sir, battery level $percentage% hai aur device abhi $chargeState."
        } catch (e: Exception) {
            "Battery status read karne me error aaya."
        }
    }

    // --- WI-FI SETTINGS PANEL ---
    fun openWifiSettings(): String {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Wi-Fi settings, Sir."
        } catch (e: Exception) {
            "Unable to open Wi-Fi settings."
        }
    }

    // --- BLUETOOTH SETTINGS PANEL ---
    fun openBluetoothSettings(): String {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Bluetooth settings, Sir."
        } catch (e: Exception) {
            "Unable to open Bluetooth settings."
        }
    }

    // =========================================================================
    // ⏰ 3. DIALPAD & ALARMS (Bonus Shortcuts)
    // =========================================================================
    fun openDialpad(number: String = ""): String {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opening dialpad, Sir."
    }

    fun setQuickAlarm(hour: Int, minutes: Int, message: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Alarm set for $hour:$minutes, Sir."
    }
}
