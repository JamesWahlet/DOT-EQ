package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioMath
import com.example.model.CompressorPreset
import com.example.model.CompressorSettings
import com.example.model.DspStatus
import com.example.model.VisualizerFrame
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingHorizontalDotSlider
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingSwitchPill
import com.example.ui.theme.LocalNothingColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DynamicsScreen(
    ultraBassEnabled: Boolean,
    ultraBassLevel: Float,
    compressor: CompressorSettings,
    balance: Float,
    dspStatus: DspStatus,
    visualizerFrame: VisualizerFrame,
    onUpdateUltraBass: (enabled: Boolean?, level: Float?) -> Unit,
    onUpdateSimpleCompressor: (enabled: Boolean?, intensity: Float?, preset: CompressorPreset?) -> Unit,
    onBalanceChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Intelligent Beat-Detecting Ultra Bass
        NothingCard {
            val beatDotColor by animateColorAsState(
                targetValue = if (ultraBassEnabled && dspStatus.beatDetected) {
                    colors.yellow
                } else if (ultraBassEnabled) {
                    colors.inkPrimary
                } else {
                    colors.inkMuted.copy(alpha = 0.4f)
                },
                label = "ultra_bass_beat"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingSectionHeader(
                    title = "ULTRA BASS",
                    dotColor = beatDotColor,
                    trailingText = if (ultraBassEnabled) {
                        if (dspStatus.ultraBassBoostDb > 0.2f) {
                            "+%.1f dB".format(dspStatus.ultraBassBoostDb)
                        } else {
                            "${(ultraBassLevel * 100).roundToInt()}%"
                        }
                    } else {
                        "OFF"
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(10.dp))
                NothingSwitchPill(
                    checked = ultraBassEnabled,
                    onCheckedChange = { onUpdateUltraBass(it, null) },
                    testTag = "ultra_bass_switch"
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (ultraBassEnabled && dspStatus.beatDetected) {
                    "Beat erkannt · Sub-Bass aktiv verstärkt"
                } else {
                    "Erkennt Bässe & Beats intelligent"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            NothingHorizontalDotSlider(
                value = ultraBassLevel,
                valueRange = 0f..1f,
                onValueChange = { onUpdateUltraBass(null, it) },
                testTag = "ultra_bass_slider"
            )
        }

        // 2. Simplified, Responsive Compressor Card
        NothingCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingSectionHeader(
                    title = "KOMPRESSOR",
                    trailingText = if (compressor.enabled) {
                        "GR -%.1f dB".format(dspStatus.estimatedGainReductionDb)
                    } else {
                        "OFF"
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(10.dp))
                NothingSwitchPill(
                    checked = compressor.enabled,
                    onCheckedChange = { onUpdateSimpleCompressor(it, null, null) },
                    testTag = "compressor_switch"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Preset Pills (SANFT, BALANCED, PUNCH, LIMITER)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompressorPreset.entries.forEach { preset ->
                    val selected = compressor.preset == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.inkPrimary else colors.pillTrack)
                            .border(
                                1.dp,
                                if (selected) colors.inkPrimary else colors.cardBorder,
                                RoundedCornerShape(50)
                            )
                            .clickable { onUpdateSimpleCompressor(true, null, preset) }
                            .testTag("comp_preset_${preset.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.pillActiveContent else colors.inkSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(144.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.graphBackdrop)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pad = 16.dp.toPx()
                        val gw = size.width - pad * 2f
                        val gh = size.height - pad * 2f

                        for (c in 0..5) {
                            for (r in 0..5) {
                                drawCircle(
                                    color = colors.graphGridDot.copy(alpha = 0.45f),
                                    radius = 1.2.dp.toPx(),
                                    center = Offset(pad + gw * (c / 5f), pad + gh * (r / 5f))
                                )
                            }
                        }

                        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        drawLine(
                            color = colors.inkMuted.copy(alpha = 0.55f),
                            start = Offset(pad, pad + gh),
                            end = Offset(pad + gw, pad),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = dash
                        )

                        val curve = Path()
                        val steps = 40
                        for (s in 0..steps) {
                            val inDb = -48f + (s / steps.toFloat()) * 48f
                            val outDb = AudioMath.evaluateCompressorOutputDb(inDb, compressor).coerceIn(-48f, 0f)
                            val x = pad + ((inDb + 48f) / 48f) * gw
                            val y = pad + (1f - ((outDb + 48f) / 48f)) * gh
                            if (s == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
                        }
                        drawPath(
                            path = curve,
                            color = if (compressor.enabled) colors.inkPrimary else colors.inkMuted,
                            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .height(144.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.graphBackdrop)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "GR",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted
                    )
                    val grDots = 7
                    val litDots = ((dspStatus.estimatedGainReductionDb / 14f) * grDots).toInt().coerceIn(0, grDots)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 0 until grDots) {
                            val active = d < litDots
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) colors.inkPrimary else colors.graphGridDot.copy(alpha = 0.45f)
                                    )
                            )
                        }
                    }
                    Text(
                        text = "-${dspStatus.estimatedGainReductionDb.roundToInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (litDots > 0) colors.inkPrimary else colors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "STÄRKE",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSecondary
                )
                Text(
                    text = "${(compressor.intensity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inkPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            NothingHorizontalDotSlider(
                value = compressor.intensity,
                valueRange = 0f..1f,
                onValueChange = { onUpdateSimpleCompressor(null, it, null) },
                testTag = "comp_intensity_slider"
            )
        }

        // 3. Stereo L / R Balance Card
        NothingCard {
            val (leftDb, rightDb) = AudioMath.balanceToChannelGainsDb(balance)
            val balanceText = when {
                abs(balance) < 0.03f -> "MITTE"
                balance < 0f -> "L ${(-balance * 100).roundToInt()}%"
                else -> "R ${(balance * 100).roundToInt()}%"
            }

            NothingSectionHeader(
                title = "L / R BALANCE",
                trailingText = balanceText
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelMeterPill("L", visualizerFrame.leftLevel, leftDb, Modifier.weight(1f))
                ChannelMeterPill("R", visualizerFrame.rightLevel, rightDb, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            NothingHorizontalDotSlider(
                value = balance,
                valueRange = -1f..1f,
                onValueChange = onBalanceChange,
                bipolarFromCenter = true,
                testTag = "balance_slider"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "L ${AudioMath.formatDb(leftDb)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.pillTrack)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                        .clickable { onBalanceChange(0f) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("balance_center_button")
                ) {
                    Text(
                        text = "MITTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkPrimary
                    )
                }
                Text(
                    text = "R ${AudioMath.formatDb(rightDb)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary
                )
            }
        }
    }
}

@Composable
private fun ChannelMeterPill(
    channel: String,
    level: Float,
    attenDb: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.graphBackdrop)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = channel,
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkPrimary
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            drawRoundRect(
                color = colors.graphGridDot.copy(alpha = 0.5f),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
            val fillW = (size.width * level.coerceIn(0f, 1f)).coerceAtLeast(4f)
            drawRoundRect(
                color = colors.inkPrimary,
                size = Size(fillW, size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }
        Text(
            text = "${attenDb.roundToInt()}dB",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted
        )
    }
}
