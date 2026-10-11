package com.philkolar.volumekeeper.runtime

import android.content.Context
import android.content.pm.PackageManager

/** App settings (SharedPreferences). Native owns them; JS reads and writes through the module. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vk_settings", Context.MODE_PRIVATE)

    /** The dev variant (`com.philkolar.volumekeeper.dev`): ships with the master switch off. */
    val isDevVariant: Boolean = context.packageName.endsWith(".dev")

    /** `<meta-data vk.simulation=true>`, added only to the dev variant by plugins/withDevTools.js. */
    val simulationEnabled: Boolean = try {
        val info = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )
        info.metaData?.getBoolean("vk.simulation", false) ?: false
    } catch (_: Exception) {
        false
    }

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, !isDevVariant)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    var keepAlive: Boolean
        get() = sp.getBoolean(KEY_KEEP_ALIVE, false)
        set(v) = sp.edit().putBoolean(KEY_KEEP_ALIVE, v).apply()

    var notifyOnRestore: Boolean
        get() = sp.getBoolean(KEY_NOTIFY_RESTORE, false)
        set(v) = sp.edit().putBoolean(KEY_NOTIFY_RESTORE, v).apply()

    var notifyOnFailure: Boolean
        get() = sp.getBoolean(KEY_NOTIFY_FAILURE, true)
        set(v) = sp.edit().putBoolean(KEY_NOTIFY_FAILURE, v).apply()

    var dismissSystemNotice: Boolean
        get() = sp.getBoolean(KEY_DISMISS_NOTICE, false)
        set(v) = sp.edit().putBoolean(KEY_DISMISS_NOTICE, v).apply()

    /** Dev variant only: resolve the phone speaker as a headphone (RS1 6) so simulations run without a headset. */
    var treatSpeakerAsHeadphones: Boolean
        get() = simulationEnabled && sp.getBoolean(KEY_SPEAKER_TEST, false)
        set(v) = sp.edit().putBoolean(KEY_SPEAKER_TEST, v).apply()

    fun set(name: String, value: Boolean): Boolean {
        when (name) {
            KEY_ENABLED -> enabled = value
            KEY_KEEP_ALIVE -> keepAlive = value
            KEY_NOTIFY_RESTORE -> notifyOnRestore = value
            KEY_NOTIFY_FAILURE -> notifyOnFailure = value
            KEY_DISMISS_NOTICE -> dismissSystemNotice = value
            KEY_SPEAKER_TEST -> treatSpeakerAsHeadphones = value
            else -> return false
        }
        return true
    }

    fun toMap(): Map<String, Any?> = mapOf(
        KEY_ENABLED to enabled,
        KEY_KEEP_ALIVE to keepAlive,
        KEY_NOTIFY_RESTORE to notifyOnRestore,
        KEY_NOTIFY_FAILURE to notifyOnFailure,
        KEY_DISMISS_NOTICE to dismissSystemNotice,
        KEY_SPEAKER_TEST to treatSpeakerAsHeadphones,
    )

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_KEEP_ALIVE = "keepAlive"
        const val KEY_NOTIFY_RESTORE = "notifyOnRestore"
        const val KEY_NOTIFY_FAILURE = "notifyOnFailure"
        const val KEY_DISMISS_NOTICE = "dismissSystemNotice"
        const val KEY_SPEAKER_TEST = "treatSpeakerAsHeadphones"
    }
}
