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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.BluetoothAudioState
import com.example.auth.AuthenticatedUser
import com.example.i18n.LocalAppStrings
import com.example.model.AppLanguage
import com.example.model.AudioPreset
import com.example.model.BluetoothAudioSettings
import com.example.model.BluetoothDeviceRoute
import com.example.model.BluetoothDeviceRule
import com.example.model.GitHubUpdateState
import com.example.model.SealAudioFormat
import com.example.model.SealDownloadTask
import com.example.model.SealSettings
import com.example.model.SettingsSubgroup
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingSwitchPill
import com.example.ui.theme.LocalNothingColors
import com.example.ui.theme.ThemeMode
import com.example.update.GitHubUpdateRepository

@Composable
fun SettingsSheet(
    bluetoothState: BluetoothAudioState,
    bluetoothSettings: BluetoothAudioSettings,
    presets: List<AudioPreset>,
    themeMode: ThemeMode,
    monochromeEnabled: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.DE,
    authUser: AuthenticatedUser? = null,
    isAuthLoading: Boolean = false,
    authError: String? = null,
    githubRepo: String = GitHubUpdateRepository.DEFAULT_GITHUB_REPO,
    updateState: GitHubUpdateState = GitHubUpdateState.Idle,
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
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetMonochromeEnabled: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onSignInGoogle: () -> Unit = {},
    onSignOutGoogle: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onUpdateGithubRepo: (String) -> Unit = {},
    onShowMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalNothingColors.current
    val strings = LocalAppStrings.current
    var selectedSubgroup by remember { mutableStateOf(SettingsSubgroup.CONNECTIONS) }

    BackHandler {
        onClose()
    }

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
                        text = strings.settingsTitle,
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
                            contentDescription = strings.settingsCloseDesc,
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Subgroup quick-jump / filter bar ordered by relevance
                val subgroupPills = listOf(
                    SettingsSubgroup.CONNECTIONS to strings.groupConnections,
                    SettingsSubgroup.SPECIAL to strings.groupSpecial,
                    SettingsSubgroup.DESIGN to strings.groupDesign,
                    SettingsSubgroup.ACCOUNT to strings.groupAccount,
                    SettingsSubgroup.LANGUAGE to strings.groupLanguage,
                    SettingsSubgroup.INFO to strings.groupInfo
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subgroupPills.forEach { (group, label) ->
                        val active = selectedSubgroup == group
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) colors.pillActiveContainer else colors.card)
                                .border(
                                    width = 1.dp,
                                    color = if (active) colors.inkPrimary else colors.cardBorder,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { selectedSubgroup = group }
                                .padding(horizontal = 14.dp)
                                .testTag("settings_group_${group.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) colors.pillActiveContent else colors.inkPrimary,
                                maxLines = 1
                            )
                        }
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
                    // ============================================================
                    // 1. VERBINDUNGEN (Bluetooth, Geräte-EQ, Standard-Ausgang)
                    // ============================================================
                    if (selectedSubgroup == SettingsSubgroup.CONNECTIONS) {
                        SettingsSubgroupBanner(
                            title = strings.groupConnections,
                            subtitle = strings.groupConnectionsSub,
                            icon = Icons.Filled.BluetoothConnected,
                            testTag = "settings_banner_connections"
                        )

                        // Live Bluetooth Auto-Bypass Card
                        NothingCard {
                            NothingSectionHeader(
                                title = strings.btSectionTitle,
                                trailingText = if (bluetoothState.isBluetoothConnected) strings.btConnected else strings.btDisconnected
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = strings.btAutoBypassTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = strings.btAutoBypassDesc,
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

                    // Per-Device Bluetooth Custom EQ Manager
                    NothingCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NothingSectionHeader(
                                title = strings.deviceEqTitle,
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
                                    text = strings.noPairedBt,
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

                    // Normal (Non-Bluetooth) Output Connection Rule
                    NothingCard {
                        NothingSectionHeader(
                            title = strings.standardOutputTitle,
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
                                    text = strings.standardOutputSwitchTitle,
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
                                Text(strings.saveCurrentAsStandardBtn)
                            }
                        }
                    }
                    } // end CONNECTIONS

                    // ============================================================
                    // 2. SPEZIALFUNKTIONEN (Link Downloader, QR-Code Studio)
                    // ============================================================
                    if (selectedSubgroup == SettingsSubgroup.SPECIAL) {
                        SettingsSubgroupBanner(
                            title = strings.groupSpecial,
                            subtitle = strings.groupSpecialSub,
                            icon = Icons.Filled.AutoAwesome,
                            testTag = "settings_banner_special"
                        )

                        // Link Downloader
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
                                Text(strings.openLinkDownloaderBtn)
                            }
                        }

                        // QR-Code Share & Scan entry
                        NothingCard {
                            NothingSectionHeader(
                                title = strings.qrSectionTitle,
                                trailingText = "NOTHING X"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.qrSectionDesc,
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
                                Text(strings.qrOpenBtn)
                            }
                        }
                    } // end SPECIAL

                    // ============================================================
                    // 3. DESIGN (Hell / Dunkel / System / System UI + Monochrom)
                    // ============================================================
                    if (selectedSubgroup == SettingsSubgroup.DESIGN) {
                        SettingsSubgroupBanner(
                            title = strings.groupDesign,
                            subtitle = strings.groupDesignSub,
                            icon = Icons.Filled.Palette,
                            testTag = "settings_banner_design"
                        )

                        NothingCard {
                            NothingSectionHeader(
                                title = strings.designSectionTitle,
                                trailingText = themeMode.label
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = when (themeMode) {
                                    ThemeMode.LIGHT -> strings.designLightDesc
                                    ThemeMode.DARK -> strings.designDarkDesc
                                    ThemeMode.SYSTEM -> strings.designSystemDesc
                                    ThemeMode.SYSTEM_UI -> strings.designSystemUiDesc
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val topModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK)
                            val bottomModes = listOf(ThemeMode.SYSTEM, ThemeMode.SYSTEM_UI)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(topModes, bottomModes).forEach { rowModes ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowModes.forEach { mode ->
                                            val selected = themeMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(if (selected) colors.pillActiveContainer else colors.pillTrack)
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (selected) colors.inkPrimary else colors.cardBorder,
                                                        shape = RoundedCornerShape(50)
                                                    )
                                                    .clickable { onSelectThemeMode(mode) }
                                                    .testTag("theme_mode_${mode.name.lowercase()}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = mode.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) colors.pillActiveContent else colors.inkPrimary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.cardBorder.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.monochromeTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = strings.monochromeDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                NothingSwitchPill(
                                    checked = monochromeEnabled,
                                    onCheckedChange = onSetMonochromeEnabled,
                                    testTag = "settings_monochrome_switch"
                                )
                            }
                        }
                    } // end DESIGN

                    // ============================================================
                    // 4. KONTO (Google-Konto & KI-Zugang)
                    // ============================================================
                    if (selectedSubgroup == SettingsSubgroup.ACCOUNT) {
                        SettingsSubgroupBanner(
                            title = strings.groupAccount,
                            subtitle = strings.groupAccountSub,
                            icon = Icons.Filled.AccountCircle,
                            testTag = "settings_banner_account"
                        )
                        AccountSettingsCard(
                            authUser = authUser,
                            isAuthLoading = isAuthLoading,
                            authError = authError,
                            onSignInGoogle = onSignInGoogle,
                            onSignOutGoogle = onSignOutGoogle
                        )
                    } // end ACCOUNT

                    // ============================================================
                    // 5. SPRACHE (Deutsch / Englisch)
                    // ============================================================
                    if (selectedSubgroup == SettingsSubgroup.LANGUAGE) {
                        SettingsSubgroupBanner(
                            title = strings.groupLanguage,
                            subtitle = strings.groupLanguageSub,
                            icon = Icons.Filled.Language,
                            testTag = "settings_banner_language"
                        )
                        LanguageSettingsCard(
                            appLanguage = appLanguage,
                            onSelectLanguage = onSelectLanguage
                        )
                    } // end LANGUAGE

                    // ============================================================
                    // 6. INFO (Funktionen, Version, GitHub Updates & App-Website)
                    // ============================================================
                    if (selectedSubgroup == SettingsSubgroup.INFO) {
                        SettingsSubgroupBanner(
                            title = strings.groupInfo,
                            subtitle = strings.groupInfoSub,
                            icon = Icons.Filled.Info,
                            testTag = "settings_banner_info"
                        )
                        InfoSettingsCard(
                            githubRepo = githubRepo,
                            updateState = updateState,
                            onCheckForUpdates = onCheckForUpdates,
                            onUpdateGithubRepo = onUpdateGithubRepo,
                            onShowMessage = onShowMessage
                        )
                    } // end INFO

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
