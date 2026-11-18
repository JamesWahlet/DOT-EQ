package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.AudioMath
import com.example.model.AudioPreset
import com.example.model.BluetoothAudioSettings
import com.example.model.BluetoothDeviceRule
import com.example.model.CompressorPreset
import com.example.model.CompressorSettings
import com.example.model.CrossoverSettings
import com.example.model.EqMode
import com.example.model.EqProfileSnapshot
import com.example.model.EqState
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealDownloadStatus
import com.example.model.SealDownloadTask
import com.example.model.SealSaveLocation
import com.example.model.SealSettings
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.eqDataStore: DataStore<Preferences> by preferencesDataStore(name = "doteq_preferences")

class EqPreferencesRepository(private val context: Context) {

    private object Keys {
        val EQ_STATE_JSON = stringPreferencesKey("eq_state_json")
        val CUSTOM_PRESETS_JSON = stringPreferencesKey("custom_presets_json")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BLUETOOTH_SETTINGS_JSON = stringPreferencesKey("bluetooth_settings_json")
        val SEAL_SETTINGS_JSON = stringPreferencesKey("seal_settings_json")
        val SEAL_HISTORY_JSON = stringPreferencesKey("seal_history_json")
    }

    val eqStateFlow: Flow<EqState> = context.eqDataStore.data.map { prefs ->
        val raw = prefs[Keys.EQ_STATE_JSON]
        if (raw.isNullOrBlank()) EqState() else decodeEqState(raw)
    }

    val customPresetsFlow: Flow<List<AudioPreset>> = context.eqDataStore.data.map { prefs ->
        val raw = prefs[Keys.CUSTOM_PRESETS_JSON]
        if (raw.isNullOrBlank()) emptyList() else decodePresetList(raw)
    }

    val themeModeFlow: Flow<ThemeMode> = context.eqDataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else -> ThemeMode.LIGHT
        }
    }

    val bluetoothSettingsFlow: Flow<BluetoothAudioSettings> = context.eqDataStore.data.map { prefs ->
        val raw = prefs[Keys.BLUETOOTH_SETTINGS_JSON]
        if (raw.isNullOrBlank()) BluetoothAudioSettings() else decodeBluetoothSettings(raw)
    }

    val sealSettingsFlow: Flow<SealSettings> = context.eqDataStore.data.map { prefs ->
        val raw = prefs[Keys.SEAL_SETTINGS_JSON]
        if (raw.isNullOrBlank()) SealSettings() else decodeSealSettings(raw)
    }

    val sealHistoryFlow: Flow<List<SealDownloadTask>> = context.eqDataStore.data.map { prefs ->
        val raw = prefs[Keys.SEAL_HISTORY_JSON]
        if (raw.isNullOrBlank()) emptyList() else decodeSealHistory(raw)
    }

    suspend fun saveEqState(state: EqState) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.EQ_STATE_JSON] = encodeEqState(state)
        }
    }

    suspend fun saveCustomPresets(presets: List<AudioPreset>) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_PRESETS_JSON] = encodePresetList(presets)
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun saveBluetoothSettings(settings: BluetoothAudioSettings) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.BLUETOOTH_SETTINGS_JSON] = encodeBluetoothSettings(settings)
        }
    }

    suspend fun saveSealSettings(settings: SealSettings) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.SEAL_SETTINGS_JSON] = encodeSealSettings(settings)
        }
    }

    suspend fun saveSealHistory(tasks: List<SealDownloadTask>) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.SEAL_HISTORY_JSON] = encodeSealHistory(tasks.take(30))
        }
    }

    private fun encodeEqState(state: EqState): String {
        return JSONObject().apply {
            put("masterEnabled", state.masterEnabled)
            put("systemServiceEnabled", state.systemServiceEnabled)
            put("mode", state.mode.name)
            put("simpleLowDb", state.simpleLowDb.toDouble())
            put("simpleMidDb", state.simpleMidDb.toDouble())
            put("simpleHighDb", state.simpleHighDb.toDouble())
            put("expertBandsDb", JSONArray(state.expertBandsDb.map { it.toDouble() }))
            put("preampDb", state.preampDb.toDouble())
            put("ultraBassEnabled", state.ultraBassEnabled)
            put("ultraBassLevel", state.ultraBassLevel.toDouble())
            put("crossover", encodeCrossover(state.crossover))
            put("compressor", encodeCompressor(state.compressor))
            put("balance", state.balance.toDouble())
            put("activePresetName", state.activePresetName)
        }.toString()
    }

    private fun decodeEqState(raw: String): EqState {
        return try {
            val o = JSONObject(raw)
            val bandsJson = o.optJSONArray("expertBandsDb")
            val bands = if (bandsJson != null && bandsJson.length() == 10) {
                (0 until 10).map { bandsJson.getDouble(it).toFloat() }
            } else {
                EqState().expertBandsDb
            }
            EqState(
                masterEnabled = o.optBoolean("masterEnabled", true),
                systemServiceEnabled = o.optBoolean("systemServiceEnabled", true),
                mode = if (o.optString("mode") == EqMode.EXPERT.name) EqMode.EXPERT else EqMode.SIMPLE,
                simpleLowDb = o.optDouble("simpleLowDb", 2.0).toFloat(),
                simpleMidDb = o.optDouble("simpleMidDb", 0.0).toFloat(),
                simpleHighDb = o.optDouble("simpleHighDb", 1.5).toFloat(),
                expertBandsDb = bands,
                preampDb = o.optDouble("preampDb", 0.0).toFloat(),
                ultraBassEnabled = o.optBoolean("ultraBassEnabled", false),
                ultraBassLevel = o.optDouble("ultraBassLevel", 0.55).toFloat().coerceIn(0f, 1f),
                crossover = o.optJSONObject("crossover")?.let(::decodeCrossover) ?: CrossoverSettings(),
                compressor = o.optJSONObject("compressor")?.let(::decodeCompressor) ?: CompressorSettings(),
                balance = o.optDouble("balance", 0.0).toFloat(),
                activePresetName = o.optString("activePresetName", "DEFAULT // 01")
            )
        } catch (_: Exception) {
            EqState()
        }
    }

    private fun encodeCrossover(c: CrossoverSettings): JSONObject = JSONObject().apply {
        put("enabled", c.enabled)
        put("lowEnabled", c.lowEnabled)
        put("midEnabled", c.midEnabled)
        put("highEnabled", c.highEnabled)
        put("lowMidHz", c.lowMidHz.toDouble())
        put("midHighHz", c.midHighHz.toDouble())
        put("lowGainDb", c.lowGainDb.toDouble())
        put("midGainDb", c.midGainDb.toDouble())
        put("highGainDb", c.highGainDb.toDouble())
        put("lowSolo", c.lowSolo)
        put("midSolo", c.midSolo)
        put("highSolo", c.highSolo)
        put("lowMute", c.lowMute)
        put("midMute", c.midMute)
        put("highMute", c.highMute)
        put("slopeDbPerOct", c.slopeDbPerOct)
    }

    private fun decodeCrossover(o: JSONObject): CrossoverSettings = CrossoverSettings(
        enabled = o.optBoolean("enabled", true),
        lowEnabled = o.optBoolean("lowEnabled", true),
        midEnabled = o.optBoolean("midEnabled", true),
        highEnabled = o.optBoolean("highEnabled", true),
        lowMidHz = o.optDouble("lowMidHz", 220.0).toFloat(),
        midHighHz = o.optDouble("midHighHz", 2800.0).toFloat(),
        lowGainDb = o.optDouble("lowGainDb", 0.0).toFloat(),
        midGainDb = o.optDouble("midGainDb", 0.0).toFloat(),
        highGainDb = o.optDouble("highGainDb", 0.0).toFloat(),
        lowSolo = o.optBoolean("lowSolo", false),
        midSolo = o.optBoolean("midSolo", false),
        highSolo = o.optBoolean("highSolo", false),
        lowMute = o.optBoolean("lowMute", false),
        midMute = o.optBoolean("midMute", false),
        highMute = o.optBoolean("highMute", false),
        slopeDbPerOct = o.optInt("slopeDbPerOct", 24)
    )

    private fun encodeCompressor(c: CompressorSettings): JSONObject = JSONObject().apply {
        put("enabled", c.enabled)
        put("intensity", c.intensity.toDouble())
        put("preset", c.preset.name)
        put("thresholdDb", c.thresholdDb.toDouble())
        put("ratio", c.ratio.toDouble())
        put("attackMs", c.attackMs.toDouble())
        put("releaseMs", c.releaseMs.toDouble())
        put("kneeDb", c.kneeDb.toDouble())
        put("makeupGainDb", c.makeupGainDb.toDouble())
    }

    private fun decodeCompressor(o: JSONObject): CompressorSettings {
        val enabled = o.optBoolean("enabled", false)
        val presetName = o.optString("preset", CompressorPreset.BALANCED.name)
        val preset = CompressorPreset.entries.firstOrNull { it.name == presetName }
            ?: CompressorPreset.BALANCED
        val intensity = o.optDouble("intensity", 0.45).toFloat().coerceIn(0f, 1f)
        val base = AudioMath.compressorFromSimple(enabled, intensity, preset)
        return base.copy(
            thresholdDb = o.optDouble("thresholdDb", base.thresholdDb.toDouble()).toFloat(),
            ratio = o.optDouble("ratio", base.ratio.toDouble()).toFloat(),
            attackMs = o.optDouble("attackMs", base.attackMs.toDouble()).toFloat(),
            releaseMs = o.optDouble("releaseMs", base.releaseMs.toDouble()).toFloat(),
            kneeDb = o.optDouble("kneeDb", base.kneeDb.toDouble()).toFloat(),
            makeupGainDb = o.optDouble("makeupGainDb", base.makeupGainDb.toDouble()).toFloat()
        )
    }

    private fun encodePresetList(list: List<AudioPreset>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("tag", p.tag)
                    put("description", p.description)
                    put("isCustom", p.isCustom)
                    put("isAiGenerated", p.isAiGenerated)
                    put("mode", p.mode.name)
                    put("simpleLowDb", p.simpleLowDb.toDouble())
                    put("simpleMidDb", p.simpleMidDb.toDouble())
                    put("simpleHighDb", p.simpleHighDb.toDouble())
                    put("expertBandsDb", JSONArray(p.expertBandsDb.map { it.toDouble() }))
                    put("preampDb", p.preampDb.toDouble())
                    put("crossover", encodeCrossover(p.crossover))
                    put("compressor", encodeCompressor(p.compressor))
                    put("balance", p.balance.toDouble())
                    put("ultraBassEnabled", p.ultraBassEnabled)
                    put("ultraBassLevel", p.ultraBassLevel.toDouble())
                }
            )
        }
        return arr.toString()
    }

    private fun decodePresetList(raw: String): List<AudioPreset> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                val bandsJson = o.getJSONArray("expertBandsDb")
                val bands = (0 until 10).map { i -> bandsJson.optDouble(i, 0.0).toFloat() }
                AudioPreset(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    tag = o.optString("tag", "CUSTOM"),
                    description = o.optString("description", ""),
                    isCustom = o.optBoolean("isCustom", true),
                    isAiGenerated = o.optBoolean("isAiGenerated", false),
                    mode = if (o.optString("mode") == EqMode.EXPERT.name) EqMode.EXPERT else EqMode.SIMPLE,
                    simpleLowDb = o.optDouble("simpleLowDb", 0.0).toFloat(),
                    simpleMidDb = o.optDouble("simpleMidDb", 0.0).toFloat(),
                    simpleHighDb = o.optDouble("simpleHighDb", 0.0).toFloat(),
                    expertBandsDb = bands,
                    preampDb = o.optDouble("preampDb", 0.0).toFloat(),
                    crossover = o.optJSONObject("crossover")?.let(::decodeCrossover) ?: CrossoverSettings(),
                    compressor = o.optJSONObject("compressor")?.let(::decodeCompressor) ?: CompressorSettings(),
                    balance = o.optDouble("balance", 0.0).toFloat(),
                    ultraBassEnabled = o.optBoolean("ultraBassEnabled", false),
                    ultraBassLevel = o.optDouble("ultraBassLevel", 0.55).toFloat().coerceIn(0f, 1f)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeProfileSnapshot(p: EqProfileSnapshot): JSONObject = JSONObject().apply {
        put("presetName", p.presetName)
        put("mode", p.mode.name)
        put("simpleLowDb", p.simpleLowDb.toDouble())
        put("simpleMidDb", p.simpleMidDb.toDouble())
        put("simpleHighDb", p.simpleHighDb.toDouble())
        put("expertBandsDb", JSONArray(p.expertBandsDb.map { it.toDouble() }))
        put("preampDb", p.preampDb.toDouble())
        put("crossover", encodeCrossover(p.crossover))
        put("compressor", encodeCompressor(p.compressor))
        put("balance", p.balance.toDouble())
        put("ultraBassEnabled", p.ultraBassEnabled)
        put("ultraBassLevel", p.ultraBassLevel.toDouble())
    }

    private fun decodeProfileSnapshot(o: JSONObject): EqProfileSnapshot {
        val bandsJson = o.optJSONArray("expertBandsDb")
        val bands = if (bandsJson != null && bandsJson.length() == 10) {
            (0 until 10).map { bandsJson.optDouble(it, 0.0).toFloat() }
        } else {
            List(10) { 0f }
        }
        return EqProfileSnapshot(
            presetName = o.optString("presetName", "CUSTOM EQ"),
            mode = if (o.optString("mode") == EqMode.SIMPLE.name) EqMode.SIMPLE else EqMode.EXPERT,
            simpleLowDb = o.optDouble("simpleLowDb", 0.0).toFloat(),
            simpleMidDb = o.optDouble("simpleMidDb", 0.0).toFloat(),
            simpleHighDb = o.optDouble("simpleHighDb", 0.0).toFloat(),
            expertBandsDb = bands,
            preampDb = o.optDouble("preampDb", 0.0).toFloat(),
            crossover = o.optJSONObject("crossover")?.let(::decodeCrossover) ?: CrossoverSettings(),
            compressor = o.optJSONObject("compressor")?.let(::decodeCompressor) ?: CompressorSettings(),
            balance = o.optDouble("balance", 0.0).toFloat(),
            ultraBassEnabled = o.optBoolean("ultraBassEnabled", false),
            ultraBassLevel = o.optDouble("ultraBassLevel", 0.55).toFloat().coerceIn(0f, 1f)
        )
    }

    private fun encodeBluetoothSettings(settings: BluetoothAudioSettings): String {
        return JSONObject().apply {
            put("autoBypassOnBluetooth", settings.autoBypassOnBluetooth)
            put("standardOutputRequiresRule", settings.standardOutputRequiresRule)
            put("standardOutputRuleEnabled", settings.standardOutputRuleEnabled)
            if (settings.standardOutputProfile != null) {
                put("standardOutputProfile", encodeProfileSnapshot(settings.standardOutputProfile))
            }
            val rulesArr = JSONArray()
            settings.deviceRules.values.forEach { rule ->
                rulesArr.put(
                    JSONObject().apply {
                        put("deviceKey", rule.deviceKey)
                        put("deviceName", rule.deviceName)
                        put("customEqEnabled", rule.customEqEnabled)
                        put("profile", encodeProfileSnapshot(rule.profile))
                    }
                )
            }
            put("deviceRules", rulesArr)
        }.toString()
    }

    private fun decodeBluetoothSettings(raw: String): BluetoothAudioSettings {
        return try {
            val o = JSONObject(raw)
            val rulesMap = linkedMapOf<String, BluetoothDeviceRule>()
            val rulesArr = o.optJSONArray("deviceRules")
            if (rulesArr != null) {
                for (i in 0 until rulesArr.length()) {
                    val r = rulesArr.optJSONObject(i) ?: continue
                    val key = r.optString("deviceKey").takeIf { it.isNotBlank() } ?: continue
                    val rule = BluetoothDeviceRule(
                        deviceKey = key,
                        deviceName = r.optString("deviceName", key),
                        customEqEnabled = r.optBoolean("customEqEnabled", false),
                        profile = r.optJSONObject("profile")?.let(::decodeProfileSnapshot) ?: EqProfileSnapshot()
                    )
                    rulesMap[key] = rule
                }
            }
            BluetoothAudioSettings(
                autoBypassOnBluetooth = o.optBoolean("autoBypassOnBluetooth", true),
                standardOutputRequiresRule = o.optBoolean("standardOutputRequiresRule", false),
                standardOutputRuleEnabled = o.optBoolean("standardOutputRuleEnabled", false),
                standardOutputProfile = o.optJSONObject("standardOutputProfile")?.let(::decodeProfileSnapshot),
                deviceRules = rulesMap
            )
        } catch (_: Exception) {
            BluetoothAudioSettings()
        }
    }

    private fun encodeSealSettings(s: SealSettings): String {
        return JSONObject().apply {
            put("quality", s.quality.name)
            put("saveLocation", s.saveLocation.name)
            if (!s.customFolderUri.isNullOrBlank()) put("customFolderUri", s.customFolderUri)
            if (s.customFolderLabel.isNotBlank()) put("customFolderLabel", s.customFolderLabel)
            put("format", s.format.name)
            put("autoConvertAudioOnly", s.autoConvertAudioOnly)
            put("filenameTemplate", s.filenameTemplate)
        }.toString()
    }

    private fun decodeSealSettings(raw: String): SealSettings {
        return try {
            val o = JSONObject(raw)
            val parsedQuality = SealAudioQuality.entries.firstOrNull {
                it.name == o.optString("quality")
            } ?: when (o.optString("bitrate")) {
                "LITE_128" -> SealAudioQuality.LOW
                "STD_192", "HIGH_256" -> SealAudioQuality.MEDIUM
                "MAX_320" -> SealAudioQuality.LOSSLESS
                else -> SealAudioQuality.AUTO
            }
            val customUri = o.optString("customFolderUri").takeIf { it.isNotBlank() }
            val customLabel = o.optString("customFolderLabel", "").trim()
            val parsedLoc = SealSaveLocation.entries.firstOrNull { it.name == o.optString("saveLocation") }
                ?: SealSaveLocation.MUSIC
            val resolvedLoc = if (parsedLoc == SealSaveLocation.CUSTOM && customUri == null) {
                SealSaveLocation.MUSIC
            } else {
                parsedLoc
            }
            SealSettings(
                quality = parsedQuality,
                saveLocation = resolvedLoc,
                customFolderUri = customUri,
                customFolderLabel = customLabel,
                format = SealAudioFormat.entries.firstOrNull { it.name == o.optString("format") }
                    ?: SealAudioFormat.M4A,
                autoConvertAudioOnly = o.optBoolean("autoConvertAudioOnly", true),
                filenameTemplate = o.optString("filenameTemplate", "%(artist)s - %(title)s")
                    .ifBlank { "%(artist)s - %(title)s" }
            )
        } catch (_: Exception) {
            SealSettings()
        }
    }

    private fun encodeSealHistory(list: List<SealDownloadTask>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("sourceUrl", t.sourceUrl)
                    put("resolvedMediaUrl", t.resolvedMediaUrl)
                    put("fileName", t.fileName)
                    put("formatLabel", t.formatLabel)
                    put("progress", t.progress.toDouble())
                    put("bytesDownloaded", t.bytesDownloaded)
                    put("totalBytes", t.totalBytes)
                    put("status", t.status.name)
                    if (t.savedUriString != null) put("savedUriString", t.savedUriString)
                    if (t.errorMessage != null) put("errorMessage", t.errorMessage)
                    put("timestampMs", t.timestampMs)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeSealHistory(raw: String): List<SealDownloadTask> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val rawStatus = SealDownloadStatus.entries.firstOrNull { it.name == o.optString("status") }
                    ?: SealDownloadStatus.COMPLETED
                val restoredStatus = if (rawStatus == SealDownloadStatus.DOWNLOADING || rawStatus == SealDownloadStatus.QUEUED) {
                    SealDownloadStatus.CANCELLED
                } else {
                    rawStatus
                }
                SealDownloadTask(
                    id = o.optString("id"),
                    title = o.optString("title", "Audio"),
                    artist = o.optString("artist", ""),
                    sourceUrl = o.optString("sourceUrl", ""),
                    resolvedMediaUrl = o.optString("resolvedMediaUrl", ""),
                    fileName = o.optString("fileName", "audio.m4a"),
                    formatLabel = o.optString("formatLabel", "M4A"),
                    progress = o.optDouble("progress", 1.0).toFloat().coerceIn(0f, 1f),
                    bytesDownloaded = o.optLong("bytesDownloaded", 0L),
                    totalBytes = o.optLong("totalBytes", -1L),
                    status = restoredStatus,
                    savedUriString = o.optString("savedUriString").takeIf { it.isNotBlank() },
                    errorMessage = o.optString("errorMessage").takeIf { it.isNotBlank() },
                    timestampMs = o.optLong("timestampMs", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        val BUILT_IN_PRESETS: List<AudioPreset> = listOf(
            AudioPreset(
                id = "flat_ref",
                name = "FLAT // REF",
                tag = "STUDIO",
                description = "Lineare Studio-Referenz ohne Verfärbung.",
                mode = EqMode.EXPERT,
                simpleLowDb = 0f,
                simpleMidDb = 0f,
                simpleHighDb = 0f,
                expertBandsDb = List(10) { 0f },
                preampDb = 0f,
                crossover = CrossoverSettings(),
                compressor = CompressorSettings(enabled = false),
                balance = 0f,
                ultraBassEnabled = false
            ),
            AudioPreset(
                id = "dot_punch",
                name = "DOT PUNCH",
                tag = "SIGNATURE",
                description = "Knackiger Sub-Bass, präsente Mitten & seidige Höhen.",
                mode = EqMode.SIMPLE,
                simpleLowDb = 4.2f,
                simpleMidDb = 0.5f,
                simpleHighDb = 3.0f,
                expertBandsDb = listOf(4.5f, 3.8f, 2.5f, 0.5f, 0.0f, 0.5f, 1.4f, 2.2f, 2.8f, 3.2f),
                preampDb = -1.5f,
                crossover = CrossoverSettings(lowMidHz = 180f, midHighHz = 3200f, lowGainDb = 1.5f, highGainDb = 1.0f),
                compressor = AudioMath.compressorFromSimple(enabled = true, intensity = 0.45f, preset = CompressorPreset.PUNCH),
                balance = 0f,
                ultraBassEnabled = true,
                ultraBassLevel = 0.55f
            ),
            AudioPreset(
                id = "vocal_clarity",
                name = "VOCAL LAB",
                tag = "VOICE",
                description = "Fokussierte Stimmverständlichkeit für Podcasts & Vocals.",
                mode = EqMode.EXPERT,
                simpleLowDb = -2.5f,
                simpleMidDb = 4.0f,
                simpleHighDb = 2.0f,
                expertBandsDb = listOf(-4.0f, -3.0f, -1.5f, 0.5f, 2.5f, 4.0f, 3.8f, 2.5f, 1.0f, -0.5f),
                preampDb = 0f,
                crossover = CrossoverSettings(lowMidHz = 240f, midHighHz = 3800f, lowGainDb = -1.5f, midGainDb = 2.0f),
                compressor = AudioMath.compressorFromSimple(enabled = true, intensity = 0.5f, preset = CompressorPreset.BALANCED),
                balance = 0f
            ),
            AudioPreset(
                id = "sub_club",
                name = "SUB CHAMBER",
                tag = "ELECTRONIC",
                description = "Tiefer Club-Sub mit straffem Kompressor-Punch.",
                mode = EqMode.EXPERT,
                simpleLowDb = 6.5f,
                simpleMidDb = -1.0f,
                simpleHighDb = 2.5f,
                expertBandsDb = listOf(7.0f, 6.2f, 4.0f, 0.5f, -1.5f, -1.0f, 0.5f, 2.0f, 3.2f, 2.8f),
                preampDb = -3.0f,
                crossover = CrossoverSettings(lowMidHz = 140f, midHighHz = 2600f, lowGainDb = 2.5f, midGainDb = -0.5f, highGainDb = 1.0f),
                compressor = AudioMath.compressorFromSimple(enabled = true, intensity = 0.65f, preset = CompressorPreset.PUNCH),
                balance = 0f,
                ultraBassEnabled = true,
                ultraBassLevel = 0.8f
            ),
            AudioPreset(
                id = "late_night",
                name = "NIGHT WARM",
                tag = "COMFORT",
                description = "Sanfte Höhen und voller Tiefgang für leises Hören.",
                mode = EqMode.SIMPLE,
                simpleLowDb = 3.0f,
                simpleMidDb = -0.5f,
                simpleHighDb = -2.5f,
                expertBandsDb = listOf(3.2f, 2.8f, 1.8f, 0.5f, -0.5f, -0.8f, -1.2f, -2.0f, -2.8f, -3.5f),
                preampDb = 0f,
                crossover = CrossoverSettings(lowMidHz = 220f, midHighHz = 2400f, lowGainDb = 1.5f, highGainDb = -2.0f),
                compressor = AudioMath.compressorFromSimple(enabled = true, intensity = 0.55f, preset = CompressorPreset.SOFT),
                balance = 0f
            )
        )
    }
}
