package com.philkolar.volumekeeper.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * The three volume/route signals, all delivered on the engine thread ([handler]):
 *  - android.media.VOLUME_CHANGED_ACTION (protected, sent by system_server; carries the previous
 *    index — that is the pre-drop level). Only sent for the CURRENT device of the stream.
 *  - output changes: STREAM_DEVICES_CHANGED_ACTION + AudioDeviceCallback.
 *  - Settings.System volume_music_* (persisted ~500 ms after a change): a backup trigger only — the
 *    key is not readable by apps, so the runtime re-reads getStreamVolume when it fires.
 */
class AudioObserver(
    private val context: Context,
    private val audio: AudioManager,
    private val handler: Handler,
    private val onVolume: (prev: Int, new: Int, t: Long) -> Unit,
    private val onOutputs: (t: Long) -> Unit,
    private val onSettings: (name: String, t: Long) -> Unit,
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val t = SystemClock.elapsedRealtime()
            when (intent.action) {
                ACTION_VOLUME_CHANGED -> {
                    if (intent.getIntExtra(EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
                    val prev = intent.getIntExtra(EXTRA_PREV_VALUE, -1)
                    val new = intent.getIntExtra(EXTRA_VALUE, -1)
                    if (prev < 0 || new < 0) return
                    onVolume(prev, new, t)
                }
                ACTION_STREAM_DEVICES_CHANGED -> onOutputs(t)
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = onOutputs(SystemClock.elapsedRealtime())
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = onOutputs(SystemClock.elapsedRealtime())
    }

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val name = uri?.lastPathSegment ?: return
            if (name.startsWith("volume_music")) onSettings(name, SystemClock.elapsedRealtime())
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(ACTION_STREAM_DEVICES_CHANGED)
        }
        context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_NOT_EXPORTED)
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        try {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, settingsObserver)
        } catch (e: Exception) {
            Log.w(TAG, "settings observer unavailable", e)
        }
    }

    companion object {
        private const val TAG = "VolumeKeeper"
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val ACTION_STREAM_DEVICES_CHANGED = "android.media.STREAM_DEVICES_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        const val EXTRA_PREV_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
    }
}
