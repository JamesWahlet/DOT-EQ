package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.audio.AudioMath
import com.example.audio.rememberTactileHaptics
import com.example.model.CrossoverSettings
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingHorizontalDotSlider
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingSwitchPill
import com.example.ui.theme.LocalNothingColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CrossoverScreen(
    crossover: CrossoverSettings,
    onUpdateCrossover: (CrossoverSettings.() -> CrossoverSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current
    val latestXover by rememberUpdatedState(crossover)
    val latestUpdate by rememberUpdatedState(onUpdateCrossover)

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeHandle by remember { mutableIntStateOf(-1) }
    val haptics = rememberTactileHaptics()
    var lastXoverDetent by remember { mutableIntStateOf(-1) }

    fun dbToY(db: Float, h: Float): Float {
        val pad = 20f
        val usable = (h - pad * 2f).coerceAtLeast(1f)
        val norm = ((db + 24f) / 36f).coerceIn(0f, 1f)
        return pad + (1f - norm) * usable
    }

    fun handleTouch(x: Float, isStart: Boolean) {
        val w = canvasSize.width.toFloat().coerceAtLeast(1f)
        val norm = (x / w).coerceIn(0f, 1f)
        val freq = AudioMath.normToFreq(norm)

        if (isStart || activeHandle !in 0..1) {
            val dLow = abs(AudioMath.freqToNorm(latestXover.lowMidHz) - norm)
            val dHigh = abs(AudioMath.freqToNorm(latestXover.midHighHz) - norm)
            activeHandle = if (dLow <= dHigh) 0 else 1
        }
        val detent = (norm * 24f).roundToInt()
        if (isStart || detent != lastXoverDetent) {
            lastXoverDetent = detent
            haptics.tick(isCenterSnap = false)
        }
        if (activeHandle == 0) {
            latestUpdate { copy(lowMidHz = freq.coerceIn(50f, minOf(800f, midHighHz - 250f))) }
        } else {
            latestUpdate { copy(midHighHz = freq.coerceIn(maxOf(700f, lowMidHz + 250f), 10000f)) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NothingCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingSectionHeader(
                    title = "FREQUENZWEICHE",
                    modifier = Modifier.weight(1f)
                )
                NothingSwitchPill(
                    checked = crossover.enabled,
                    onCheckedChange = { enabled -> onUpdateCrossover { copy(enabled = enabled) } },
                    testTag = "crossover_enable_switch"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Touch-Interactive 3-Band Crossover Curve
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.graphBackdrop)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> handleTouch(offset.x, isStart = true) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> handleTouch(offset.x, isStart = true) },
                            onDrag = { change, _ ->
                                change.consume()
                                handleTouch(change.position.x, isStart = false)
                            },
                            onDragEnd = { activeHandle = -1 },
                            onDragCancel = { activeHandle = -1 }
                        )
                    }
                    .testTag("crossover_touch_preview")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val zeroY = dbToY(0f, h)

                    for (c in 1 until 16) {
                        val gx = w * (c / 16f)
                        for (r in 0 until 7) {
                            val gy = 18f + (h - 36f) * (r / 6f)
                            drawCircle(
                                color = colors.graphGridDot.copy(alpha = 0.48f),
                                radius = 1.2.dp.toPx(),
                                center = Offset(gx, gy)
                            )
                        }
                    }

                    drawLine(
                        color = colors.inkMuted.copy(alpha = 0.45f),
                        start = Offset(12f, zeroY),
                        end = Offset(w - 12f, zeroY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val bandEnabledFlags = listOf(
                        crossover.lowEnabled,
                        crossover.midEnabled,
                        crossover.highEnabled
                    )
                    val bandColors = listOf(
                        colors.inkPrimary,
                        colors.inkPrimary.copy(alpha = 0.7f),
                        colors.inkPrimary.copy(alpha = 0.48f)
                    )
                    val steps = 64
                    val masterMul = if (crossover.enabled) 1f else 0.3f

                    for (b in 0 until 3) {
                        val path = Path()
                        for (s in 0..steps) {
                            val normX = s / steps.toFloat()
                            val freq = AudioMath.normToFreq(normX)
                            val db = AudioMath.evaluateCrossoverBandCurveDb(freq, b, crossover)
                            val x = normX * w
                            val y = dbToY(db, h)
                            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        val active = bandEnabledFlags[b]
                        drawPath(
                            path = path,
                            color = bandColors[b].copy(alpha = (if (active) 1f else 0.2f) * bandColors[b].alpha * masterMul),
                            style = Stroke(width = if (active) 3.0.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    val x1 = w * AudioMath.freqToNorm(crossover.lowMidHz)
                    val x2 = w * AudioMath.freqToNorm(crossover.midHighHz)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                    drawLine(colors.inkPrimary.copy(alpha = 0.65f), Offset(x1, 16f), Offset(x1, h - 16f), 1.6.dp.toPx(), pathEffect = dash)
                    drawCircle(colors.inkPrimary, 10.dp.toPx(), Offset(x1, h * 0.5f))
                    drawCircle(colors.card, 3.5.dp.toPx(), Offset(x1, h * 0.5f))

                    drawLine(colors.inkPrimary.copy(alpha = 0.65f), Offset(x2, 16f), Offset(x2, h - 16f), 1.6.dp.toPx(), pathEffect = dash)
                    drawCircle(colors.inkPrimary, 10.dp.toPx(), Offset(x2, h * 0.5f))
                    drawCircle(colors.card, 3.5.dp.toPx(), Offset(x2, h * 0.5f))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = AudioMath.formatFreq(crossover.lowMidHz),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = AudioMath.formatFreq(crossover.midHighHz),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Frequency Selection Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickXoverChip(
                    label = "ALLE AN",
                    selected = crossover.lowEnabled && crossover.midEnabled && crossover.highEnabled,
                    onClick = {
                        onUpdateCrossover {
                            copy(enabled = true, lowEnabled = true, midEnabled = true, highEnabled = true)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "xover_quick_all"
                )
                QuickXoverChip(
                    label = "NUR BASS",
                    selected = crossover.lowEnabled && !crossover.midEnabled && !crossover.highEnabled,
                    onClick = {
                        onUpdateCrossover {
                            copy(enabled = true, lowEnabled = true, midEnabled = false, highEnabled = false)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "xover_quick_bass"
                )
                QuickXoverChip(
                    label = "STIMMEN",
                    selected = !crossover.lowEnabled && crossover.midEnabled && crossover.highEnabled,
                    onClick = {
                        onUpdateCrossover {
                            copy(enabled = true, lowEnabled = false, midEnabled = true, highEnabled = true)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "xover_quick_vocal"
                )
            }
        }

        CrossoverBandCard(
            title = "BASS",
            rangeText = "20 Hz – ${AudioMath.formatFreq(crossover.lowMidHz)}",
            bandEnabled = crossover.lowEnabled,
            gainDb = crossover.lowGainDb,
            onToggleBand = { onUpdateCrossover { copy(lowEnabled = it) } },
            onGainChange = { g -> onUpdateCrossover { copy(lowGainDb = g) } },
            testTagPrefix = "xover_low"
        )

        CrossoverBandCard(
            title = "MITTEN",
            rangeText = "${AudioMath.formatFreq(crossover.lowMidHz)} – ${AudioMath.formatFreq(crossover.midHighHz)}",
            bandEnabled = crossover.midEnabled,
            gainDb = crossover.midGainDb,
            onToggleBand = { onUpdateCrossover { copy(midEnabled = it) } },
            onGainChange = { g -> onUpdateCrossover { copy(midGainDb = g) } },
            testTagPrefix = "xover_mid"
        )

        CrossoverBandCard(
            title = "HÖHEN",
            rangeText = "${AudioMath.formatFreq(crossover.midHighHz)} – 20 kHz",
            bandEnabled = crossover.highEnabled,
            gainDb = crossover.highGainDb,
            onToggleBand = { onUpdateCrossover { copy(highEnabled = it) } },
            onGainChange = { g -> onUpdateCrossover { copy(highGainDb = g) } },
            testTagPrefix = "xover_high"
        )
    }
}

@Composable
private fun QuickXoverChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val colors = LocalNothingColors.current
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.inkPrimary else colors.pillTrack)
            .border(1.dp, if (selected) colors.inkPrimary else colors.cardBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.pillActiveContent else colors.inkSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun CrossoverBandCard(
    title: String,
    rangeText: String,
    bandEnabled: Boolean,
    gainDb: Float,
    onToggleBand: (Boolean) -> Unit,
    onGainChange: (Float) -> Unit,
    testTagPrefix: String
) {
    val colors = LocalNothingColors.current
    NothingCard(contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (bandEnabled) colors.inkPrimary else colors.inkMuted
                )
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (bandEnabled) {
                    Text(
                        text = AudioMath.formatDb(gainDb),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.inkPrimary
                    )
                }
                NothingSwitchPill(
                    checked = bandEnabled,
                    onCheckedChange = onToggleBand,
                    testTag = "${testTagPrefix}_switch"
                )
            }
        }

        if (bandEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            NothingHorizontalDotSlider(
                value = gainDb,
                valueRange = -12f..12f,
                onValueChange = onGainChange,
                bipolarFromCenter = true,
                testTag = "${testTagPrefix}_slider"
            )
        }
    }
}
