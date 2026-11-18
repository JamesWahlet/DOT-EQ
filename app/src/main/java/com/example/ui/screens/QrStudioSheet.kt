package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Size as AndroidSize
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.audio.AudioMath
import com.example.model.AudioPreset
import com.example.model.EqState
import com.example.qr.NothingQrCodec
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingSegmentedPill
import com.example.ui.theme.LocalNothingColors
import com.example.ui.theme.NothingRed
import com.example.ui.theme.TimesTitleFontFamily
import java.util.concurrent.Executors

private enum class QrStudioMode {
    CREATE,
    SCAN
}

@Composable
fun QrStudioSheet(
    eqState: EqState,
    onSaveAndApplyPreset: (AudioPreset) -> Unit,
    onShowMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalNothingColors.current
    val context = LocalContext.current

    var studioMode by remember { mutableStateOf(QrStudioMode.CREATE) }
    var cardTitle by remember(eqState.activePresetName) {
        mutableStateOf(eqState.activePresetName.ifBlank { "Live" })
    }
    var scannedPreset by remember { mutableStateOf<AudioPreset?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    BackHandler { onClose() }

    val qrPayloadString = remember(eqState, cardTitle) {
        NothingQrCodec.encodeToNothingQrString(eqState, cardTitle)
    }
    val qrMatrix = remember(qrPayloadString) {
        NothingQrCodec.generateQrMatrix(qrPayloadString)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } ?: throw IllegalStateException("Bild konnte nicht geladen werden.")

            val rawQr = NothingQrCodec.decodeQrFromBitmap(bitmap)
                ?: throw IllegalArgumentException("Kein QR-Code erkannt.")
            NothingQrCodec.decodeNothingQrString(rawQr).getOrThrow()
        }.onSuccess { preset ->
            scannedPreset = preset
            scanError = null
        }.onFailure { err ->
            scannedPreset = null
            scanError = err.message ?: "QR-Import fehlgeschlagen."
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("qr_studio_sheet"),
        color = colors.canvas
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "QR-CODE",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.inkPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.cardBorder, CircleShape)
                            .clickable { onClose() }
                            .testTag("qr_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "QR schließen",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                NothingSegmentedPill(
                    options = listOf(
                        QrStudioMode.CREATE to "ERSTELLEN",
                        QrStudioMode.SCAN to "SCANNEN"
                    ),
                    selected = studioMode,
                    onSelect = { studioMode = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    testTagPrefix = "qr_tab"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (studioMode == QrStudioMode.CREATE) {
                        NothingCard {
                            NothingSectionHeader(
                                title = "ERSTELLEN",
                                trailingText = "TLV // JSON"
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = cardTitle,
                                onValueChange = { if (it.length <= 24) cardTitle = it },
                                singleLine = true,
                                placeholder = { Text("z. B. Live") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.inkPrimary,
                                    unfocusedBorderColor = colors.cardBorder,
                                    focusedTextColor = colors.inkPrimary,
                                    unfocusedTextColor = colors.inkPrimary
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("qr_title_input")
                            )
                        }

                        NothingStyleQrPoster(
                            eqState = eqState,
                            qrSize = qrMatrix.size,
                            qrModules = qrMatrix.modules,
                            presetTitle = cardTitle.ifBlank { "Custom EQ" }
                        )

                        Button(
                            onClick = {
                                runCatching {
                                    val bmp = NothingQrCodec.renderShareCardBitmap(
                                        context = context,
                                        eqState = eqState,
                                        presetName = cardTitle.ifBlank { "Custom EQ" }
                                    )
                                    val shareIntent = NothingQrCodec.shareQrCardBitmap(
                                        context = context,
                                        bitmap = bmp,
                                        presetName = cardTitle.ifBlank { "Custom EQ" }
                                    )
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "EQ QR-Code teilen")
                                    )
                                }.onFailure {
                                    onShowMessage("Teilen fehlgeschlagen: ${it.message}")
                                }
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.inkPrimary,
                                contentColor = colors.pillActiveContent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("qr_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("QR-CODE TEILEN", maxLines = 1)
                        }
                    } else {
                        // SCAN & USE TAB
                        CameraQrScannerCard(
                            onQrDetected = { raw ->
                                NothingQrCodec.decodeNothingQrString(raw)
                                    .onSuccess { preset ->
                                        scannedPreset = preset
                                        scanError = null
                                    }.onFailure { err ->
                                        scanError = err.message ?: "QR-Format ungültig."
                                    }
                            }
                        )

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("qr_gallery_import_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("QR AUS GALERIE SCANNEN")
                        }

                        if (scanError != null) {
                            NothingCard(borderColor = NothingRed) {
                                Text(
                                    text = scanError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NothingRed
                                )
                            }
                        }

                        scannedPreset?.let { preset ->
                            ScannedPresetResultCard(
                                preset = preset,
                                onUseNow = {
                                    onSaveAndApplyPreset(preset)
                                    onShowMessage("QR-Profil '${preset.name}' aktiv")
                                    onClose()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun NothingStyleQrPoster(
    eqState: EqState,
    qrSize: Int,
    qrModules: BooleanArray,
    presetTitle: String
) {
    val effective10 = remember(eqState) { AudioMath.effectiveEqBands(eqState) }
    val parametric8 = remember(effective10) { NothingQrCodec.map10BandsTo8Parametric(effective10) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .border(1.dp, Color(0xFF262A30), RoundedCornerShape(28.dp))
            .padding(22.dp)
            .testTag("nothing_qr_poster")
    ) {
        Text(
            text = "NOTHING (R)  //  DOTEQ",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            letterSpacing = 1.6.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.fillMaxWidth(0.76f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cell = size.minDimension / qrSize.toFloat()
                    val dotInk = Color(0xFF111316)

                    fun drawFinder(fx: Int, fy: Int) {
                        val l = fx * cell + cell * 0.5f
                        val t = fy * cell + cell * 0.5f
                        val s = 6f * cell
                        drawRoundRect(
                            color = dotInk,
                            topLeft = Offset(l, t),
                            size = Size(s, s),
                            cornerRadius = CornerRadius(cell * 1.35f, cell * 1.35f),
                            style = Stroke(width = cell * 0.92f)
                        )
                        drawRoundRect(
                            color = dotInk,
                            topLeft = Offset((fx + 2) * cell, (fy + 2) * cell),
                            size = Size(3f * cell, 3f * cell),
                            cornerRadius = CornerRadius(cell * 0.7f, cell * 0.7f)
                        )
                    }

                    drawFinder(0, 0)
                    drawFinder(qrSize - 7, 0)
                    drawFinder(0, qrSize - 7)

                    for (y in 0 until qrSize) {
                        for (x in 0 until qrSize) {
                            if (!qrModules[y * qrSize + x]) continue
                            if (NothingQrCodec.isFinderModule(x, y, qrSize) ||
                                NothingQrCodec.isCenterLogoHole(x, y, qrSize)
                            ) {
                                continue
                            }
                            drawCircle(
                                color = dotInk,
                                radius = cell * 0.47f,
                                center = Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
                            )
                        }
                    }

                    val mid = Offset(size.width / 2f, size.height / 2f)
                    val pillW = cell * 3.4f
                    val pillH = cell * 1.55f
                    rotate(degrees = 45f, pivot = mid) {
                        drawRoundRect(
                            color = dotInk,
                            topLeft = Offset(mid.x - pillW / 2f, mid.y - pillH / 2f),
                            size = Size(pillW, pillH),
                            cornerRadius = CornerRadius(pillH / 2f, pillH / 2f)
                        )
                        drawCircle(
                            color = NothingRed,
                            radius = pillH * 0.34f,
                            center = Offset(mid.x + pillW * 0.24f, mid.y)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF181A1D))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height * 0.52f
                    val amp = size.height * 0.36f
                    val linePath = Path()
                    val fillPath = Path()
                    val steps = 56
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        val freq = AudioMath.normToFreq(t)
                        val db = AudioMath.evaluateEqCurveDb(freq, effective10, eqState.preampDb)
                        val x = t * size.width
                        val y = midY - (db / 12f).coerceIn(-1f, 1f) * amp
                        if (i == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, midY)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(size.width, midY)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = Color.White.copy(alpha = 0.65f),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                    parametric8.forEach { pb ->
                        val t = AudioMath.freqToNorm(pb.freqHz)
                        val db = AudioMath.evaluateEqCurveDb(pb.freqHz, effective10, eqState.preampDb)
                        drawCircle(
                            color = Color.White,
                            radius = 2.6.dp.toPx(),
                            center = Offset(t * size.width, midY - (db / 12f).coerceIn(-1f, 1f) * amp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = presetTitle,
            fontFamily = TimesTitleFontFamily,
            fontSize = 32.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column {
                Text("Scannen mit", color = Color(0xFFB5B8BF), fontSize = 11.sp)
                Text("Nothing X / DotEQ", color = Color(0xFFB5B8BF), fontSize = 11.sp)
            }
            Column {
                Text("Playground-kompatibel", color = Color(0xFFB5B8BF), fontSize = 11.sp)
                Text("Equalizer-Profil", color = Color(0xFFB5B8BF), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CameraQrScannerCard(
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNothingColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
    }

    NothingCard(
        borderColor = if (hasCameraPerm) colors.yellow.copy(alpha = 0.55f) else null
    ) {
        NothingSectionHeader(
            title = "OPTISCHER SCANNER",
            trailingText = if (hasCameraPerm) "LIVE" else "STANDBY"
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (!hasCameraPerm) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.pillTrack)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.inkPrimary,
                            contentColor = colors.pillActiveContent
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("qr_camera_permission_button")
                    ) {
                        Text("KAMERA AKTIVIEREN")
                    }
                }
            }
        } else {
            val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
            DisposableEffect(Unit) {
                onDispose {
                    analysisExecutor.shutdown()
                }
            }

            val infiniteTransition = rememberInfiniteTransition(label = "qr_scanner_active")
            val scanSweep by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scan_sweep"
            )
            val yellowGlowPulse by infiniteTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "yellow_glow_pulse"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(264.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft outer yellow glow halo around the active camera frame
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val corner = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    val insetOuter = 2.dp.toPx()
                    drawRoundRect(
                        color = colors.yellow.copy(alpha = 0.16f * yellowGlowPulse),
                        topLeft = Offset(insetOuter, insetOuter),
                        size = Size(size.width - insetOuter * 2f, size.height - insetOuter * 2f),
                        cornerRadius = corner,
                        style = Stroke(width = 8.dp.toPx())
                    )
                    drawRoundRect(
                        color = colors.yellow.copy(alpha = 0.34f * yellowGlowPulse),
                        topLeft = Offset(insetOuter + 2.dp.toPx(), insetOuter + 2.dp.toPx()),
                        size = Size(
                            size.width - (insetOuter + 2.dp.toPx()) * 2f,
                            size.height - (insetOuter + 2.dp.toPx()) * 2f
                        ),
                        cornerRadius = CornerRadius(23.dp.toPx(), 23.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black)
                        .border(
                            width = 2.dp,
                            color = colors.yellow.copy(alpha = 0.78f + 0.22f * yellowGlowPulse),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                runCatching {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val analysis = ImageAnalysis.Builder()
                                        .setTargetResolution(AndroidSize(960, 720))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { imageAnalysis ->
                                            imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                                                try {
                                                    val text = NothingQrCodec.decodeQrFromYuv(proxy)
                                                    if (!text.isNullOrBlank()) {
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onQrDetected(text)
                                                        }
                                                    }
                                                } finally {
                                                    proxy.close()
                                                }
                                            }
                                        }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )

                    // Clean yellow glowing scan frame + yellow dot-matrix laser sweep (no 90° corner brackets)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val boxSize = size.minDimension * 0.64f
                        val left = (size.width - boxSize) / 2f
                        val top = (size.height - boxSize) / 2f
                        val frameCorner = CornerRadius(18.dp.toPx(), 18.dp.toPx())

                        // Soft yellow inner scan-area glow ring
                        drawRoundRect(
                            color = colors.yellow.copy(alpha = 0.22f * yellowGlowPulse),
                            topLeft = Offset(left, top),
                            size = Size(boxSize, boxSize),
                            cornerRadius = frameCorner,
                            style = Stroke(width = 5.dp.toPx())
                        )
                        drawRoundRect(
                            color = colors.yellow.copy(alpha = 0.85f),
                            topLeft = Offset(left, top),
                            size = Size(boxSize, boxSize),
                            cornerRadius = frameCorner,
                            style = Stroke(width = 1.6.dp.toPx())
                        )

                        // Animated yellow Dot-Matrix Laser Sweep inside frame
                        val laserY = top + 12.dp.toPx() + (boxSize - 24.dp.toPx()) * scanSweep
                        val dotCount = 14
                        for (d in 0 until dotCount) {
                            val dx = left + 14.dp.toPx() + ((boxSize - 28.dp.toPx()) * (d / (dotCount - 1).toFloat()))
                            drawCircle(
                                color = colors.yellow.copy(alpha = 0.9f),
                                radius = 2.dp.toPx(),
                                center = Offset(dx, laserY)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedPresetResultCard(
    preset: AudioPreset,
    onUseNow: () -> Unit
) {
    val colors = LocalNothingColors.current
    NothingCard(borderColor = colors.inkPrimary) {
        NothingSectionHeader(
            title = preset.name,
            trailingText = preset.tag
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.pillTrack)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height * 0.5f
                val amp = size.height * 0.4f
                val path = Path()
                val steps = 48
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val freq = AudioMath.normToFreq(t)
                    val db = AudioMath.evaluateEqCurveDb(freq, preset.expertBandsDb, 0f)
                    val x = t * size.width
                    val y = midY - (db / 12f).coerceIn(-1f, 1f) * amp
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = colors.inkPrimary,
                    style = Stroke(width = 2.4.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onUseNow,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.inkPrimary,
                contentColor = colors.pillActiveContent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("qr_apply_button")
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("QR-PROFIL BENUTZEN", maxLines = 1)
        }
    }
}
