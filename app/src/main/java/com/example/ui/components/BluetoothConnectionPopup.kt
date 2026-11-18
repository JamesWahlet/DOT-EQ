package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AudioPreset
import com.example.model.BluetoothAudioSettings
import com.example.model.BluetoothDeviceRoute
import com.example.ui.theme.LocalNothingColors

/**
 * Hardware-style floating Bluetooth connection card shown when a wireless audio device connects.
 * Allows immediate inspection of bypass vs custom profile, preset binding, or opening settings.
 */
@Composable
fun BluetoothConnectionPopupDialog(
    device: BluetoothDeviceRoute,
    bluetoothSettings: BluetoothAudioSettings,
    presets: List<AudioPreset>,
    onToggleCustomRule: (Boolean) -> Unit,
    onAssignCurrentEq: () -> Unit,
    onAssignPreset: (AudioPreset) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalNothingColors.current
    val existingRule = bluetoothSettings.deviceRules[device.key]
        ?: bluetoothSettings.deviceRules.values.firstOrNull {
            it.deviceName.equals(device.name, ignoreCase = true)
        }
    val hasCustomRule = existingRule?.customEqEnabled == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(colors.frostedCard)
                    .border(1.2.dp, colors.cardBorder, RoundedCornerShape(32.dp))
                    .testTag("bluetooth_popup_window")
            ) {
                NothingMatrixReticleOverlay(
                    modifier = Modifier.matchParentSize(),
                    bracketColor = colors.blue.copy(alpha = 0.65f),
                    dotAccentColor = colors.inkMuted
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Status Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.pillTrack)
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BluetoothConnected,
                                contentDescription = null,
                                tint = colors.blue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "AUDIO VERBUNDEN",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.pillTrack)
                                .border(1.dp, colors.cardBorder, CircleShape)
                                .clickable { onDismiss() }
                                .testTag("bt_popup_close_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Popup schließen",
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hardware Illustration with Glyph Ring
                    NothingBudsGlyphIllustration(
                        accentColor = colors.blue,
                        inkColor = colors.inkPrimary,
                        modifier = Modifier.size(116.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val statusBadge = when {
                        hasCustomRule -> "PROFIL: ${existingRule?.profile?.presetName ?: "CUSTOM"}"
                        bluetoothSettings.autoBypassOnBluetooth -> "ALLE EFFEKTE AUS (BYPASS)"
                        else -> "STANDARD-DSP AKTIV"
                    }
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasCustomRule) colors.blue else colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom EQ vs Effects Off Toggle Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.pillTrack)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Geräte-EQ aktiv",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = if (hasCustomRule) "Angepasstes Profil" else "Aus bei Verbindung",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSecondary
                            )
                        }
                        NothingSwitchPill(
                            checked = hasCustomRule,
                            onCheckedChange = onToggleCustomRule,
                            activeDotColor = Color.White,
                            testTag = "bt_popup_custom_switch"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Preset Strip for Instant Assignment
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.take(6).forEach { preset ->
                            val isBound = hasCustomRule && existingRule?.profile?.presetName == preset.name
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isBound) colors.blue else colors.card)
                                    .border(
                                        1.dp,
                                        if (isBound) colors.blue else colors.cardBorder,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { onAssignPreset(preset) }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isBound) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isBound) Color.White else colors.inkPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("bt_popup_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Einstellungen",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = {
                                onAssignCurrentEq()
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
                                .testTag("bt_popup_save_custom_button")
                        ) {
                            Text(
                                text = "EQ SPEICHERN",
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
