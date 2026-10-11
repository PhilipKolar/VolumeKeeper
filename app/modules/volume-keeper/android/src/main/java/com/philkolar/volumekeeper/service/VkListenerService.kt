package com.philkolar.volumekeeper.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.philkolar.volumekeeper.core.NotificationInfo
import com.philkolar.volumekeeper.core.Windows
import com.philkolar.volumekeeper.runtime.VkRuntime
import java.lang.ref.WeakReference

/**
 * The host. NotificationManagerService binds this with BIND_FOREGROUND_SERVICE from system_server,
 * which keeps the process at BOUND_FOREGROUND_SERVICE with the audio-control capability — enough for
 * Android 17's AudioHardening to accept setStreamVolume from the background (docs/csd-findings.md).
 * Must stay in the app's main process (no android:process) so the JS module sees the same runtime.
 */
class VkListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = WeakReference(this)
        VkRuntime.ensureStarted(applicationContext)
        VkRuntime.onListenerConnected()
        // A lowering that happened while we were away is old news unless it is very fresh.
        val now = System.currentTimeMillis()
        try {
            activeNotifications?.forEach { sbn ->
                if (now - sbn.postTime < Windows.STALE) VkRuntime.onNotification(snapshot(sbn))
            }
        } catch (e: Exception) {
            Log.w(TAG, "activeNotifications failed", e)
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        VkRuntime.onListenerDisconnected()
        rebind(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        VkRuntime.ensureStarted(applicationContext)
        VkRuntime.onNotification(snapshot(sbn))
    }

    companion object {
        private const val TAG = "VolumeKeeper"
        @Volatile private var instance: WeakReference<VkListenerService>? = null

        fun snapshot(sbn: StatusBarNotification): NotificationInfo {
            val n = sbn.notification
            val extras = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            val actions = n.actions?.mapNotNull { it.title?.toString() } ?: emptyList()
            return NotificationInfo(sbn.packageName, sbn.id, sbn.tag, sbn.key, sbn.postTime, n.channelId, title, text, actions)
        }

        /** Dismiss SystemUI's "Volume lowered" notification (optional setting). */
        fun cancel(key: String) {
            try {
                instance?.get()?.cancelNotification(key)
            } catch (e: Exception) {
                Log.w(TAG, "cancelNotification failed", e)
            }
        }

        fun rebind(context: Context) {
            try {
                requestRebind(ComponentName(context, VkListenerService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "requestRebind failed", e)
            }
        }
    }
}
