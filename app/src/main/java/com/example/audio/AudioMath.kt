package com.example.audio

import com.example.model.CompressorPreset
import com.example.model.CompressorSettings
import com.example.model.CrossoverSettings
import com.example.model.EQ_FREQUENCIES_HZ
import com.example.model.EqMode
import com.example.model.EqState
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

object AudioMath {

    const val MIN_FREQ_HZ = 20f
    const val MAX_FREQ_HZ = 20000f
    const val MIN_DB = -12f
    const val MAX_DB = 12f

    /**
     * Maps Simple Mode (LOW / MID / HIGH) smoothly onto the 10 ISO EQ bands.
     */
    fun simpleToExpertBands(lowDb: Float, midDb: Float, highDb: Float): List<Float> {
        val lowWeights = listOf(1.00f, 0.95f, 0.78f, 0.42f, 0.12f, 0.02f, 0.00f, 0.00f, 0.00f, 0.00f)
        val midWeights = listOf(0.00f, 0.05f, 0.22f, 0.58f, 0.92f, 1.00f, 0.88f, 0.52f, 0.15f, 0.02f)
        val highWeights = listOf(0.00f, 0.00f, 0.00f, 0.00f, 0.04f, 0.14f, 0.42f, 0.78f, 0.96f, 1.00f)

        return (0 until 10).map { i ->
            val combined = lowDb * lowWeights[i] + midDb * midWeights[i] + highDb * highWeights[i]
            combined.coerceIn(MIN_DB, MAX_DB)
        }
    }

    /**
     * Approximates Simple Mode (LOW / MID / HIGH) averages from 10-band Expert gains.
     */
    fun expertToSimpleSummary(bandsDb: List<Float>): Triple<Float, Float, Float> {
        val safe = if (bandsDb.size == 10) bandsDb else List(10) { 0f }
        val low = ((safe[0] + safe[1] + safe[2]) / 3f).coerceIn(MIN_DB, MAX_DB)
        val mid = ((safe[3] + safe[4] + safe[5] + safe[6]) / 4f).coerceIn(MIN_DB, MAX_DB)
        val high = ((safe[7] + safe[8] + safe[9]) / 3f).coerceIn(MIN_DB, MAX_DB)
        return Triple(low, mid, high)
    }

    /**
     * Returns the effective 10-band EQ gains according to current mode.
     */
    fun effectiveEqBands(state: EqState): List<Float> {
        return when (state.mode) {
            EqMode.SIMPLE -> simpleToExpertBands(state.simpleLowDb, state.simpleMidDb, state.simpleHighDb)
            EqMode.EXPERT -> if (state.expertBandsDb.size == 10) state.expertBandsDb else List(10) { 0f }
        }
    }

    /**
     * Derives responsive, audibly effective Compressor parameters from a single Strength/Intensity + Preset.
     */
    fun compressorFromSimple(
        enabled: Boolean,
        intensity: Float,
        preset: CompressorPreset
    ): CompressorSettings {
        val k = intensity.coerceIn(0f, 1f)
        return when (preset) {
            CompressorPreset.SOFT -> CompressorSettings(
                enabled = enabled,
                intensity = k,
                preset = preset,
                thresholdDb = -14f - k * 12f,
                ratio = 2.0f + k * 2.2f,
                attackMs = 15f,
                releaseMs = 150f,
                kneeDb = 6f,
                makeupGainDb = 1.2f + k * 3.0f
            )
            CompressorPreset.BALANCED -> CompressorSettings(
                enabled = enabled,
                intensity = k,
                preset = preset,
                thresholdDb = -16f - k * 14f,
                ratio = 3.0f + k * 3.5f,
                attackMs = 8f,
                releaseMs = 110f,
                kneeDb = 4f,
                makeupGainDb = 2.0f + k * 4.0f
            )
            CompressorPreset.PUNCH -> CompressorSettings(
                enabled = enabled,
                intensity = k,
                preset = preset,
                thresholdDb = -18f - k * 15f,
                ratio = 4.2f + k * 4.8f,
                attackMs = 18f,
                releaseMs = 85f,
                kneeDb = 3f,
                makeupGainDb = 2.5f + k * 4.5f
            )
            CompressorPreset.LIMITER -> CompressorSettings(
                enabled = enabled,
                intensity = k,
                preset = preset,
                thresholdDb = -10f - k * 15f,
                ratio = 8.0f + k * 10.0f,
                attackMs = 2f,
                releaseMs = 70f,
                kneeDb = 1f,
                makeupGainDb = 3.0f + k * 5.0f
            )
        }
    }

    /**
     * Normalized logarithmic X position [0..1] for a given frequency in [20..20000] Hz.
     */
    fun freqToNorm(freqHz: Float): Float {
        val clamped = freqHz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)
        val minLog = ln(MIN_FREQ_HZ)
        val maxLog = ln(MAX_FREQ_HZ)
        return ((ln(clamped) - minLog) / (maxLog - minLog)).coerceIn(0f, 1f)
    }

    fun normToFreq(norm: Float): Float {
        val minLog = ln(MIN_FREQ_HZ)
        val maxLog = ln(MAX_FREQ_HZ)
        return exp(minLog + norm.coerceIn(0f, 1f) * (maxLog - minLog))
    }

    /**
     * Evaluates smooth EQ response (dB) at frequency [freqHz] for curve preview rendering.
     */
    fun evaluateEqCurveDb(
        freqHz: Float,
        bandsDb: List<Float>,
        preampDb: Float = 0f,
        includeCrossover: CrossoverSettings? = null
    ): Float {
        val logF = ln(freqHz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ))
        var num = 0f
        var den = 0f
        val sigma = 0.62f

        for (i in EQ_FREQUENCIES_HZ.indices) {
            val bandLog = ln(EQ_FREQUENCIES_HZ[i])
            val dist = (logF - bandLog) / sigma
            val w = exp(-0.5f * dist * dist)
            num += w * bandsDb.getOrElse(i) { 0f }
            den += w
        }
        var totalDb = if (den > 0.0001f) (num / den) + preampDb else preampDb

        if (includeCrossover != null && includeCrossover.enabled) {
            val (lowEff, midEff, highEff) = effectiveCrossoverGains(includeCrossover)
            val lowBlend = sigmoidDown(logF, ln(includeCrossover.lowMidHz), sharpness = 3.5f)
            val highBlend = sigmoidUp(logF, ln(includeCrossover.midHighHz), sharpness = 3.5f)
            val midBlend = (1f - lowBlend) * (1f - highBlend)
            totalDb += lowEff * lowBlend + midEff * midBlend + highEff * highBlend
        }

        return totalDb.coerceIn(-24f, 18f)
    }

    /**
     * Computes effective gain per Crossover band considering band enable switches, Solo, and Mute.
     */
    fun effectiveCrossoverGains(crossover: CrossoverSettings): Triple<Float, Float, Float> {
        val anySolo = crossover.lowSolo || crossover.midSolo || crossover.highSolo
        val lowActive = crossover.lowEnabled && !crossover.lowMute && (!anySolo || crossover.lowSolo)
        val midActive = crossover.midEnabled && !crossover.midMute && (!anySolo || crossover.midSolo)
        val highActive = crossover.highEnabled && !crossover.highMute && (!anySolo || crossover.highSolo)

        val lowGain = if (lowActive) crossover.lowGainDb else -36f
        val midGain = if (midActive) crossover.midGainDb else -36f
        val highGain = if (highActive) crossover.highGainDb else -36f
        return Triple(lowGain, midGain, highGain)
    }

    /**
     * Evaluates individual Crossover filter band curve (dB) at [freqHz] for LOW (0), MID (1), HIGH (2).
     */
    fun evaluateCrossoverBandCurveDb(
        freqHz: Float,
        bandIndex: Int,
        crossover: CrossoverSettings
    ): Float {
        val logF = ln(freqHz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ))
        val logLow = ln(crossover.lowMidHz.coerceIn(40f, 1000f))
        val logHigh = ln(crossover.midHighHz.coerceIn(600f, 12000f))
        val slopeFactor = if (crossover.slopeDbPerOct >= 24) 6.0f else 3.2f

        val (lowEff, midEff, highEff) = effectiveCrossoverGains(crossover)

        val attenuationDb = when (bandIndex) {
            0 -> {
                if (logF <= logLow) 0f else -slopeFactor * (logF - logLow) * 3.2f
            }
            1 -> {
                val hp = if (logF >= logLow) 0f else -slopeFactor * (logLow - logF) * 3.2f
                val lp = if (logF <= logHigh) 0f else -slopeFactor * (logF - logHigh) * 3.2f
                hp + lp
            }
            else -> {
                if (logF >= logHigh) 0f else -slopeFactor * (logHigh - logF) * 3.2f
            }
        }

        val bandGain = when (bandIndex) {
            0 -> lowEff
            1 -> midEff
            else -> highEff
        }

        return (bandGain + attenuationDb).coerceIn(-28f, 14f)
    }

    /**
     * Computes Compressor output dB for an input level dB (with soft knee + makeup gain).
     */
    fun evaluateCompressorOutputDb(inputDb: Float, comp: CompressorSettings): Float {
        if (!comp.enabled) return inputDb
        val t = comp.thresholdDb
        val r = max(1f, comp.ratio)
        val w = max(0f, comp.kneeDb)

        val compressed = when {
            w <= 0.1f -> {
                if (inputDb <= t) inputDb else t + (inputDb - t) / r
            }
            2f * (inputDb - t) < -w -> inputDb
            2f * (inputDb - t) > w -> t + (inputDb - t) / r
            else -> {
                val x = inputDb - t + w / 2f
                inputDb + ((1f / r) - 1f) * (x * x) / (2f * w)
            }
        }
        return (compressed + comp.makeupGainDb).coerceIn(-60f, 6f)
    }

    /**
     * Evaluates the frequency-dependent Ultra Bass curve boost (dB) for display & PCM/DSP application.
     */
    fun evaluateUltraBassBoostDb(freqHz: Float, level: Float, dynamicPulse: Float = 0f): Float {
        val lvl = level.coerceIn(0f, 1f)
        val maxBoost = 2.0f + lvl * 5.0f + dynamicPulse.coerceIn(0f, 1f) * (1.2f + lvl * 2.5f)
        val weight = when {
            freqHz <= 45f -> 1.0f
            freqHz <= 90f -> 0.85f
            freqHz <= 145f -> 0.45f
            else -> 0f
        }
        return (maxBoost * weight).coerceIn(0f, 10f)
    }

    /**
     * Converts Balance [-1..1] to (leftGainDb, rightGainDb).
     */
    fun balanceToChannelGainsDb(balance: Float): Pair<Float, Float> {
        val b = balance.coerceIn(-1f, 1f)
        val leftAtten = if (b > 0f) -b * 24f else 0f
        val rightAtten = if (b < 0f) b * 24f else 0f
        return Pair(leftAtten, rightAtten)
    }

    private fun sigmoidDown(x: Float, center: Float, sharpness: Float): Float {
        return 1f / (1f + exp((x - center) * sharpness))
    }

    private fun sigmoidUp(x: Float, center: Float, sharpness: Float): Float {
        return 1f / (1f + exp(-(x - center) * sharpness))
    }

    fun formatDb(db: Float): String {
        val rounded = (kotlin.math.round(db * 10f) / 10f)
        return when {
            rounded > 0.05f -> "+%.1f dB".format(rounded)
            rounded < -0.05f -> "%.1f dB".format(rounded)
            else -> "0.0 dB"
        }
    }

    fun formatFreq(hz: Float): String {
        return if (hz >= 1000f) {
            val k = hz / 1000f
            if (k % 1f < 0.05f) "${k.toInt()} kHz" else "%.1f kHz".format(k)
        } else {
            "${hz.toInt()} Hz"
        }
    }
}
