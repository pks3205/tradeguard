package com.tradeguard.xauusd.detect

import com.tradeguard.xauusd.data.Candle

enum class SetupType { SWEEP_FVG_BUY, SWEEP_FVG_SELL, ORB_BUY, ORB_SELL }

/**
 * A final, entry-ready setup. The detector only emits a [Signal] once every
 * condition (including the minimum 1:2 risk/reward) has been met on a
 * completed candle. Raw sweeps, raw breakouts, and in-progress sequences
 * never produce a signal.
 */
data class Signal(
    val type: SetupType,
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskReward: Double,
    val entryCandleTimeMs: Long
) {
    /** Stable identity used for local duplicate suppression. */
    val id: String get() = "${type.name}:$entryCandleTimeMs"

    val isBuy: Boolean
        get() = type == SetupType.SWEEP_FVG_BUY || type == SetupType.ORB_BUY
}

/**
 * Pure, unit-testable setup detection for XAUUSD on the 15-minute timeframe.
 *
 * The scanner must feed this object a list of COMPLETED candles, newest last.
 * The final element of the list is treated as the "now" (entry/retrace/
 * continuation) candle.
 */
object SetupDetector {

    const val MIN_RISK_REWARD = 2.0
    const val DISPLACEMENT_MULT = 1.5
    const val MIN_SWEEP_CANDLES = 40
    const val MIN_ORB_CANDLES = 60
    const val ATR_PERIOD = 14
    const val DISP_LOOKBACK = 14
    const val EMA_FAST = 20
    const val EMA_SLOW = 50
    const val FRACTAL_K = 2

    private const val M15_MS = 15 * 60 * 1000L
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Fixed opening range: 12:00-12:15 UTC == 17:30-18:00 Asia/Kolkata, all year (no DST shift). */
    private const val UTC_RANGE_START = 12 * 60 * 60 * 1000L

    fun detect(candles: List<Candle>): Signal? = detectSweepFvg(candles) ?: detectOrb(candles)

    // ---------------------------------------------------------------------
    // Setup 1: Liquidity Sweep -> MSS -> FVG entry
    // ---------------------------------------------------------------------

    fun detectSweepFvg(candles: List<Candle>): Signal? {
        if (candles.size < MIN_SWEEP_CANDLES) return null
        return detectSweepBullish(candles) ?: detectSweepBearish(candles)
    }

    private fun detectSweepBullish(candles: List<Candle>): Signal? {
        val r = candles.lastIndex
        val retrace = candles[r]

        // 1) bullish 3-candle FVG: candle1.high < candle3.low
        val f = findBullishFvg(candles, r) ?: return null
        val fvgLower = candles[f].high
        val fvgUpper = candles[f + 2].low

        // 2) a later candle retraces/touches the FVG and closes back above the lower boundary
        if (!(retrace.low < fvgUpper && retrace.close > fvgLower)) return null

        // 3) bullish displacement candle (body >= 1.5x average of previous 14)
        val d = findBullishDisplacement(candles, f) ?: return null

        // 4) prior swing low swept (wick below, close back above)
        val sweep = findSweepOfSwingLow(candles, d) ?: return null
        val swingLowIdx = sweep.first
        val sweepIdx = sweep.second

        // 5) bullish MSS: a close above the latest relevant lower high
        val lowerHighIdx = latestFractalHigh(candles, d) ?: return null
        if (!hasCloseAbove(candles, d, minOf(f + 2, r - 1), candles[lowerHighIdx].high)) return null

        // 6) levels + minimum 1:2 R:R
        val entry = retrace.close
        val tpIdx = fractalHighsAbove(candles, d, entry) ?: return null
        val tp = candles[tpIdx].high
        val atrSweep = Indicators.atr(candles, ATR_PERIOD)[sweepIdx]
        val buffer = maxOf(0.3 * atrSweep, 0.05)
        val stopLoss = candles[sweepIdx].low - buffer
        return buildSignal(SetupType.SWEEP_FVG_BUY, entry, stopLoss, tp, retrace.timestampMs)
    }

    private fun detectSweepBearish(candles: List<Candle>): Signal? {
        val r = candles.lastIndex
        val retrace = candles[r]

        // bearish 3-candle FVG: candle1.low > candle3.high
        val f = findBearishFvg(candles, r) ?: return null
        val fvgHigh = candles[f].low
        val fvgLow = candles[f + 2].high

        // retrace up into the gap, closing back below the upper boundary
        if (!(retrace.high > fvgLow && retrace.close < fvgHigh)) return null

        val d = findBearishDisplacement(candles, f) ?: return null
        val sweep = findSweepOfSwingHigh(candles, d) ?: return null
        val swingHighIdx = sweep.first
        val sweepIdx = sweep.second

        // bearish MSS: a close below the latest relevant higher low
        val higherLowIdx = latestFractalLow(candles, d) ?: return null
        if (!hasCloseBelow(candles, d, minOf(f + 2, r - 1), candles[higherLowIdx].low)) return null

        val entry = retrace.close
        val tpIdx = fractalLowsBelow(candles, d, entry) ?: return null
        val tp = candles[tpIdx].low
        val atrSweep = Indicators.atr(candles, ATR_PERIOD)[sweepIdx]
        val buffer = maxOf(0.3 * atrSweep, 0.05)
        val stopLoss = candles[sweepIdx].high + buffer
        return buildSignal(SetupType.SWEEP_FVG_SELL, entry, stopLoss, tp, retrace.timestampMs)
    }

    // ---------------------------------------------------------------------
    // Setup 2: New York Opening Range Breakout (fixed 17:30-18:00 IST)
    // ---------------------------------------------------------------------

    fun detectOrb(candles: List<Candle>): Signal? {
        if (candles.size < MIN_ORB_CANDLES) return null
        return detectOrbBullish(candles) ?: detectOrbBearish(candles)
    }

    private fun detectOrbBullish(candles: List<Candle>): Signal? {
        val r = candles.lastIndex
        val entryCandle = candles[r]
        val range = findRangeCandles(candles, r) ?: return null
        val rangeHigh = maxOf(candles[range.first].high, candles[range.second].high)

        // 1) a completed candle closes above the range high
        var breakoutIdx = -1
        for (i in range.second + 1 until r) {
            if (candles[i].close > rangeHigh) { breakoutIdx = i; break }
        }
        if (breakoutIdx < 0) return null

        // 2) price later retests the broken range high
        var retestIdx = -1
        for (i in breakoutIdx + 1 until r) {
            if (candles[i].low <= rangeHigh) { retestIdx = i; break }
        }
        if (retestIdx < 0) return null

        // 3) bullish continuation close above the retest candle high
        if (!entryCandle.isBullish) return null
        if (entryCandle.close <= candles[retestIdx].high) return null

        // 4) trend filter: EMA20 > EMA50 and price above EMA20
        val closes = candles.map { it.close }.toDoubleArray()
        val emaFast = Indicators.ema(closes, EMA_FAST)
        val emaSlow = Indicators.ema(closes, EMA_SLOW)
        if (!(emaFast[r] > emaSlow[r] && entryCandle.close > emaFast[r])) return null

        val entry = entryCandle.close
        val tpIdx = fractalHighsAbove(candles, r, entry) ?: return null
        val tp = candles[tpIdx].high
        val atrNow = Indicators.atr(candles, ATR_PERIOD)[r]
        val buffer = maxOf(0.3 * atrNow, 0.05)
        val stopLoss = minOf(candles[retestIdx].low, rangeHigh) - buffer
        return buildSignal(SetupType.ORB_BUY, entry, stopLoss, tp, entryCandle.timestampMs)
    }

    private fun detectOrbBearish(candles: List<Candle>): Signal? {
        val r = candles.lastIndex
        val entryCandle = candles[r]
        val range = findRangeCandles(candles, r) ?: return null
        val rangeLow = minOf(candles[range.first].low, candles[range.second].low)

        // breakdown below the range low
        var breakdownIdx = -1
        for (i in range.second + 1 until r) {
            if (candles[i].close < rangeLow) { breakdownIdx = i; break }
        }
        if (breakdownIdx < 0) return null

        // retest of the broken range low
        var retestIdx = -1
        for (i in breakdownIdx + 1 until r) {
            if (candles[i].high >= rangeLow) { retestIdx = i; break }
        }
        if (retestIdx < 0) return null

        // bearish continuation close below the retest candle low
        if (!entryCandle.isBearish) return null
        if (entryCandle.close >= candles[retestIdx].low) return null

        // trend filter: EMA20 < EMA50 and price below EMA20
        val closes = candles.map { it.close }.toDoubleArray()
        val emaFast = Indicators.ema(closes, EMA_FAST)
        val emaSlow = Indicators.ema(closes, EMA_SLOW)
        if (!(emaFast[r] < emaSlow[r] && entryCandle.close < emaFast[r])) return null

        val entry = entryCandle.close
        val tpIdx = fractalLowsBelow(candles, r, entry) ?: return null
        val tp = candles[tpIdx].low
        val atrNow = Indicators.atr(candles, ATR_PERIOD)[r]
        val buffer = maxOf(0.3 * atrNow, 0.05)
        val stopLoss = maxOf(candles[retestIdx].high, rangeLow) + buffer
        return buildSignal(SetupType.ORB_SELL, entry, stopLoss, tp, entryCandle.timestampMs)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun buildSignal(type: SetupType, entry: Double, stopLoss: Double, tp: Double, timeMs: Long): Signal? {
        val risk = if (type == SetupType.SWEEP_FVG_BUY || type == SetupType.ORB_BUY) entry - stopLoss else stopLoss - entry
        val reward = if (type == SetupType.SWEEP_FVG_BUY || type == SetupType.ORB_BUY) tp - entry else entry - tp
        if (risk <= 0.0 || reward <= 0.0) return null
        val rr = reward / risk
        if (rr < MIN_RISK_REWARD) return null
        return Signal(type, entry, stopLoss, tp, rr, timeMs)
    }

    private fun findBullishFvg(candles: List<Candle>, r: Int): Int? {
        for (i in r - 3 downTo 0) {
            if (candles[i].high < candles[i + 2].low) return i
        }
        return null
    }

    private fun findBearishFvg(candles: List<Candle>, r: Int): Int? {
        for (i in r - 3 downTo 0) {
            if (candles[i].low > candles[i + 2].high) return i
        }
        return null
    }

    private fun findBullishDisplacement(candles: List<Candle>, f: Int): Int? {
        for (i in f downTo 0) {
            val c = candles[i]
            if (c.isBullish && c.body >= DISPLACEMENT_MULT * Indicators.averageBody(candles, i - 1, DISP_LOOKBACK)) return i
        }
        return null
    }

    private fun findBearishDisplacement(candles: List<Candle>, f: Int): Int? {
        for (i in f downTo 0) {
            val c = candles[i]
            if (c.isBearish && c.body >= DISPLACEMENT_MULT * Indicators.averageBody(candles, i - 1, DISP_LOOKBACK)) return i
        }
        return null
    }

    private fun latestFractalLow(candles: List<Candle>, before: Int): Int? =
        Indicators.fractalLowIndices(candles, before).lastOrNull()

    private fun latestFractalHigh(candles: List<Candle>, before: Int): Int? =
        Indicators.fractalHighIndices(candles, before).lastOrNull()

    private fun findSweepOfSwingLow(candles: List<Candle>, d: Int): Pair<Int, Int>? {
        val lows = Indicators.fractalLowIndices(candles, d)
        for (k in lows.indices.reversed()) {
            val swingLowIdx = lows[k]
            for (j in swingLowIdx + 1 until d) {
                val c = candles[j]
                if (c.low < candles[swingLowIdx].low && c.close > candles[swingLowIdx].low) {
                    return swingLowIdx to j
                }
            }
        }
        return null
    }

    private fun findSweepOfSwingHigh(candles: List<Candle>, d: Int): Pair<Int, Int>? {
        val highs = Indicators.fractalHighIndices(candles, d)
        for (k in highs.indices.reversed()) {
            val swingHighIdx = highs[k]
            for (j in swingHighIdx + 1 until d) {
                val c = candles[j]
                if (c.high > candles[swingHighIdx].high && c.close < candles[swingHighIdx].high) {
                    return swingHighIdx to j
                }
            }
        }
        return null
    }

    private fun hasCloseAbove(candles: List<Candle>, from: Int, to: Int, level: Double): Boolean {
        for (i in from..to) if (candles[i].close > level) return true
        return false
    }

    private fun hasCloseBelow(candles: List<Candle>, from: Int, to: Int, level: Double): Boolean {
        for (i in from..to) if (candles[i].close < level) return true
        return false
    }

    private fun fractalHighsAbove(candles: List<Candle>, before: Int, level: Double): Int? =
        Indicators.fractalHighIndices(candles, before).lastOrNull { candles[it].high > level }

    private fun fractalLowsBelow(candles: List<Candle>, before: Int, level: Double): Int? =
        Indicators.fractalLowIndices(candles, before).lastOrNull { candles[it].low < level }

    /** Fixed IST opening range mapped to UTC: 17:30 IST == 12:00 UTC, 18:00 IST == 12:15 UTC. */
    private fun findRangeCandles(candles: List<Candle>, r: Int): Pair<Int, Int>? {
        val entryMs = candles[r].timestampMs
        val dayStartUtc = Math.floorDiv(entryMs, DAY_MS) * DAY_MS
        val t1 = dayStartUtc + UTC_RANGE_START
        val t2 = t1 + M15_MS
        var i1 = -1
        var i2 = -1
        for (i in 0 until r) {
            val t = candles[i].timestampMs
            if (t == t1) i1 = i
            else if (t == t2) i2 = i
        }
        return if (i1 >= 0 && i2 >= 0) i1 to i2 else null
    }
}
