package com.treni.tracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.treni.tracker.R

object Notifier {

    private const val CHANNEL_ID = "treni_tracker_channel"
    private const val CHANNEL_NAME = "Aggiornamenti treni"

    fun creaCanale(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifiche su ritardi, fermate e arrivi dei treni monitorati"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifica(context: Context, notificationId: Int, titolo: String, corpo: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(titolo)
            .setContentText(corpo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, builder.build())
    }
}
