package com.example.model

enum class EqMode {
    SIMPLE,
    EXPERT
}

enum class VisualizerMode {
    SPECTRUM,
    WAVEFORM
}

enum class DspEngineType(val label: String) {
    DYNAMICS_PROCESSING("DYNAMICS PRO // S0"),
    LEGACY_EQUALIZER("LEGACY EQ // S0"),
    TEST_SESSION("SYNTH LAB // LOCAL"),
    STANDBY("STANDBY")
}

enum class CompressorPreset(val label: String) {
    SOFT("SANFT"),
    BALANCED("BALANCED"),
    PUNCH("PUNCH"),
    LIMITER("LIMITER")
}

data class CrossoverSettings(
    val enabled: Boolean = true,
    val lowEnabled: Boolean = true,
    val midEnabled: Boolean = true,
    val highEnabled: Boolean = true,
    val lowMidHz: Float = 220f,
    val midHighHz: Float = 2800f,
    val lowGainDb: Float = 0f,
    val midGainDb: Float = 0f,
    val highGainDb: Float = 0f,
    val lowSolo: Boolean = false,
    val midSolo: Boolean = false,
    val highSolo: Boolean = false,
    val lowMute: Boolean = false,
    val midMute: Boolean = false,
    val highMute: Boolean = false,
    val slopeDbPerOct: Int = 24
)

data class CompressorSettings(
    val enabled: Boolean = false,
    val intensity: Float = 0.45f,
    val preset: CompressorPreset = CompressorPreset.BALANCED,
    val thresholdDb: Float = -22f,
    val ratio: Float = 4.2f,
    val attackMs: Float = 8f,
    val releaseMs: Float = 110f,
    val kneeDb: Float = 4f,
    val makeupGainDb: Float = 3.0f
)

data class EqState(
    val masterEnabled: Boolean = true,
    val systemServiceEnabled: Boolean = true,
    val mode: EqMode = EqMode.SIMPLE,
    val simpleLowDb: Float = 2.0f,
    val simpleMidDb: Float = 0.0f,
    val simpleHighDb: Float = 1.5f,
    val expertBandsDb: List<Float> = listOf(2.0f, 1.8f, 1.2f, 0.4f, 0.0f, 0.0f, 0.5f, 1.0f, 1.4f, 1.5f),
    val preampDb: Float = 0f,
    val ultraBassEnabled: Boolean = false,
    val ultraBassLevel: Float = 0.55f,
    val crossover: CrossoverSettings = CrossoverSettings(),
    val compressor: CompressorSettings = CompressorSettings(),
    val balance: Float = 0f, // -1.0f (Full Left) .. 0f (Center) .. +1.0f (Full Right)
    val activePresetName: String = "DEFAULT // 01"
)

data class AudioPreset(
    val id: String,
    val name: String,
    val tag: String,
    val description: String,
    val isCustom: Boolean = false,
    val isAiGenerated: Boolean = false,
    val mode: EqMode,
    val simpleLowDb: Float,
    val simpleMidDb: Float,
    val simpleHighDb: Float,
    val expertBandsDb: List<Float>,
    val preampDb: Float,
    val crossover: CrossoverSettings,
    val compressor: CompressorSettings,
    val balance: Float,
    val ultraBassEnabled: Boolean = false,
    val ultraBassLevel: Float = 0.55f
)

data class EqProfileSnapshot(
    val presetName: String = "CUSTOM EQ",
    val mode: EqMode = EqMode.EXPERT,
    val simpleLowDb: Float = 0f,
    val simpleMidDb: Float = 0f,
    val simpleHighDb: Float = 0f,
    val expertBandsDb: List<Float> = List(10) { 0f },
    val preampDb: Float = 0f,
    val crossover: CrossoverSettings = CrossoverSettings(),
    val compressor: CompressorSettings = CompressorSettings(),
    val balance: Float = 0f,
    val ultraBassEnabled: Boolean = false,
    val ultraBassLevel: Float = 0.55f
)

data class BluetoothDeviceRoute(
    val key: String,
    val name: String,
    val address: String? = null,
    val isConnected: Boolean = false,
    val isBonded: Boolean = false
)

data class BluetoothDeviceRule(
    val deviceKey: String,
    val deviceName: String,
    val customEqEnabled: Boolean = false,
    val profile: EqProfileSnapshot = EqProfileSnapshot()
)

data class BluetoothAudioSettings(
    val autoBypassOnBluetooth: Boolean = true,
    val standardOutputRequiresRule: Boolean = false,
    val standardOutputRuleEnabled: Boolean = false,
    val standardOutputProfile: EqProfileSnapshot? = null,
    val deviceRules: Map<String, BluetoothDeviceRule> = emptyMap()
)

fun EqState.toProfileSnapshot(): EqProfileSnapshot = EqProfileSnapshot(
    presetName = activePresetName,
    mode = mode,
    simpleLowDb = simpleLowDb,
    simpleMidDb = simpleMidDb,
    simpleHighDb = simpleHighDb,
    expertBandsDb = expertBandsDb,
    preampDb = preampDb,
    crossover = crossover,
    compressor = compressor,
    balance = balance,
    ultraBassEnabled = ultraBassEnabled,
    ultraBassLevel = ultraBassLevel
)

fun AudioPreset.toProfileSnapshot(): EqProfileSnapshot = EqProfileSnapshot(
    presetName = name,
    mode = mode,
    simpleLowDb = simpleLowDb,
    simpleMidDb = simpleMidDb,
    simpleHighDb = simpleHighDb,
    expertBandsDb = expertBandsDb,
    preampDb = preampDb,
    crossover = crossover,
    compressor = compressor,
    balance = balance,
    ultraBassEnabled = ultraBassEnabled,
    ultraBassLevel = ultraBassLevel
)

fun EqState.withProfileSnapshot(snapshot: EqProfileSnapshot, masterEnabled: Boolean): EqState = copy(
    masterEnabled = masterEnabled,
    mode = snapshot.mode,
    simpleLowDb = snapshot.simpleLowDb,
    simpleMidDb = snapshot.simpleMidDb,
    simpleHighDb = snapshot.simpleHighDb,
    expertBandsDb = if (snapshot.expertBandsDb.size == 10) snapshot.expertBandsDb else expertBandsDb,
    preampDb = snapshot.preampDb,
    crossover = snapshot.crossover,
    compressor = snapshot.compressor,
    balance = snapshot.balance,
    ultraBassEnabled = snapshot.ultraBassEnabled,
    ultraBassLevel = snapshot.ultraBassLevel,
    activePresetName = snapshot.presetName
)

data class DspStatus(
    val engineType: DspEngineType = DspEngineType.STANDBY,
    val sessionId: Int = 0,
    val isDspAttached: Boolean = false,
    val isServiceActive: Boolean = false,
    val isTestAudioPlaying: Boolean = false,
    val isVisualizerRunning: Boolean = false,
    val estimatedGainReductionDb: Float = 0f,
    val ultraBassBoostDb: Float = 0f,
    val beatDetected: Boolean = false,
    val statusDetail: String = "Initialisiere Audio-Engine..."
)

data class VisualizerFrame(
    val bars: List<Float> = List(24) { 0.04f },
    val peaks: List<Float> = List(24) { 0.04f },
    val waveform: List<Float> = List(48) { 0f },
    val leftLevel: Float = 0f,
    val rightLevel: Float = 0f,
    val hasSignal: Boolean = false
)

val EQ_FREQUENCIES_HZ = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_FREQUENCY_LABELS = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
val EQ_BAND_CUTOFFS_HZ = listOf(44f, 88f, 177f, 355f, 710f, 1420f, 2840f, 5680f, 11360f, 20000f)
