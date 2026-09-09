package com.tradeguard.xauusd

import com.tradeguard.xauusd.data.Candle
import com.tradeguard.xauusd.detect.SetupDetector
import com.tradeguard.xauusd.detect.SetupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupDetectorTest {

    private val M15 = 15 * 60 * 1000L
    private val EPOCH_2024_01_10 = 1704844800000L // 2024-01-10T00:00:00Z

    private fun candle(o: Double, h: Double, l: Double, c: Double, t: Long) = Candle(o, h, l, c, t)

    // ------------------------------------------------------------------
    // Test data (validated against an independent reference implementation)
    // ------------------------------------------------------------------

    /** Uptrend -> pullback -> 12:00/12:15 UTC range -> breakout -> retest -> continuation. */
    private fun buildOrbBullish(): List<Candle> {
        val list = ArrayList<Candle>()
        val rangeT1 = EPOCH_2024_01_10 + 12 * 3600 * 1000L
        var t = rangeT1 - 55 * M15
        for (i in 0 until 52) {
            val o = 50.0 + i * 1.0
            list.add(candle(o, o + 0.8, o - 0.1, o + 0.5, t))
            t += M15
        }
        list.add(candle(101.5, 200.0, 101.2, 103.0, t)); t += M15 // spike (previous high / TP)
        list.add(candle(103.0, 103.5, 102.6, 103.2, t)); t += M15
        list.add(candle(103.2, 103.4, 100.0, 100.4, t)); t += M15 // pullback
        list.add(candle(100.4, 100.8, 99.8, 100.1, t)); t += M15  // range 1 (12:00 UTC)
        list.add(candle(100.1, 100.5, 99.6, 99.9, t)); t += M15   // range 2 (12:15 UTC)
        list.add(candle(99.9, 102.2, 99.9, 101.9, t)); t += M15   // breakout
        list.add(candle(101.9, 102.1, 100.6, 101.0, t)); t += M15 // retest
        list.add(candle(101.0, 102.4, 100.9, 102.2, t)); t += M15 // continuation (entry)
        return list
    }

    /** Peak -> decline -> lower high -> swing low -> sweep -> displacement -> FVG -> retrace. */
    private fun buildSweepBullish(): List<Candle> {
        val list = ArrayList<Candle>()
        var t = EPOCH_2024_01_10
        fun add(o: Double, h: Double, l: Double, c: Double) {
            list.add(candle(o, h, l, c, t)); t += M15
        }
        add(124.0, 124.4, 123.7, 124.1)
        add(124.1, 124.5, 123.8, 124.3)
        add(124.3, 135.0, 124.1, 124.7) // peak (previous high / TP)
        add(124.7, 124.9, 124.2, 124.5)
        add(124.5, 124.7, 124.1, 124.4)
        var p = 124.0
        repeat(16) { add(p, p + 0.2, p - 0.4, p - 0.3); p -= 0.5 }
        add(115.7, 116.1, 115.5, 115.9)
        add(115.9, 117.3, 115.7, 117.1) // lower high
        add(117.1, 117.2, 116.5, 116.8)
        add(116.8, 117.0, 116.3, 116.6)
        p = 116.3
        repeat(6) { add(p, p + 0.2, p - 0.4, p - 0.3); p -= 0.5 }
        add(113.0, 113.3, 112.6, 113.1)
        add(112.8, 113.1, 112.4, 112.9) // swing low (fractal)
        add(112.9, 113.2, 112.7, 113.0)
        add(113.0, 113.3, 112.8, 113.1)
        add(113.1, 113.2, 112.2, 112.9) // sweep below swing low, close back above
        add(112.9, 118.6, 112.7, 118.4) // displacement (breaks the lower high)
        add(118.4, 119.0, 118.0, 118.8)
        add(118.8, 119.2, 118.7, 119.0) // FVG candle 3
        add(119.0, 119.2, 118.5, 118.7)
        add(118.7, 118.9, 118.3, 118.6)
        add(118.6, 118.8, 118.2, 118.4)
        add(118.4, 118.9, 118.3, 118.7) // retrace into FVG (entry)
        return list
    }

    private fun mirror(candles: List<Candle>, center: Double): List<Candle> =
        candles.map { Candle(center - it.open, center - it.low, center - it.high, center - it.close, it.timestampMs) }

    // ------------------------------------------------------------------
    // Required tests
    // ------------------------------------------------------------------

    @Test
    fun `full bullish NY opening-range breakout retest continuation produces a signal`() {
        val signal = SetupDetector.detectOrb(buildOrbBullish())
        assertNotNull(signal)
        signal!!
        assertEquals(SetupType.ORB_BUY, signal.type)
        assertEquals(102.2, signal.entry, 1e-6)
        assertEquals(98.06928571428571, signal.stopLoss, 1e-6)
        assertEquals(200.0, signal.takeProfit, 1e-6)
        assertEquals(23.676292581704967, signal.riskReward, 1e-6)
        assertTrue("R:R must be at least 1:2", signal.riskReward >= 2.0)
        assertTrue(signal.stopLoss < signal.entry && signal.entry < signal.takeProfit)
    }

    @Test
    fun `incomplete ORB sequence must not alert`() {
        val candles = buildOrbBullish().toMutableList()
        // Replace the bullish continuation candle with a bearish candle that
        // closes back below the retest high: the sequence never completes.
        candles[candles.lastIndex] = candle(102.2, 102.4, 100.9, 101.5, candles.last().timestampMs)
        assertNull(SetupDetector.detectOrb(candles))
    }

    @Test
    fun `insufficient candle history produces no signal`() {
        assertNull(SetupDetector.detectOrb(buildOrbBullish().subList(0, 40)))
        assertNull(SetupDetector.detectSweepFvg(buildSweepBullish().subList(0, 20)))
    }

    @Test
    fun `no alert until the retrace candle actually closes inside the FVG`() {
        // Drop the final retrace candle: the previous consolidation candle does
        // not close back above the lower FVG boundary, so no signal.
        val withoutRetrace = buildSweepBullish().subList(0, buildSweepBullish().size - 1)
        assertNull(SetupDetector.detectSweepFvg(withoutRetrace))
    }

    // ------------------------------------------------------------------
    // Extra coverage: sweep setup + SELL (inverse) paths
    // ------------------------------------------------------------------

    @Test
    fun `full bullish liquidity sweep MSS FVG entry produces a signal`() {
        val signal = SetupDetector.detectSweepFvg(buildSweepBullish())
        assertNotNull(signal)
        signal!!
        assertEquals(SetupType.SWEEP_FVG_BUY, signal.type)
        assertEquals(118.7, signal.entry, 1e-6)
        assertEquals(111.97928571428572, signal.stopLoss, 1e-6)
        assertEquals(135.0, signal.takeProfit, 1e-6)
        assertEquals(2.425337442873846, signal.riskReward, 1e-6)
        assertTrue(signal.riskReward >= 2.0)
    }

    @Test
    fun `full bearish sweep produces a SELL signal`() {
        val signal = SetupDetector.detectSweepFvg(mirror(buildSweepBullish(), 240.0))
        assertNotNull(signal)
        signal!!
        assertEquals(SetupType.SWEEP_FVG_SELL, signal.type)
        assertEquals(121.3, signal.entry, 1e-6)
        assertEquals(128.0207142857143, signal.stopLoss, 1e-6)
        assertEquals(105.0, signal.takeProfit, 1e-6)
        assertTrue(signal.riskReward >= 2.0)
        assertTrue(signal.stopLoss > signal.entry && signal.entry > signal.takeProfit)
    }

    @Test
    fun `full bearish ORB produces a SELL signal`() {
        val signal = SetupDetector.detectOrb(mirror(buildOrbBullish(), 240.0))
        assertNotNull(signal)
        signal!!
        assertEquals(SetupType.ORB_SELL, signal.type)
        assertEquals(137.8, signal.entry, 1e-6)
        assertEquals(141.9307142857143, signal.stopLoss, 1e-6)
        assertEquals(40.0, signal.takeProfit, 1e-6)
        assertTrue(signal.riskReward >= 2.0)
        assertTrue(signal.stopLoss > signal.entry && signal.entry > signal.takeProfit)
    }
}
