package com.example

import com.example.audio.AudioMath
import com.example.model.CompressorPreset
import com.example.model.CompressorSettings
import com.example.model.CrossoverSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMathTest {

    @Test
    fun `simpleToExpertBands maps low mid high smoothly`() {
        val bands = AudioMath.simpleToExpertBands(lowDb = 6f, midDb = 0f, highDb = -4f)
        assertEquals(10, bands.size)
        assertTrue(bands[0] > 5f)
        assertTrue(bands[9] < -3f)
    }

    @Test
    fun `balanceToChannelGainsDb attenuates opposite channel`() {
        val (lCenter, rCenter) = AudioMath.balanceToChannelGainsDb(0f)
        assertEquals(0f, lCenter, 0.01f)
        assertEquals(0f, rCenter, 0.01f)

        val (lRight, rRight) = AudioMath.balanceToChannelGainsDb(0.5f)
        assertTrue(lRight < -10f)
        assertEquals(0f, rRight, 0.01f)
    }

    @Test
    fun `compressorFromSimple scales ratio threshold and makeup cleanly`() {
        val softLow = AudioMath.compressorFromSimple(true, 0.1f, CompressorPreset.SOFT)
        val punchHigh = AudioMath.compressorFromSimple(true, 0.9f, CompressorPreset.PUNCH)
        assertTrue(punchHigh.ratio > softLow.ratio)
        assertTrue(punchHigh.thresholdDb < softLow.thresholdDb)
        assertTrue(punchHigh.makeupGainDb > softLow.makeupGainDb)
    }

    @Test
    fun `compressor reduces signal above threshold`() {
        val comp = CompressorSettings(enabled = true, thresholdDb = -20f, ratio = 4f, kneeDb = 0f, makeupGainDb = 0f)
        val out = AudioMath.evaluateCompressorOutputDb(-8f, comp)
        assertEquals(-17f, out, 0.1f)
    }

    @Test
    fun `crossover band toggles mute disabled frequency bands`() {
        val xover = CrossoverSettings(
            lowEnabled = true,
            midEnabled = false,
            highEnabled = false,
            lowGainDb = 3f
        )
        val (l, m, h) = AudioMath.effectiveCrossoverGains(xover)
        assertEquals(3f, l, 0.01f)
        assertTrue(m <= -30f)
        assertTrue(h <= -30f)
    }

    @Test
    fun `ultra bass evaluates sub-bass boost without boosting highs`() {
        val boostSub = AudioMath.evaluateUltraBassBoostDb(freqHz = 40f, level = 0.8f, dynamicPulse = 1.0f)
        val boostHigh = AudioMath.evaluateUltraBassBoostDb(freqHz = 2000f, level = 0.8f, dynamicPulse = 1.0f)
        assertTrue(boostSub > 6.0f)
        assertEquals(0f, boostHigh, 0.01f)
    }
}
