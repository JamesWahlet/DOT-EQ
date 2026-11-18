package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiChatMessage
import com.example.ai.SoundTuningAiRepository
import com.example.ai.VoiceCaptureState
import com.example.audio.BluetoothAudioState
import com.example.auth.AuthenticatedUser
import com.example.model.EqState
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealSettings
import com.example.seal.YouTubeAudioExtractor
import com.example.seal.YouTubeAudioStream
import com.example.ui.screens.AiTunerScreen
import com.example.ui.theme.DotEqTheme
import com.example.ui.theme.ThemeMode
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouTubeExtractorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `extractVideoId supports watch, youtu_be, music, shorts, embed and live urls`() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeAudioExtractor.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeAudioExtractor.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc123")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeAudioExtractor.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeAudioExtractor.extractVideoId("https://m.youtube.com/shorts/dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeAudioExtractor.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeAudioExtractor.extractVideoId("https://www.youtube.com/live/dQw4w9WgXcQ?feature=share")
        )
        assertTrue(YouTubeAudioExtractor.isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(YouTubeAudioExtractor.isYouTubeUrl("https://example.com/song.mp3"))
        assertNull(YouTubeAudioExtractor.extractVideoId("not a url"))
    }

    @Test
    fun `parses InnerTube player JSON and HTML embedded ytInitialPlayerResponse`() {
        val playerJson = """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": {
                "videoId": "pAzJPmsWgKU",
                "title": "Nothing Essential Demo",
                "author": "Nothing Tech",
                "lengthSeconds": "215"
              },
              "streamingData": {
                "adaptiveFormats": [
                  {
                    "itag": 140,
                    "url": "https://rr1.googlevideo.com/videoplayback?mime=audio/mp4&id=1",
                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                    "bitrate": 129400,
                    "audioQuality": "AUDIO_QUALITY_MEDIUM"
                  },
                  {
                    "itag": 251,
                    "url": "https://rr1.googlevideo.com/videoplayback?mime=audio/webm&id=2",
                    "mimeType": "audio/webm; codecs=\"opus\"",
                    "bitrate": 162000,
                    "audioQuality": "AUDIO_QUALITY_MEDIUM"
                  },
                  {
                    "itag": 137,
                    "url": "https://rr1.googlevideo.com/videoplayback?mime=video/mp4&id=3",
                    "mimeType": "video/mp4; codecs=\"avc1.640028\"",
                    "bitrate": 2400000
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = YouTubeAudioExtractor.parseInnerTubePlayerJson(
            json = JSONObject(playerJson),
            videoId = "pAzJPmsWgKU",
            userAgent = "TestAgent"
        )
        assertNotNull(parsed)
        assertEquals("Nothing Essential Demo", parsed!!.title)
        assertEquals("Nothing Tech", parsed.author)
        assertEquals(215_000L, parsed.durationMs)
        assertEquals(2, parsed.streams.size)
        assertEquals("m4a", parsed.streams[0].extension)
        assertEquals("ogg", parsed.streams[1].extension)

        val html = """
            <html><head><script>
            var ytInitialPlayerResponse = $playerJson;
            var otherData = {"nested":{"a":1}};
            </script></head><body></body></html>
        """.trimIndent()
        val extracted = YouTubeAudioExtractor.extractPlayerJsonFromHtml(html)
        assertNotNull(extracted)
        val parsedHtml = YouTubeAudioExtractor.parseInnerTubePlayerJson(extracted!!, "pAzJPmsWgKU", "WebAgent")
        assertNotNull(parsedHtml)
        assertEquals(2, parsedHtml!!.streams.size)
    }

    @Test
    fun `parses Piped streams and selects best stream for AUTO, LOW, MEDIUM, LOSSLESS`() {
        val pipedArr = JSONArray(
            """
            [
              { "url": "https://piped.test/low.m4a", "mimeType": "audio/mp4", "bitrate": 48000, "quality": "48 kbps" },
              { "url": "https://piped.test/med.m4a", "mimeType": "audio/mp4", "bitrate": 128000, "quality": "128 kbps" },
              { "url": "https://piped.test/hi.m4a", "mimeType": "audio/mp4", "bitrate": 256000, "quality": "256 kbps" },
              { "url": "https://piped.test/opus.webm", "mimeType": "audio/webm", "bitrate": 160000, "quality": "160 kbps" }
            ]
            """.trimIndent()
        )
        val streams: List<YouTubeAudioStream> = YouTubeAudioExtractor.parsePipedAudioStreams(pipedArr)
        assertEquals(4, streams.size)

        val autoPick = YouTubeAudioExtractor.selectBestStream(
            streams,
            SealSettings(format = SealAudioFormat.ORIGINAL, quality = SealAudioQuality.AUTO)
        )
        assertEquals("https://piped.test/hi.m4a", autoPick.url)

        val lowPick = YouTubeAudioExtractor.selectBestStream(
            streams,
            SealSettings(format = SealAudioFormat.ORIGINAL, quality = SealAudioQuality.LOW)
        )
        assertEquals("https://piped.test/low.m4a", lowPick.url)

        val medPick = YouTubeAudioExtractor.selectBestStream(
            streams,
            SealSettings(format = SealAudioFormat.ORIGINAL, quality = SealAudioQuality.MEDIUM)
        )
        assertEquals("https://piped.test/med.m4a", medPick.url)

        val oggPick = YouTubeAudioExtractor.selectBestStream(
            streams,
            SealSettings(format = SealAudioFormat.OGG, quality = SealAudioQuality.LOSSLESS)
        )
        assertEquals("https://piped.test/opus.webm", oggPick.url)
    }

    @Test
    fun `SoundTuningAiRepository handles sparse optional JSON fields and formats network errors clearly`() {
        val repo = SoundTuningAiRepository(ApplicationProvider.getApplicationContext())
        val sparseJson = """
            {
              "assistantReply": "Bass leicht angehoben.",
              "applyEq": true,
              "eqMode": "SIMPLE",
              "simpleLowDb": 4.0,
              "applyUltraBass": false,
              "applyCrossover": false,
              "applyCompressor": false,
              "applyBalance": false,
              "setMasterEnabled": false,
              "resetFlat": false,
              "saveAsPreset": false,
              "bindToBluetooth": false,
              "setGlobalAutoBypass": false,
              "requestSongDownload": false
            }
        """.trimIndent()
        val parsed = repo.parseAssistantActionJson(sparseJson, EqState())
        assertTrue(parsed.applyEq)
        assertEquals(4.0f, parsed.simpleLowDb, 0.01f)
        assertEquals("GEMINI // EQ", parsed.profileName)

        val netMsg = repo.formatAiFailureMessage(UnknownHostException("generativelanguage.googleapis.com"), "Fehler")
        assertTrue(netMsg.contains("Internetverbindung"))
        assertTrue(repo.isNetworkException(RuntimeException(UnknownHostException("dns failed"))))

        val appCheckMsg = repo.formatAiFailureMessage(RuntimeException("HTTP 403 AppCheck rejected"), "Fehler")
        assertTrue(appCheckMsg.contains("403") || appCheckMsg.contains("Internetverbindung"))
    }

    @Test
    fun `ai tuner screen shows retry button on error bubble and triggers retry callback`() {
        var retried = false
        val fakeUser = AuthenticatedUser(uid = "u1", displayName = "Test", email = "test@example.com")
        val chat = listOf(
            AiChatMessage(isUser = true, text = "Mehr Bass"),
            AiChatMessage(
                isUser = false,
                text = "Keine Internetverbindung zu Google Gemini.",
                isError = true
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
                        onRetryLastMessage = { retried = true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("ai_retry_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(retried)
    }
}
