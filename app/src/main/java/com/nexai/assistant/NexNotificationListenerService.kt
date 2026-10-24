package com.nexai.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NexNotificationListenerService : NotificationListenerService() {

    companion object {
        var lastSender: String = ""
        var lastMessage: String = ""
        var lastApp: String = ""
        var hasNewMessage: Boolean = false

        // Yeh function TABHI chalega jab user khud aawaz dekar poochega
        fun getLatestNotificationReport(): String {
            return if (hasNewMessage && lastSender.isNotBlank()) {
                hasNewMessage = false // Padhne ke baad mark as read
                "Sir, $lastApp par $lastSender ka message aaya hai: $lastMessage"
            } else {
                "Sir, abhi koi naya unread message nahi hai."
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras ?: return

        val appName = when {
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("messaging") || packageName.contains("mms") -> "SMS"
            else -> null
        }

        // Message aane par sirf chup-chap memory me save karega, BOLEGA BILKUL NAHI!
        if (appName != null) {
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            if (title.isNotBlank() && text.isNotBlank()) {
                lastSender = title
                lastMessage = text
                lastApp = appName
                hasNewMessage = true
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
