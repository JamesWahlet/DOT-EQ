package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.DspEngineType
import com.example.model.DspStatus
import com.example.model.EQ_BAND_CUTOFFS_HZ
import com.example.model.EqState
import com.example.model.VisualizerFrame
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SystemDspEngine"

object SystemDspEngine {

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var activeSessionId: Int = 0
    private var currentEqState: EqState = EqState()

    @Volatile
    private var testAudioThread: Thread? = null

    @Volatile
    private var isTestAudioRunning = false

    @Volatile
    private var testAudioTrack: AudioTrack? = null

    private val _status = MutableStateFlow(DspStatus())
    val status: StateFlow<DspStatus> = _status.asStateFlow()

    private val _visualizerFrame = MutableStateFlow(VisualizerFrame())
    val visualizerFrame: StateFlow<VisualizerFrame> = _visualizerFrame.asStateFlow()

    private var peakHoldBars = FloatArray(24) { 0.04f }

    // Intelligent Beat & Sub-Bass Detector state
    @Volatile
    private var bassEnergyEma = 0.08f

    @Volatile
    private var beatPulse = 0f

    @Volatile
    private var currentDynamicUltraBassDb = 0f

    @Volatile
    private var lastAppliedUltraBassDb = -1f

    @Volatile
    private var lastUltraBassApplyMs = 0L

    @Volatile
    private var compressorEnvelope = 0.1f

    @Synchronized
    fun initialize(context: Context, initialState: EqState) {
        currentEqState = initialState
        attachDspToSession(sessionId = 0, isTestSession = false)
        applyState(initialState)
        tryStartVisualizer(context)
    }

    @Synchronized
    fun attachDspToSession(sessionId: Int, isTestSession: Boolean) {
        releaseDspOnly()
        activeSessionId = sessionId

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val config = buildDynamicsConfig(currentEqState)
                val dp = DynamicsProcessing(0, sessionId, config)
                dp.enabled = currentEqState.masterEnabled
                dynamicsProcessing = dp
                val engineType = if (isTestSession) DspEngineType.TEST_SESSION else DspEngineType.DYNAMICS_PROCESSING
                _status.value = _status.value.copy(
                    engineType = engineType,
                    sessionId = sessionId,
                    isDspAttached = true,
                    statusDetail = if (isTestSession) {
                        "Test-Synth · Ultra Bass & Kompressor aktiv"
                    } else {
                        "Systemweit aktiv · EQ, Crossover, Ultra Bass & Kompressor"
                    }
                )
                return
            } catch (e: Exception) {
                Log.w(TAG, "DynamicsProcessing init failed on session $sessionId: ${e.message}")
            }
        }

        try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = currentEqState.masterEnabled
            legacyEqualizer = eq
            val engineType = if (isTestSession) DspEngineType.TEST_SESSION else DspEngineType.LEGACY_EQUALIZER
            _status.value = _status.value.copy(
                engineType = engineType,
                sessionId = sessionId,
                isDspAttached = true,
                statusDetail = "Basis-EQ aktiv auf Session $sessionId"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Legacy Equalizer init failed on session $sessionId", e)
            _status.value = _status.value.copy(
                engineType = DspEngineType.STANDBY,
                sessionId = sessionId,
                isDspAttached = false,
                statusDetail = "Audio-HAL im Standby (${e.localizedMessage ?: "nicht verfügbar"})"
            )
        }
    }

    @Synchronized
    fun applyState(state: EqState) {
        currentEqState = state
        val effectiveBands = AudioMath.effectiveEqBands(state)
        val (leftBalanceDb, rightBalanceDb) = AudioMath.balanceToChannelGainsDb(state.balance)

        val ultraBoostDb = if (state.masterEnabled && state.ultraBassEnabled) {
            val dynamicPart = beatPulse * (1.2f + state.ultraBassLevel * 2.8f)
            val basePart = 2.0f + state.ultraBassLevel * 4.2f
            (basePart + dynamicPart).coerceIn(0f, 9.5f)
        } else {
            0f
        }
        currentDynamicUltraBassDb = ultraBoostDb
        lastAppliedUltraBassDb = ultraBoostDb

        if (!state.ultraBassEnabled || !state.masterEnabled) {
            _status.value = _status.value.copy(ultraBassBoostDb = 0f, beatDetected = false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = dynamicsProcessing
            if (dp != null) {
                try {
                    dp.enabled = state.masterEnabled

                    // 1. Preamp + Stereo L/R Balance via per-channel input gain
                    dp.setInputGainbyChannel(0, (state.preampDb + leftBalanceDb).coerceIn(-30f, 15f))
                    dp.setInputGainbyChannel(1, (state.preampDb + rightBalanceDb).coerceIn(-30f, 15f))

                    // 2. 10-Band Pre-EQ + Adaptive Ultra Bass low-shelf shaping
                    for (bandIdx in 0 until 10) {
                        val ultraAdd = when (bandIdx) {
                            0 -> ultraBoostDb * 1.00f // 31 Hz
                            1 -> ultraBoostDb * 0.85f // 62 Hz
                            2 -> ultraBoostDb * 0.42f // 125 Hz
                            else -> 0f
                        }
                        val bandGain = (effectiveBands[bandIdx] + ultraAdd).coerceIn(-15f, 15f)
                        val band = DynamicsProcessing.EqBand(
                            true,
                            EQ_BAND_CUTOFFS_HZ[bandIdx],
                            bandGain
                        )
                        dp.setPreEqBandAllChannelsTo(bandIdx, band)
                    }

                    // 3. 3-Band Crossover + Compressor (MBC)
                    val xover = state.crossover
                    val comp = state.compressor
                    val (lowEff, midEff, highEff) = if (xover.enabled) {
                        AudioMath.effectiveCrossoverGains(xover)
                    } else {
                        Triple(0f, 0f, 0f)
                    }

                    val cutoffs = listOf(
                        xover.lowMidHz.coerceIn(40f, 1000f),
                        xover.midHighHz.coerceIn(max(xover.lowMidHz + 150f, 600f), 14000f),
                        20000f
                    )
                    val bandGains = listOf(lowEff, midEff, highEff)

                    // preGain drives audio noticeably above threshold when Compressor is active
                    val compPreDriveDb = if (comp.enabled) (2.0f + comp.intensity * 4.5f) else 0f

                    for (b in 0 until 3) {
                        val preGain = (bandGains[b] + compPreDriveDb).coerceIn(-36f, 16f)
                        val postGain = if (comp.enabled) {
                            comp.makeupGainDb.coerceIn(-6f, 12f)
                        } else {
                            0f
                        }
                        val mbcBand = DynamicsProcessing.MbcBand(
                            true,
                            cutoffs[b],
                            comp.attackMs.coerceIn(1f, 200f),
                            comp.releaseMs.coerceIn(10f, 1000f),
                            if (comp.enabled) comp.ratio.coerceIn(1f, 24f) else 1f,
                            if (comp.enabled) comp.thresholdDb.coerceIn(-60f, 0f) else 0f,
                            if (comp.enabled) comp.kneeDb.coerceIn(0f, 24f) else 0f,
                            -90f,
                            1f,
                            preGain,
                            postGain
                        )
                        dp.setMbcBandAllChannelsTo(b, mbcBand)
                    }

                    // 4. Safety Limiter prevents clipping from Ultra Bass + Compressor Makeup
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        1.5f,
                        55f,
                        10f,
                        -0.6f,
                        0f
                    )
                    dp.setLimiterAllChannelsTo(limiter)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Failed updating DynamicsProcessing params: ${e.message}")
                }
            }
        }

        // Fallback Legacy Equalizer
        legacyEqualizer?.let { eq ->
            try {
                eq.enabled = state.masterEnabled
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minLevel = range[0].toFloat()
                val maxLevel = range[1].toFloat()
                for (b in 0 until numBands) {
                    val centerHz = eq.getCenterFreq(b.toShort()) / 1000f
                    val baseDb = AudioMath.evaluateEqCurveDb(
                        freqHz = centerHz,
                        bandsDb = effectiveBands,
                        preampDb = state.preampDb,
                        includeCrossover = state.crossover
                    )
                    val ultraAdd = if (state.ultraBassEnabled) {
                        AudioMath.evaluateUltraBassBoostDb(centerHz, state.ultraBassLevel, beatPulse)
                    } else 0f
                    val mB = ((baseDb + ultraAdd) * 100f).coerceIn(minLevel, maxLevel).toInt().toShort()
                    eq.setBandLevel(b.toShort(), mB)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating Legacy Equalizer: ${e.message}")
            }
        }
    }

    /**
     * Dynamically updates just the low Pre-EQ bands during live beat transients without resetting MBC state.
     */
    private fun applyLiveUltraBassModulation(targetBoostDb: Float, beatActive: Boolean) {
        val state = currentEqState
        if (!state.masterEnabled || !state.ultraBassEnabled) return

        currentDynamicUltraBassDb = targetBoostDb
        _status.value = _status.value.copy(
            ultraBassBoostDb = targetBoostDb,
            beatDetected = beatActive
        )

        val now = System.currentTimeMillis()
        if (abs(targetBoostDb - lastAppliedUltraBassDb) < 0.35f && now - lastUltraBassApplyMs < 140L) {
            return
        }
        if (now - lastUltraBassApplyMs < 55L) return
        lastAppliedUltraBassDb = targetBoostDb
        lastUltraBassApplyMs = now

        val effectiveBands = AudioMath.effectiveEqBands(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = dynamicsProcessing ?: return
            try {
                dp.setPreEqBandAllChannelsTo(
                    0,
                    DynamicsProcessing.EqBand(true, EQ_BAND_CUTOFFS_HZ[0], (effectiveBands[0] + targetBoostDb).coerceIn(-15f, 15f))
                )
                dp.setPreEqBandAllChannelsTo(
                    1,
                    DynamicsProcessing.EqBand(true, EQ_BAND_CUTOFFS_HZ[1], (effectiveBands[1] + targetBoostDb * 0.85f).coerceIn(-15f, 15f))
                )
                dp.setPreEqBandAllChannelsTo(
                    2,
                    DynamicsProcessing.EqBand(true, EQ_BAND_CUTOFFS_HZ[2], (effectiveBands[2] + targetBoostDb * 0.42f).coerceIn(-15f, 15f))
                )
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    fun tryStartVisualizer(context: Context, sessionIdOverride: Int? = null) {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission && sessionIdOverride == null) {
            _status.value = _status.value.copy(isVisualizerRunning = false)
            return
        }

        val targetSession = sessionIdOverride ?: activeSessionId
        try {
            visualizer?.release()
            visualizer = null

            val viz = Visualizer(targetSession)
            val range = Visualizer.getCaptureSizeRange()
            val captureSize = min(1024, range[1]).coerceAtLeast(range[0])
            viz.captureSize = captureSize
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform != null && waveform.isNotEmpty()) {
                            processWaveformBytes(waveform)
                        }
                    }

                    override fun onFftDataCapture(
                        v: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft != null && fft.size >= 4) {
                            processFftBytes(fft)
                        }
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                true
            )
            viz.enabled = true
            visualizer = viz
            _status.value = _status.value.copy(isVisualizerRunning = true)
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer init failed on session $targetSession: ${e.message}")
            _status.value = _status.value.copy(isVisualizerRunning = false)
        }
    }

    fun startOrStopTestAudio(context: Context, enable: Boolean) {
        if (enable == isTestAudioRunning) return
        if (enable) {
            startTestAudio(context)
        } else {
            stopTestAudio(context)
        }
    }

    private fun startTestAudio(context: Context) {
        isTestAudioRunning = true
        _status.value = _status.value.copy(isTestAudioPlaying = true)

        testAudioThread = thread(start = true, name = "DotEqTestSynth") {
            val sampleRate = 44100
            val bufferSamples = 2048
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuf, bufferSamples * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create test AudioTrack", e)
                isTestAudioRunning = false
                _status.value = _status.value.copy(isTestAudioPlaying = false)
                return@thread
            }

            testAudioTrack = track
            val trackSession = track.audioSessionId

            if (!_status.value.isDspAttached || activeSessionId != 0) {
                attachDspToSession(trackSession, isTestSession = true)
                applyState(currentEqState)
            }
            if (!_status.value.isVisualizerRunning) {
                tryStartVisualizer(context, sessionIdOverride = trackSession)
            }

            val pcm = ShortArray(bufferSamples * 2)
            var phaseSub = 0.0
            var phaseMid = 0.0
            var phaseHi = 0.0
            var beatClock = 0.0
            val freqs = doubleArrayOf(55.0, 110.0, 220.0, 440.0, 880.0, 1760.0, 3520.0, 7040.0)
            val phases = DoubleArray(freqs.size)

            try {
                track.play()
                val fftInput = FloatArray(512)
                var frameCounter = 0

                while (isTestAudioRunning) {
                    val state = currentEqState
                    val bands = AudioMath.effectiveEqBands(state)
                    val (lBalDb, rBalDb) = AudioMath.balanceToChannelGainsDb(state.balance)
                    val lBalLin = 10.0.pow(lBalDb / 20.0)
                    val rBalLin = 10.0.pow(rBalDb / 20.0)
                    val comp = state.compressor

                    for (i in 0 until bufferSamples) {
                        // ~120 BPM kick-beat pulse (2 beats/sec) for clear beat detection & compressor testing
                        beatClock = (beatClock + 2.0 / sampleRate) % 1.0
                        val kickEnv = kotlin.math.exp(-beatClock * 7.5)
                        val pulse = 0.35 + 0.65 * kickEnv

                        phaseSub += 2.0 * PI * (58.0 + 30.0 * kickEnv) / sampleRate
                        phaseMid += 2.0 * PI * 330.0 / sampleRate
                        phaseHi += 2.0 * PI * 3200.0 / sampleRate

                        val lowPart = 0.36 * pulse * sin(phaseSub)
                        val midPart = 0.18 * sin(phaseMid) + 0.10 * sin(phaseMid * 1.5)
                        val highPart = 0.07 * pulse * sin(phaseHi)

                        // Honor Crossover active bands directly in synth too
                        val xover = state.crossover
                        var sample = if (xover.enabled && state.masterEnabled) {
                            val (lEff, mEff, hEff) = AudioMath.effectiveCrossoverGains(xover)
                            val lLin = if (lEff <= -32f) 0.0 else 10.0.pow(lEff / 20.0)
                            val mLin = if (mEff <= -32f) 0.0 else 10.0.pow(mEff / 20.0)
                            val hLin = if (hEff <= -32f) 0.0 else 10.0.pow(hEff / 20.0)
                            lowPart * lLin + midPart * mLin + highPart * hLin
                        } else {
                            lowPart + midPart + highPart
                        }

                        for (k in freqs.indices) {
                            phases[k] += 2.0 * PI * freqs[k] / sampleRate
                            sample += 0.032 * sin(phases[k])
                        }

                        // Ultra Bass adaptive low-harmonic reinforcement inside PCM synth
                        if (state.masterEnabled && state.ultraBassEnabled) {
                            val boostLin = 10.0.pow(currentDynamicUltraBassDb / 26.0)
                            sample += lowPart * (boostLin - 1.0) * 0.72
                        }

                        // Software envelope-following Compressor in synth loop when enabled
                        var processed = if (!_status.value.isDspAttached && state.masterEnabled) {
                            val avgBoostDb = bands.average() * 0.35
                            sample * 10.0.pow((avgBoostDb + state.preampDb * 0.5) / 20.0)
                        } else {
                            sample
                        }

                        if (state.masterEnabled && comp.enabled) {
                            val absLevel = abs(processed).toFloat().coerceAtLeast(0.0001f)
                            val coeff = if (absLevel > compressorEnvelope) 0.025f else 0.0012f
                            compressorEnvelope += (absLevel - compressorEnvelope) * coeff
                            val inDb = 20f * ln(compressorEnvelope) / ln(10f)
                            val outDb = AudioMath.evaluateCompressorOutputDb(inDb, comp)
                            val gainMul = 10.0.pow(((outDb - inDb) / 20f).toDouble()).coerceIn(0.15, 2.5)
                            processed = tanh(processed * gainMul * 1.15)
                        }

                        val left = (processed * lBalLin * 11000.0).toInt().coerceIn(-32767, 32767).toShort()
                        val right = (processed * rBalLin * 11000.0).toInt().coerceIn(-32767, 32767).toShort()

                        pcm[i * 2] = left
                        pcm[i * 2 + 1] = right

                        if (i < fftInput.size) {
                            fftInput[i] = ((left + right) / 65536f)
                        }
                    }

                    track.write(pcm, 0, pcm.size)

                    frameCounter++
                    if (!_status.value.isVisualizerRunning && frameCounter % 2 == 0) {
                        emitSynthesizedVisualizerFrame(fftInput, state)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Test audio loop stopped: ${e.message}")
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {
                }
                testAudioTrack = null
            }
        }
    }

    private fun stopTestAudio(context: Context) {
        isTestAudioRunning = false
        testAudioThread?.interrupt()
        testAudioThread = null
        _status.value = _status.value.copy(
            isTestAudioPlaying = false,
            estimatedGainReductionDb = 0f,
            ultraBassBoostDb = 0f,
            beatDetected = false
        )
        if (activeSessionId != 0) {
            attachDspToSession(0, isTestSession = false)
            applyState(currentEqState)
        }
        tryStartVisualizer(context)
        if (!_status.value.isVisualizerRunning) {
            _visualizerFrame.value = VisualizerFrame()
        }
    }

    fun setServiceActive(active: Boolean) {
        _status.value = _status.value.copy(isServiceActive = active)
    }

    private fun processWaveformBytes(waveform: ByteArray) {
        val sampleCount = 48
        val step = max(1, waveform.size / sampleCount)
        val wave = MutableList(sampleCount) { idx ->
            val raw = (waveform[(idx * step).coerceAtMost(waveform.lastIndex)].toInt() and 0xFF) - 128
            (raw / 128f).coerceIn(-1f, 1f)
        }
        val rms = kotlin.math.sqrt(wave.sumOf { (it * it).toDouble() } / wave.size).toFloat()
        val (lBalDb, rBalDb) = AudioMath.balanceToChannelGainsDb(currentEqState.balance)
        val lFactor = 10f.pow(lBalDb / 20f)
        val rFactor = 10f.pow(rBalDb / 20f)

        val comp = currentEqState.compressor
        val gr = if (comp.enabled && currentEqState.masterEnabled && rms > 0.01f) {
            // Adjust for MBC preGain drive so real GR meter reflects active compression accurately
            val driveDb = 2.0f + comp.intensity * 4.5f
            val inputDb = (20f * ln(rms) / ln(10f)) + driveDb
            if (inputDb > comp.thresholdDb) {
                val outDb = AudioMath.evaluateCompressorOutputDb(inputDb, comp) - comp.makeupGainDb
                (inputDb - outDb).coerceIn(0f, 16f)
            } else {
                0.8f * comp.intensity
            }
        } else {
            0f
        }

        _status.value = _status.value.copy(estimatedGainReductionDb = gr)
        _visualizerFrame.value = _visualizerFrame.value.copy(
            waveform = wave,
            leftLevel = (rms * 2.2f * lFactor).coerceIn(0f, 1f),
            rightLevel = (rms * 2.2f * rFactor).coerceIn(0f, 1f),
            hasSignal = rms > 0.01f
        )
    }

    private fun processFftBytes(fft: ByteArray) {
        val bins = fft.size / 2
        val barCount = 24
        val magnitudes = FloatArray(bins)
        magnitudes[0] = abs(fft[0].toInt()).toFloat()
        for (i in 1 until bins) {
            val re = fft[i * 2].toFloat()
            val im = fft[i * 2 + 1].toFloat()
            magnitudes[i] = hypot(re, im)
        }

        val bars = MutableList(barCount) { b ->
            val startBin = (b * b * bins / (barCount * barCount)).coerceIn(1, max(1, bins - 1))
            val endBin = (((b + 1) * (b + 1) * bins) / (barCount * barCount)).coerceIn(startBin + 1, bins)
            var maxMag = 0f
            for (k in startBin until endBin) {
                if (magnitudes[k] > maxMag) maxMag = magnitudes[k]
            }
            val db = if (maxMag > 1f) (20f * ln(maxMag) / ln(10f)) else 0f
            ((db / 46f)).coerceIn(0.03f, 1f)
        }

        for (i in 0 until barCount) {
            peakHoldBars[i] = max(bars[i], peakHoldBars[i] * 0.94f)
        }

        // Intelligent Bass & Beat Flux Detection on lowest bins (~20..140 Hz)
        val subBassEnergy = ((bars[0] + bars[1] + bars[2] + bars[3]) / 4f).coerceIn(0f, 1f)
        bassEnergyEma = bassEnergyEma * 0.88f + subBassEnergy * 0.12f
        val isBeatHit = subBassEnergy > 0.14f && subBassEnergy > bassEnergyEma * 1.16f
        beatPulse = if (isBeatHit) {
            max(beatPulse, (subBassEnergy * 1.25f).coerceIn(0.55f, 1f))
        } else {
            (beatPulse * 0.84f).coerceAtLeast(0f)
        }

        val state = currentEqState
        if (state.masterEnabled && state.ultraBassEnabled) {
            val dynamicBoost = 2.0f + state.ultraBassLevel * 4.2f + beatPulse * (1.2f + state.ultraBassLevel * 2.8f)
            applyLiveUltraBassModulation(dynamicBoost.coerceIn(0f, 9.5f), beatActive = beatPulse > 0.38f)
        }

        _visualizerFrame.value = _visualizerFrame.value.copy(
            bars = bars,
            peaks = peakHoldBars.toList(),
            hasSignal = bars.any { it > 0.08f }
        )
    }

    private fun emitSynthesizedVisualizerFrame(samples: FloatArray, state: EqState) {
        val wave = MutableList(48) { idx ->
            val i = (idx * samples.size / 48).coerceIn(0, samples.lastIndex)
            (samples[i] * 2.5f).coerceIn(-1f, 1f)
        }
        val bands = AudioMath.effectiveEqBands(state)
        val rms = kotlin.math.sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()

        // Intelligent sub-bass transient detector for synth audio
        val subEnergy = (rms * 3.4f).coerceIn(0f, 1f)
        bassEnergyEma = bassEnergyEma * 0.86f + subEnergy * 0.14f
        val isBeatHit = subEnergy > 0.22f && subEnergy > bassEnergyEma * 1.12f
        beatPulse = if (isBeatHit) 1f else (beatPulse * 0.82f)

        val ultraGain = if (state.masterEnabled && state.ultraBassEnabled) {
            val boostDb = (2.0f + state.ultraBassLevel * 4.2f + beatPulse * (1.2f + state.ultraBassLevel * 2.8f))
                .coerceIn(0f, 9.5f)
            currentDynamicUltraBassDb = boostDb
            boostDb
        } else {
            0f
        }

        val bars = MutableList(24) { b ->
            val freq = AudioMath.normToFreq(b / 23f)
            val eqDb = if (state.masterEnabled) {
                val base = AudioMath.evaluateEqCurveDb(freq, bands, state.preampDb, state.crossover)
                val uAdd = if (state.ultraBassEnabled) {
                    AudioMath.evaluateUltraBassBoostDb(freq, state.ultraBassLevel, beatPulse)
                } else 0f
                base + uAdd
            } else {
                0f
            }
            val baseEnergy = when {
                freq < 180f -> 0.58f + beatPulse * 0.18f
                freq < 900f -> 0.48f
                freq < 4000f -> 0.36f
                else -> 0.24f
            }
            val eqScale = 10f.pow(eqDb / 24f).coerceIn(0.15f, 1.9f)
            val jitter = 0.86f + 0.14f * sin(System.currentTimeMillis() * 0.008f + b * 0.7f)
            (baseEnergy * eqScale * jitter).coerceIn(0.04f, 1f)
        }
        for (i in 0 until 24) {
            peakHoldBars[i] = max(bars[i], peakHoldBars[i] * 0.94f)
        }

        val (lBalDb, rBalDb) = AudioMath.balanceToChannelGainsDb(state.balance)
        val lLevel = (rms * 3.8f * 10f.pow(lBalDb / 20f)).coerceIn(0f, 1f)
        val rLevel = (rms * 3.8f * 10f.pow(rBalDb / 20f)).coerceIn(0f, 1f)

        val comp = state.compressor
        val gr = if (comp.enabled && state.masterEnabled) {
            val driveDb = 2.0f + comp.intensity * 4.5f
            val pseudoInDb = (20f * ln(rms.coerceAtLeast(0.02f)) / ln(10f)) + driveDb + 6f
            if (pseudoInDb > comp.thresholdDb) {
                (pseudoInDb - (AudioMath.evaluateCompressorOutputDb(pseudoInDb, comp) - comp.makeupGainDb))
                    .coerceIn(0.5f, 16f)
            } else {
                1.0f + comp.intensity * 2.5f
            }
        } else 0f

        _status.value = _status.value.copy(
            estimatedGainReductionDb = gr,
            ultraBassBoostDb = ultraGain,
            beatDetected = state.masterEnabled && state.ultraBassEnabled && beatPulse > 0.38f
        )
        _visualizerFrame.value = VisualizerFrame(
            bars = bars,
            peaks = peakHoldBars.toList(),
            waveform = wave,
            leftLevel = lLevel,
            rightLevel = rLevel,
            hasSignal = true
        )
    }

    private fun buildDynamicsConfig(state: EqState): DynamicsProcessing.Config {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                10,
                true,
                3,
                false,
                0,
                true
            )
        } else {
            throw UnsupportedOperationException("DynamicsProcessing requires API 28+")
        }

        val bands = AudioMath.effectiveEqBands(state)
        val preEq = DynamicsProcessing.Eq(true, true, 10)
        for (i in 0 until 10) {
            preEq.setBand(i, DynamicsProcessing.EqBand(true, EQ_BAND_CUTOFFS_HZ[i], bands[i]))
        }
        builder.setPreEqAllChannelsTo(preEq)

        val mbc = DynamicsProcessing.Mbc(true, true, 3)
        val xover = state.crossover
        val cutoffs = listOf(xover.lowMidHz, xover.midHighHz, 20000f)
        for (b in 0 until 3) {
            mbc.setBand(
                b,
                DynamicsProcessing.MbcBand(
                    true,
                    cutoffs[b],
                    state.compressor.attackMs,
                    state.compressor.releaseMs,
                    if (state.compressor.enabled) state.compressor.ratio else 1f,
                    if (state.compressor.enabled) state.compressor.thresholdDb else 0f,
                    state.compressor.kneeDb,
                    -90f,
                    1f,
                    0f,
                    if (state.compressor.enabled) state.compressor.makeupGainDb else 0f
                )
            )
        }
        builder.setMbcAllChannelsTo(mbc)
        return builder.build()
    }

    private fun releaseDspOnly() {
        try {
            dynamicsProcessing?.release()
        } catch (_: Exception) {
        }
        dynamicsProcessing = null

        try {
            legacyEqualizer?.release()
        } catch (_: Exception) {
        }
        legacyEqualizer = null
    }

    @Synchronized
    fun releaseAll() {
        isTestAudioRunning = false
        testAudioThread?.interrupt()
        testAudioThread = null
        try {
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        releaseDspOnly()
    }
}
