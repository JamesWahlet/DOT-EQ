package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ai.VoiceCaptureState
import com.example.audio.BluetoothAudioState
import com.example.auth.AuthenticatedUser
import com.example.model.AudioPreset
import com.example.model.BluetoothAudioSettings
import com.example.model.BluetoothDeviceRoute
import com.example.model.CompressorSettings
import com.example.model.CrossoverSettings
import com.example.model.EqMode
import com.example.model.EqState
import com.example.ui.components.BluetoothConnectionPopupDialog
import com.example.ui.screens.AiTunerScreen
import com.example.ui.theme.DotEqTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Turn11ComponentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `bluetooth connection popup renders device info and handles actions`() {
        var customToggled = false
        var savedCustom = false
        var closed = false

        val device = BluetoothDeviceRoute(
            key = "AA:BB:CC:11:22:33",
            name = "Nothing Ear (a)",
            address = "AA:BB:CC:11:22:33",
            isConnected = true,
            isBonded = true
        )
        val presets = listOf(
            AudioPreset(
                id = "live",
                name = "Live",
                tag = "CUSTOM",
                description = "Preset",
                mode = EqMode.SIMPLE,
                simpleLowDb = 2f,
                simpleMidDb = 0f,
                simpleHighDb = 2f,
                expertBandsDb = List(10) { 0f },
                preampDb = 0f,
                crossover = CrossoverSettings(),
                compressor = CompressorSettings(),
                balance = 0f
            )
        )

        composeRule.setContent {
            DotEqTheme(themeMode = ThemeMode.DARK) {
                BluetoothConnectionPopupDialog(
                    device = device,
                    bluetoothSettings = BluetoothAudioSettings(autoBypassOnBluetooth = true),
                    presets = presets,
                    onToggleCustomRule = { customToggled = true },
                    onAssignCurrentEq = { savedCustom = true },
                    onAssignPreset = {},
                    onOpenSettings = {},
                    onDismiss = { closed = true }
                )
            }
        }

        composeRule.onNodeWithTag("bluetooth_popup_window").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing Ear (a)").assertIsDisplayed()
        composeRule.onNodeWithTag("bt_popup_custom_switch").performClick()
        assertTrue(customToggled)

        composeRule.onNodeWithTag("bt_popup_save_custom_button").performClick()
        assertTrue(savedCustom)
        assertTrue(closed)
    }

    @Test
    fun `ai tuner screen renders voice button and active waveform pill when listening`() {
        var stopPressed = false
        var cancelPressed = false

        val fakeUser = AuthenticatedUser(
            uid = "user-123",
            displayName = "Nothing Studio",
            email = "studio@example.com"
        )

        composeRule.setContent {
            DotEqTheme(themeMode = ThemeMode.DARK) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AiTunerScreen(
                    authUser = fakeUser,
                    isAuthLoading = false,
                    authError = null,
                    eqState = EqState(),
                    bluetoothState = BluetoothAudioState(),
                    chatMessages = emptyList(),
                    isSending = false,
                    voiceState = VoiceCaptureState(
                        isListening = true,
                        amplitude = 0.6f,
                        partialTranscript = "Mehr Bass und Balance rechts"
                    ),
                    onSignInGoogle = {},
                    onSignOutGoogle = {},
                    onSendMessage = {},
                    onStartVoiceInput = {},
                    onStopAndSendVoice = { stopPressed = true },
                    onCancelVoice = { cancelPressed = true },
                    onResetChat = {},
                    onApplyRecommendation = {},
                    onSaveAiPreset = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("ai_voice_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ai_voice_listening_pill").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mehr Bass und Balance rechts").assertIsDisplayed()

        composeRule.onNodeWithTag("ai_voice_stop_button").performClick()
        assertTrue(stopPressed)

        composeRule.onNodeWithTag("ai_voice_cancel_button").performClick()
        assertTrue(cancelPressed)
    }
}
