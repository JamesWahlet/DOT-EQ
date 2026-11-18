package com.example.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.camera.core.ImageProxy
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.audio.AudioMath
import com.example.model.AudioPreset
import com.example.model.CompressorSettings
import com.example.model.CrossoverSettings
import com.example.model.EQ_FREQUENCIES_HZ
import com.example.model.EqMode
import com.example.model.EqState
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

data class ParametricBand(
    val gainDb: Float,
    val freqHz: Float,
    val qFactor: Float
)

data class QrMatrixData(
    val size: Int,
    val modules: BooleanArray
)

object NothingQrCodec {

    // Standard 8-band Parametric Frequencies compatible with Nothing X / Nothing Playground
    val NOTHING_PARAMETRIC_FREQS_HZ = floatArrayOf(55f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 9500f)
    private const val DEFAULT_Q = 1.41f

    /**
     * Converts current [EqState] into the Base64(Gzip(TLV)) payload compatible with
     * Nothing X App & Nothing Playground EQ QR codes (starts with `H4sI`).
     */
    fun encodeToNothingQrString(
        eqState: EqState,
        presetName: String = eqState.activePresetName
    ): String {
        val effective10 = AudioMath.effectiveEqBands(eqState)
        val parametric8 = map10BandsTo8Parametric(effective10)
        return encodeParametricBandsToNothingQr(parametric8, presetName)
    }

    fun encodeParametricBandsToNothingQr(
        bands: List<ParametricBand>,
        presetName: String
    ): String {
        val safeBands = (0 until 8).map { idx ->
            bands.getOrElse(idx) {
                ParametricBand(0f, NOTHING_PARAMETRIC_FREQS_HZ[idx], DEFAULT_Q)
            }
        }
        val cleanName = presetName.trim().ifBlank { "DotEQ Tune" }
        val nameBytes = cleanName.toByteArray(Charsets.UTF_8).let { bytes ->
            if (bytes.size > 60) bytes.copyOf(60) else bytes
        }

        // Tag 0x00 (96 bytes = 8 * 3 floats LE) + Tag 0x01 (nameLen + UTF8 name)
        val totalSize = 2 + (8 * 12) + 2 + nameBytes.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x00.toByte())
        buffer.put((8 * 12).toByte()) // 0x60 = 96 bytes
        for (band in safeBands) {
            buffer.putFloat(band.gainDb.coerceIn(-12f, 12f))
            buffer.putFloat(band.freqHz.coerceIn(20f, 20000f))
            buffer.putFloat(band.qFactor.coerceIn(0.3f, 10f))
        }
        buffer.put(0x01.toByte())
        buffer.put(nameBytes.size.toByte())
        buffer.put(nameBytes)

        val rawBytes = buffer.array()
        val gzipBytes = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(rawBytes)
            }
            baos.toByteArray()
        }
        return encodeBase64(gzipBytes)
    }

    /**
     * Decodes QR payloads from:
     * 1. Nothing X / Nothing Playground Base64(Gzip(TLV binary)) (e.g. `H4sI...`)
     * 2. Base64(Gzip(JSON))
     * 3. Plain JSON (Nothing Playground / community JSON exports)
     * 4. URLs wrapping `?data=...`, `?eq=...`, or path Base64 strings
     */
    fun decodeNothingQrString(rawInput: String): Result<AudioPreset> {
        return runCatching {
            val trimmed = rawInput.trim()
            require(trimmed.isNotBlank()) { "Leerer QR-Inhalt." }

            val extracted = extractPayloadCandidate(trimmed)

            // Direct JSON?
            if (extracted.startsWith("{") || extracted.startsWith("[")) {
                return@runCatching parseJsonPreset(extracted)
            }

            // Try Base64 decode -> Gzip decompress -> TLV or JSON
            val decodedBytes = decodeBase64Flexible(extracted)
                ?: throw IllegalArgumentException("Unbekanntes QR-Format (kein gültiges Nothing X / DotEQ Profil).")

            val payloadBytes = if (isGzipHeader(decodedBytes)) {
                GZIPInputStream(ByteArrayInputStream(decodedBytes)).use { it.readBytes() }
            } else {
                decodedBytes
            }

            val asUtf8 = payloadBytes.toString(Charsets.UTF_8).trim()
            if (asUtf8.startsWith("{") || asUtf8.startsWith("[")) {
                parseJsonPreset(asUtf8)
            } else {
                parseNothingTlvPreset(payloadBytes)
            }
        }
    }

    private fun extractPayloadCandidate(input: String): String {
        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) ||
            input.startsWith("nothing://", ignoreCase = true)
        ) {
            val queryPart = input.substringAfter('?', "")
            if (queryPart.isNotBlank()) {
                val params = queryPart.split('&')
                for (key in listOf("data", "eq", "profile", "preset", "q")) {
                    val match = params.firstOrNull { it.startsWith("$key=", ignoreCase = true) }
                    if (match != null) {
                        val value = match.substringAfter('=')
                        val decoded = if (value.contains('%')) {
                            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
                        } else {
                            value
                        }
                        return decoded.trim()
                    }
                }
            }
            val lastSeg = input.substringAfterLast('/').substringBefore('?')
            if (lastSeg.startsWith("H4sI")) return lastSeg
        }
        return input
    }

    private fun parseNothingTlvPreset(bytes: ByteArray): AudioPreset {
        require(bytes.size >= 14) { "Binär-Profil zu kurz (${bytes.size} Bytes)." }
        var offset = 0
        val parametricBands = mutableListOf<ParametricBand>()
        var profileName = "NOTHING QR"

        while (offset + 2 <= bytes.size) {
            val tag = bytes[offset].toInt() and 0xFF
            val len = bytes[offset + 1].toInt() and 0xFF
            offset += 2
            val end = min(bytes.size, offset + len)
            val slice = bytes.copyOfRange(offset, end)
            offset = end

            when (tag) {
                0x00 -> {
                    val bb = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN)
                    while (bb.remaining() >= 12) {
                        val gain = bb.float
                        val freq = bb.float
                        val q = bb.float
                        if (!gain.isNaN() && !freq.isNaN() && !q.isNaN() && freq in 15f..24000f) {
                            parametricBands += ParametricBand(
                                gainDb = gain.coerceIn(-12f, 12f),
                                freqHz = freq.coerceIn(20f, 20000f),
                                qFactor = q.coerceIn(0.2f, 12f)
                            )
                        }
                    }
                }
                0x01 -> {
                    val decoded = slice.toString(Charsets.UTF_8).trim().trimEnd('\u0000')
                    if (decoded.isNotBlank()) profileName = decoded
                }
            }
        }

        // Fallback: raw packed 8x3 floats without TLV header
        if (parametricBands.isEmpty() && bytes.size >= 96 && bytes.size % 12 == 0) {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            while (bb.remaining() >= 12) {
                val gain = bb.float
                val freq = bb.float
                val q = bb.float
                if (!gain.isNaN() && !freq.isNaN() && freq in 15f..24000f) {
                    parametricBands += ParametricBand(
                        gainDb = gain.coerceIn(-12f, 12f),
                        freqHz = freq.coerceIn(20f, 20000f),
                        qFactor = if (q.isNaN()) DEFAULT_Q else q.coerceIn(0.2f, 12f)
                    )
                }
            }
        }

        require(parametricBands.isNotEmpty()) { "Keine gültigen EQ-Bänder im QR-Code gefunden." }
        return buildPresetFromParametric(profileName, parametricBands)
    }

    private fun parseJsonPreset(jsonText: String): AudioPreset {
        val obj = if (jsonText.startsWith("[")) {
            JSONObject().put("bands", JSONArray(jsonText))
        } else {
            JSONObject(jsonText)
        }
        val name = obj.optString("name").ifBlank {
            obj.optString("title").ifBlank {
                obj.optString("presetName").ifBlank { "PLAYGROUND QR" }
            }
        }

        val bands = mutableListOf<ParametricBand>()
        val bandsArray = obj.optJSONArray("bands")
            ?: obj.optJSONArray("eq")
            ?: obj.optJSONArray("parametricBands")
            ?: obj.optJSONArray("gains")

        val freqArray = obj.optJSONArray("frequencies") ?: obj.optJSONArray("freqs")

        if (bandsArray != null) {
            for (i in 0 until bandsArray.length()) {
                val item = bandsArray.opt(i)
                when (item) {
                    is JSONObject -> {
                        val gain = item.optDouble("gain", item.optDouble("gainDb", item.optDouble("db", 0.0))).toFloat()
                        val freq = item.optDouble("freq", item.optDouble("frequency", item.optDouble("hz", NOTHING_PARAMETRIC_FREQS_HZ.getOrElse(i) { 1000f }.toDouble()))).toFloat()
                        val q = item.optDouble("q", item.optDouble("qFactor", DEFAULT_Q.toDouble())).toFloat()
                        bands += ParametricBand(gain.coerceIn(-12f, 12f), freq.coerceIn(20f, 20000f), q.coerceIn(0.2f, 12f))
                    }
                    is Number -> {
                        val defaultFreq = if (bandsArray.length() == 10) {
                            EQ_FREQUENCIES_HZ.getOrElse(i) { 1000f }
                        } else {
                            NOTHING_PARAMETRIC_FREQS_HZ.getOrElse(i) { 1000f }
                        }
                        val freq = freqArray?.optDouble(i, defaultFreq.toDouble())?.toFloat() ?: defaultFreq
                        bands += ParametricBand(item.toFloat().coerceIn(-12f, 12f), freq.coerceIn(20f, 20000f), DEFAULT_Q)
                    }
                }
            }
        }

        require(bands.isNotEmpty()) { "JSON enthält keine EQ-Werte." }
        return buildPresetFromParametric(name, bands)
    }

    private fun buildPresetFromParametric(
        name: String,
        parametricBands: List<ParametricBand>
    ): AudioPreset {
        val tenBands = mapParametricTo10Bands(parametricBands)
        val (low, mid, high) = AudioMath.expertToSimpleSummary(tenBands)
        return AudioPreset(
            id = "qr_${UUID.randomUUID()}",
            name = name.trim().take(24).ifBlank { "NOTHING QR" },
            tag = "NOTHING QR",
            description = "Importiertes Nothing X / Playground EQ-Profil (${parametricBands.size}-Band)",
            isCustom = true,
            isAiGenerated = false,
            mode = EqMode.EXPERT,
            simpleLowDb = low,
            simpleMidDb = mid,
            simpleHighDb = high,
            expertBandsDb = tenBands,
            preampDb = 0f,
            crossover = CrossoverSettings(),
            compressor = CompressorSettings(),
            balance = 0f
        )
    }

    fun map10BandsTo8Parametric(bands10: List<Float>): List<ParametricBand> {
        val b = if (bands10.size == 10) bands10 else List(10) { 0f }
        val g55 = ((b[0] * 0.45f) + (b[1] * 0.55f)).coerceIn(-12f, 12f)
        val g125 = b[2].coerceIn(-12f, 12f)
        val g250 = b[3].coerceIn(-12f, 12f)
        val g500 = b[4].coerceIn(-12f, 12f)
        val g1k = b[5].coerceIn(-12f, 12f)
        val g2k = b[6].coerceIn(-12f, 12f)
        val g4k = b[7].coerceIn(-12f, 12f)
        val g9500 = ((b[8] * 0.65f) + (b[9] * 0.35f)).coerceIn(-12f, 12f)
        val gains = floatArrayOf(g55, g125, g250, g500, g1k, g2k, g4k, g9500)
        return gains.indices.map { idx ->
            ParametricBand(
                gainDb = gains[idx],
                freqHz = NOTHING_PARAMETRIC_FREQS_HZ[idx],
                qFactor = DEFAULT_Q
            )
        }
    }

    fun mapParametricTo10Bands(parametric: List<ParametricBand>): List<Float> {
        if (parametric.isEmpty()) return List(10) { 0f }
        if (parametric.size == 10 && parametric.indices.all { abs(parametric[it].freqHz - EQ_FREQUENCIES_HZ[it]) < 5f }) {
            return parametric.map { it.gainDb.coerceIn(-12f, 12f) }
        }
        val sorted = parametric.sortedBy { it.freqHz }
        if (sorted.size == 1) {
            return List(10) { sorted[0].gainDb.coerceIn(-12f, 12f) }
        }

        return EQ_FREQUENCIES_HZ.map { targetHz ->
            val targetLog = ln(targetHz)
            val firstLog = ln(sorted.first().freqHz.coerceIn(20f, 20000f))
            val lastLog = ln(sorted.last().freqHz.coerceIn(20f, 20000f))

            val gain = when {
                targetLog <= firstLog -> sorted.first().gainDb
                targetLog >= lastLog -> sorted.last().gainDb
                else -> {
                    var interpolated = 0f
                    for (i in 0 until sorted.lastIndex) {
                        val f0 = ln(sorted[i].freqHz.coerceIn(20f, 20000f))
                        val f1 = ln(sorted[i + 1].freqHz.coerceIn(20f, 20000f))
                        if (targetLog in f0..f1) {
                            val span = (f1 - f0).coerceAtLeast(0.0001f)
                            val t = ((targetLog - f0) / span).coerceIn(0f, 1f)
                            // Smooth cosine easing preserves exact endpoint gains (peaks & dips)
                            val mu = (1f - kotlin.math.cos(t * kotlin.math.PI.toFloat())) * 0.5f
                            interpolated = sorted[i].gainDb * (1f - mu) + sorted[i + 1].gainDb * mu
                            break
                        }
                    }
                    interpolated
                }
            }
            gain.coerceIn(-12f, 12f)
        }
    }

    fun generateQrMatrix(payload: String): QrMatrixData {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = bitMatrix.width
        val modules = BooleanArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                modules[y * size + x] = bitMatrix.get(x, y)
            }
        }
        return QrMatrixData(size = size, modules = modules)
    }

    /**
     * Checks whether (x, y) lies inside one of the three 7x7 finder patterns or the center logo zone.
     */
    fun isFinderModule(x: Int, y: Int, size: Int): Boolean {
        val inTopLeft = x < 7 && y < 7
        val inTopRight = x >= size - 7 && y < 7
        val inBottomLeft = x < 7 && y >= size - 7
        return inTopLeft || inTopRight || inBottomLeft
    }

    fun isCenterLogoHole(x: Int, y: Int, size: Int): Boolean {
        val half = size / 2f
        val holeRadius = max(3.4f, size * 0.076f)
        return abs(x + 0.5f - half) < holeRadius && abs(y + 0.5f - half) < holeRadius
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val scaled = if (bitmap.width > 2000 || bitmap.height > 2000) {
            val scale = 2000f / max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(64),
                (bitmap.height * scale).toInt().coerceAtLeast(64),
                true
            )
        } else {
            bitmap
        }
        val intArray = IntArray(scaled.width * scaled.height)
        scaled.getPixels(intArray, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val source = RGBLuminanceSource(scaled.width, scaled.height, intArray)
        return decodeFromLuminanceSource(source)
    }

    fun decodeQrFromYuv(image: ImageProxy): String? {
        val yPlane = image.planes.firstOrNull() ?: return null
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        // Normalize Y plane if rowStride > width
        val yBytes = if (rowStride == width) {
            data
        } else {
            ByteArray(width * height).also { out ->
                for (row in 0 until height) {
                    val srcPos = row * rowStride
                    if (srcPos + width <= data.size) {
                        System.arraycopy(data, srcPos, out, row * width, width)
                    }
                }
            }
        }

        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        return decodeFromLuminanceSource(source)
    }

    private fun decodeFromLuminanceSource(source: LuminanceSource): String? {
        val reader = QRCodeReader()
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val attempts = listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source)),
            BinaryBitmap(HybridBinarizer(source.invert()))
        )
        for (bitmap in attempts) {
            try {
                val result = reader.decode(bitmap, hints)
                if (!result.text.isNullOrBlank()) return result.text
            } catch (_: Exception) {
                reader.reset()
            }
        }
        return null
    }

    /**
     * Renders a high-resolution Nothing X style share card PNG matching IMG_2976.
     */
    fun renderShareCardBitmap(
        context: Context,
        eqState: EqState,
        presetName: String
    ): Bitmap {
        val width = 1080
        val height = 1620
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)

        // Pure black studio canvas
        canvas.drawColor(AndroidColor.BLACK)

        val dotFont = runCatching { ResourcesCompat.getFont(context, R.font.dotgothic16) }.getOrNull() ?: Typeface.MONOSPACE
        val serifFont = runCatching { ResourcesCompat.getFont(context, R.font.tinos) }.getOrNull() ?: Typeface.SERIF
        val bodyFont = runCatching { ResourcesCompat.getFont(context, R.font.gothic_a1) }.getOrNull() ?: Typeface.SANS_SERIF

        // Header brand text
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 40f
            typeface = dotFont
            letterSpacing = 0.08f
        }
        canvas.drawText("NOTHING (R)  //  DOTEQ", 68f, 112f, brandPaint)

        // QR White Card
        val cardLeft = 68f
        val cardSize = 680f
        val cardTop = 290f
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardSize, cardTop + cardSize)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, 56f, 56f, whitePaint)

        // Draw Dot-Matrix QR inside white card
        val qrString = encodeToNothingQrString(eqState, presetName)
        val qrMatrix = generateQrMatrix(qrString)
        val qrInset = 56f
        val qrDrawSize = cardSize - qrInset * 2f
        val cell = qrDrawSize / qrMatrix.size.toFloat()
        val qrLeft = cardLeft + qrInset
        val qrTop = cardTop + qrInset

        val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            style = Paint.Style.FILL
        }
        val blackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.96f
        }

        // Finder patterns (TopLeft, TopRight, BottomLeft)
        fun drawFinder(fx: Int, fy: Int) {
            val l = qrLeft + fx * cell + cell * 0.5f
            val t = qrTop + fy * cell + cell * 0.5f
            val s = 6f * cell
            canvas.drawRoundRect(RectF(l, t, l + s, t + s), cell * 1.4f, cell * 1.4f, blackStrokePaint)
            val il = qrLeft + (fx + 2) * cell
            val it = qrTop + (fy + 2) * cell
            val isize = 3f * cell
            canvas.drawRoundRect(RectF(il, it, il + isize, it + isize), cell * 0.75f, cell * 0.75f, blackPaint)
        }
        drawFinder(0, 0)
        drawFinder(qrMatrix.size - 7, 0)
        drawFinder(0, qrMatrix.size - 7)

        for (y in 0 until qrMatrix.size) {
            for (x in 0 until qrMatrix.size) {
                if (!qrMatrix.modules[y * qrMatrix.size + x]) continue
                if (isFinderModule(x, y, qrMatrix.size) || isCenterLogoHole(x, y, qrMatrix.size)) continue
                val cx = qrLeft + (x + 0.5f) * cell
                val cy = qrTop + (y + 0.5f) * cell
                canvas.drawCircle(cx, cy, cell * 0.47f, blackPaint)
            }
        }

        // Center pill + Nothing red dot emblem
        val midX = qrLeft + qrDrawSize / 2f
        val midY = qrTop + qrDrawSize / 2f
        canvas.save()
        canvas.rotate(45f, midX, midY)
        val pillW = cell * 3.4f
        val pillH = cell * 1.55f
        canvas.drawRoundRect(
            RectF(midX - pillW / 2f, midY - pillH / 2f, midX + pillW / 2f, midY + pillH / 2f),
            pillH / 2f,
            pillH / 2f,
            blackPaint
        )
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#F22626")
        }
        canvas.drawCircle(midX + pillW * 0.24f, midY, pillH * 0.34f, redPaint)
        canvas.restore()

        // Attached dark EQ Curve Strip directly below QR card
        val stripTop = cardTop + cardSize + 8f
        val stripHeight = 200f
        val stripRect = RectF(cardLeft, stripTop, cardLeft + cardSize, stripTop + stripHeight)
        val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#181A1D")
        }
        canvas.drawRoundRect(stripRect, 36f, 36f, stripPaint)

        val effective10 = AudioMath.effectiveEqBands(eqState)
        val pBands = map10BandsTo8Parametric(effective10)
        val padX = 48f
        val curveLeft = cardLeft + padX
        val curveWidth = cardSize - padX * 2f
        val midCurveY = stripTop + stripHeight * 0.52f
        val curveAmp = stripHeight * 0.34f

        val linePath = Path()
        val fillPath = Path()
        val steps = 72
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val freq = AudioMath.normToFreq(t)
            val db = AudioMath.evaluateEqCurveDb(freq, effective10, eqState.preampDb)
            val x = curveLeft + t * curveWidth
            val y = midCurveY - (db / 12f).coerceIn(-1f, 1f) * curveAmp
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, midCurveY)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(curveLeft + curveWidth, midCurveY)
        fillPath.close()

        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, stripTop + 20f, 0f, stripTop + stripHeight - 20f,
                AndroidColor.argb(95, 255, 255, 255),
                AndroidColor.argb(8, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawPath(fillPath, gradPaint)

        val curveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(180, 235, 235, 240)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawPath(linePath, curveStrokePaint)

        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
        }
        for (pb in pBands) {
            val t = AudioMath.freqToNorm(pb.freqHz)
            val db = AudioMath.evaluateEqCurveDb(pb.freqHz, effective10, eqState.preampDb)
            val nx = curveLeft + t * curveWidth
            val ny = midCurveY - (db / 12f).coerceIn(-1f, 1f) * curveAmp
            canvas.drawCircle(nx, ny, 5.5f, nodePaint)
        }

        // Large Times New Roman Preset Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 76f
            typeface = serifFont
        }
        canvas.drawText(presetName.trim().ifBlank { "Custom EQ" }, cardLeft, stripTop + stripHeight + 170f, titlePaint)

        // Footer metadata matching IMG_2976
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#B5B8BF")
            textSize = 30f
            typeface = bodyFont
        }
        canvas.drawText("Scannen mit", cardLeft, height - 150f, footerPaint)
        canvas.drawText("Nothing X / DotEQ", cardLeft, height - 108f, footerPaint)

        canvas.drawText("Playground-kompatibel", 450f, height - 150f, footerPaint)
        canvas.drawText("Erweitertes Equalizer-Profil", 450f, height - 108f, footerPaint)

        return bmp
    }

    fun shareQrCardBitmap(context: Context, bitmap: Bitmap, presetName: String): Intent {
        val dir = File(context.cacheDir, "qr_exports").apply { mkdirs() }
        val safeName = presetName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24).ifBlank { "doteq_eq" }
        val file = File(dir, "${safeName}_qr.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DotEQ / Nothing X EQ: $presetName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isGzipHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0x1F &&
            (bytes[1].toInt() and 0xFF) == 0x8B
    }

    // Pure Kotlin RFC 4648 Base64 codec (works across JVM unit tests and Android API 24+)
    private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encodeBase64(data: ByteArray): String {
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0

            val rem = data.size - i
            sb.append(B64_ALPHABET[(b0 ushr 2) and 0x3F])
            sb.append(B64_ALPHABET[((b0 shl 4) or (b1 ushr 4)) and 0x3F])
            sb.append(if (rem > 1) B64_ALPHABET[((b1 shl 2) or (b2 ushr 6)) and 0x3F] else '=')
            sb.append(if (rem > 2) B64_ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    fun decodeBase64Flexible(input: String): ByteArray? {
        val cleaned = input
            .trim()
            .replace('-', '+')
            .replace('_', '/')
            .filterNot { it.isWhitespace() || it == '=' }
        if (cleaned.isEmpty()) return null

        val lookup = IntArray(128) { -1 }
        for (idx in B64_ALPHABET.indices) {
            lookup[B64_ALPHABET[idx].code] = idx
        }

        val out = ByteArrayOutputStream((cleaned.length * 3) / 4)
        var acc = 0
        var bits = 0
        for (ch in cleaned) {
            val code = ch.code
            if (code >= 128) return null
            val v = lookup[code]
            if (v < 0) return null
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((acc ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
