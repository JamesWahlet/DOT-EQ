package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DotEQ", appName)
    }

    @Test
    fun `main studio launches and navigates all tabs`() {
        composeRule.onNodeWithText("DOTEQ (01)").assertIsDisplayed()
        composeRule.onNodeWithTag("interactive_eq_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("simple_fader_low").assertExists()

        // Switch Simple -> Expert EQ mode
        composeRule.onNodeWithTag("eq_mode_expert").performScrollTo().performClick()
        composeRule.onNodeWithTag("expert_fader_31").assertExists()
        composeRule.onNodeWithTag("expert_fader_1K").assertExists()
        composeRule.onNodeWithTag("expert_fader_16K").assertExists()

        // Navigate to simplified Crossover tab with band switches
        composeRule.onNodeWithTag("nav_crossover").performClick()
        composeRule.onNodeWithTag("crossover_touch_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("xover_quick_bass").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("xover_low_switch").performScrollTo().assertExists()

        // Navigate to Dynamics tab (Ultra Bass, Simple Compressor & L/R Balance)
        composeRule.onNodeWithTag("nav_dynamics").performClick()
        composeRule.onNodeWithTag("ultra_bass_switch").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ultra_bass_slider").performScrollTo().assertExists()
        composeRule.onNodeWithTag("compressor_switch").performScrollTo().assertExists()
        composeRule.onNodeWithTag("comp_intensity_slider").performScrollTo().assertExists()
        composeRule.onNodeWithTag("balance_slider").performScrollTo().assertIsDisplayed()

        // Navigate to AI Tuner tab -> Google Sign-In prompt when signed out
        composeRule.onNodeWithTag("nav_ai").performClick()
        composeRule.onNodeWithTag("ai_google_signin_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `qr studio opens from settings and exposes create share and scan controls`() {
        composeRule.onNodeWithTag("top_settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_open_qr_button").performScrollTo().performClick()

        composeRule.onNodeWithTag("qr_studio_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("nothing_qr_poster").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("qr_share_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("qr_tab_scannen").performClick()
        composeRule.onNodeWithTag("qr_gallery_import_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("qr_close_button").performClick()
        composeRule.onNodeWithTag("settings_close_button").performClick()
    }

    @Test
    fun `settings sheet opens and exposes bluetooth auto-bypass and normal connection toggles`() {
        composeRule.onNodeWithTag("top_settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_bt_autobypass_switch").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("settings_normal_require_rule_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        val hasSaveButton = runCatching {
            composeRule.onNodeWithTag("settings_normal_save_button").performScrollTo().assertIsDisplayed()
            true
        }.getOrDefault(false)
        if (!hasSaveButton) {
            composeRule.onNodeWithTag("settings_normal_require_rule_switch").performScrollTo().performClick()
            composeRule.onNodeWithTag("settings_normal_save_button").performScrollTo().assertIsDisplayed()
        }
        // Restore switch to default false so subsequent tests start cleanly
        composeRule.onNodeWithTag("settings_normal_require_rule_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_close_button").performClick()
    }

    @Test
    fun `hardware led power switch toggles between ON and OFF`() {
        val powerNode = composeRule.onNodeWithTag("master_power_button")
        powerNode.assertIsDisplayed()
        val initialIsOn = runCatching {
            composeRule.onNodeWithText("ON").fetchSemanticsNode()
            true
        }.getOrDefault(false)
        val firstExpected = if (initialIsOn) "ON" else "OFF"
        val toggledExpected = if (initialIsOn) "OFF" else "ON"

        val textNode = composeRule.onNodeWithText(firstExpected)
        textNode.assertIsDisplayed()

        powerNode.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(toggledExpected).assertIsDisplayed()

        powerNode.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(firstExpected).assertIsDisplayed()
    }

    @Test
    fun `preset creation opens frosted nothing modal and saves preset`() {
        composeRule.onNodeWithTag("system_service_switch").performScrollTo()
        composeRule.onNodeWithTag("save_preset_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_preset_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("preset_name_input").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_save_preset_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_preset_dialog").assertDoesNotExist()
    }

    @Test
    fun `link downloader opens from settings with quality prompt on load folder picker and no metadata toggle`() {
        composeRule.onNodeWithTag("top_settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_seal_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("LINK DOWNLOADER").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_open_seal_button").performScrollTo().performClick()

        composeRule.onNodeWithTag("seal_studio_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("seal_input_field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seal_search_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seal_quality_auto").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seal_quality_low").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seal_quality_medium").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seal_quality_lossless").performScrollTo().performClick()
        composeRule.onNodeWithTag("link_pick_folder_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seal_loc_downloads").performScrollTo().performClick()
        composeRule.onNodeWithTag("seal_format_mp3").performScrollTo().performClick()
        composeRule.onNodeWithTag("seal_template_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Metadaten & Tags").assertDoesNotExist()

        // Pressing LOAD opens quality prompt dialog
        composeRule.onNodeWithTag("seal_input_field").performScrollTo().performTextInput("https://cdn.example.org/test.flac")
        composeRule.onNodeWithTag("seal_download_now_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("link_quality_prompt_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("quality_prompt_auto").performScrollTo().performClick()
        composeRule.onNodeWithTag("quality_prompt_cancel_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("link_quality_prompt_dialog").assertDoesNotExist()

        composeRule.onNodeWithTag("seal_close_button").performClick()
        composeRule.onNodeWithTag("settings_close_button").performClick()
    }
}
