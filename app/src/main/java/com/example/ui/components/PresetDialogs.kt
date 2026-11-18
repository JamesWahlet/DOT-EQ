package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.AudioMath
import com.example.i18n.LocalAppStrings
import com.example.model.AudioPreset
import com.example.model.EqState
import com.example.ui.theme.LocalNothingColors

@Composable
fun TopCreatePresetDialog(
    eqState: EqState,
    onSavePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalNothingColors.current
    val strings = LocalAppStrings.current
    var presetNameInput by remember { mutableStateOf("") }
    val effective10 = remember(eqState) { AudioMath.effectiveEqBands(eqState) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                    .testTag("top_create_preset_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NothingSectionHeader(
                        title = strings.createPresetTitle,
                        trailingText = if (eqState.ultraBassEnabled) "ULTRA BASS" else eqState.mode.name
                    )

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
                        placeholder = { Text("Preset-Name (z. B. STUDIO 01)") },
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
                            .testTag("top_preset_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(strings.cancel, color = colors.inkSecondary)
                        }
                        Button(
                            onClick = {
                                onSavePreset(presetNameInput)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.inkPrimary,
                                contentColor = colors.pillActiveContent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("top_confirm_save_preset_button")
                        ) {
                            Text(strings.save)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetManagerDialog(
    presets: List<AudioPreset>,
    activePresetName: String,
    onSelectPreset: (AudioPreset) -> Unit,
    onUpdatePreset: (presetId: String, newName: String, overwriteWithCurrentEq: Boolean) -> Unit,
    onDeletePreset: (presetId: String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalNothingColors.current
    val strings = LocalAppStrings.current
    var editingPreset by remember { mutableStateOf<AudioPreset?>(null) }
    var editNameInput by remember { mutableStateOf("") }
    var overwriteWithCurrent by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.frostedCard)
                    .border(1.2.dp, colors.cardBorder, RoundedCornerShape(30.dp))
                    .testTag("preset_manager_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.managePresetsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = strings.managePresetsSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.pillTrack)
                                .border(1.dp, colors.cardBorder, CircleShape)
                                .clickable { onDismiss() }
                                .testTag("preset_manager_close_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Schließen",
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    val targetEdit = editingPreset
                    if (targetEdit != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card)
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                                .padding(14.dp)
                                .testTag("preset_edit_section"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NothingSectionHeader(
                                title = "PRESET ÄNDERN",
                                trailingText = targetEdit.name
                            )

                            OutlinedTextField(
                                value = editNameInput,
                                onValueChange = { if (it.length <= 18) editNameInput = it },
                                label = { Text("Neuer Preset-Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.inkPrimary,
                                    unfocusedBorderColor = colors.cardBorder,
                                    focusedTextColor = colors.inkPrimary,
                                    unfocusedTextColor = colors.inkPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("preset_edit_name_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Aktuellen EQ übernehmen",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = "Überschreibt Bänder & Effekte mit aktuellem Stand",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.inkSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                NothingSwitchPill(
                                    checked = overwriteWithCurrent,
                                    onCheckedChange = { overwriteWithCurrent = it },
                                    testTag = "preset_edit_overwrite_switch"
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { editingPreset = null },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("preset_edit_cancel_button")
                                ) {
                                    Text("ZURÜCK", color = colors.inkSecondary)
                                }
                                Button(
                                    onClick = {
                                        onUpdatePreset(
                                            targetEdit.id,
                                            editNameInput,
                                            overwriteWithCurrent
                                        )
                                        editingPreset = null
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.inkPrimary,
                                        contentColor = colors.pillActiveContent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("preset_edit_save_button")
                                ) {
                                    Text("SPEICHERN")
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (presets.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.pillTrack)
                                        .padding(18.dp)
                                        .testTag("preset_manager_empty_state"),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = strings.noCustomPresetsTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = strings.noCustomPresetsDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkSecondary
                                    )
                                }
                            } else {
                                presets.forEach { preset ->
                                    val isActive = preset.name == activePresetName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isActive) colors.pillActiveContainer else colors.card)
                                            .border(
                                                width = 1.dp,
                                                color = if (isActive) colors.inkPrimary else colors.cardBorder,
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clickable {
                                                onSelectPreset(preset)
                                                onDismiss()
                                            }
                                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                                            .testTag("preset_manager_select_${preset.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isActive) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Aktiv",
                                                    tint = colors.pillActiveContent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = preset.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isActive) colors.pillActiveContent else colors.inkPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${preset.mode.name} · ${preset.tag}" +
                                                        if (preset.ultraBassEnabled) " · ULTRA BASS" else "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isActive) {
                                                        colors.pillActiveContent.copy(alpha = 0.78f)
                                                    } else {
                                                        colors.inkSecondary
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    editNameInput = preset.name
                                                    overwriteWithCurrent = false
                                                    editingPreset = preset
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("preset_manager_edit_${preset.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Preset ändern",
                                                    tint = if (isActive) colors.pillActiveContent else colors.inkPrimary,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeletePreset(preset.id) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("preset_manager_delete_${preset.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Preset löschen",
                                                    tint = if (isActive) {
                                                        colors.pillActiveContent
                                                    } else if (colors.isMonochrome) {
                                                        colors.inkSecondary
                                                    } else {
                                                        colors.red
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onCreateNew()
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.inkPrimary,
                                contentColor = colors.pillActiveContent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("preset_manager_new_button")
                        ) {
                            Text(strings.createNewPresetBtn)
                        }
                    }
                }
            }
        }
    }
}
