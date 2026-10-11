package com.philkolar.volumekeeper.runtime

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.philkolar.volumekeeper.core.Device
import com.philkolar.volumekeeper.core.OutputType

/**
 * Which output STREAM_MUSIC is playing on right now, as a [Device] — or null for anything
 * VolumeKeeper ignores (speaker, earpiece, HDMI, hearing aids, …).
 *
 * Bluetooth keys use only the last two address bytes ("bt:C1:2E"): without BLUETOOTH_CONNECT
 * Android anonymises addresses to "XX:XX:XX:XX:C1:2E", so this key is the same before and after
 * the permission is granted.
 */
class DeviceResolver(private val context: Context, private val audio: AudioManager, private val prefs: Prefs) {
    private val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()

    fun current(): Device? {
        val info = try {
            audio.getAudioDevicesForAttributes(media).firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return null
        val type = typeOf(info.type) ?: return null
        val address = info.address.orEmpty()
        val key = when (type) {
            OutputType.BT_A2DP -> "bt:" + address.takeLast(5).uppercase()
            OutputType.BLE_HEADSET, OutputType.BLE_BROADCAST -> "ble:" + address.takeLast(5).uppercase()
            OutputType.USB_HEADSET, OutputType.USB_DEVICE -> "usb:" + address.ifEmpty { info.productName?.toString().orEmpty() }
            OutputType.WIRED_HEADSET, OutputType.WIRED_HEADPHONES -> "wired:" + type.settingsSuffix
            OutputType.SPEAKER_TEST -> "speaker:test"
        }
        return Device(key, type, nameOf(info, type, address))
    }

    private fun typeOf(t: Int): OutputType? = when (t) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputType.BT_A2DP
        AudioDeviceInfo.TYPE_BLE_HEADSET -> OutputType.BLE_HEADSET
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> OutputType.BLE_BROADCAST
        AudioDeviceInfo.TYPE_USB_HEADSET -> OutputType.USB_HEADSET
        AudioDeviceInfo.TYPE_USB_DEVICE -> OutputType.USB_DEVICE
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputType.WIRED_HEADSET
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputType.WIRED_HEADPHONES
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> if (prefs.treatSpeakerAsHeadphones) OutputType.SPEAKER_TEST else null
        else -> null
    }

    private fun nameOf(info: AudioDeviceInfo, type: OutputType, address: String): String {
        if ((type == OutputType.BT_A2DP || type == OutputType.BLE_HEADSET) && hasBtConnect()) {
            try {
                val bt = context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
                val alias = bt?.alias ?: bt?.name
                if (!alias.isNullOrBlank()) return alias
            } catch (_: Exception) {
                // anonymised address or permission race: fall back to the product name
            }
        }
        val product = info.productName?.toString()
        if (!product.isNullOrBlank() && product != android.os.Build.MODEL) return product
        return when (type) {
            OutputType.BT_A2DP -> "Bluetooth headphones"
            OutputType.BLE_HEADSET, OutputType.BLE_BROADCAST -> "LE Audio headphones"
            OutputType.USB_HEADSET, OutputType.USB_DEVICE -> "USB audio"
            OutputType.WIRED_HEADSET -> "Wired headset"
            OutputType.WIRED_HEADPHONES -> "Wired headphones"
            OutputType.SPEAKER_TEST -> "Phone speaker (test)"
        }
    }

    fun hasBtConnect(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
