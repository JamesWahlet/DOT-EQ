package com.example

import com.example.ai.SoundTuningAiRepository
import com.example.model.CompressorPreset
import com.example.model.EqMode
import com.example.model.EqState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundTuningAiActionTest {

    @Test
    fun `parses compound Gemini action for bass ultraBass crossover balance and bluetooth binding`() {
        val repo = SoundTuningAiRepository()
        val json = """
            {
              "assistantReply": "Ultra Bass & Crossover auf Nur-Bass gestellt, Balance rechts (+35%) und für Ear (2) gespeichert.",
              "profileName": "EAR2 BASS R",
              "applyEq": true,
              "eqMode": "EXPERT",
              "simpleLowDb": 5.5,
              "simpleMidDb": 0.0,
              "simpleHighDb": 1.5,
              "expertBandsDb": [6.0, 5.5, 4.0, 1.5, 0.0, 0.0, 0.5, 1.0, 1.5, 1.5],
              "preampDb": -1.5,
              "applyUltraBass": true,
              "ultraBassEnabled": true,
              "ultraBassLevel": 0.75,
              "applyCrossover": true,
              "crossoverLowEnabled": true,
              "crossoverMidEnabled": false,
              "crossoverHighEnabled": false,
              "crossoverLowMidHz": 150.0,
              "crossoverMidHighHz": 2800.0,
              "crossoverLowGainDb": 1.5,
              "crossoverMidGainDb": 0.0,
              "crossoverHighGainDb": 0.0,
              "crossoverSlopeDbPerOct": 24,
              "applyCompressor": true,
              "compressorEnabled": true,
              "compressorIntensity": 0.65,
              "compressorPreset": "PUNCH",
              "applyBalance": true,
              "balance": 0.35,
              "setMasterEnabled": false,
              "targetMasterEnabled": true,
              "resetFlat": false,
              "saveAsPreset": true,
              "bindToBluetooth": true,
              "bluetoothTargetDeviceName": "Ear (2)",
              "bluetoothCustomEqEnabled": true,
              "setGlobalAutoBypass": false,
              "targetGlobalAutoBypass": true
            }
        """.trimIndent()

        val action = repo.parseAssistantActionJson(json, EqState(mode = EqMode.EXPERT))

        assertTrue(action.applyEq)
        assertTrue(action.applyUltraBass)
        assertTrue(action.ultraBassEnabled)
        assertEquals(0.75f, action.ultraBassLevel, 0.01f)
        assertTrue(action.applyCrossover)
        assertTrue(action.crossoverLowEnabled)
        assertFalse(action.crossoverMidEnabled)
        assertFalse(action.crossoverHighEnabled)
        assertTrue(action.applyCompressor)
        assertTrue(action.compressorEnabled)
        assertEquals(CompressorPreset.PUNCH, action.compressorPreset)
        assertEquals(0.65f, action.compressorIntensity, 0.01f)
        assertTrue(action.applyBalance)
        assertTrue(action.saveAsPreset)
        assertTrue(action.bindToBluetooth)
        assertEquals(150.0f, action.crossoverLowMidHz, 0.1f)
        assertEquals(0.35f, action.balance, 0.01f)
        assertEquals("Ear (2)", action.bluetoothTargetDeviceName)
        assertTrue(action.bluetoothCustomEqEnabled)
        assertEquals("EAR2 BASS R", action.profileName)
        assertTrue(action.appliedBadges.any { it.contains("BT: EAR (2)") })
        assertTrue(action.appliedBadges.any { it.contains("BAL") })
        assertTrue(action.appliedBadges.any { it.contains("ULTRA BASS") })
    }

    @Test
    fun `parses Gemini song download command for Seal integration`() {
        val repo = SoundTuningAiRepository()
        val json = """
            {
              "assistantReply": "Hier sind die Download-Links für Daft Punk - Around the World im MP3-Format.",
              "profileName": "SEAL TRACK",
              "applyEq": false,
              "eqMode": "SIMPLE",
              "simpleLowDb": 0.0,
              "simpleMidDb": 0.0,
              "simpleHighDb": 0.0,
              "expertBandsDb": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
              "preampDb": 0.0,
              "applyUltraBass": false,
              "ultraBassEnabled": false,
              "ultraBassLevel": 0.55,
              "applyCrossover": false,
              "crossoverLowEnabled": true,
              "crossoverMidEnabled": true,
              "crossoverHighEnabled": true,
              "crossoverLowMidHz": 220.0,
              "crossoverMidHighHz": 2800.0,
              "crossoverLowGainDb": 0.0,
              "crossoverMidGainDb": 0.0,
              "crossoverHighGainDb": 0.0,
              "crossoverSlopeDbPerOct": 24,
              "applyCompressor": false,
              "compressorEnabled": false,
              "compressorIntensity": 0.45,
              "compressorPreset": "BALANCED",
              "applyBalance": false,
              "balance": 0.0,
              "setMasterEnabled": false,
              "targetMasterEnabled": true,
              "resetFlat": false,
              "saveAsPreset": false,
              "bindToBluetooth": false,
              "bluetoothTargetDeviceName": "",
              "bluetoothCustomEqEnabled": false,
              "setGlobalAutoBypass": false,
              "targetGlobalAutoBypass": true,
              "requestSongDownload": true,
              "songSearchQuery": "Daft Punk Around the World",
              "songUrl": "",
              "preferredDownloadFormat": "MP3"
            }
        """.trimIndent()

        val action = repo.parseAssistantActionJson(json, EqState())

        assertFalse(action.applyEq)
        assertTrue(action.requestSongDownload)
        assertEquals("Daft Punk Around the World", action.songSearchQuery)
        assertEquals("", action.songUrl)
        assertEquals("MP3", action.preferredDownloadFormat)
        assertTrue(action.appliedBadges.contains("LINK MP3"))
    }
}
