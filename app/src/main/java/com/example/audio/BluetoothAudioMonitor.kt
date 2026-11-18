package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.BluetoothDeviceRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothAudioState(
    val connectedDevice: BluetoothDeviceRoute? = null,
    val bondedDevices: List<BluetoothDeviceRoute> = emptyList(),
    val hasBluetoothPermission: Boolean = false
) {
    val isBluetoothConnected: Boolean
        get() = connectedDevice != null
}

class BluetoothAudioMonitor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(BluetoothAudioState())
    val state: StateFlow<BluetoothAudioState> = _state.asStateFlow()

    private var started = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            // AudioManager routing can lag slightly behind ACL broadcast; refresh immediately & shortly after
            refresh()
            mainHandler.postDelayed({ refresh() }, 350L)
        }
    }

    fun start() {
        if (started) {
            refresh()
            return
        }
        started = true
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        } catch (_: Exception) {
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(bluetoothReceiver, filter)
            }
        } catch (_: Exception) {
        }

        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {
        }
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {
        }
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        val permGranted = hasConnectPermission()
        val bondedList = mutableListOf<BluetoothDeviceRoute>()

        if (permGranted) {
            try {
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter
                adapter?.bondedDevices?.forEach { device ->
                    val addr = device.address?.takeIf { it.isNotBlank() }
                    val devName = device.name?.takeIf { it.isNotBlank() } ?: addr ?: "Bluetooth Audio"
                    val key = buildDeviceKey(addr, devName)
                    bondedList += BluetoothDeviceRoute(
                        key = key,
                        name = devName,
                        address = addr,
                        isConnected = false,
                        isBonded = true
                    )
                }
            } catch (_: Exception) {
            }
        }

        val activeBtDevice = detectActiveBluetoothOutput(bondedList)
        val mergedDevices = mergeConnectedAndBonded(activeBtDevice, bondedList)

        _state.value = BluetoothAudioState(
            connectedDevice = activeBtDevice,
            bondedDevices = mergedDevices,
            hasBluetoothPermission = permGranted
        )
    }

    private fun detectActiveBluetoothOutput(bonded: List<BluetoothDeviceRoute>): BluetoothDeviceRoute? {
        return try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btOutputs = outputs.filter { info -> isBluetoothAudioType(info.type) }
            // Prefer A2DP / BLE headset over SCO when multiple profiles exist
            val preferred = btOutputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
            } ?: btOutputs.firstOrNull()

            preferred?.let { info ->
                val rawName = info.productName?.toString()?.trim().orEmpty()
                val rawAddr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { info.address }.getOrNull()?.trim()?.takeIf {
                        it.isNotBlank() && it != "00:00:00:00:00:00"
                    }
                } else null

                val matchedBonded = bonded.firstOrNull { b ->
                    (rawAddr != null && b.address.equals(rawAddr, ignoreCase = true)) ||
                        (rawName.isNotBlank() && b.name.equals(rawName, ignoreCase = true))
                }

                val finalName = matchedBonded?.name
                    ?: rawName.takeIf { it.isNotBlank() }
                    ?: rawAddr
                    ?: "Bluetooth Audio"
                val finalAddr = matchedBonded?.address ?: rawAddr
                val finalKey = matchedBonded?.key ?: buildDeviceKey(finalAddr, finalName)

                BluetoothDeviceRoute(
                    key = finalKey,
                    name = finalName,
                    address = finalAddr,
                    isConnected = true,
                    isBonded = matchedBonded != null
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeConnectedAndBonded(
        connected: BluetoothDeviceRoute?,
        bonded: List<BluetoothDeviceRoute>
    ): List<BluetoothDeviceRoute> {
        val byKey = linkedMapOf<String, BluetoothDeviceRoute>()
        if (connected != null) {
            byKey[connected.key] = connected
        }
        bonded.sortedBy { it.name.lowercase() }.forEach { device ->
            val existing = byKey[device.key]
            if (existing == null) {
                byKey[device.key] = device
            } else {
                byKey[device.key] = existing.copy(isBonded = true, address = existing.address ?: device.address)
            }
        }
        return byKey.values.toList()
    }

    private fun isBluetoothAudioType(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                return true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (type == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                return true
            }
        }
        return type == AudioDeviceInfo.TYPE_HEARING_AID
    }

    companion object {
        fun buildDeviceKey(address: String?, name: String): String {
            val cleanAddr = address?.trim()?.uppercase()?.takeIf {
                it.isNotBlank() && it != "00:00:00:00:00:00"
            }
            if (cleanAddr != null) return "mac:$cleanAddr"
            val normalized = name.trim().lowercase().replace(Regex("\\s+"), "_")
            return "name:${normalized.ifBlank { "bluetooth_audio" }}"
        }
    }
}
