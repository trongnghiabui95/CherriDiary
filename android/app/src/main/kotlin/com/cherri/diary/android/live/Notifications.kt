package com.cherri.diary.android.live

import android.app.*
import android.content.Intent
import com.cherri.diary.android.ui.MainActivity

fun Service.liveNotification(message: String): Notification {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("cherri_live", "Cherri Diary Live", NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val stop = PendingIntent.getService(this, 2, Intent(this, FloatingWindowService::class.java).setAction(FloatingWindowService.STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return Notification.Builder(this, "cherri_live").setSmallIcon(android.R.drawable.ic_menu_edit)
        .setContentTitle("Cherri Diary").setContentText(message).setContentIntent(open).setOngoing(true)
        .addAction(Notification.Action.Builder(null, "Tắt nút nổi", stop).build()).build()
}
