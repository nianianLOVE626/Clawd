package com.clawd.pet

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ClawdNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        AppState.lastNotification = sbn.notification.extras?.getCharSequence("android.text")?.toString().orEmpty()
    }
}
