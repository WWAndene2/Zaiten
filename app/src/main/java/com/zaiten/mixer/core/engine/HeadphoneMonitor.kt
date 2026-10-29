package com.zaiten.mixer.core.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Indique si un casque ou des écouteurs sont branchés : la spatialisation n'a de sens qu'au casque
 * (§3.3) et se coupe automatiquement sur le haut-parleur.
 */
object HeadphoneMonitor {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )

    fun start(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        fun refresh() {
            _connected.value = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headphoneTypes }
        }
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
        }, null)
        refresh()
    }
}
