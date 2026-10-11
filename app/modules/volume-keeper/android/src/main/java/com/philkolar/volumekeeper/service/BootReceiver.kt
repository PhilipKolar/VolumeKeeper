package com.philkolar.volumekeeper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.philkolar.volumekeeper.runtime.VkRuntime

/** Boot and app update: start the runtime, ask for the listener binding, start keep-alive if on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VkRuntime.ensureStarted(context)
        VkListenerService.rebind(context)
        VkRuntime.applyKeepAlive()
    }
}
