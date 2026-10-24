package com.nexai.assistant

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils

data class ReceivedMessage(
    val sender: String,
    val message: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)

class NexNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // Ignore self

        val extras = sbn.notification?.extras ?: return
        val isOngoing = sbn.isOngoing
        if (isOngoing) return // Ignore background downloads, music player

        val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (!title.isNullOrBlank() && !text.isNullOrBlank()) {
            val appName = when {
                pkg.contains("whatsapp") -> "WhatsApp"
                pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms") -> "SMS"
                pkg.contains("instagram") -> "Instagram"
                pkg.contains("telegram") -> "Telegram"
                else -> "Message"
            }

            recentMessages.add(0, ReceivedMessage(sender = title, message = text, appName = appName))
            if (recentMessages.size > 15) {
                recentMessages.removeAt(recentMessages.size - 1)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    companion object {
        var isConnected = false
        val recentMessages = mutableListOf<ReceivedMessage>()

        fun getRecentMessagesSummary(): String {
            if (recentMessages.isEmpty()) {
                return "Sir, abhi koi naya unread message nahi aaya hai."
            }
            val latest = recentMessages.take(3)
            val sb = StringBuilder("Sir, naye messages ye hain:\n")
            latest.forEachIndexed { index, msg ->
                sb.append("${index + 1}. ${msg.sender} ne ${msg.appName} par bheja hai: '${msg.message}'.\n")
            }
            return sb.toString().trim()
        }

        fun isAccessGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":").toTypedArray()
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(packageName, cn.packageName)) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
