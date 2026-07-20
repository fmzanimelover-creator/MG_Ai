package com.example.automation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MGAINotificationListener : NotificationListenerService() {
    companion object {
        @Volatile
        var activeListener: MGAINotificationListener? = null
        
        var onNotificationReceived: ((title: String, body: String, appName: String) -> Unit)? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeListener = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        activeListener = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            val extras = it.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            
            val appLabel = when {
                packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                packageName.contains("mms", ignoreCase = true) || 
                packageName.contains("messaging", ignoreCase = true) || 
                packageName.contains("sms", ignoreCase = true) || 
                packageName.contains("android.apps.messaging", ignoreCase = true) -> "SMS"
                packageName.contains("facebook", ignoreCase = true) || 
                packageName.contains("katana", ignoreCase = true) -> "Facebook"
                packageName.contains("instagram", ignoreCase = true) -> "Instagram"
                packageName.contains("telegram", ignoreCase = true) -> "Telegram"
                else -> null
            }
            
            if (appLabel != null && text.isNotEmpty()) {
                onNotificationReceived?.invoke(title, text, appLabel)
            }
        }
    }
}
