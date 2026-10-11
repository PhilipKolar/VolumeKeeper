package com.philkolar.volumekeeper.core

/**
 * The index SoundDoseHelper.onLowerVolumeToRs1() lands on, per output type:
 * safeMediaVolumeIndex(type) / 10, from this Pixel's `dumpsys audio` (mSafeMediaVolumeIndex[128]=60
 * for A2DP, [536870912]=60 BLE headset, [67108864]=60 USB headset, [4]/[8]=100 wired). STREAM_MUSIC
 * runs 0–25 on the Pixel 10.
 */
object Rs1 {
    fun of(type: OutputType): Int = when (type) {
        OutputType.WIRED_HEADSET, OutputType.WIRED_HEADPHONES -> 10
        else -> 6
    }
}
