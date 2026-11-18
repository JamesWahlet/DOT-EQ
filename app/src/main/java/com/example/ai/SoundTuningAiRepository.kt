package com.example.ai

import android.util.Log
import com.example.audio.AudioMath
import com.example.audio.BluetoothAudioState
import com.example.model.AudioPreset
import com.example.model.BluetoothAudioSettings
import com.example.model.CompressorPreset
import com.example.model.CompressorSettings
import com.example.model.EqMode
import com.example.model.EqState
import com.example.model.SealTrackSuggestion
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "SoundTuningAiRepo"
const val DOTEQ_GEMINI_MODEL_ID = "gemini-3.8-flash"

data class AiSoundRecommendation(
    val preset: AudioPreset,
    val acousticNotes: String
)

data class AiChatActionResult(
    val assistantReply: String,
    val appliedBadges: List<String>,
    val recommendation: AiSoundRecommendation?,
    val applyEq: Boolean,
    val applyUltraBass: Boolean,
    val ultraBassEnabled: Boolean,
    val ultraBassLevel: Float,
    val applyCrossover: Boolean,
    val crossoverLowEnabled: Boolean,
    val crossoverMidEnabled: Boolean,
    val crossoverHighEnabled: Boolean,
    val applyCompressor: Boolean,
    val applyBalance: Boolean,
    val setMasterEnabled: Boolean,
    val targetMasterEnabled: Boolean,
    val resetFlat: Boolean,
    val saveAsPreset: Boolean,
    val bindToBluetooth: Boolean,
    val bluetoothTargetDeviceName: String,
    val bluetoothCustomEqEnabled: Boolean,
    val setGlobalAutoBypass: Boolean,
    val targetGlobalAutoBypass: Boolean,
    val eqMode: EqMode,
    val simpleLowDb: Float,
    val simpleMidDb: Float,
    val simpleHighDb: Float,
    val expertBandsDb: List<Float>,
    val preampDb: Float,
    val crossoverLowMidHz: Float,
    val crossoverMidHighHz: Float,
    val crossoverLowGainDb: Float,
    val crossoverMidGainDb: Float,
    val crossoverHighGainDb: Float,
    val crossoverSlopeDbPerOct: Int,
    val compressorEnabled: Boolean,
    val compressorIntensity: Float,
    val compressorPreset: CompressorPreset,
    val compressorThresholdDb: Float,
    val compressorRatio: Float,
    val compressorAttackMs: Float,
    val compressorReleaseMs: Float,
    val compressorMakeupGainDb: Float,
    val balance: Float,
    val profileName: String,
    val requestSongDownload: Boolean = false,
    val songSearchQuery: String = "",
    val songUrl: String = "",
    val preferredDownloadFormat: String = ""
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val appliedBadges: List<String> = emptyList(),
    val previewBandsDb: List<Float>? = null,
    val recommendation: AiSoundRecommendation? = null,
    val downloadSuggestions: List<SealTrackSuggestion> = emptyList(),
    val downloadLookupError: String? = null,
    val isError: Boolean = false
)

class SoundTuningAiRepository {

    private val assistantActionSchema = Schema.obj(
        properties = mapOf(
            "assistantReply" to Schema.string(
                description = "Concise reply in the user's language (German if user writes German) confirming exact adjustments."
            ),
            "profileName" to Schema.string(
                description = "Short industrial preset name (max 18 chars, uppercase), e.g. 'BASS CLUB', 'EAR2 LIVE'."
            ),
            "applyEq" to Schema.boolean(
                description = "True if EQ sliders/bands/preamp should be updated now."
            ),
            "eqMode" to Schema.enumeration(
                values = listOf("SIMPLE", "EXPERT"),
                description = "Target EQ mode: SIMPLE or EXPERT."
            ),
            "simpleLowDb" to Schema.double(description = "Simple low gain (-12.0 to 12.0 dB)."),
            "simpleMidDb" to Schema.double(description = "Simple mid gain (-12.0 to 12.0 dB)."),
            "simpleHighDb" to Schema.double(description = "Simple high gain (-12.0 to 12.0 dB)."),
            "expertBandsDb" to Schema.array(
                items = Schema.double(),
                description = "10 EQ gains in dB (-12.0 to 12.0) for 31..16k Hz."
            ),
            "preampDb" to Schema.double(description = "Preamp (-8.0 to 6.0 dB)."),
            "applyUltraBass" to Schema.boolean(
                description = "True if intelligent Beat-Detecting Ultra Bass should be toggled or adjusted."
            ),
            "ultraBassEnabled" to Schema.boolean(description = "Enable/disable intelligent Ultra Bass."),
            "ultraBassLevel" to Schema.double(description = "Ultra Bass intensity (0.0 to 1.0)."),
            "applyCrossover" to Schema.boolean(
                description = "True if Crossover frequencies, active bands, or trims should be updated."
            ),
            "crossoverLowEnabled" to Schema.boolean(description = "Enable LOW frequency band."),
            "crossoverMidEnabled" to Schema.boolean(description = "Enable MID frequency band."),
            "crossoverHighEnabled" to Schema.boolean(description = "Enable HIGH frequency band."),
            "crossoverLowMidHz" to Schema.double(description = "Low-Mid crossover Hz (60 to 600)."),
            "crossoverMidHighHz" to Schema.double(description = "Mid-High crossover Hz (1000 to 8000)."),
            "crossoverLowGainDb" to Schema.double(description = "Crossover low gain (-10.0 to 10.0 dB)."),
            "crossoverMidGainDb" to Schema.double(description = "Crossover mid gain (-10.0 to 10.0 dB)."),
            "crossoverHighGainDb" to Schema.double(description = "Crossover high gain (-10.0 to 10.0 dB)."),
            "crossoverSlopeDbPerOct" to Schema.integer(description = "Slope: 12 or 24."),
            "applyCompressor" to Schema.boolean(
                description = "True if Compressor should be updated."
            ),
            "compressorEnabled" to Schema.boolean(description = "Enable/disable compressor."),
            "compressorIntensity" to Schema.double(description = "Compressor strength (0.0 to 1.0)."),
            "compressorPreset" to Schema.enumeration(
                values = listOf("SOFT", "BALANCED", "PUNCH", "LIMITER"),
                description = "Compressor style preset."
            ),
            "compressorThresholdDb" to Schema.double(description = "Optional threshold override (-36.0 to -4.0)."),
            "compressorRatio" to Schema.double(description = "Optional ratio override (1.5 to 16.0)."),
            "compressorAttackMs" to Schema.double(description = "Optional attack ms (2.0 to 60.0)."),
            "compressorReleaseMs" to Schema.double(description = "Optional release ms (40.0 to 300.0)."),
            "compressorMakeupGainDb" to Schema.double(description = "Optional makeup dB (0.0 to 10.0)."),
            "applyBalance" to Schema.boolean(description = "True if L/R stereo balance should change."),
            "balance" to Schema.double(description = "Stereo balance -1.0 (left) .. 0.0 (center) .. +1.0 (right)."),
            "setMasterEnabled" to Schema.boolean(description = "True if master DSP power ON/OFF should toggle."),
            "targetMasterEnabled" to Schema.boolean(description = "Target master power."),
            "resetFlat" to Schema.boolean(description = "True ONLY when user asks to reset EQ flat."),
            "saveAsPreset" to Schema.boolean(description = "True when user asks to save a preset."),
            "bindToBluetooth" to Schema.boolean(description = "True to bind EQ/preset to a Bluetooth device."),
            "bluetoothTargetDeviceName" to Schema.string(description = "Bluetooth device name or 'CURRENT'."),
            "bluetoothCustomEqEnabled" to Schema.boolean(description = "True to activate custom EQ on Bluetooth device."),
            "setGlobalAutoBypass" to Schema.boolean(description = "True to change global BT auto-bypass."),
            "targetGlobalAutoBypass" to Schema.boolean(description = "Target global BT auto-bypass."),
            "requestSongDownload" to Schema.boolean(description = "True when user wants to search, copy link for, or download a song/track via Link Downloader."),
            "songSearchQuery" to Schema.string(description = "Clean 'Artist - Title' or song search keywords when requestSongDownload=true."),
            "songUrl" to Schema.string(description = "Direct URL if user pasted a link to download, else empty string."),
            "preferredDownloadFormat" to Schema.string(description = "Optional target format: M4A, MP3, FLAC, OGG, or empty.")
        )
    )

    private val chatModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = assistantActionSchema
            temperature = 0.35f
        }
        Firebase.ai.generativeModel(
            modelName = DOTEQ_GEMINI_MODEL_ID,
            generationConfig = config,
            systemInstruction = content {
                text(
                    "You are DotEQ's built-in audio DSP & Link Downloader assistant powered by Google Gemini. " +
                        "Every turn includes [LIVE_DSP_CONTEXT] with current EQ, Ultra Bass (smart bass/beat boost), Crossover (including active low/mid/high bands), Compressor, L/R Balance, Presets, and Bluetooth devices.\n\n" +
                        "RULES:\n" +
                        "1. Act immediately on requests: 'mehr Bass' -> boost low EQ and optionally enable/raise Ultra Bass; 'Ultra Bass an' -> applyUltraBass=true, ultraBassEnabled=true.\n" +
                        "2. 'Crossover' requests -> set applyCrossover=true, adjust crossoverLowMidHz/crossoverMidHighHz and crossoverLowEnabled/MidEnabled/HighEnabled (e.g. 'nur Bass' -> lowEnabled=true, midEnabled=false, highEnabled=false).\n" +
                        "3. 'Balance rechts/links/mitte' -> set applyBalance=true and balance (-1.0..+1.0).\n" +
                        "4. 'Kompressor' -> set applyCompressor=true with compressorEnabled, compressorIntensity (0..1), and compressorPreset (SOFT/BALANCED/PUNCH/LIMITER).\n" +
                        "5. 'Preset erstellen/speichern' -> saveAsPreset=true.\n" +
                        "6. Bluetooth device requests -> bindToBluetooth=true with bluetoothTargetDeviceName (matching device or 'CURRENT') and bluetoothCustomEqEnabled.\n" +
                        "7. Song / music download requests (e.g. 'Lade <Song> herunter', 'Song suchen', 'Link für <Song>'): set requestSongDownload=true, songSearchQuery='<Artist Title>', songUrl if URL provided, and preferredDownloadFormat if requested (MP3/M4A/FLAC). Unless EQ changes were also asked, set applyEq=false.\n" +
                        "8. Reply concisely in the user's language in assistantReply."
                )
            }
        )
    }

    private var activeChat: Chat? = null

    fun resetConversation() {
        activeChat = null
    }

    private fun getOrCreateChat(): Chat {
        return activeChat ?: chatModel.startChat().also { activeChat = it }
    }

    suspend fun sendAssistantMessage(
        userMessage: String,
        currentEqState: EqState,
        bluetoothState: BluetoothAudioState,
        bluetoothSettings: BluetoothAudioSettings,
        presets: List<AudioPreset>
    ): Result<AiChatActionResult> {
        return try {
            val chat = getOrCreateChat()
            val contextualPrompt = buildContextualPrompt(
                userMessage = userMessage,
                state = currentEqState,
                btState = bluetoothState,
                btSettings = bluetoothSettings,
                presets = presets
            )

            val response = withTimeout(60_000L) {
                chat.sendMessage(contextualPrompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Keine Antwort erhalten.")
            val parsed = parseAssistantActionJson(jsonText, currentEqState)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini chat request timed out", e)
            Result.failure(Exception("Zeitüberschreitung nach 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini chat failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gemini-Fehler."))
        }
    }

    suspend fun sendAssistantAudioMessage(
        wavBytes: ByteArray,
        currentEqState: EqState,
        bluetoothState: BluetoothAudioState,
        bluetoothSettings: BluetoothAudioSettings,
        presets: List<AudioPreset>
    ): Result<AiChatActionResult> {
        return try {
            val chat = getOrCreateChat()
            val contextualPrompt = buildContextualPrompt(
                userMessage = "[VOICE_AUDIO] Höre die Sprachnachricht an, fasse den Wunsch in assistantReply kurz auf Deutsch zusammen und wende alle passenden DSP-Einstellungen direkt an.",
                state = currentEqState,
                btState = bluetoothState,
                btSettings = bluetoothSettings,
                presets = presets
            )

            val response = withTimeout(60_000L) {
                chat.sendMessage(
                    content {
                        inlineData(wavBytes, "audio/wav")
                        text(contextualPrompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Keine Antwort erhalten.")
            val parsed = parseAssistantActionJson(jsonText, currentEqState)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini voice request timed out", e)
            Result.failure(Exception("Zeitüberschreitung nach 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini voice failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Sprachbefehl fehlgeschlagen."))
        }
    }

    private fun buildContextualPrompt(
        userMessage: String,
        state: EqState,
        btState: BluetoothAudioState,
        btSettings: BluetoothAudioSettings,
        presets: List<AudioPreset>
    ): String {
        val effective10 = AudioMath.effectiveEqBands(state).joinToString(",") { "%.1f".format(it) }
        val connectedBt = btState.connectedDevice?.name ?: "None"
        val knownBt = btState.bondedDevices.joinToString(", ") { dev ->
            val rule = btSettings.deviceRules[dev.key]
            val ruleDesc = if (rule?.customEqEnabled == true) "Custom(${rule.profile.presetName})" else "Bypass"
            "${dev.name}[$ruleDesc]"
        }.ifBlank { "None" }
        val presetNames = presets.take(8).joinToString(", ") { it.name }

        return """
            [LIVE_DSP_CONTEXT]
            MasterDSP=${if (state.masterEnabled) "ON" else "BYPASS"}
            ActivePreset=${state.activePresetName}
            Mode=${state.mode.name}
            SimpleEQ=(Low=${"%.1f".format(state.simpleLowDb)}, Mid=${"%.1f".format(state.simpleMidDb)}, High=${"%.1f".format(state.simpleHighDb)})
            Expert10Bands=[$effective10]
            Preamp=${"%.1f".format(state.preampDb)}dB
            UltraBass=(Enabled=${state.ultraBassEnabled}, Level=${"%.2f".format(state.ultraBassLevel)})
            Crossover=(Enabled=${state.crossover.enabled}, Bands=[L=${state.crossover.lowEnabled},M=${state.crossover.midEnabled},H=${state.crossover.highEnabled}], LowMid=${state.crossover.lowMidHz.toInt()}Hz, MidHigh=${state.crossover.midHighHz.toInt()}Hz, Trims=[${"%.1f".format(state.crossover.lowGainDb)},${"%.1f".format(state.crossover.midGainDb)},${"%.1f".format(state.crossover.highGainDb)}])
            Compressor=(Enabled=${state.compressor.enabled}, Preset=${state.compressor.preset.name}, Intensity=${"%.2f".format(state.compressor.intensity)})
            Balance=${"%.2f".format(state.balance)}
            BluetoothConnected=$connectedBt
            BluetoothKnown=$knownBt
            BluetoothAutoBypass=${btSettings.autoBypassOnBluetooth}
            Presets=[$presetNames]
            [/LIVE_DSP_CONTEXT]

            User message: $userMessage
        """.trimIndent()
    }

    internal fun parseAssistantActionJson(
        jsonText: String,
        fallbackState: EqState
    ): AiChatActionResult {
        val json = JSONObject(jsonText)
        val reply = json.optString("assistantReply").trim().ifBlank {
            "Angepasst."
        }
        val profileName = json.optString("profileName").trim().uppercase().take(18).ifBlank { "GEMINI // EQ" }
        val eqMode = if (json.optString("eqMode") == EqMode.SIMPLE.name) EqMode.SIMPLE else EqMode.EXPERT

        val applyEq = json.optBoolean("applyEq", false)
        val applyUltraBass = json.optBoolean("applyUltraBass", false)
        val ultraBassEnabled = json.optBoolean("ultraBassEnabled", fallbackState.ultraBassEnabled)
        val ultraBassLevel = json.optDouble("ultraBassLevel", fallbackState.ultraBassLevel.toDouble())
            .toFloat().coerceIn(0f, 1f)

        val applyCrossover = json.optBoolean("applyCrossover", false)
        val xLowEnabled = json.optBoolean("crossoverLowEnabled", fallbackState.crossover.lowEnabled)
        val xMidEnabled = json.optBoolean("crossoverMidEnabled", fallbackState.crossover.midEnabled)
        val xHighEnabled = json.optBoolean("crossoverHighEnabled", fallbackState.crossover.highEnabled)

        val applyCompressor = json.optBoolean("applyCompressor", false)
        val applyBalance = json.optBoolean("applyBalance", false)
        val setMasterEnabled = json.optBoolean("setMasterEnabled", false)
        val targetMasterEnabled = json.optBoolean("targetMasterEnabled", true)
        val resetFlat = json.optBoolean("resetFlat", false)
        val saveAsPreset = json.optBoolean("saveAsPreset", false)
        val bindToBluetooth = json.optBoolean("bindToBluetooth", false)
        val btTargetName = json.optString("bluetoothTargetDeviceName", "").trim()
        val btCustomEqEnabled = json.optBoolean("bluetoothCustomEqEnabled", true)
        val setGlobalAutoBypass = json.optBoolean("setGlobalAutoBypass", false)
        val targetGlobalAutoBypass = json.optBoolean("targetGlobalAutoBypass", true)
        val requestSongDownload = json.optBoolean("requestSongDownload", false)
        val songSearchQuery = json.optString("songSearchQuery", "").trim()
        val songUrl = json.optString("songUrl", "").trim()
        val preferredDownloadFormat = json.optString("preferredDownloadFormat", "").trim().uppercase()

        val bandsArray = json.optJSONArray("expertBandsDb")
        val parsedBands = if (bandsArray != null && bandsArray.length() >= 10) {
            (0 until 10).map { bandsArray.optDouble(it, 0.0).toFloat().coerceIn(-12f, 12f) }
        } else {
            AudioMath.effectiveEqBands(fallbackState)
        }

        val simpleLow = json.optDouble("simpleLowDb", fallbackState.simpleLowDb.toDouble()).toFloat().coerceIn(-12f, 12f)
        val simpleMid = json.optDouble("simpleMidDb", fallbackState.simpleMidDb.toDouble()).toFloat().coerceIn(-12f, 12f)
        val simpleHigh = json.optDouble("simpleHighDb", fallbackState.simpleHighDb.toDouble()).toFloat().coerceIn(-12f, 12f)
        val preampDb = json.optDouble("preampDb", fallbackState.preampDb.toDouble()).toFloat().coerceIn(-8f, 6f)

        val lowMidHz = json.optDouble("crossoverLowMidHz", fallbackState.crossover.lowMidHz.toDouble())
            .toFloat().coerceIn(60f, 600f)
        val midHighHz = json.optDouble("crossoverMidHighHz", fallbackState.crossover.midHighHz.toDouble())
            .toFloat().coerceIn(maxOf(lowMidHz + 250f, 1000f), 8000f)
        val xLowGain = json.optDouble("crossoverLowGainDb", fallbackState.crossover.lowGainDb.toDouble())
            .toFloat().coerceIn(-10f, 10f)
        val xMidGain = json.optDouble("crossoverMidGainDb", fallbackState.crossover.midGainDb.toDouble())
            .toFloat().coerceIn(-10f, 10f)
        val xHighGain = json.optDouble("crossoverHighGainDb", fallbackState.crossover.highGainDb.toDouble())
            .toFloat().coerceIn(-10f, 10f)
        val xSlope = if (json.optInt("crossoverSlopeDbPerOct", fallbackState.crossover.slopeDbPerOct) <= 12) 12 else 24

        val compEnabled = json.optBoolean("compressorEnabled", fallbackState.compressor.enabled)
        val compPresetName = json.optString("compressorPreset", fallbackState.compressor.preset.name)
        val compPreset = CompressorPreset.entries.firstOrNull { it.name == compPresetName }
            ?: fallbackState.compressor.preset
        val compIntensity = json.optDouble("compressorIntensity", fallbackState.compressor.intensity.toDouble())
            .toFloat().coerceIn(0f, 1f)
        val derivedComp = AudioMath.compressorFromSimple(compEnabled, compIntensity, compPreset)

        val compThresh = json.optDouble("compressorThresholdDb", derivedComp.thresholdDb.toDouble())
            .toFloat().coerceIn(-40f, -2f)
        val compRatio = json.optDouble("compressorRatio", derivedComp.ratio.toDouble())
            .toFloat().coerceIn(1f, 18f)
        val compAttack = json.optDouble("compressorAttackMs", derivedComp.attackMs.toDouble())
            .toFloat().coerceIn(1f, 100f)
        val compRelease = json.optDouble("compressorReleaseMs", derivedComp.releaseMs.toDouble())
            .toFloat().coerceIn(20f, 500f)
        val compMakeup = json.optDouble("compressorMakeupGainDb", derivedComp.makeupGainDb.toDouble())
            .toFloat().coerceIn(0f, 12f)

        val balance = json.optDouble("balance", fallbackState.balance.toDouble()).toFloat().coerceIn(-1f, 1f)

        val badges = buildList {
            if (resetFlat) add("FLAT 0dB")
            if (applyEq) add("EQ")
            if (applyUltraBass) add(if (ultraBassEnabled) "ULTRA BASS ${(ultraBassLevel * 100).toInt()}%" else "ULTRA BASS AUS")
            if (applyCrossover) add("X-OVER ${lowMidHz.toInt()}/${midHighHz.toInt()}Hz")
            if (applyBalance) {
                val balStr = when {
                    balance > 0.05f -> "BAL +${(balance * 100).toInt()}% R"
                    balance < -0.05f -> "BAL ${-(balance * 100).toInt()}% L"
                    else -> "BAL MITTE"
                }
                add(balStr)
            }
            if (applyCompressor) add(if (compEnabled) "KOMPRESSOR ${compPreset.label}" else "KOMPRESSOR AUS")
            if (saveAsPreset) add("PRESET: $profileName")
            if (bindToBluetooth) {
                val label = btTargetName.ifBlank { "BT" }.uppercase()
                add(if (btCustomEqEnabled) "BT: $label" else "BT BYPASS: $label")
            }
            if (setMasterEnabled) add(if (targetMasterEnabled) "DSP AN" else "DSP AUS")
            if (requestSongDownload) {
                add(if (preferredDownloadFormat.isNotBlank()) "LINK $preferredDownloadFormat" else "LINK DOWNLOAD")
            }
        }

        val anyAudioChange = applyEq || applyUltraBass || applyCrossover || applyCompressor || applyBalance || saveAsPreset
        val recommendation = if (anyAudioChange) {
            val effBands = if (eqMode == EqMode.SIMPLE) {
                AudioMath.simpleToExpertBands(simpleLow, simpleMid, simpleHigh)
            } else {
                parsedBands
            }
            val preset = AudioPreset(
                id = "ai_${UUID.randomUUID()}",
                name = profileName,
                tag = "GEMINI",
                description = reply,
                isCustom = saveAsPreset,
                isAiGenerated = true,
                mode = eqMode,
                simpleLowDb = simpleLow,
                simpleMidDb = simpleMid,
                simpleHighDb = simpleHigh,
                expertBandsDb = effBands,
                preampDb = preampDb,
                crossover = if (applyCrossover) {
                    fallbackState.crossover.copy(
                        enabled = true,
                        lowEnabled = xLowEnabled,
                        midEnabled = xMidEnabled,
                        highEnabled = xHighEnabled,
                        lowMidHz = lowMidHz,
                        midHighHz = midHighHz,
                        lowGainDb = xLowGain,
                        midGainDb = xMidGain,
                        highGainDb = xHighGain,
                        slopeDbPerOct = xSlope
                    )
                } else {
                    fallbackState.crossover
                },
                compressor = if (applyCompressor) {
                    CompressorSettings(
                        enabled = compEnabled,
                        intensity = compIntensity,
                        preset = compPreset,
                        thresholdDb = compThresh,
                        ratio = compRatio,
                        attackMs = compAttack,
                        releaseMs = compRelease,
                        kneeDb = derivedComp.kneeDb,
                        makeupGainDb = compMakeup
                    )
                } else {
                    fallbackState.compressor
                },
                balance = if (applyBalance) balance else fallbackState.balance,
                ultraBassEnabled = if (applyUltraBass) ultraBassEnabled else fallbackState.ultraBassEnabled,
                ultraBassLevel = if (applyUltraBass) ultraBassLevel else fallbackState.ultraBassLevel
            )
            AiSoundRecommendation(preset = preset, acousticNotes = reply)
        } else {
            null
        }

        return AiChatActionResult(
            assistantReply = reply,
            appliedBadges = badges,
            recommendation = recommendation,
            applyEq = applyEq,
            applyUltraBass = applyUltraBass,
            ultraBassEnabled = ultraBassEnabled,
            ultraBassLevel = ultraBassLevel,
            applyCrossover = applyCrossover,
            crossoverLowEnabled = xLowEnabled,
            crossoverMidEnabled = xMidEnabled,
            crossoverHighEnabled = xHighEnabled,
            applyCompressor = applyCompressor,
            applyBalance = applyBalance,
            setMasterEnabled = setMasterEnabled,
            targetMasterEnabled = targetMasterEnabled,
            resetFlat = resetFlat,
            saveAsPreset = saveAsPreset,
            bindToBluetooth = bindToBluetooth,
            bluetoothTargetDeviceName = btTargetName,
            bluetoothCustomEqEnabled = btCustomEqEnabled,
            setGlobalAutoBypass = setGlobalAutoBypass,
            targetGlobalAutoBypass = targetGlobalAutoBypass,
            eqMode = eqMode,
            simpleLowDb = simpleLow,
            simpleMidDb = simpleMid,
            simpleHighDb = simpleHigh,
            expertBandsDb = parsedBands,
            preampDb = preampDb,
            crossoverLowMidHz = lowMidHz,
            crossoverMidHighHz = midHighHz,
            crossoverLowGainDb = xLowGain,
            crossoverMidGainDb = xMidGain,
            crossoverHighGainDb = xHighGain,
            crossoverSlopeDbPerOct = xSlope,
            compressorEnabled = compEnabled,
            compressorIntensity = compIntensity,
            compressorPreset = compPreset,
            compressorThresholdDb = compThresh,
            compressorRatio = compRatio,
            compressorAttackMs = compAttack,
            compressorReleaseMs = compRelease,
            compressorMakeupGainDb = compMakeup,
            balance = balance,
            profileName = profileName,
            requestSongDownload = requestSongDownload,
            songSearchQuery = songSearchQuery,
            songUrl = songUrl,
            preferredDownloadFormat = preferredDownloadFormat
        )
    }
}
