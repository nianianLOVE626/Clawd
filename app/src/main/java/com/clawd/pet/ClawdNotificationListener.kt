package com.clawd.pet

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class NotificationItem(val packageName:String,val title:String,val text:String,val time:Long)
object NotificationStore { @Volatile var latest:NotificationItem? = null }

class ClawdNotificationListener:NotificationListenerService() {
    override fun onNotificationPosted(sbn:StatusBarNotification) {
        val e=sbn.notification.extras
        NotificationStore.latest=NotificationItem(
            sbn.packageName,
            e.getString("android.title") ?: "",
            e.getCharSequence("android.text")?.toString() ?: "",
            System.currentTimeMillis()
        )
    }
}
