package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.BluetoothAudioState
import com.example.model.AudioPreset
import com.example.model.BluetoothAudioSettings
import com.example.model.BluetoothDeviceRoute
import com.example.model.BluetoothDeviceRule
import com.example.model.SealAudioFormat
import com.example.model.SealDownloadTask
import com.example.model.SealSettings
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingSwitchPill
import com.example.ui.theme.LocalNothingColors
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsSheet(
    bluetoothState: BluetoothAudioState,
    bluetoothSettings: BluetoothAudioSettings,
    presets: List<AudioPreset>,
    themeMode: ThemeMode,
    activePresetName: String,
    sealSettings: SealSettings = SealSettings(),
    sealDownloads: List<SealDownloadTask> = emptyList(),
    onOpenQrStudio: () -> Unit,
    onOpenSealDownloader: () -> Unit = {},
    onUpdateSealFormat: (SealAudioFormat) -> Unit = {},
    onRefreshBluetooth: () -> Unit,
    onSetAutoBypassOnBluetooth: (Boolean) -> Unit,
    onSetStandardOutputRequiresRule: (Boolean) -> Unit,
    onSetStandardOutputRuleEnabled: (Boolean) -> Unit,
    onSaveCurrentAsStandardOutput: () -> Unit,
    onSetDeviceCustomEqEnabled: (deviceKey: String, deviceName: String, enabled: Boolean) -> Unit,
    onAssignCurrentEqToDevice: (deviceKey: String, deviceName: String) -> Unit,
    onAssignPresetToDevice: (deviceKey: String, deviceName: String, preset: AudioPreset) -> Unit,
    onRemoveDeviceRule: (deviceKey: String) -> Unit,
    onCycleThemeMode: () -> Unit,
    onShowMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalNothingColors.current

    BackHandler { onClose() }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshBluetooth()
        if (!granted) {
            onShowMessage("Bluetooth-Berechtigung verweigert.")
        }
    }

    val knownDevices: List<BluetoothDeviceRoute> = run {
        val map = linkedMapOf<String, BluetoothDeviceRoute>()
        bluetoothState.bondedDevices.forEach { map[it.key] = it }
        bluetoothSettings.deviceRules.values.forEach { rule ->
            if (!map.containsKey(rule.deviceKey) &&
                map.values.none { it.name.equals(rule.deviceName, ignoreCase = true) }
            ) {
                map[rule.deviceKey] = BluetoothDeviceRoute(
                    key = rule.deviceKey,
                    name = rule.deviceName,
                    isConnected = false,
                    isBonded = false
                )
            }
        }
        map.values.toList()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_sheet"),
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
                        text = "EINSTELLUNGEN",
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
                            .testTag("settings_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Einstellungen schließen",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Live Bluetooth Auto-Bypass Card (Highest relevance: active audio routing)
                    NothingCard {
                        NothingSectionHeader(
                            title = "BLUETOOTH",
                            trailingText = if (bluetoothState.isBluetoothConnected) "VERBUNDEN" else "GETRENNT"
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Effekte bei Bluetooth aus",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = "Bypass aktiv, außer eigenes Geräte-Profil.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkSecondary
                                )
                            }
                            NothingSwitchPill(
                                checked = bluetoothSettings.autoBypassOnBluetooth,
                                onCheckedChange = onSetAutoBypassOnBluetooth,
                                testTag = "settings_bt_autobypass_switch"
                            )
                        }

                        if (bluetoothState.connectedDevice != null) {
                            val activeBt = bluetoothState.connectedDevice
                            val activeRule = findRule(bluetoothSettings, activeBt)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.pillTrack)
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BluetoothConnected,
                                    contentDescription = null,
                                    tint = colors.blue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeBt.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (activeRule?.customEqEnabled == true) {
                                            "Profil: ${activeRule.profile.presetName}"
                                        } else if (bluetoothSettings.autoBypassOnBluetooth) {
                                            "Effekte aus"
                                        } else {
                                            "Standard-EQ aktiv"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 2. Per-Device Bluetooth Custom EQ Manager
                    NothingCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NothingSectionHeader(
                                title = "GERÄTE-EQ",
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.pillTrack)
                                    .clickable { onRefreshBluetooth() }
                                    .testTag("settings_bt_refresh_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Bluetooth aktualisieren",
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !bluetoothState.hasBluetoothPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.inkPrimary,
                                    contentColor = colors.pillActiveContent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_bt_permission_button")
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("GERÄTE LADEN")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (knownDevices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.pillTrack)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Keine gekoppelten Bluetooth-Geräte gefunden.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkSecondary
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                knownDevices.forEachIndexed { idx, device ->
                                    val rule = findRule(bluetoothSettings, device)
                                    val customEnabled = rule?.customEqEnabled == true
                                    BluetoothDeviceRuleCard(
                                        index = idx,
                                        device = device,
                                        rule = rule,
                                        customEnabled = customEnabled,
                                        presets = presets,
                                        onToggleCustom = { enable ->
                                            onSetDeviceCustomEqEnabled(device.key, device.name, enable)
                                        },
                                        onAssignCurrentEq = {
                                            onAssignCurrentEqToDevice(device.key, device.name)
                                            onShowMessage("EQ für '${device.name}' gespeichert")
                                        },
                                        onAssignPreset = { preset ->
                                            onAssignPresetToDevice(device.key, device.name, preset)
                                            onShowMessage("'${preset.name}' mit '${device.name}' verknüpft")
                                        },
                                        onRemoveRule = if (rule != null && !device.isConnected && !device.isBonded) {
                                            { onRemoveDeviceRule(rule.deviceKey) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    // 3. Normal (Non-Bluetooth) Output Connection Rule
                    NothingCard {
                        NothingSectionHeader(
                            title = "STANDARD-AUSGANG",
                            trailingText = if (bluetoothSettings.standardOutputRequiresRule) {
                                if (bluetoothSettings.standardOutputRuleEnabled) "DEFINIERT" else "AUS"
                            } else {
                                "MANUELL"
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Ohne Bluetooth aus (außer definiert)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.inkPrimary
                                )
                            }
                            NothingSwitchPill(
                                checked = bluetoothSettings.standardOutputRequiresRule,
                                onCheckedChange = onSetStandardOutputRequiresRule,
                                testTag = "settings_normal_require_rule_switch"
                            )
                        }

                        if (bluetoothSettings.standardOutputRequiresRule) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.pillTrack)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Speaker,
                                        contentDescription = null,
                                        tint = colors.inkPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (bluetoothSettings.standardOutputRuleEnabled) {
                                            bluetoothSettings.standardOutputProfile?.presetName ?: activePresetName
                                        } else {
                                            "Effekte AUS"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.inkPrimary
                                    )
                                }
                                NothingSwitchPill(
                                    checked = bluetoothSettings.standardOutputRuleEnabled,
                                    onCheckedChange = onSetStandardOutputRuleEnabled,
                                    testTag = "settings_normal_custom_switch"
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    onSaveCurrentAsStandardOutput()
                                    onShowMessage("Standard-EQ gespeichert")
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_normal_save_button")
                            ) {
                                Text("AKTUELLEN EQ ALS STANDARD DEFINIEREN")
                            }
                        }
                    }

                    // 4. Link Downloader (renamed, no blue accents)
                    NothingCard(modifier = Modifier.testTag("settings_seal_card")) {
                        NothingSectionHeader(
                            title = "LINK DOWNLOADER",
                            trailingText = if (sealDownloads.isNotEmpty()) "${sealDownloads.size} DATEIEN" else sealSettings.quality.label
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Qualität: ${sealSettings.quality.label} · Format: ${sealSettings.format.label} · ${sealSettings.effectiveLocationLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                SealAudioFormat.M4A,
                                SealAudioFormat.MP3,
                                SealAudioFormat.FLAC,
                                SealAudioFormat.OGG
                            ).forEach { fmt ->
                                val selected = sealSettings.format == fmt
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) colors.inkPrimary else colors.pillTrack)
                                        .border(
                                            1.dp,
                                            if (selected) colors.inkPrimary else colors.cardBorder,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { onUpdateSealFormat(fmt) }
                                        .padding(horizontal = 12.dp)
                                        .testTag("settings_seal_fmt_${fmt.extension}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = fmt.extension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colors.pillActiveContent else colors.inkPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenSealDownloader,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.inkPrimary,
                                contentColor = colors.pillActiveContent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_open_seal_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("LINK DOWNLOADER ÖFFNEN")
                        }
                    }

                    // 5. QR-Code Share & Scan entry
                    NothingCard {
                        NothingSectionHeader(
                            title = "QR-CODE",
                            trailingText = "NOTHING X"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "EQ als QR teilen oder QR scannen & anwenden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenQrStudio,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.inkPrimary,
                                contentColor = colors.pillActiveContent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_open_qr_button")
                        ) {
                            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("QR ERSTELLEN & SCANNEN")
                        }
                    }

                    // 6. Appearance card (Theme)
                    NothingCard {
                        NothingSectionHeader(title = "DARSTELLUNG")
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Design-Modus",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = themeMode.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = onCycleThemeMode,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("settings_theme_button")
                            ) {
                                Icon(Icons.Filled.Brightness4, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("WECHSELN")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceRuleCard(
    index: Int,
    device: BluetoothDeviceRoute,
    rule: BluetoothDeviceRule?,
    customEnabled: Boolean,
    presets: List<AudioPreset>,
    onToggleCustom: (Boolean) -> Unit,
    onAssignCurrentEq: () -> Unit,
    onAssignPreset: (AudioPreset) -> Unit,
    onRemoveRule: (() -> Unit)?
) {
    val colors = LocalNothingColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.pillTrack)
            .border(
                width = 1.dp,
                color = if (device.isConnected) colors.inkPrimary else colors.cardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (device.isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (device.isConnected) colors.blue else colors.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (customEnabled) {
                            rule?.profile?.presetName ?: "CUSTOM"
                        } else {
                            "AUS (BYPASS)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onRemoveRule != null) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Entfernen",
                    tint = colors.inkMuted,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onRemoveRule() }
                        .padding(6.dp)
                )
                Spacer(Modifier.width(4.dp))
            }

            NothingSwitchPill(
                checked = customEnabled,
                onCheckedChange = onToggleCustom,
                testTag = "bt_rule_switch_$index"
            )
        }

        if (customEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.inkPrimary)
                        .clickable { onAssignCurrentEq() }
                        .padding(horizontal = 14.dp)
                        .testTag("bt_assign_current_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AKTUELLEN EQ ÜBERNEHMEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.pillActiveContent
                    )
                }

                presets.take(6).forEach { preset ->
                    val isSelected = rule?.profile?.presetName == preset.name
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) colors.card else colors.canvas)
                            .border(
                                1.dp,
                                if (isSelected) colors.inkPrimary else colors.cardBorder,
                                RoundedCornerShape(50)
                            )
                            .clickable { onAssignPreset(preset) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun findRule(
    settings: BluetoothAudioSettings,
    device: BluetoothDeviceRoute
): BluetoothDeviceRule? {
    return settings.deviceRules[device.key]
        ?: settings.deviceRules.values.firstOrNull {
            it.deviceName.equals(device.name, ignoreCase = true)
        }
}
