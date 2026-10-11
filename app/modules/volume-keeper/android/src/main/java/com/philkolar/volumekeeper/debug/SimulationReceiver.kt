package com.philkolar.volumekeeper.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.philkolar.volumekeeper.runtime.Prefs
import com.philkolar.volumekeeper.runtime.VkRuntime

/**
 * Dev variant only: declared by plugins/withDevTools.js with android:permission="android.permission.DUMP",
 * so only adb/shell can send it. Inert anywhere the vk.simulation meta-data is missing. Driven by
 * scripts/vk.sh:
 *   op=simulate  order=drop-first|notif-first|drop-only|notif-only  gap=<ms>  from=<level>  kind=1x|5x
 *   op=set       value=<level>          (S3: background setStreamVolume + read-back, logged as "test")
 *   op=speaker   on=true|false          (treat the phone speaker as headphones, RS1 6)
 *   op=enable    on=true|false          (master switch)
 */
class SimulationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VkRuntime.ensureStarted(context)
        if (!VkRuntime.prefs.simulationEnabled) return
        when (intent.getStringExtra("op") ?: "simulate") {
            "simulate" -> VkRuntime.simulate(
                order = intent.getStringExtra("order") ?: "drop-first",
                gapMs = intent.getIntExtra("gap", 300).toLong(),
                from = if (intent.hasExtra("from")) intent.getIntExtra("from", 0) else null,
                fiveX = intent.getStringExtra("kind") == "5x",
            )
            "set" -> VkRuntime.testSet(intent.getIntExtra("value", 10))
            "speaker" -> VkRuntime.setSetting(Prefs.KEY_SPEAKER_TEST, intent.getStringExtra("on") != "false")
            "enable" -> VkRuntime.setSetting(Prefs.KEY_ENABLED, intent.getStringExtra("on") != "false")
        }
    }
}
