package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiChatActionResult
import com.example.ai.AiChatMessage
import com.example.ai.AiSoundRecommendation
import com.example.ai.SoundTuningAiRepository
import com.example.ai.VoiceCaptureState
import com.example.ai.VoiceInputController
import com.example.audio.AudioMath
import com.example.audio.BluetoothAudioMonitor
import com.example.audio.BluetoothAudioState
import com.example.audio.SystemDspEngine
import com.example.audio.SystemEqService
import com.example.auth.AuthenticatedUser
import com.example.auth.GoogleAuthManager
import com.example.data.EqPreferencesRepository
import com.example.model.AudioPreset
import com.example.model.BluetoothAudioSettings
import com.example.model.BluetoothDeviceRoute
import com.example.model.BluetoothDeviceRule
import com.example.model.CompressorPreset
import com.example.model.CompressorSettings
import com.example.model.CrossoverSettings
import com.example.model.DspStatus
import com.example.model.EqMode
import com.example.model.EqProfileSnapshot
import com.example.model.EqState
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealDownloadTask
import com.example.model.SealSaveLocation
import com.example.model.SealSettings
import com.example.model.SealTrackSuggestion
import com.example.model.VisualizerFrame
import com.example.model.VisualizerMode
import com.example.model.toProfileSnapshot
import com.example.model.withProfileSnapshot
import com.example.seal.SealDownloadManager
import com.example.seal.SealMediaRepository
import com.example.ui.theme.ThemeMode
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiTuningUiState {
    data object Idle : AiTuningUiState
    data object Loading : AiTuningUiState
    data class Success(val recommendation: AiSoundRecommendation) : AiTuningUiState
    data class Error(val message: String) : AiTuningUiState
}

enum class StudioTab {
    EQ,
    CROSSOVER,
    DYNAMICS,
    AI
}

class EqViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = EqPreferencesRepository(application)
    private val aiRepo = SoundTuningAiRepository()
    private val authManager = GoogleAuthManager(application)
    private val bluetoothMonitor = BluetoothAudioMonitor(application)
    private val sealRepo = SealMediaRepository(application)
    private val sealManager = SealDownloadManager(viewModelScope, prefsRepo, sealRepo)

    private val _eqState = MutableStateFlow(EqState())
    val eqState: StateFlow<EqState> = _eqState.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _visualizerMode = MutableStateFlow(VisualizerMode.SPECTRUM)
    val visualizerMode: StateFlow<VisualizerMode> = _visualizerMode.asStateFlow()

    private val _activeTab = MutableStateFlow(StudioTab.EQ)
    val activeTab: StateFlow<StudioTab> = _activeTab.asStateFlow()

    private val _aiState = MutableStateFlow<AiTuningUiState>(AiTuningUiState.Idle)
    val aiState: StateFlow<AiTuningUiState> = _aiState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiSending = MutableStateFlow(false)
    val isAiSending: StateFlow<Boolean> = _isAiSending.asStateFlow()

    private val _authUser = MutableStateFlow(authManager.currentUser())
    val authUser: StateFlow<AuthenticatedUser?> = _authUser.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _bluetoothSettings = MutableStateFlow(BluetoothAudioSettings())
    val bluetoothSettings: StateFlow<BluetoothAudioSettings> = _bluetoothSettings.asStateFlow()

    val bluetoothState: StateFlow<BluetoothAudioState> = bluetoothMonitor.state

    private val _activeRouteNotice = MutableStateFlow<String?>(null)
    val activeRouteNotice: StateFlow<String?> = _activeRouteNotice.asStateFlow()

    private val _bluetoothPopupDevice = MutableStateFlow<BluetoothDeviceRoute?>(null)
    val bluetoothPopupDevice: StateFlow<BluetoothDeviceRoute?> = _bluetoothPopupDevice.asStateFlow()

    private val voiceController = VoiceInputController(
        context = application,
        onTranscriptReady = { spokenText ->
            sendAiChatMessage(spokenText)
        },
        onAudioWavReady = { wavBytes ->
            sendAiAudioMessage(wavBytes)
        }
    )
    val voiceState: StateFlow<VoiceCaptureState> = voiceController.state

    val sealSettings: StateFlow<SealSettings> = sealManager.settings
    val sealDownloads: StateFlow<List<SealDownloadTask>> = sealManager.downloads
    val sealSearchResults: StateFlow<List<SealTrackSuggestion>> = sealManager.searchResults
    val isSealSearching: StateFlow<Boolean> = sealManager.isSearching
    val sealSearchError: StateFlow<String?> = sealManager.searchError

    val dspStatus: StateFlow<DspStatus> = SystemDspEngine.status
    val visualizerFrame: StateFlow<VisualizerFrame> = SystemDspEngine.visualizerFrame

    val allPresets: StateFlow<List<AudioPreset>> = prefsRepo.customPresetsFlow
        .combine(MutableStateFlow(EqPreferencesRepository.BUILT_IN_PRESETS)) { custom, builtIn ->
            custom + builtIn
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EqPreferencesRepository.BUILT_IN_PRESETS
        )

    private var saveJob: Job? = null
    private var lastObservedBtKey: String? = null
    private var lastPopupDeviceKey: String? = null
    private var lastNonBtSnapshot: EqProfileSnapshot? = null
    private var lastNonBtMasterEnabled: Boolean = true
    private var isInitialized = false
    private var userMutatedStateBeforeInit = false
    private var userMutatedBtBeforeInit = false

    init {
        viewModelScope.launch {
            val savedState = prefsRepo.eqStateFlow.first()
            val savedTheme = prefsRepo.themeModeFlow.first()
            val savedBt = prefsRepo.bluetoothSettingsFlow.first()

            if (!userMutatedStateBeforeInit) {
                _eqState.value = savedState
                lastNonBtSnapshot = savedState.toProfileSnapshot()
                lastNonBtMasterEnabled = savedState.masterEnabled
            }
            _themeMode.value = savedTheme
            if (!userMutatedBtBeforeInit) {
                _bluetoothSettings.value = savedBt
            }

            SystemDspEngine.initialize(getApplication(), _eqState.value)
            sealManager.loadInitial()
            bluetoothMonitor.start()

            val initialBt = bluetoothMonitor.state.value.connectedDevice
            isInitialized = true
            applyRoutePolicy(connectedBt = initialBt, isTransitionFromBt = false)
            if (initialBt != null) {
                lastPopupDeviceKey = initialBt.key
                _bluetoothPopupDevice.value = initialBt
            }

            if (_eqState.value.systemServiceEnabled) {
                startSystemServiceSafely(_eqState.value)
            }

            // Attempt silent Google Sign-In if already authorized on device
            launch {
                val silentUser = authManager.attemptSilentSignIn()
                if (silentUser != null && _authUser.value == null) {
                    _authUser.value = silentUser
                }
            }

            bluetoothMonitor.state.collect { btState ->
                val connected = btState.connectedDevice
                val newKey = connected?.key
                if (newKey != lastObservedBtKey) {
                    val wasOnBt = lastObservedBtKey != null
                    lastObservedBtKey = newKey
                    applyRoutePolicy(connectedBt = connected, isTransitionFromBt = wasOnBt)
                }
                if (connected != null && connected.key != lastPopupDeviceKey) {
                    lastPopupDeviceKey = connected.key
                    _bluetoothPopupDevice.value = connected
                } else if (connected == null && lastPopupDeviceKey != null) {
                    lastPopupDeviceKey = null
                    _bluetoothPopupDevice.value = null
                }
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_isAuthLoading.value) return
        _isAuthLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val result = authManager.signInInteractive(activityContext)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { user ->
                    _authUser.value = user
                    _authError.value = null
                },
                onFailure = { err ->
                    _authError.value = err.message ?: "Google-Anmeldung fehlgeschlagen."
                }
            )
        }
    }

    fun signOutGoogle(activityContext: Context) {
        viewModelScope.launch {
            authManager.signOut(activityContext)
            _authUser.value = null
            _authError.value = null
            resetAiChat()
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun refreshBluetoothDevices() {
        bluetoothMonitor.refresh()
        val connected = bluetoothMonitor.state.value.connectedDevice
        applyRoutePolicy(connectedBt = connected, isTransitionFromBt = false)
        if (connected != null && connected.key != lastPopupDeviceKey) {
            lastPopupDeviceKey = connected.key
            _bluetoothPopupDevice.value = connected
        }
    }

    fun dismissBluetoothPopup() {
        _bluetoothPopupDevice.value = null
    }

    fun openBluetoothPopupForDevice(device: BluetoothDeviceRoute) {
        lastPopupDeviceKey = device.key
        _bluetoothPopupDevice.value = device
    }

    private fun findMatchingBluetoothRule(
        device: BluetoothDeviceRoute,
        settings: BluetoothAudioSettings
    ): BluetoothDeviceRule? {
        return settings.deviceRules[device.key]
            ?: settings.deviceRules.values.firstOrNull {
                it.deviceName.equals(device.name, ignoreCase = true)
            }
    }

    private fun applyRoutePolicy(
        connectedBt: BluetoothDeviceRoute?,
        isTransitionFromBt: Boolean
    ) {
        if (!isInitialized) return
        val settings = _bluetoothSettings.value

        if (connectedBt != null) {
            if (!isTransitionFromBt && lastObservedBtKey == null) {
                lastObservedBtKey = connectedBt.key
            }
            val rule = findMatchingBluetoothRule(connectedBt, settings)
            if (rule != null && rule.customEqEnabled) {
                val updated = _eqState.value.withProfileSnapshot(rule.profile, masterEnabled = true)
                commitState(updated, updateNonBtBackup = false)
                _activeRouteNotice.value = "BT: ${connectedBt.name} · ${rule.profile.presetName}"
            } else if (settings.autoBypassOnBluetooth) {
                val updated = _eqState.value.copy(masterEnabled = false)
                commitState(updated, updateNonBtBackup = false)
                _activeRouteNotice.value = "BT: ${connectedBt.name} · Effekte aus"
            } else {
                _activeRouteNotice.value = "BT: ${connectedBt.name}"
            }
        } else {
            if (settings.standardOutputRequiresRule) {
                val stdProfile = settings.standardOutputProfile
                if (settings.standardOutputRuleEnabled && stdProfile != null) {
                    val updated = _eqState.value.withProfileSnapshot(stdProfile, masterEnabled = true)
                    commitState(updated, updateNonBtBackup = false)
                    _activeRouteNotice.value = "Standard · ${stdProfile.presetName}"
                } else {
                    val updated = _eqState.value.copy(masterEnabled = false)
                    commitState(updated, updateNonBtBackup = false)
                    _activeRouteNotice.value = "Standard · Effekte aus"
                }
            } else if (isTransitionFromBt) {
                val backup = lastNonBtSnapshot
                val updated = if (backup != null) {
                    _eqState.value.withProfileSnapshot(backup, masterEnabled = lastNonBtMasterEnabled)
                } else {
                    _eqState.value.copy(masterEnabled = lastNonBtMasterEnabled)
                }
                commitState(updated, updateNonBtBackup = false)
                _activeRouteNotice.value = null
            } else {
                _activeRouteNotice.value = null
            }
        }
    }

    fun setAutoBypassOnBluetooth(enabled: Boolean) {
        val next = _bluetoothSettings.value.copy(autoBypassOnBluetooth = enabled)
        saveAndApplyBluetoothSettings(next)
    }

    fun setStandardOutputRequiresRule(requiresRule: Boolean) {
        val current = _bluetoothSettings.value
        val next = current.copy(
            standardOutputRequiresRule = requiresRule,
            standardOutputProfile = current.standardOutputProfile ?: _eqState.value.toProfileSnapshot()
        )
        saveAndApplyBluetoothSettings(next)
    }

    fun setStandardOutputRuleEnabled(enabled: Boolean) {
        val current = _bluetoothSettings.value
        val next = current.copy(
            standardOutputRuleEnabled = enabled,
            standardOutputProfile = current.standardOutputProfile ?: _eqState.value.toProfileSnapshot()
        )
        saveAndApplyBluetoothSettings(next)
    }

    fun saveCurrentEqAsStandardOutputRule() {
        val snapshot = _eqState.value.toProfileSnapshot()
        val next = _bluetoothSettings.value.copy(
            standardOutputRuleEnabled = true,
            standardOutputProfile = snapshot
        )
        saveAndApplyBluetoothSettings(next)
    }

    fun setBluetoothDeviceCustomEqEnabled(
        deviceKey: String,
        deviceName: String,
        enabled: Boolean
    ) {
        val current = _bluetoothSettings.value
        val existing = current.deviceRules[deviceKey]
        val rule = existing?.copy(
            deviceName = deviceName,
            customEqEnabled = enabled
        ) ?: BluetoothDeviceRule(
            deviceKey = deviceKey,
            deviceName = deviceName,
            customEqEnabled = enabled,
            profile = _eqState.value.toProfileSnapshot()
        )
        val updatedMap = current.deviceRules.toMutableMap().apply {
            put(deviceKey, rule)
        }
        saveAndApplyBluetoothSettings(current.copy(deviceRules = updatedMap))
    }

    fun assignCurrentEqToBluetoothDevice(deviceKey: String, deviceName: String) {
        val current = _bluetoothSettings.value
        val rule = BluetoothDeviceRule(
            deviceKey = deviceKey,
            deviceName = deviceName,
            customEqEnabled = true,
            profile = _eqState.value.toProfileSnapshot()
        )
        val updatedMap = current.deviceRules.toMutableMap().apply {
            put(deviceKey, rule)
        }
        saveAndApplyBluetoothSettings(current.copy(deviceRules = updatedMap))
    }

    fun assignPresetToBluetoothDevice(
        deviceKey: String,
        deviceName: String,
        preset: AudioPreset
    ) {
        val current = _bluetoothSettings.value
        val rule = BluetoothDeviceRule(
            deviceKey = deviceKey,
            deviceName = deviceName,
            customEqEnabled = true,
            profile = preset.toProfileSnapshot()
        )
        val updatedMap = current.deviceRules.toMutableMap().apply {
            put(deviceKey, rule)
        }
        saveAndApplyBluetoothSettings(current.copy(deviceRules = updatedMap))
    }

    fun removeBluetoothDeviceRule(deviceKey: String) {
        val current = _bluetoothSettings.value
        if (!current.deviceRules.containsKey(deviceKey)) return
        val updatedMap = current.deviceRules.toMutableMap().apply {
            remove(deviceKey)
        }
        saveAndApplyBluetoothSettings(current.copy(deviceRules = updatedMap))
    }

    private fun saveAndApplyBluetoothSettings(next: BluetoothAudioSettings) {
        userMutatedBtBeforeInit = true
        _bluetoothSettings.value = next
        viewModelScope.launch {
            prefsRepo.saveBluetoothSettings(next)
        }
        applyRoutePolicy(
            connectedBt = bluetoothMonitor.state.value.connectedDevice,
            isTransitionFromBt = false
        )
    }

    fun selectTab(tab: StudioTab) {
        _activeTab.value = tab
    }

    fun cycleThemeMode() {
        val next = when (_themeMode.value) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        _themeMode.value = next
        viewModelScope.launch {
            prefsRepo.saveThemeMode(next)
        }
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        _visualizerMode.value = mode
    }

    fun onAudioPermissionResult(granted: Boolean) {
        _hasAudioPermission.value = granted
        if (granted) {
            SystemDspEngine.tryStartVisualizer(getApplication())
        }
    }

    fun toggleMasterEnabled() {
        updateState { copy(masterEnabled = !masterEnabled) }
    }

    fun toggleSystemService(enabled: Boolean) {
        updateState { copy(systemServiceEnabled = enabled) }
        if (enabled) {
            startSystemServiceSafely(_eqState.value)
        } else {
            try {
                SystemEqService.stop(getApplication())
            } catch (_: Exception) {
            }
        }
    }

    fun toggleTestAudio() {
        val currentlyPlaying = dspStatus.value.isTestAudioPlaying
        SystemDspEngine.startOrStopTestAudio(getApplication(), !currentlyPlaying)
    }

    fun setEqMode(mode: EqMode) {
        val current = _eqState.value
        if (current.mode == mode) return
        val updated = if (mode == EqMode.EXPERT && current.mode == EqMode.SIMPLE) {
            current.copy(
                mode = EqMode.EXPERT,
                expertBandsDb = AudioMath.simpleToExpertBands(
                    current.simpleLowDb,
                    current.simpleMidDb,
                    current.simpleHighDb
                )
            )
        } else {
            val (low, mid, high) = AudioMath.expertToSimpleSummary(current.expertBandsDb)
            current.copy(
                mode = EqMode.SIMPLE,
                simpleLowDb = low,
                simpleMidDb = mid,
                simpleHighDb = high
            )
        }
        commitState(updated)
    }

    fun updateSimpleBands(lowDb: Float? = null, midDb: Float? = null, highDb: Float? = null) {
        updateState {
            val newLow = (lowDb ?: simpleLowDb).coerceIn(-12f, 12f)
            val newMid = (midDb ?: simpleMidDb).coerceIn(-12f, 12f)
            val newHigh = (highDb ?: simpleHighDb).coerceIn(-12f, 12f)
            copy(
                simpleLowDb = newLow,
                simpleMidDb = newMid,
                simpleHighDb = newHigh,
                expertBandsDb = AudioMath.simpleToExpertBands(newLow, newMid, newHigh),
                activePresetName = "CUSTOM // SIMPLE"
            )
        }
    }

    fun updateExpertBand(index: Int, gainDb: Float) {
        updateState {
            val mutable = expertBandsDb.toMutableList()
            if (index in mutable.indices) {
                mutable[index] = gainDb.coerceIn(-12f, 12f)
            }
            val (l, m, h) = AudioMath.expertToSimpleSummary(mutable)
            copy(
                expertBandsDb = mutable,
                simpleLowDb = l,
                simpleMidDb = m,
                simpleHighDb = h,
                activePresetName = "CUSTOM // EXPERT"
            )
        }
    }

    fun updateExpertBandsBatch(newBands: List<Float>) {
        if (newBands.size != 10) return
        updateState {
            val clamped = newBands.map { it.coerceIn(-12f, 12f) }
            val (l, m, h) = AudioMath.expertToSimpleSummary(clamped)
            copy(
                expertBandsDb = clamped,
                simpleLowDb = l,
                simpleMidDb = m,
                simpleHighDb = h,
                activePresetName = "CUSTOM // CURVE"
            )
        }
    }

    fun updatePreamp(preampDb: Float) {
        updateState { copy(preampDb = preampDb.coerceIn(-12f, 12f)) }
    }

    fun updateUltraBass(enabled: Boolean? = null, level: Float? = null) {
        updateState {
            copy(
                ultraBassEnabled = enabled ?: ultraBassEnabled,
                ultraBassLevel = (level ?: ultraBassLevel).coerceIn(0f, 1f)
            )
        }
    }

    fun resetEqFlat() {
        updateState {
            copy(
                simpleLowDb = 0f,
                simpleMidDb = 0f,
                simpleHighDb = 0f,
                expertBandsDb = List(10) { 0f },
                preampDb = 0f,
                ultraBassEnabled = false,
                activePresetName = "FLAT // 0dB"
            )
        }
    }

    fun updateCrossover(transform: CrossoverSettings.() -> CrossoverSettings) {
        updateState {
            val next = crossover.transform()
            val safeLowMid = next.lowMidHz.coerceIn(50f, 800f)
            val safeMidHigh = next.midHighHz.coerceIn(maxOf(safeLowMid + 250f, 700f), 10000f)
            copy(
                crossover = next.copy(
                    lowMidHz = safeLowMid,
                    midHighHz = safeMidHigh,
                    lowGainDb = next.lowGainDb.coerceIn(-12f, 12f),
                    midGainDb = next.midGainDb.coerceIn(-12f, 12f),
                    highGainDb = next.highGainDb.coerceIn(-12f, 12f)
                )
            )
        }
    }

    fun updateSimpleCompressor(
        enabled: Boolean? = null,
        intensity: Float? = null,
        preset: CompressorPreset? = null
    ) {
        updateState {
            val nextEnabled = enabled ?: compressor.enabled
            val nextIntensity = (intensity ?: compressor.intensity).coerceIn(0f, 1f)
            val nextPreset = preset ?: compressor.preset
            copy(
                compressor = AudioMath.compressorFromSimple(nextEnabled, nextIntensity, nextPreset)
            )
        }
    }

    fun updateCompressor(transform: CompressorSettings.() -> CompressorSettings) {
        updateState {
            val next = compressor.transform()
            copy(
                compressor = next.copy(
                    intensity = next.intensity.coerceIn(0f, 1f),
                    thresholdDb = next.thresholdDb.coerceIn(-40f, 0f),
                    ratio = next.ratio.coerceIn(1f, 20f),
                    attackMs = next.attackMs.coerceIn(1f, 100f),
                    releaseMs = next.releaseMs.coerceIn(20f, 500f),
                    kneeDb = next.kneeDb.coerceIn(0f, 12f),
                    makeupGainDb = next.makeupGainDb.coerceIn(0f, 18f)
                )
            )
        }
    }

    fun updateBalance(balance: Float) {
        val snapped = if (kotlin.math.abs(balance) < 0.03f) 0f else balance.coerceIn(-1f, 1f)
        updateState { copy(balance = snapped) }
    }

    fun applyPreset(preset: AudioPreset) {
        updateState {
            copy(
                masterEnabled = true,
                mode = preset.mode,
                simpleLowDb = preset.simpleLowDb,
                simpleMidDb = preset.simpleMidDb,
                simpleHighDb = preset.simpleHighDb,
                expertBandsDb = preset.expertBandsDb,
                preampDb = preset.preampDb,
                ultraBassEnabled = preset.ultraBassEnabled,
                ultraBassLevel = preset.ultraBassLevel,
                crossover = preset.crossover,
                compressor = preset.compressor,
                balance = preset.balance,
                activePresetName = preset.name
            )
        }
    }

    fun saveImportedQrPreset(preset: AudioPreset, applyNow: Boolean) {
        viewModelScope.launch {
            val existing = prefsRepo.customPresetsFlow.first()
            prefsRepo.saveCustomPresets(listOf(preset) + existing)
            if (applyNow) {
                applyPreset(preset)
            }
        }
    }

    fun saveCurrentAsPreset(name: String, isAiGenerated: Boolean = false) {
        val trimmed = name.trim().uppercase().take(18).ifBlank { "CUSTOM EQ" }
        val s = _eqState.value
        val newPreset = AudioPreset(
            id = "custom_${UUID.randomUUID()}",
            name = trimmed,
            tag = if (isAiGenerated) "AI // SAVED" else "USER",
            description = "Benutzerdefiniertes Klangprofil",
            isCustom = true,
            isAiGenerated = isAiGenerated,
            mode = s.mode,
            simpleLowDb = s.simpleLowDb,
            simpleMidDb = s.simpleMidDb,
            simpleHighDb = s.simpleHighDb,
            expertBandsDb = s.expertBandsDb,
            preampDb = s.preampDb,
            crossover = s.crossover,
            compressor = s.compressor,
            balance = s.balance,
            ultraBassEnabled = s.ultraBassEnabled,
            ultraBassLevel = s.ultraBassLevel
        )
        viewModelScope.launch {
            val existing = prefsRepo.customPresetsFlow.first()
            prefsRepo.saveCustomPresets(listOf(newPreset) + existing)
            updateState { copy(activePresetName = trimmed) }
        }
    }

    fun deleteCustomPreset(presetId: String) {
        viewModelScope.launch {
            val existing = prefsRepo.customPresetsFlow.first()
            prefsRepo.saveCustomPresets(existing.filterNot { it.id == presetId })
        }
    }

    fun startVoiceInput() {
        if (_authUser.value == null || _isAiSending.value) return
        voiceController.startListening()
    }

    fun stopAndSendVoiceInput() {
        voiceController.finishListeningAndSubmit()
    }

    fun cancelVoiceInput() {
        voiceController.cancelListening()
    }

    fun updateSealFormat(format: SealAudioFormat) = sealManager.updateFormat(format)
    fun updateSealQuality(quality: SealAudioQuality) = sealManager.updateQuality(quality)
    fun updateSealSaveLocation(location: SealSaveLocation) = sealManager.updateSaveLocation(location)
    fun updateSealCustomFolder(uri: android.net.Uri?, label: String) = sealManager.updateCustomFolder(uri, label)
    fun updateSealFilenameTemplate(template: String) = sealManager.updateFilenameTemplate(template)
    fun searchSealSongs(query: String) = sealManager.searchSongs(query)
    fun startSealDownloadFromInput(input: String, qualityOverride: SealAudioQuality? = null) =
        sealManager.startDownloadFromInput(input, qualityOverride)
    fun startSealDownload(
        suggestion: SealTrackSuggestion,
        formatOverride: String? = null,
        qualityOverride: SealAudioQuality? = null
    ) = sealManager.startDownload(suggestion, formatOverride, qualityOverride)
    fun cancelSealDownload(taskId: String) = sealManager.cancelDownload(taskId)
    fun removeSealDownloadTask(taskId: String) = sealManager.removeTask(taskId)
    fun clearSealDownloadHistory() = sealManager.clearHistory()

    fun sendAiAudioMessage(wavBytes: ByteArray) {
        if (_authUser.value == null || _isAiSending.value || wavBytes.isEmpty()) return

        _chatMessages.value = _chatMessages.value + AiChatMessage(
            isUser = true,
            text = "Sprachbefehl gesendet"
        )
        _isAiSending.value = true
        _aiState.value = AiTuningUiState.Loading

        viewModelScope.launch {
            val result = aiRepo.sendAssistantAudioMessage(
                wavBytes = wavBytes,
                currentEqState = _eqState.value,
                bluetoothState = bluetoothMonitor.state.value,
                bluetoothSettings = _bluetoothSettings.value,
                presets = allPresets.value
            )

            result.fold(
                onSuccess = { action ->
                    val updatedEq = applyAiChatAction(action)
                    val (songHits, songErr) = sealManager.resolveForAiAction(action)
                    _isAiSending.value = false
                    val assistantMsg = AiChatMessage(
                        isUser = false,
                        text = action.assistantReply,
                        appliedBadges = action.appliedBadges,
                        previewBandsDb = if (action.applyEq || action.resetFlat || action.saveAsPreset) {
                            AudioMath.effectiveEqBands(updatedEq)
                        } else {
                            null
                        },
                        recommendation = action.recommendation,
                        downloadSuggestions = songHits,
                        downloadLookupError = songErr
                    )
                    _chatMessages.value = _chatMessages.value + assistantMsg
                    _aiState.value = action.recommendation?.let { AiTuningUiState.Success(it) }
                        ?: AiTuningUiState.Idle
                },
                onFailure = { err ->
                    _isAiSending.value = false
                    val errorMsg = err.message ?: "Sprachbefehl fehlgeschlagen."
                    _chatMessages.value = _chatMessages.value + AiChatMessage(
                        isUser = false,
                        text = errorMsg,
                        isError = true
                    )
                    _aiState.value = AiTuningUiState.Error(errorMsg)
                }
            )
        }
    }

    fun sendAiChatMessage(prompt: String) {
        if (_authUser.value == null) return
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _isAiSending.value) return

        val userMsg = AiChatMessage(isUser = true, text = trimmed)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiSending.value = true
        _aiState.value = AiTuningUiState.Loading

        viewModelScope.launch {
            val result = aiRepo.sendAssistantMessage(
                userMessage = trimmed,
                currentEqState = _eqState.value,
                bluetoothState = bluetoothMonitor.state.value,
                bluetoothSettings = _bluetoothSettings.value,
                presets = allPresets.value
            )

            result.fold(
                onSuccess = { action ->
                    val updatedEq = applyAiChatAction(action)
                    val (songHits, songErr) = sealManager.resolveForAiAction(action)
                    _isAiSending.value = false
                    val assistantMsg = AiChatMessage(
                        isUser = false,
                        text = action.assistantReply,
                        appliedBadges = action.appliedBadges,
                        previewBandsDb = if (action.applyEq || action.resetFlat || action.saveAsPreset) {
                            AudioMath.effectiveEqBands(updatedEq)
                        } else {
                            null
                        },
                        recommendation = action.recommendation,
                        downloadSuggestions = songHits,
                        downloadLookupError = songErr
                    )
                    _chatMessages.value = _chatMessages.value + assistantMsg
                    _aiState.value = action.recommendation?.let { AiTuningUiState.Success(it) }
                        ?: AiTuningUiState.Idle
                },
                onFailure = { err ->
                    _isAiSending.value = false
                    val errorMsg = err.message ?: "Verbindung zu Gemini fehlgeschlagen."
                    _chatMessages.value = _chatMessages.value + AiChatMessage(
                        isUser = false,
                        text = errorMsg,
                        isError = true
                    )
                    _aiState.value = AiTuningUiState.Error(errorMsg)
                }
            )
        }
    }

    private suspend fun applyAiChatAction(action: AiChatActionResult): EqState {
        var next = _eqState.value

        if (action.resetFlat) {
            next = next.copy(
                simpleLowDb = 0f,
                simpleMidDb = 0f,
                simpleHighDb = 0f,
                expertBandsDb = List(10) { 0f },
                preampDb = 0f,
                ultraBassEnabled = false,
                activePresetName = "FLAT // 0dB"
            )
        }

        if (action.applyEq) {
            val resolvedBands = if (action.eqMode == EqMode.SIMPLE) {
                AudioMath.simpleToExpertBands(action.simpleLowDb, action.simpleMidDb, action.simpleHighDb)
            } else {
                action.expertBandsDb
            }
            val (sLow, sMid, sHigh) = if (action.eqMode == EqMode.SIMPLE) {
                Triple(action.simpleLowDb, action.simpleMidDb, action.simpleHighDb)
            } else {
                AudioMath.expertToSimpleSummary(resolvedBands)
            }
            next = next.copy(
                masterEnabled = true,
                mode = action.eqMode,
                simpleLowDb = sLow,
                simpleMidDb = sMid,
                simpleHighDb = sHigh,
                expertBandsDb = resolvedBands,
                preampDb = action.preampDb,
                activePresetName = action.profileName
            )
        }

        if (action.applyUltraBass) {
            next = next.copy(
                masterEnabled = true,
                ultraBassEnabled = action.ultraBassEnabled,
                ultraBassLevel = action.ultraBassLevel.coerceIn(0f, 1f)
            )
        }

        if (action.applyCrossover) {
            val safeLowMid = action.crossoverLowMidHz.coerceIn(50f, 800f)
            val safeMidHigh = action.crossoverMidHighHz.coerceIn(maxOf(safeLowMid + 250f, 700f), 10000f)
            next = next.copy(
                masterEnabled = true,
                crossover = next.crossover.copy(
                    enabled = true,
                    lowEnabled = action.crossoverLowEnabled,
                    midEnabled = action.crossoverMidEnabled,
                    highEnabled = action.crossoverHighEnabled,
                    lowMidHz = safeLowMid,
                    midHighHz = safeMidHigh,
                    lowGainDb = action.crossoverLowGainDb.coerceIn(-12f, 12f),
                    midGainDb = action.crossoverMidGainDb.coerceIn(-12f, 12f),
                    highGainDb = action.crossoverHighGainDb.coerceIn(-12f, 12f),
                    slopeDbPerOct = action.crossoverSlopeDbPerOct
                )
            )
        }

        if (action.applyCompressor) {
            next = next.copy(
                masterEnabled = true,
                compressor = CompressorSettings(
                    enabled = action.compressorEnabled,
                    intensity = action.compressorIntensity,
                    preset = action.compressorPreset,
                    thresholdDb = action.compressorThresholdDb.coerceIn(-40f, 0f),
                    ratio = action.compressorRatio.coerceIn(1f, 20f),
                    attackMs = action.compressorAttackMs.coerceIn(1f, 100f),
                    releaseMs = action.compressorReleaseMs.coerceIn(20f, 500f),
                    makeupGainDb = action.compressorMakeupGainDb.coerceIn(0f, 18f)
                )
            )
        }

        if (action.applyBalance) {
            val snapped = if (kotlin.math.abs(action.balance) < 0.03f) 0f else action.balance.coerceIn(-1f, 1f)
            next = next.copy(
                masterEnabled = true,
                balance = snapped
            )
        }

        if (action.setMasterEnabled) {
            next = next.copy(masterEnabled = action.targetMasterEnabled)
        }

        val anyDspMutated = action.resetFlat || action.applyEq || action.applyUltraBass ||
            action.applyCrossover || action.applyCompressor || action.applyBalance || action.setMasterEnabled
        if (anyDspMutated) {
            commitState(next)
        }

        if (action.saveAsPreset) {
            val newPreset = AudioPreset(
                id = "ai_${UUID.randomUUID()}",
                name = action.profileName,
                tag = "GEMINI // AI",
                description = action.assistantReply.take(140),
                isCustom = true,
                isAiGenerated = true,
                mode = next.mode,
                simpleLowDb = next.simpleLowDb,
                simpleMidDb = next.simpleMidDb,
                simpleHighDb = next.simpleHighDb,
                expertBandsDb = next.expertBandsDb,
                preampDb = next.preampDb,
                crossover = next.crossover,
                compressor = next.compressor,
                balance = next.balance,
                ultraBassEnabled = next.ultraBassEnabled,
                ultraBassLevel = next.ultraBassLevel
            )
            val existing = prefsRepo.customPresetsFlow.first()
            prefsRepo.saveCustomPresets(listOf(newPreset) + existing)
            next = next.copy(activePresetName = action.profileName)
            commitState(next)
        }

        if (action.setGlobalAutoBypass) {
            setAutoBypassOnBluetooth(action.targetGlobalAutoBypass)
        }

        if (action.bindToBluetooth) {
            val btState = bluetoothMonitor.state.value
            val btSettings = _bluetoothSettings.value
            val query = action.bluetoothTargetDeviceName.trim()

            val resolvedDevice = when {
                query.equals("CURRENT", ignoreCase = true) || query.isBlank() -> {
                    btState.connectedDevice
                }
                else -> {
                    btState.bondedDevices.firstOrNull {
                        it.name.contains(query, ignoreCase = true)
                    } ?: btSettings.deviceRules.values.firstOrNull {
                        it.deviceName.contains(query, ignoreCase = true)
                    }?.let {
                        BluetoothDeviceRoute(key = it.deviceKey, name = it.deviceName)
                    } ?: BluetoothDeviceRoute(
                        key = BluetoothAudioMonitor.buildDeviceKey(null, query),
                        name = query
                    )
                }
            }

            if (resolvedDevice != null) {
                val rule = BluetoothDeviceRule(
                    deviceKey = resolvedDevice.key,
                    deviceName = resolvedDevice.name,
                    customEqEnabled = action.bluetoothCustomEqEnabled,
                    profile = next.toProfileSnapshot()
                )
                val updatedMap = _bluetoothSettings.value.deviceRules.toMutableMap().apply {
                    put(resolvedDevice.key, rule)
                }
                saveAndApplyBluetoothSettings(_bluetoothSettings.value.copy(deviceRules = updatedMap))
            }
        }

        return next
    }

    fun resetAiChat() {
        aiRepo.resetConversation()
        _chatMessages.value = emptyList()
        _aiState.value = AiTuningUiState.Idle
    }

    fun applyAiRecommendation(rec: AiSoundRecommendation) {
        applyPreset(rec.preset)
    }

    fun clearAiState() {
        _aiState.value = AiTuningUiState.Idle
    }

    private fun updateState(transform: EqState.() -> EqState) {
        commitState(_eqState.value.transform())
    }

    private fun commitState(next: EqState, updateNonBtBackup: Boolean = true) {
        userMutatedStateBeforeInit = true
        _eqState.value = next
        if (updateNonBtBackup && !bluetoothMonitor.state.value.isBluetoothConnected) {
            lastNonBtSnapshot = next.toProfileSnapshot()
            lastNonBtMasterEnabled = next.masterEnabled
        }
        SystemDspEngine.applyState(next)
        if (next.systemServiceEnabled) {
            startSystemServiceSafely(next)
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(250)
            prefsRepo.saveEqState(next)
        }
    }

    private fun startSystemServiceSafely(state: EqState) {
        try {
            SystemEqService.startOrUpdate(
                getApplication(),
                state.masterEnabled,
                state.activePresetName
            )
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothMonitor.stop()
        voiceController.release()
    }
}
