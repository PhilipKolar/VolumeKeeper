package com.philkolar.volumekeeper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.philkolar.volumekeeper.runtime.VkRuntime
import com.philkolar.volumekeeper.service.VkListenerService
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * JS bridge. Read-mostly: the UI shows what the native runtime decided; the only writes are
 * settings, per-device exclusion and the dev-variant simulation. Emits onChange({ what }) whenever
 * the runtime records something, so screens refetch.
 */
class VolumeKeeperModule : Module() {
    private val context: Context
        get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

    private val listener: (String) -> Unit = { what ->
        try {
            sendEvent("onChange", mapOf("what" to what))
        } catch (_: Throwable) {
            // JS not ready yet; the next fetch picks it up.
        }
    }

    private fun start(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun definition() = ModuleDefinition {
        Name("VolumeKeeper")
        Events("onChange")

        OnCreate {
            VkRuntime.ensureStarted(context)
            VkRuntime.addListener(listener)
        }

        OnDestroy {
            VkRuntime.removeListener(listener)
        }

        AsyncFunction("getStatus") { VkRuntime.status() }

        AsyncFunction("getDevices") { VkRuntime.store.devices() }

        AsyncFunction("getEvents") { limit: Int -> VkRuntime.store.events(limit) }

        AsyncFunction("getTrace") { VkRuntime.traceSnapshot() }

        AsyncFunction("clearEvents") {
            VkRuntime.store.clearEvents()
            listener("event")
        }

        AsyncFunction("setSetting") { name: String, value: Boolean -> VkRuntime.setSetting(name, value) }

        AsyncFunction("setDeviceExcluded") { key: String, excluded: Boolean -> VkRuntime.setExcluded(key, excluded) }

        AsyncFunction("forgetDevice") { key: String -> VkRuntime.forget(key) }

        AsyncFunction("requestRebind") { VkListenerService.rebind(context) }

        AsyncFunction("openListenerSettings") {
            val component = ComponentName(context, VkListenerService::class.java).flattenToString()
            start(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
            )
        }

        AsyncFunction("openBatterySettings") {
            start(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
        }

        AsyncFunction("openAppSettings") {
            start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }

        AsyncFunction("simulate") { order: String, gapMs: Int, from: Int?, fiveX: Boolean ->
            VkRuntime.simulate(order, gapMs.toLong(), from, fiveX)
        }
    }
}
