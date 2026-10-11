package com.philkolar.volumekeeper.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.philkolar.volumekeeper.runtime.Notifier
import com.philkolar.volumekeeper.runtime.VkRuntime

/**
 * Optional (Settings → Keep-alive service). A silent min-importance foreground service: "any FGS
 * grants audio control" in Android 17, and an FGS process is never cached or frozen. The fallback
 * if Google ever freezes idle notification listeners or tightens hardening for them.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        VkRuntime.ensureStarted(applicationContext)
        startForeground(Notifier.ID_KEEPALIVE, Notifier.keepAlive(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set
    }
}
