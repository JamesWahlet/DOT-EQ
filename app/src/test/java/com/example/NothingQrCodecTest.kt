package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.model.EqMode
import com.example.model.EqState
import com.example.qr.NothingQrCodec
import com.example.qr.ParametricBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NothingQrCodecTest {

    @Test
    fun `encode and decode Nothing TLV payload preserves name and tonal balance`() {
        val state = EqState(
            mode = EqMode.EXPERT,
            expertBandsDb = listOf(5.0f, 4.5f, 3.0f, 1.0f, 0.0f, -1.0f, 0.5f, 2.0f, 3.5f, 4.0f),
            activePresetName = "Live"
        )

        val encoded = NothingQrCodec.encodeToNothingQrString(state, "Live")
        assertTrue("Expected gzip Base64 prefix H4sI", encoded.startsWith("H4sI"))

        val decodedResult = NothingQrCodec.decodeNothingQrString(encoded)
        assertTrue("Expected successful decode: ${decodedResult.exceptionOrNull()}", decodedResult.isSuccess)

        val preset = decodedResult.getOrThrow()
        assertEquals("Live", preset.name)
        assertEquals(10, preset.expertBandsDb.size)
        assertTrue("Expected bass boost, got ${preset.expertBandsDb[1]}", preset.expertBandsDb[1] > 2.5f)
        assertTrue("Expected mid near zero, got ${preset.expertBandsDb[4]}", kotlin.math.abs(preset.expertBandsDb[4]) < 1.5f)
        assertTrue("Expected treble boost, got ${preset.expertBandsDb[8]}", preset.expertBandsDb[8] > 2.0f)
    }

    @Test
    fun `rendered DotEQ share card bitmap decodes back via NothingQrCodec`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val state = EqState(
            mode = EqMode.SIMPLE,
            simpleLowDb = 4.5f,
            simpleMidDb = -1.0f,
            simpleHighDb = 3.0f,
            activePresetName = "Club Roundtrip"
        )
        val bmp = NothingQrCodec.renderShareCardBitmap(ctx, state, "Club Roundtrip")
        val decodedText = NothingQrCodec.decodeQrFromBitmap(bmp)
        assertTrue("Generated share bitmap should scan cleanly", !decodedText.isNullOrBlank())
        val preset = NothingQrCodec.decodeNothingQrString(decodedText!!).getOrThrow()
        assertEquals("Club Roundtrip", preset.name)
    }

    @Test
    fun `decode Nothing Playground JSON and URL wrapped payloads succeed`() {
        val jsonPayload = """
            {
              "name": "Nothing Studio Mix",
              "bands": [
                {"freq": 60, "gain": 4.5, "q": 1.4},
                {"freq": 250, "gain": -1.5, "q": 1.2},
                {"freq": 1000, "gain": 2.5, "q": 1.4},
                {"freq": 8000, "gain": 3.5, "q": 1.5}
              ]
            }
        """.trimIndent()

        val jsonResult = NothingQrCodec.decodeNothingQrString(jsonPayload)
        assertTrue(jsonResult.isSuccess)
        val jsonPreset = jsonResult.getOrThrow()
        assertEquals("Nothing Studio Mix", jsonPreset.name)
        assertTrue(jsonPreset.expertBandsDb[1] > 2.0f)
        assertTrue(jsonPreset.expertBandsDb[8] > 2.0f)

        val tlvString = NothingQrCodec.encodeParametricBandsToNothingQr(
            bands = listOf(
                ParametricBand(6.0f, 55f, 1.41f),
                ParametricBand(3.0f, 125f, 1.41f),
                ParametricBand(0.0f, 250f, 1.41f),
                ParametricBand(-2.0f, 500f, 1.41f),
                ParametricBand(1.0f, 1000f, 1.41f),
                ParametricBand(2.0f, 2000f, 1.41f),
                ParametricBand(3.0f, 4000f, 1.41f),
                ParametricBand(4.0f, 9500f, 1.41f)
            ),
            presetName = "Playground V2"
        )
        val urlWrapped = "https://playground.nothing.tech/eq?data=$tlvString"
        val urlResult = NothingQrCodec.decodeNothingQrString(urlWrapped)
        assertTrue(urlResult.isSuccess)
        assertEquals("Playground V2", urlResult.getOrThrow().name)
    }

    @Test
    fun `qr matrix generation produces square matrix with valid dimensions`() {
        val payload = NothingQrCodec.encodeToNothingQrString(EqState(), "Test")
        val matrix = NothingQrCodec.generateQrMatrix(payload)
        assertTrue(matrix.size >= 21)
        assertEquals(matrix.size * matrix.size, matrix.modules.size)
    }

    @Test
    fun `real Nothing X QR image IMG_3094 decodes and parses Live preset`() {
        val candidates = listOf(
            java.io.File("../public/assets/aistudio/IMG_3094_6b3ff333.jpeg"),
            java.io.File("public/assets/aistudio/IMG_3094_6b3ff333.jpeg")
        )
        val file = candidates.firstOrNull { it.exists() }
        if (file == null) return

        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        assertTrue("Bitmap must decode", bmp != null)

        val rawQr = NothingQrCodec.decodeQrFromBitmap(bmp!!)
        assertTrue("Expected QR string from IMG_3094", !rawQr.isNullOrBlank())

        val presetRes = NothingQrCodec.decodeNothingQrString(rawQr!!)
        assertTrue("Expected valid preset parse: ${presetRes.exceptionOrNull()}", presetRes.isSuccess)
        val preset = presetRes.getOrThrow()
        assertEquals("Live", preset.name)
        assertTrue("Expected sub-bass boost, got ${preset.expertBandsDb[1]}", preset.expertBandsDb[1] > 4.0f)
        assertTrue("Expected 440/500 Hz dip below 0 dB, got ${preset.expertBandsDb[4]}", preset.expertBandsDb[4] < -2.0f)
        assertTrue("Expected treble boost, got ${preset.expertBandsDb[9]}", preset.expertBandsDb[9] > 4.0f)
    }
}
