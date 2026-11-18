package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.DspStatus
import com.example.model.VisualizerFrame
import com.example.model.VisualizerMode
import com.example.ui.theme.LocalNothingColors

@Composable
fun VisualizerWidget(
    frame: VisualizerFrame,
    status: DspStatus,
    mode: VisualizerMode,
    hasAudioPermission: Boolean,
    onModeChange: (VisualizerMode) -> Unit,
    onToggleTestAudio: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current

    NothingCard(
        modifier = modifier,
        cornerRadius = 26.dp,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (status.isTestAudioPlaying || frame.hasSignal) colors.inkPrimary
                            else colors.inkMuted
                        )
                )
                Text(
                    text = "SPECTRUM",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.inkPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (status.isTestAudioPlaying) colors.inkPrimary else colors.pillTrack
                        )
                        .clickable { onToggleTestAudio() }
                        .padding(horizontal = 12.dp)
                        .testTag("test_audio_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (status.isTestAudioPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = "Test-Audio umschalten",
                        tint = if (status.isTestAudioPlaying) colors.pillActiveContent else colors.inkPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (status.isTestAudioPlaying) "SYNTH" else "TEST",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.isTestAudioPlaying) colors.pillActiveContent else colors.inkPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.pillTrack)
                        .clickable {
                            onModeChange(
                                if (mode == VisualizerMode.SPECTRUM) VisualizerMode.WAVEFORM
                                else VisualizerMode.SPECTRUM
                            )
                        }
                        .padding(horizontal = 12.dp)
                        .testTag("visualizer_mode_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Visualizer Modus",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (mode == VisualizerMode.SPECTRUM) "FFT" else "WAVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.graphBackdrop)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                if (mode == VisualizerMode.SPECTRUM) {
                    val bars = frame.bars
                    val peaks = frame.peaks
                    val colCount = bars.size.coerceAtLeast(1)
                    val colW = w / colCount
                    val dotsPerCol = 7
                    val dotRadius = minOf(colW * 0.28f, (h / dotsPerCol) * 0.34f)

                    for (c in 0 until colCount) {
                        val cx = colW * c + colW / 2f
                        val level = bars[c].coerceIn(0f, 1f)
                        val activeDots = (level * dotsPerCol).toInt().coerceIn(0, dotsPerCol)
                        val peakDot = ((peaks.getOrElse(c) { 0f }) * (dotsPerCol - 1)).toInt()
                            .coerceIn(0, dotsPerCol - 1)

                        for (d in 0 until dotsPerCol) {
                            val cy = h - ((d + 0.5f) / dotsPerCol) * h
                            val isLit = d < activeDots
                            val isPeak = frame.hasSignal && d == peakDot
                            val dotColor = when {
                                isPeak -> colors.inkPrimary
                                isLit -> colors.inkPrimary.copy(alpha = 0.78f)
                                else -> colors.graphGridDot.copy(alpha = 0.4f)
                            }
                            drawCircle(
                                color = dotColor,
                                radius = dotRadius,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                } else {
                    val wave = frame.waveform
                    val cy = h / 2f
                    for (i in 0 until 24) {
                        drawCircle(
                            color = colors.graphGridDot.copy(alpha = 0.5f),
                            radius = 1.2.dp.toPx(),
                            center = Offset(w * (i / 23f), cy)
                        )
                    }
                    if (wave.isNotEmpty()) {
                        val path = Path()
                        for (i in wave.indices) {
                            val x = w * (i / (wave.size - 1).coerceAtLeast(1).toFloat())
                            val y = cy - wave[i] * (h * 0.42f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = if (frame.hasSignal) colors.inkPrimary else colors.inkMuted,
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            if (!hasAudioPermission && !status.isTestAudioPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(colors.card.copy(alpha = 0.94f))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                        .clickable { onRequestAudioPermission() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("grant_audio_permission_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Audio Capture erlauben",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "LIVE AUDIO FREIGEBEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkPrimary
                        )
                    }
                }
            }
        }
    }
}
