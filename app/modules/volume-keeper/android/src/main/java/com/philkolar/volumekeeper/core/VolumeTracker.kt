package com.philkolar.volumekeeper.core

data class TrackedLevel(val level: Int, val at: Long, val source: Source)

/**
 * Last observed STREAM_MUSIC index per device key, for THIS process lifetime only. Levels persisted
 * by the store are for display; they are never loaded back here, so a restart can never restore a
 * stale level (the "unknown pre-drop level" case does nothing instead).
 */
class VolumeTracker {
    private val levels = HashMap<String, TrackedLevel>()

    fun observe(device: Device, level: Int, at: Long, source: Source) {
        levels[device.key] = TrackedLevel(level, at, source)
    }

    operator fun get(key: String): TrackedLevel? = levels[key]

    fun snapshot(): Map<String, TrackedLevel> = HashMap(levels)
}
