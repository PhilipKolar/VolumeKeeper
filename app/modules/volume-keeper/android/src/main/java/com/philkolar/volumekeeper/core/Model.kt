// PURE: no android.*, androidx.* or expo.* imports anywhere in core/ (scripts/smoke-test.sh enforces).
// Everything the decision rule needs is passed in as plain data, so it unit-tests on the JVM
// (core-tests/) exactly as it runs on the phone.
package com.philkolar.volumekeeper.core

/** Output types VolumeKeeper manages. Speaker, earpiece, HDMI, hearing aids etc. resolve to no device at all. */
enum class OutputType(val settingsSuffix: String) {
    BT_A2DP("bt_a2dp"),
    BLE_HEADSET("ble_headset"),
    BLE_BROADCAST("ble_broadcast"),
    USB_HEADSET("usb_headset"),
    USB_DEVICE("usb_device"),
    WIRED_HEADSET("headset"),
    WIRED_HEADPHONES("headphone"),

    /** Dev variant only ("treat speaker as headphones"), so simulations run without a headset. */
    SPEAKER_TEST("speaker"),
}

/** One physical output. [key] is the identity: `bt:<MAC>`, `ble:<MAC>`, `usb:<addr>`, `wired:<type>`. */
data class Device(val key: String, val type: OutputType, val name: String)

enum class Source { BROADCAST, SETTINGS, READ, SELF }

/** A STREAM_MUSIC index change on [device] (null = not a managed output), `t` in elapsedRealtime ms. */
data class VolumeChange(val t: Long, val prev: Int, val new: Int, val device: Device?, val source: Source)

enum class CsdKind { DOSE_1X, DOSE_5X, UNKNOWN }

/** A matched SystemUI "Volume lowered to safer level" notification. */
data class CsdNotice(
    /** StatusBarNotification key. */
    val key: String,
    /** sbn.postTime (wall clock), used for de-duplication and display. */
    val postWall: Long,
    /** postTime converted to elapsedRealtime at receipt. */
    val postT: Long,
    /** elapsedRealtime when the listener received it. */
    val recvT: Long,
    val kind: CsdKind,
    val id: Int,
    val channel: String?,
    val title: String?,
    val text: String?,
    val actions: List<String>,
    val simulated: Boolean,
)

/** What the adapter read from AudioManager immediately before calling into the engine. */
data class Probe(val now: Long, val device: Device?, val volume: Int, val max: Int)

data class EngineSettings(val enabled: Boolean, val excluded: Set<String>)

enum class Outcome { PENDING, RESTORED, FAILED, SKIPPED }

/** One row of the event log. Re-recorded under the same [id] as it progresses (PENDING → RESTORED/FAILED). */
data class Decision(
    val id: Long,
    val notice: CsdNotice?,
    val device: Device?,
    val before: Int?,
    val lowered: Int?,
    val restored: Int?,
    val signal: String?,
    val outcome: Outcome,
    val reason: String?,
    val tDrop: Long? = null,
    val tRestore: Long? = null,
    val tVerified: Long? = null,
) {
    /** Drop → verified restore, when both ends were observed. */
    val latencyMs: Long? get() = if (tDrop != null && tVerified != null) tVerified - tDrop else null
}

sealed interface Action {
    /** Call setStreamVolume(STREAM_MUSIC, target, 0) now, then engine.onVerify(restoreId, probe) after [Windows.VERIFY]. */
    data class Restore(val restoreId: Long, val device: Device, val target: Int) : Action

    /** Call engine.onTimer(token, probe) at elapsedRealtime [at]. */
    data class Schedule(val at: Long, val token: Long) : Action

    /** Persist this decision (insert or replace by id). */
    data class Record(val decision: Decision) : Action
}
