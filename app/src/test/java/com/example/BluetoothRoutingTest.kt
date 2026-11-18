package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.BluetoothAudioMonitor
import com.example.data.EqPreferencesRepository
import com.example.model.BluetoothAudioSettings
import com.example.model.BluetoothDeviceRule
import com.example.model.EqMode
import com.example.model.EqState
import com.example.model.toProfileSnapshot
import com.example.model.withProfileSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BluetoothRoutingTest {

    @Test
    fun `bluetooth settings persist auto bypass and per device custom eq rules`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EqPreferencesRepository(context)

        val customEq = EqState(
            mode = EqMode.EXPERT,
            expertBandsDb = listOf(4f, 3f, 2f, 0f, -1f, 0f, 1f, 2f, 3f, 4f),
            activePresetName = "NOTHING EAR PRO"
        ).toProfileSnapshot()

        val settings = BluetoothAudioSettings(
            autoBypassOnBluetooth = true,
            standardOutputRequiresRule = true,
            standardOutputRuleEnabled = false,
            standardOutputProfile = customEq,
            deviceRules = mapOf(
                "mac:AA:BB:CC:DD:EE:FF" to BluetoothDeviceRule(
                    deviceKey = "mac:AA:BB:CC:DD:EE:FF",
                    deviceName = "Nothing Ear (2)",
                    customEqEnabled = true,
                    profile = customEq
                )
            )
        )

        try {
            repo.saveBluetoothSettings(settings)
            val restored = repo.bluetoothSettingsFlow.first()

            assertTrue(restored.autoBypassOnBluetooth)
            assertTrue(restored.standardOutputRequiresRule)
            assertFalse(restored.standardOutputRuleEnabled)
            val deviceRule = restored.deviceRules["mac:AA:BB:CC:DD:EE:FF"]
            assertEquals("Nothing Ear (2)", deviceRule?.deviceName)
            assertTrue(deviceRule?.customEqEnabled == true)
            assertEquals("NOTHING EAR PRO", deviceRule?.profile?.presetName)
        } finally {
            repo.saveBluetoothSettings(BluetoothAudioSettings())
        }
    }

    @Test
    fun `device key normalizes mac address and fallback name`() {
        assertEquals(
            "mac:12:34:56:AB:CD:EF",
            BluetoothAudioMonitor.buildDeviceKey("12:34:56:ab:cd:ef", "Ear (2)")
        )
        assertEquals(
            "name:cmf_buds_pro",
            BluetoothAudioMonitor.buildDeviceKey(null, "CMF Buds Pro")
        )
    }

    @Test
    fun `withProfileSnapshot applies custom profile with requested master state`() {
        val base = EqState(masterEnabled = false, activePresetName = "FLAT")
        val target = EqState(
            mode = EqMode.SIMPLE,
            simpleLowDb = 5f,
            simpleMidDb = -1f,
            simpleHighDb = 3f,
            activePresetName = "BT CAR"
        ).toProfileSnapshot()

        val applied = base.withProfileSnapshot(target, masterEnabled = true)
        assertTrue(applied.masterEnabled)
        assertEquals("BT CAR", applied.activePresetName)
        assertEquals(5f, applied.simpleLowDb, 0.01f)
    }
}
