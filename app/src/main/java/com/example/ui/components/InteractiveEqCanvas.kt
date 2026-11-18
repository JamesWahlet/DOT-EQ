package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.audio.AudioMath
import com.example.audio.rememberTactileHaptics
import com.example.model.EQ_FREQUENCIES_HZ
import com.example.model.EQ_FREQUENCY_LABELS
import com.example.model.EqMode
import com.example.model.EqState
import com.example.ui.theme.LocalNothingColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Monochrome touch-interactive EQ curve preview for BOTH Simple (LOW/MID/HIGH) and Expert (10-Band).
 */
@Composable
fun InteractiveEqCanvas(
    eqState: EqState,
    onSimpleChange: (lowDb: Float?, midDb: Float?, highDb: Float?) -> Unit,
    onExpertBandsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current
    val latestState by rememberUpdatedState(eqState)
    val latestOnSimple by rememberUpdatedState(onSimpleChange)
    val latestOnExpert by rememberUpdatedState(onExpertBandsChange)

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeSimpleZone by remember { mutableIntStateOf(-1) }
    var activeExpertBand by remember { mutableIntStateOf(-1) }
    var isTouching by remember { mutableStateOf(false) }
    val haptics = rememberTactileHaptics()
    var lastHapticDbStep by remember { mutableIntStateOf(0) }
    var lastHapticBand by remember { mutableIntStateOf(-1) }

    val effectiveBands = AudioMath.effectiveEqBands(eqState)
    val simpleNodesFreqs = listOf(80f, 1000f, 8000f)
    val simpleNodeGains = listOf(eqState.simpleLowDb, eqState.simpleMidDb, eqState.simpleHighDb)
    val simpleLabels = listOf("LOW", "MID", "HIGH")

    fun yToDb(y: Float, h: Float): Float {
        val padTop = 24f
        val padBot = 24f
        val usable = (h - padTop - padBot).coerceAtLeast(1f)
        val norm = 1f - ((y - padTop) / usable).coerceIn(0f, 1f)
        val db = -12f + norm * 24f
        return if (abs(db) < 0.35f) 0f else db.coerceIn(-12f, 12f)
    }

    fun dbToY(db: Float, h: Float): Float {
        val padTop = 24f
        val padBot = 24f
        val usable = (h - padTop - padBot).coerceAtLeast(1f)
        val norm = ((db + 12f) / 24f).coerceIn(0f, 1f)
        return padTop + (1f - norm) * usable
    }

    fun handleTouchAt(pos: Offset, isStart: Boolean) {
        val w = canvasSize.width.toFloat().coerceAtLeast(1f)
        val h = canvasSize.height.toFloat().coerceAtLeast(1f)
        val xNorm = (pos.x / w).coerceIn(0f, 1f)
        val targetDb = yToDb(pos.y, h)
        val state = latestState

        if (state.mode == EqMode.SIMPLE) {
            if (isStart || activeSimpleZone !in 0..2) {
                activeSimpleZone = when {
                    xNorm < 0.34f -> 0
                    xNorm < 0.68f -> 1
                    else -> 2
                }
            }
            val dbStep = targetDb.roundToInt()
            if (isStart || dbStep != lastHapticDbStep || activeSimpleZone != lastHapticBand) {
                lastHapticDbStep = dbStep
                lastHapticBand = activeSimpleZone
                haptics.tick(isCenterSnap = targetDb == 0f)
            }
            when (activeSimpleZone) {
                0 -> latestOnSimple(targetDb, null, null)
                1 -> latestOnSimple(null, targetDb, null)
                2 -> latestOnSimple(null, null, targetDb)
            }
        } else {
            val touchFreq = AudioMath.normToFreq(xNorm)
            var nearestIdx = 0
            var minDist = Float.MAX_VALUE
            for (i in EQ_FREQUENCIES_HZ.indices) {
                val bx = AudioMath.freqToNorm(EQ_FREQUENCIES_HZ[i])
                val d = abs(bx - xNorm)
                if (d < minDist) {
                    minDist = d
                    nearestIdx = i
                }
            }
            activeExpertBand = nearestIdx
            val dbStep = targetDb.roundToInt()
            if (isStart || dbStep != lastHapticDbStep || nearestIdx != lastHapticBand) {
                lastHapticDbStep = dbStep
                lastHapticBand = nearestIdx
                haptics.tick(isCenterSnap = targetDb == 0f)
            }
            val updated = state.expertBandsDb.toMutableList()
            if (updated.size == 10) {
                updated[nearestIdx] = targetDb
                if (!isStart && touchFreq > 0f) {
                    if (nearestIdx > 0 && abs(AudioMath.freqToNorm(EQ_FREQUENCIES_HZ[nearestIdx - 1]) - xNorm) < 0.08f) {
                        updated[nearestIdx - 1] = ((updated[nearestIdx - 1] + targetDb) * 0.5f).coerceIn(-12f, 12f)
                    }
                }
                latestOnExpert(updated)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.graphBackdrop)
                .border(
                    width = 1.dp,
                    color = if (isTouching) colors.inkPrimary else colors.cardBorder,
                    shape = RoundedCornerShape(24.dp)
                )
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        isTouching = true
                        handleTouchAt(offset, isStart = true)
                        isTouching = false
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isTouching = true
                            handleTouchAt(offset, isStart = true)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            handleTouchAt(change.position, isStart = false)
                        },
                        onDragEnd = {
                            isTouching = false
                            activeSimpleZone = -1
                            activeExpertBand = -1
                        },
                        onDragCancel = {
                            isTouching = false
                            activeSimpleZone = -1
                            activeExpertBand = -1
                        }
                    )
                }
                .testTag("interactive_eq_preview")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val zeroY = dbToY(0f, h)

                // Minimalist dot-matrix background grid
                val cols = 18
                val rows = 7
                for (c in 1 until cols) {
                    val gx = w * (c / cols.toFloat())
                    for (r in 0 until rows) {
                        val gy = 22f + (h - 44f) * (r / (rows - 1).toFloat())
                        drawCircle(
                            color = colors.graphGridDot.copy(alpha = 0.55f),
                            radius = 1.3.dp.toPx(),
                            center = Offset(gx, gy)
                        )
                    }
                }

                // Simple mode zone dividers
                if (eqState.mode == EqMode.SIMPLE) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                    drawLine(colors.cardBorder, Offset(w * 0.34f, 16f), Offset(w * 0.34f, h - 16f), 1.dp.toPx(), pathEffect = dash)
                    drawLine(colors.cardBorder, Offset(w * 0.68f, 16f), Offset(w * 0.68f, h - 16f), 1.dp.toPx(), pathEffect = dash)
                }

                // 0 dB hairline
                drawLine(
                    color = colors.inkMuted.copy(alpha = 0.55f),
                    start = Offset(12f, zeroY),
                    end = Offset(w - 12f, zeroY),
                    strokeWidth = 1.dp.toPx()
                )

                // Monochrome high-contrast composite EQ curve
                val steps = 72
                val curvePath = Path()
                val fillPath = Path()
                val curveAlpha = if (eqState.masterEnabled) 1f else 0.35f

                for (s in 0..steps) {
                    val normX = s / steps.toFloat()
                    val freq = AudioMath.normToFreq(normX)
                    val db = AudioMath.evaluateEqCurveDb(
                        freqHz = freq,
                        bandsDb = effectiveBands,
                        preampDb = eqState.preampDb,
                        includeCrossover = null
                    ).coerceIn(-12f, 12f)
                    val x = normX * w
                    val y = dbToY(db, h)
                    if (s == 0) {
                        curvePath.moveTo(x, y)
                        fillPath.moveTo(x, zeroY)
                        fillPath.lineTo(x, y)
                    } else {
                        curvePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, zeroY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.inkPrimary.copy(alpha = 0.14f * curveAlpha),
                            Color.Transparent
                        )
                    )
                )
                drawPath(
                    path = curvePath,
                    color = colors.inkPrimary.copy(alpha = curveAlpha),
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Monochrome Touch Nodes
                if (eqState.mode == EqMode.SIMPLE) {
                    for (i in 0 until 3) {
                        val nx = w * AudioMath.freqToNorm(simpleNodesFreqs[i])
                        val ny = dbToY(simpleNodeGains[i], h)
                        val isFocused = activeSimpleZone == i

                        drawLine(
                            color = colors.inkPrimary.copy(alpha = 0.25f),
                            start = Offset(nx, zeroY),
                            end = Offset(nx, ny),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        drawCircle(
                            color = colors.inkPrimary.copy(alpha = if (isFocused) 0.22f else 0.10f),
                            radius = if (isFocused) 24.dp.toPx() else 17.dp.toPx(),
                            center = Offset(nx, ny)
                        )
                        drawCircle(
                            color = colors.inkPrimary,
                            radius = 10.dp.toPx(),
                            center = Offset(nx, ny)
                        )
                        drawCircle(
                            color = colors.card,
                            radius = 4.dp.toPx(),
                            center = Offset(nx, ny)
                        )
                    }
                } else {
                    for (i in EQ_FREQUENCIES_HZ.indices) {
                        val nx = w * AudioMath.freqToNorm(EQ_FREQUENCIES_HZ[i])
                        val ny = dbToY(effectiveBands[i], h)
                        val isFocused = activeExpertBand == i
                        if (isFocused) {
                            drawCircle(
                                color = colors.inkPrimary.copy(alpha = 0.18f),
                                radius = 16.dp.toPx(),
                                center = Offset(nx, ny)
                            )
                        }
                        drawCircle(
                            color = colors.inkPrimary,
                            radius = if (isFocused) 8.dp.toPx() else 5.5.dp.toPx(),
                            center = Offset(nx, ny)
                        )
                        drawCircle(
                            color = colors.card,
                            radius = if (isFocused) 3.5.dp.toPx() else 2.2.dp.toPx(),
                            center = Offset(nx, ny)
                        )
                    }
                }
            }

            // Minimal touch feedback readout badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (eqState.mode == EqMode.SIMPLE) "TOUCH // LOW · MID · HIGH"
                    else "TOUCH // 10-BAND CURVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted
                )

                val focusBadgeText = when {
                    eqState.mode == EqMode.SIMPLE && activeSimpleZone in 0..2 -> {
                        "${simpleLabels[activeSimpleZone]} ${AudioMath.formatDb(simpleNodeGains[activeSimpleZone])}"
                    }
                    eqState.mode == EqMode.EXPERT && activeExpertBand in 0..9 -> {
                        "${EQ_FREQUENCY_LABELS[activeExpertBand]}Hz ${AudioMath.formatDb(effectiveBands[activeExpertBand])}"
                    }
                    else -> "PRE ${AudioMath.formatDb(eqState.preampDb)}"
                }
                Text(
                    text = focusBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val axisLabels = if (eqState.mode == EqMode.SIMPLE) {
                listOf("20Hz", "250Hz", "1kHz", "4kHz", "20kHz")
            } else {
                listOf("31", "125", "500", "1K", "4K", "16K")
            }
            axisLabels.forEach { lbl ->
                Text(
                    text = lbl,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted
                )
            }
        }
    }
}
