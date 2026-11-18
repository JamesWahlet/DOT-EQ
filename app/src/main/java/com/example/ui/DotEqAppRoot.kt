package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BluetoothConnectionPopupDialog
import com.example.ui.components.EssentialSpaceLogo
import com.example.ui.components.NothingHardwarePowerSwitch
import com.example.ui.screens.AiTunerScreen
import com.example.ui.screens.CrossoverScreen
import com.example.ui.screens.DynamicsScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.QrStudioSheet
import com.example.ui.screens.SealDownloaderSheet
import com.example.ui.screens.SettingsSheet
import com.example.ui.theme.LocalNothingColors
import com.example.ui.viewmodel.EqViewModel
import com.example.ui.viewmodel.StudioTab
import kotlinx.coroutines.launch

@Composable
fun DotEqAppRoot(viewModel: EqViewModel) {
    val context = LocalContext.current
    val colors = LocalNothingColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val eqState by viewModel.eqState.collectAsStateWithLifecycle()
    val dspStatus by viewModel.dspStatus.collectAsStateWithLifecycle()
    val visualizerFrame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val visualizerMode by viewModel.visualizerMode.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val presets by viewModel.allPresets.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiSending by viewModel.isAiSending.collectAsStateWithLifecycle()
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsStateWithLifecycle()
    val bluetoothState by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val bluetoothSettings by viewModel.bluetoothSettings.collectAsStateWithLifecycle()
    val bluetoothPopupDevice by viewModel.bluetoothPopupDevice.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val routeNotice by viewModel.activeRouteNotice.collectAsStateWithLifecycle()
    val sealSettings by viewModel.sealSettings.collectAsStateWithLifecycle()
    val sealDownloads by viewModel.sealDownloads.collectAsStateWithLifecycle()
    val sealSearchResults by viewModel.sealSearchResults.collectAsStateWithLifecycle()
    val isSealSearching by viewModel.isSealSearching.collectAsStateWithLifecycle()
    val sealSearchError by viewModel.sealSearchError.collectAsStateWithLifecycle()

    var showQrStudio by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSealStudio by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Audio-Berechtigung benötigt für System-Visualizer.")
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.toggleSystemService(true)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onAudioPermissionResult(granted)
        viewModel.refreshBluetoothDevices()
    }

    if (activeTab != StudioTab.EQ && !showQrStudio && !showSettings && !showSealStudio) {
        BackHandler {
            viewModel.selectTab(StudioTab.EQ)
        }
    }

    Scaffold(
        containerColor = colors.canvas,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 700.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    NothingNavigationRail(
                        activeTab = activeTab,
                        onSelectTab = viewModel::selectTab,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                    ) {
                        DotEqTopBar(
                            masterEnabled = eqState.masterEnabled,
                            engineLabel = routeNotice ?: dspStatus.engineType.label,
                            isBluetoothConnected = bluetoothState.isBluetoothConnected,
                            onToggleMaster = viewModel::toggleMasterEnabled,
                            onOpenSettings = {
                                viewModel.refreshBluetoothDevices()
                                showSettings = true
                            }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Crossfade(
                                targetState = activeTab,
                                animationSpec = tween(220),
                                label = "studio_tab"
                            ) { tab ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    when (tab) {
                                        StudioTab.EQ -> {
                                            EqualizerScreen(
                                                eqState = eqState,
                                                dspStatus = dspStatus,
                                                visualizerFrame = visualizerFrame,
                                                visualizerMode = visualizerMode,
                                                hasAudioPermission = hasAudioPermission,
                                                presets = presets,
                                                onModeChange = viewModel::setEqMode,
                                                onSimpleChange = viewModel::updateSimpleBands,
                                                onExpertBandChange = viewModel::updateExpertBand,
                                                onExpertBandsBatch = viewModel::updateExpertBandsBatch,
                                                onPreampChange = viewModel::updatePreamp,
                                                onResetFlat = viewModel::resetEqFlat,
                                                onApplyPreset = { preset ->
                                                    viewModel.applyPreset(preset)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Preset '${preset.name}' aktiv")
                                                    }
                                                },
                                                onSavePreset = { name ->
                                                    viewModel.saveCurrentAsPreset(name)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Preset gespeichert")
                                                    }
                                                },
                                                onDeletePreset = viewModel::deleteCustomPreset,
                                                onToggleSystemService = { enable ->
                                                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                        val notifGranted = ContextCompat.checkSelfPermission(
                                                            context,
                                                            Manifest.permission.POST_NOTIFICATIONS
                                                        ) == PackageManager.PERMISSION_GRANTED
                                                        if (!notifGranted) {
                                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                        } else {
                                                            viewModel.toggleSystemService(true)
                                                        }
                                                    } else {
                                                        viewModel.toggleSystemService(enable)
                                                    }
                                                },
                                                onVisualizerModeChange = viewModel::setVisualizerMode,
                                                onToggleTestAudio = viewModel::toggleTestAudio,
                                                onRequestAudioPermission = {
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            )
                                        }
                                        StudioTab.CROSSOVER -> {
                                            CrossoverScreen(
                                                crossover = eqState.crossover,
                                                onUpdateCrossover = viewModel::updateCrossover
                                            )
                                        }
                                        StudioTab.DYNAMICS -> {
                                            DynamicsScreen(
                                                ultraBassEnabled = eqState.ultraBassEnabled,
                                                ultraBassLevel = eqState.ultraBassLevel,
                                                compressor = eqState.compressor,
                                                balance = eqState.balance,
                                                dspStatus = dspStatus,
                                                visualizerFrame = visualizerFrame,
                                                onUpdateUltraBass = viewModel::updateUltraBass,
                                                onUpdateSimpleCompressor = viewModel::updateSimpleCompressor,
                                                onBalanceChange = viewModel::updateBalance
                                            )
                                        }
                                        StudioTab.AI -> {
                                            AiTunerScreen(
                                                authUser = authUser,
                                                isAuthLoading = isAuthLoading,
                                                authError = authError,
                                                eqState = eqState,
                                                bluetoothState = bluetoothState,
                                                chatMessages = chatMessages,
                                                isSending = isAiSending,
                                                voiceState = voiceState,
                                                sealDownloads = sealDownloads,
                                                onSignInGoogle = { viewModel.signInWithGoogle(context) },
                                                onSignOutGoogle = { viewModel.signOutGoogle(context) },
                                                onSendMessage = viewModel::sendAiChatMessage,
                                                onStartVoiceInput = {
                                                    viewModel.onAudioPermissionResult(true)
                                                    viewModel.startVoiceInput()
                                                },
                                                onStopAndSendVoice = viewModel::stopAndSendVoiceInput,
                                                onCancelVoice = viewModel::cancelVoiceInput,
                                                onResetChat = viewModel::resetAiChat,
                                                onApplyRecommendation = { rec ->
                                                    viewModel.applyAiRecommendation(rec)
                                                    viewModel.selectTab(StudioTab.EQ)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("KI-Profil '${rec.preset.name}' aktiv")
                                                    }
                                                },
                                                onSaveAiPreset = { rec ->
                                                    viewModel.applyAiRecommendation(rec)
                                                    viewModel.saveCurrentAsPreset(rec.preset.name, isAiGenerated = true)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("KI-Profil gespeichert")
                                                    }
                                                },
                                                onCopySongLink = { track ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Link kopiert: ${track.title}")
                                                    }
                                                },
                                                onDownloadSong = { track ->
                                                    viewModel.startSealDownload(track)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Download gestartet: ${track.title}")
                                                    }
                                                },
                                                onOpenSealDownloader = { showSealStudio = true }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        if (!isWideScreen) {
                            NothingBottomDock(
                                activeTab = activeTab,
                                onSelectTab = viewModel::selectTab,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))
            ) {
                SettingsSheet(
                    bluetoothState = bluetoothState,
                    bluetoothSettings = bluetoothSettings,
                    presets = presets,
                    themeMode = themeMode,
                    activePresetName = eqState.activePresetName,
                    sealSettings = sealSettings,
                    sealDownloads = sealDownloads,
                    onOpenQrStudio = { showQrStudio = true },
                    onOpenSealDownloader = { showSealStudio = true },
                    onUpdateSealFormat = viewModel::updateSealFormat,
                    onRefreshBluetooth = viewModel::refreshBluetoothDevices,
                    onSetAutoBypassOnBluetooth = viewModel::setAutoBypassOnBluetooth,
                    onSetStandardOutputRequiresRule = viewModel::setStandardOutputRequiresRule,
                    onSetStandardOutputRuleEnabled = viewModel::setStandardOutputRuleEnabled,
                    onSaveCurrentAsStandardOutput = viewModel::saveCurrentEqAsStandardOutputRule,
                    onSetDeviceCustomEqEnabled = viewModel::setBluetoothDeviceCustomEqEnabled,
                    onAssignCurrentEqToDevice = viewModel::assignCurrentEqToBluetoothDevice,
                    onAssignPresetToDevice = viewModel::assignPresetToBluetoothDevice,
                    onRemoveDeviceRule = viewModel::removeBluetoothDeviceRule,
                    onCycleThemeMode = viewModel::cycleThemeMode,
                    onShowMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onClose = { showSettings = false }
                )
            }

            AnimatedVisibility(
                visible = showQrStudio,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))
            ) {
                QrStudioSheet(
                    eqState = eqState,
                    onSaveAndApplyPreset = { preset ->
                        viewModel.saveImportedQrPreset(preset, applyNow = true)
                    },
                    onShowMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onClose = { showQrStudio = false }
                )
            }

            AnimatedVisibility(
                visible = showSealStudio,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))
            ) {
                SealDownloaderSheet(
                    sealSettings = sealSettings,
                    downloads = sealDownloads,
                    searchResults = sealSearchResults,
                    isSearching = isSealSearching,
                    searchError = sealSearchError,
                    onSearchSongs = viewModel::searchSealSongs,
                    onDirectDownloadInput = { input, quality ->
                        viewModel.startSealDownloadFromInput(input, quality)
                    },
                    onStartTrackDownload = { track -> viewModel.startSealDownload(track) },
                    onCancelDownload = viewModel::cancelSealDownload,
                    onRemoveTask = viewModel::removeSealDownloadTask,
                    onClearHistory = viewModel::clearSealDownloadHistory,
                    onUpdateFormat = viewModel::updateSealFormat,
                    onUpdateQuality = viewModel::updateSealQuality,
                    onUpdateSaveLocation = viewModel::updateSealSaveLocation,
                    onUpdateCustomFolder = viewModel::updateSealCustomFolder,
                    onUpdateFilenameTemplate = viewModel::updateSealFilenameTemplate,
                    onShowMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onClose = { showSealStudio = false }
                )
            }

            bluetoothPopupDevice?.let { btDevice ->
                BluetoothConnectionPopupDialog(
                    device = btDevice,
                    bluetoothSettings = bluetoothSettings,
                    presets = presets,
                    onToggleCustomRule = { enable ->
                        viewModel.setBluetoothDeviceCustomEqEnabled(btDevice.key, btDevice.name, enable)
                    },
                    onAssignCurrentEq = {
                        viewModel.assignCurrentEqToBluetoothDevice(btDevice.key, btDevice.name)
                        scope.launch {
                            snackbarHostState.showSnackbar("EQ für '${btDevice.name}' gespeichert")
                        }
                    },
                    onAssignPreset = { preset ->
                        viewModel.assignPresetToBluetoothDevice(btDevice.key, btDevice.name, preset)
                        scope.launch {
                            snackbarHostState.showSnackbar("'${preset.name}' mit '${btDevice.name}' verknüpft")
                        }
                    },
                    onOpenSettings = {
                        viewModel.dismissBluetoothPopup()
                        viewModel.refreshBluetoothDevices()
                        showSettings = true
                    },
                    onDismiss = viewModel::dismissBluetoothPopup
                )
            }
        }
    }
}

@Composable
private fun DotEqTopBar(
    masterEnabled: Boolean,
    engineLabel: String,
    isBluetoothConnected: Boolean,
    onToggleMaster: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalNothingColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "DOTEQ (01)",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.inkPrimary,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isBluetoothConnected) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothConnected,
                        contentDescription = "Bluetooth Audio",
                        tint = colors.blue,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = engineLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(1.dp, colors.cardBorder, CircleShape)
                    .clickable { onOpenSettings() }
                    .testTag("top_settings_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Einstellungen & QR",
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            NothingHardwarePowerSwitch(
                enabled = masterEnabled,
                onToggle = onToggleMaster,
                testTag = "master_power_button"
            )
        }
    }
}

/**
 * Bottom Navigation Bar carrying Nothing's signature colors:
 * EQ (Red) · X-OVER (Yellow) · DYN/BAL (Nothing Blue) · AI (Borderless Yellow Essential Space Logo)
 */
@Composable
private fun NothingBottomDock(
    activeTab: StudioTab,
    onSelectTab: (StudioTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockTabItem(
            label = "EQ",
            icon = Icons.Filled.Equalizer,
            selected = activeTab == StudioTab.EQ,
            tabColor = colors.red,
            activeContentColor = Color.White,
            onClick = { onSelectTab(StudioTab.EQ) },
            modifier = Modifier.weight(1f),
            testTag = "nav_eq"
        )
        DockTabItem(
            label = "X-OVER",
            icon = Icons.AutoMirrored.Filled.CallSplit,
            selected = activeTab == StudioTab.CROSSOVER,
            tabColor = colors.yellow,
            activeContentColor = Color(0xFF15171A),
            onClick = { onSelectTab(StudioTab.CROSSOVER) },
            modifier = Modifier.weight(1f),
            testTag = "nav_crossover"
        )
        DockTabItem(
            label = "DYN/BAL",
            icon = Icons.Filled.Tune,
            selected = activeTab == StudioTab.DYNAMICS,
            tabColor = colors.blue,
            activeContentColor = Color.White,
            onClick = { onSelectTab(StudioTab.DYNAMICS) },
            modifier = Modifier.weight(1f),
            testTag = "nav_dynamics"
        )
        DockAiTabItem(
            selected = activeTab == StudioTab.AI,
            onClick = { onSelectTab(StudioTab.AI) },
            modifier = Modifier.weight(1f),
            testTag = "nav_ai"
        )
    }
}

@Composable
private fun NothingNavigationRail(
    activeTab: StudioTab,
    onSelectTab: (StudioTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNothingColors.current
    Column(
        modifier = modifier
            .width(98.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DockTabItem("EQ", Icons.Filled.Equalizer, activeTab == StudioTab.EQ, colors.red, Color.White, { onSelectTab(StudioTab.EQ) }, Modifier.fillMaxWidth(), "nav_eq")
        DockTabItem("X-OVER", Icons.AutoMirrored.Filled.CallSplit, activeTab == StudioTab.CROSSOVER, colors.yellow, Color(0xFF15171A), { onSelectTab(StudioTab.CROSSOVER) }, Modifier.fillMaxWidth(), "nav_crossover")
        DockTabItem("DYN/BAL", Icons.Filled.Tune, activeTab == StudioTab.DYNAMICS, colors.blue, Color.White, { onSelectTab(StudioTab.DYNAMICS) }, Modifier.fillMaxWidth(), "nav_dynamics")
        DockAiTabItem(activeTab == StudioTab.AI, { onSelectTab(StudioTab.AI) }, Modifier.fillMaxWidth(), "nav_ai")
    }
}

@Composable
private fun DockTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tabColor: Color,
    activeContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val colors = LocalNothingColors.current
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) tabColor else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) activeContentColor else colors.inkSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeContentColor else colors.inkSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun DockAiTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val colors = LocalNothingColors.current
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.inkPrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Essential Space logo without border, always signature yellow
        EssentialSpaceLogo(
            color = colors.yellow,
            size = 19.dp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "KI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.pillActiveContent else colors.inkSecondary,
            maxLines = 1
        )
    }
}
