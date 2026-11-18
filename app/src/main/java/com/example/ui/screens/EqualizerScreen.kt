package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.AudioMath
import com.example.model.AudioPreset
import com.example.model.DspStatus
import com.example.model.EQ_FREQUENCY_LABELS
import com.example.model.EqMode
import com.example.model.EqState
import com.example.model.VisualizerFrame
import com.example.model.VisualizerMode
import com.example.ui.components.InteractiveEqCanvas
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingHorizontalDotSlider
import com.example.ui.components.NothingMatrixReticleOverlay
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingSegmentedPill
import com.example.ui.components.NothingSwitchPill
import com.example.ui.components.NothingVerticalPillFader
import com.example.ui.components.VisualizerWidget
import com.example.ui.theme.LocalNothingColors

@Composable
fun EqualizerScreen(
    eqState: EqState,
    dspStatus: DspStatus,
    visualizerFrame: VisualizerFrame,
    visualizerMode: VisualizerMode,
    hasAudioPermission: Boolean,
    presets: List<AudioPreset>,
    onModeChange: (EqMode) -> Unit,
    onSimpleChange: (Float?, Float?, Float?) -> Unit,
    onExpertBandChange: (Int, Float) -> Unit,
    onExpertBandsBatch: (List<Float>) -> Unit,
    onPreampChange: (Float) -> Unit,
    onResetFlat: () -> Unit,
    onApplyPreset: (AudioPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onToggleSystemService: (Boolean) -> Unit,
    onVisualizerModeChange: (VisualizerMode) -> Unit,
    onToggleTestAudio: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Monochrome Dot-Matrix Visualizer
        VisualizerWidget(
            frame = visualizerFrame,
            status = dspStatus,
            mode = visualizerMode,
            hasAudioPermission = hasAudioPermission,
            onModeChange = onVisualizerModeChange,
            onToggleTestAudio = onToggleTestAudio,
            onRequestAudioPermission = onRequestAudioPermission
        )

        // 2. Main EQ Hero Card (Monochrome + Fat Hardware Controls)
        NothingCard {
            NothingSectionHeader(
                title = eqState.activePresetName,
                trailingText = if (eqState.masterEnabled) "DSP ON" else "BYPASS"
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingSegmentedPill(
                    options = listOf(
                        EqMode.SIMPLE to "SIMPLE",
                        EqMode.EXPERT to "EXPERT"
                    ),
                    selected = eqState.mode,
                    onSelect = onModeChange,
                    modifier = Modifier.weight(1f),
                    testTagPrefix = "eq_mode"
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.pillTrack)
                        .clickable { onResetFlat() }
                        .testTag("reset_eq_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "EQ zurücksetzen",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Touch-interactive EQ Preview (Simple & Expert)
            InteractiveEqCanvas(
                eqState = eqState,
                onSimpleChange = onSimpleChange,
                onExpertBandsChange = onExpertBandsBatch
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Fat Monochrome Hardware Faders
            if (eqState.mode == EqMode.SIMPLE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NothingVerticalPillFader(
                        label = "LOW",
                        valueDb = eqState.simpleLowDb,
                        onValueChange = { onSimpleChange(it, null, null) },
                        height = 196.dp,
                        compact = false,
                        testTag = "simple_fader_low"
                    )
                    NothingVerticalPillFader(
                        label = "MID",
                        valueDb = eqState.simpleMidDb,
                        onValueChange = { onSimpleChange(null, it, null) },
                        height = 196.dp,
                        compact = false,
                        testTag = "simple_fader_mid"
                    )
                    NothingVerticalPillFader(
                        label = "HIGH",
                        valueDb = eqState.simpleHighDb,
                        onValueChange = { onSimpleChange(null, null, it) },
                        height = 196.dp,
                        compact = false,
                        testTag = "simple_fader_high"
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EQ_FREQUENCY_LABELS.forEachIndexed { idx, freqLabel ->
                        NothingVerticalPillFader(
                            label = freqLabel,
                            valueDb = eqState.expertBandsDb.getOrElse(idx) { 0f },
                            onValueChange = { onExpertBandChange(idx, it) },
                            height = 182.dp,
                            compact = true,
                            testTag = "expert_fader_$freqLabel"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Fat Preamp Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PREAMP",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSecondary
                )
                Text(
                    text = AudioMath.formatDb(eqState.preampDb),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inkPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            NothingHorizontalDotSlider(
                value = eqState.preampDb,
                valueRange = -12f..12f,
                onValueChange = onPreampChange,
                bipolarFromCenter = true,
                testTag = "preamp_slider"
            )
        }

        // 3. Minimalist Presets Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.inkPrimary)
                    .clickable { showSaveDialog = true }
                    .padding(horizontal = 16.dp)
                    .testTag("save_preset_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Preset speichern",
                    tint = colors.pillActiveContent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "SAVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.pillActiveContent
                )
            }

            presets.forEach { preset ->
                val isSelected = eqState.activePresetName == preset.name
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) colors.inkPrimary else colors.card)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) colors.inkPrimary else colors.cardBorder,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onApplyPreset(preset) }
                        .padding(horizontal = 16.dp)
                        .testTag("preset_${preset.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) colors.pillActiveContent else colors.inkPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (preset.isCustom) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Preset löschen",
                            tint = if (isSelected) colors.pillActiveContent else colors.inkMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDeletePreset(preset.id) }
                        )
                    }
                }
            }
        }

        // 4. Compact System-Wide Service Row
        NothingCard(contentPadding = 16.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYSTEM-DSP",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (dspStatus.isServiceActive) "Aktiv im Hintergrund" else "Nur in App aktiv",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                NothingSwitchPill(
                    checked = eqState.systemServiceEnabled,
                    onCheckedChange = onToggleSystemService,
                    testTag = "system_service_switch"
                )
            }
        }
    }

    if (showSaveDialog) {
        Dialog(
            onDismissRequest = { showSaveDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val effective10 = remember(eqState) { AudioMath.effectiveEqBands(eqState) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(colors.frostedCard)
                        .border(1.2.dp, colors.cardBorder, RoundedCornerShape(30.dp))
                        .testTag("save_preset_dialog")
                ) {
                    NothingMatrixReticleOverlay(
                        modifier = Modifier.matchParentSize(),
                        bracketColor = colors.inkPrimary.copy(alpha = 0.6f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NothingSectionHeader(
                            title = "PRESET ERSTELLEN",
                            trailingText = if (eqState.ultraBassEnabled) "ULTRA BASS" else eqState.mode.name
                        )

                        // Mini EQ Curve Snapshot of the Profile Being Created
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.graphBackdrop)
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val midY = size.height * 0.5f
                                val amp = size.height * 0.38f
                                for (c in 1 until 10) {
                                    val gx = size.width * (c / 10f)
                                    drawCircle(
                                        color = colors.graphGridDot.copy(alpha = 0.5f),
                                        radius = 1.2.dp.toPx(),
                                        center = Offset(gx, midY)
                                    )
                                }
                                val path = Path()
                                for (s in 0..48) {
                                    val t = s / 48f
                                    val freq = AudioMath.normToFreq(t)
                                    val db = AudioMath.evaluateEqCurveDb(freq, effective10, eqState.preampDb)
                                        .coerceIn(-12f, 12f)
                                    val x = t * size.width
                                    val y = midY - (db / 12f) * amp
                                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = colors.inkPrimary,
                                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { if (it.length <= 18) presetNameInput = it },
                            placeholder = { Text("Profilname (z. B. STUDIO 01)") },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inkPrimary,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedTextColor = colors.inkPrimary,
                                unfocusedTextColor = colors.inkPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preset_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSaveDialog = false },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("ABBRECHEN", color = colors.inkSecondary)
                            }
                            Button(
                                onClick = {
                                    onSavePreset(presetNameInput)
                                    presetNameInput = ""
                                    showSaveDialog = false
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.inkPrimary,
                                    contentColor = colors.pillActiveContent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("confirm_save_preset_button")
                            ) {
                                Text("SPEICHERN")
                            }
                        }
                    }
                }
            }
        }
    }
}
