package com.philkolar.volumekeeper.core

/**
 * Every timing constant of the detection rule (docs/detection-rule.md). Nothing else in the
 * app may hard-code one of these. Initial values from the AOSP reading; tuned by the spikes.
 */
object Windows {
    /** A drop may arrive up to this long BEFORE the notification's postTime (5× path, SystemUI lag). */
    const val W_BEFORE = 5_000L

    /** After postTime, wait this long for the drop broadcast before falling back. */
    const val W_AFTER = 2_000L

    /** A device change this close to the drop makes it ambiguous: skip. */
    const val SETTLE = 1_500L

    /** A notification received this long after its postTime is old news (listener reconnect re-delivery). */
    const val STALE = 10_000L

    /** Read the volume back this long after a restore. */
    const val VERIFY = 300L

    /** After a restore, a second drop to RS1 from exactly the restored level within this window is the 5× echo. */
    const val ECHO = 3_000L

    /** A broadcast landing on our own restore target within this window is our own change. */
    const val SELF = 1_000L

    /** Wait this long after an output change before reading the new device's level (BT applies its remembered volume). */
    const val DEVICE_READ_DELAY = 500L

    /** Circuit breaker: more than [BREAKER_MAX] restores of one device within [BREAKER_SPAN] pauses it for [BREAKER_PAUSE]. */
    const val BREAKER_MAX = 3
    const val BREAKER_SPAN = 10 * 60_000L
    const val BREAKER_PAUSE = 30 * 60_000L

    /** How long volume changes and device changes are remembered. */
    const val HISTORY_SPAN = 60_000L
}
