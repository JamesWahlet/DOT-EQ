package com.example

import android.content.ClipboardManager
import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiChatMessage
import com.example.ai.VoiceCaptureState
import com.example.audio.BluetoothAudioState
import com.example.auth.AuthenticatedUser
import com.example.model.EqState
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealSettings
import com.example.model.SealTrackSuggestion
import com.example.seal.SealMediaRepository
import com.example.ui.screens.AiTunerScreen
import com.example.ui.theme.DotEqTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SealFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `parses iTunes and Audius track results with safe filenames`() {
        val itunesJson = """
            {
              "resultCount": 1,
              "results": [
                {
                  "trackId": 9911,
                  "trackName": "Midnight City",
                  "artistName": "M83",
                  "collectionName": "Hurry Up",
                  "trackTimeMillis": 243000,
                  "trackViewUrl": "https://music.apple.com/track/9911",
                  "previewUrl": "https://audio-ssl.itunes.apple.com/midnight.m4a"
                }
              ]
            }
        """.trimIndent()

        val itunesHits = SealMediaRepository.parseItunesSearchJson(
            itunesJson,
            SealSettings(format = SealAudioFormat.MP3)
        )
        assertEquals(1, itunesHits.size)
        assertEquals("Midnight City", itunesHits[0].title)
        assertEquals("M83", itunesHits[0].artist)
        assertEquals("https://music.apple.com/track/9911", itunesHits[0].shareUrl)
        assertEquals("https://audio-ssl.itunes.apple.com/midnight.m4a", itunesHits[0].downloadUrl)
        assertEquals("4:03", itunesHits[0].durationLabel)
        assertEquals("M83 - Midnight City.mp3", itunesHits[0].suggestedFilename)

        val audiusJson = """
            {
              "data": [
                {
                  "id": "abc42",
                  "title": "Bassline / Live: Cut",
                  "permalink": "/artist/bassline",
                  "duration": 185,
                  "is_streamable": true,
                  "user": { "name": "Club Artist" }
                }
              ]
            }
        """.trimIndent()
        val audiusHits = SealMediaRepository.parseAudiusSearchJson(audiusJson, SealSettings())
        assertEquals(1, audiusHits.size)
        assertEquals("https://audius.co/artist/bassline", audiusHits[0].shareUrl)
        assertTrue(audiusHits[0].downloadUrl.contains("/v1/tracks/abc42/stream"))
        assertEquals("Club Artist - Bassline Live Cut.m4a", audiusHits[0].suggestedFilename)
    }

    @Test
    fun `extracts best available audio quality from html for AUTO vs LOW`() {
        val html = """
            <html><head>
            <meta property="og:audio" content="https://cdn.example.org/tracks/song_01.mp3?token=abc" />
            </head><body>
            <a href="https://cdn.example.org/tracks/song_01.flac">Lossless</a>
            </body></html>
        """.trimIndent()
        val bestAuto = SealMediaRepository.extractDirectAudioUrlFromHtml(html, SealAudioQuality.AUTO)
        assertEquals("https://cdn.example.org/tracks/song_01.flac", bestAuto)

        val lowPick = SealMediaRepository.extractDirectAudioUrlFromHtml(html, SealAudioQuality.LOW)
        assertEquals("https://cdn.example.org/tracks/song_01.mp3?token=abc", lowPick)
    }

    @Test
    fun `ai tuner screen renders Seal song card with working copy link and download actions`() {
        var copiedTrack: SealTrackSuggestion? = null
        var downloadedTrack: SealTrackSuggestion? = null

        val suggestion = SealTrackSuggestion(
            id = "track-1",
            title = "Around the World",
            artist = "Daft Punk",
            sourceLabel = "Audius Full MP3",
            shareUrl = "https://audius.co/daft/around-the-world",
            downloadUrl = "https://discoveryprovider.audius.co/v1/tracks/1/stream",
            durationLabel = "3:40",
            suggestedFilename = "Daft Punk - Around the World.mp3"
        )
        val fakeUser = AuthenticatedUser(
            uid = "u1",
            displayName = "Nothing User",
            email = "user@example.com"
        )
        val chat = listOf(
            AiChatMessage(isUser = true, text = "Lade Around the World herunter"),
            AiChatMessage(
                isUser = false,
                text = "Hier ist der Track über Seal:",
                appliedBadges = listOf("SEAL MP3"),
                downloadSuggestions = listOf(suggestion)
            )
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
                        chatMessages = chat,
                        isSending = false,
                        voiceState = VoiceCaptureState(),
                        onSignInGoogle = {},
                        onSignOutGoogle = {},
                        onSendMessage = {},
                        onStartVoiceInput = {},
                        onStopAndSendVoice = {},
                        onCancelVoice = {},
                        onResetChat = {},
                        onApplyRecommendation = {},
                        onSaveAiPreset = {},
                        onCopySongLink = { copiedTrack = it },
                        onDownloadSong = { downloadedTrack = it }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("ai_seal_suggestion_card_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Around the World").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_seal_copy_link_0").performScrollTo().performClick()

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(suggestion.shareUrl, cm.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(suggestion.id, copiedTrack?.id)

        composeRule.onNodeWithTag("ai_seal_download_0").performScrollTo().performClick()
        assertEquals(suggestion.id, downloadedTrack?.id)
    }
}
