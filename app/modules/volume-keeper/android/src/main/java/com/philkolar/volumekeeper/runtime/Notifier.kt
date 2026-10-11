package com.philkolar.volumekeeper.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.philkolar.volumekeeper.R
import com.philkolar.volumekeeper.core.CsdNotificationMatcher

/** VolumeKeeper's own notifications. Channel ids are immutable once created: bump the suffix to change one. */
object Notifier {
    const val CH_ALERTS = "vk_alerts_v1"
    const val CH_RESTORES = "vk_restores_v1"
    const val CH_KEEPALIVE = "vk_keepalive_v1"
    const val CH_SIM = "vk_sim"

    const val ID_KEEPALIVE = 1
    const val ID_FAILURE = 2
    const val ID_RESTORE = 3
    const val ID_HEALTH = 4

    fun ensureChannels(context: Context, simulation: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Problems", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A lowering VolumeKeeper could not undo, or the listener is not connected"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESTORES, "Restores", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Optional: a quiet note each time the volume is put back"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_KEEPALIVE, "Background service", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown only while the optional keep-alive service is on"
            },
        )
        if (simulation) {
            nm.createNotificationChannel(NotificationChannel(CH_SIM, "Simulated lowering (dev)", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun openApp(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun base(context: Context, channel: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_vk_notification)
            .setContentIntent(openApp(context))
            .setShowWhen(true)

    fun keepAlive(context: Context): Notification =
        base(context, CH_KEEPALIVE)
            .setContentTitle("VolumeKeeper is watching")
            .setContentText("Restores your headphone volume if Android lowers it")
            .setOngoing(true)
            .build()

    fun failure(context: Context, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(
            ID_FAILURE,
            base(context, CH_ALERTS).setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build(),
        )
    }

    fun restored(context: Context, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(ID_RESTORE, base(context, CH_RESTORES).setContentTitle("Volume restored").setContentText(text).setAutoCancel(true).build())
    }

    fun health(context: Context, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(ID_HEALTH, base(context, CH_ALERTS).setContentTitle("VolumeKeeper needs attention").setContentText(text).setAutoCancel(true).build())
    }

    /** Dev variant: a look-alike of SystemUI's notification, posted by this app with [CsdNotificationMatcher.SIM_TAG]. */
    fun simulatedCsd(context: Context, fiveX: Boolean) {
        val text = if (fiveX) "Headphone volume has exceeded the safe limit for this week"
        else "Headphone volume has been high for longer than recommended"
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(
            CsdNotificationMatcher.SIM_TAG, CsdNotificationMatcher.CSD_ID,
            base(context, CH_SIM).setContentTitle("Volume lowered to safer level").setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build(),
        )
    }

    fun cancelSimulated(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(CsdNotificationMatcher.SIM_TAG, CsdNotificationMatcher.CSD_ID)
    }
}
