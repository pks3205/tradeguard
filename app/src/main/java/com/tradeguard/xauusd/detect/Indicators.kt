package com.tradeguard.xauusd.detect

import com.tradeguard.xauusd.data.Candle
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure indicator helpers used by [SetupDetector]. No Android dependencies, so
 * everything here is trivially unit-testable on the JVM.
 */
object Indicators {

    fun sma(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size)
        if (period <= 0) return out
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            out[i] = if (i >= period - 1) sum / period else 0.0
        }
        return out
    }

    fun ema(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size)
        if (values.isEmpty()) return out
        val k = 2.0 / (period + 1)
        out[0] = values[0]
        for (i in 1 until values.size) {
            out[i] = values[i] * k + out[i - 1] * (1.0 - k)
        }
        return out
    }

    fun trueRange(candles: List<Candle>): DoubleArray {
        val out = DoubleArray(candles.size)
        for (i in candles.indices) {
            val c = candles[i]
            val hl = c.high - c.low
            out[i] = if (i == 0) {
                hl
            } else {
                val prev = candles[i - 1]
                max(hl, max(abs(c.high - prev.close), abs(c.low - prev.close)))
            }
        }
        return out
    }

    fun atr(candles: List<Candle>, period: Int): DoubleArray = sma(trueRange(candles), period)

    /** Average body size of the `count` candles ending at `endInclusive`. */
    fun averageBody(candles: List<Candle>, endInclusive: Int, count: Int): Double {
        if (candles.isEmpty() || count <= 0) return 0.0
        var sum = 0.0
        var n = 0
        val start = maxOf(0, endInclusive - count + 1)
        for (i in start..endInclusive) {
            if (i < 0 || i >= candles.size) continue
            sum += candles[i].body
            n++
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** 2-candle fractal swing low by default: all `k` neighbours have a higher low. */
    fun isSwingLow(candles: List<Candle>, index: Int, k: Int = 2): Boolean {
        if (index - k < 0 || index + k >= candles.size) return false
        val pivot = candles[index].low
        for (j in index - k..index + k) {
            if (j == index) continue
            if (candles[j].low <= pivot) return false
        }
        return true
    }

    fun isSwingHigh(candles: List<Candle>, index: Int, k: Int = 2): Boolean {
        if (index - k < 0 || index + k >= candles.size) return false
        val pivot = candles[index].high
        for (j in index - k..index + k) {
            if (j == index) continue
            if (candles[j].high >= pivot) return false
        }
        return true
    }

    /** Indices of fractal swing lows strictly before `before` (exclusive). */
    fun fractalLowIndices(candles: List<Candle>, before: Int): List<Int> {
        val out = ArrayList<Int>()
        for (i in 0 until before) {
            if (isSwingLow(candles, i)) out.add(i)
        }
        return out
    }

    fun fractalHighIndices(candles: List<Candle>, before: Int): List<Int> {
        val out = ArrayList<Int>()
        for (i in 0 until before) {
            if (isSwingHigh(candles, i)) out.add(i)
        }
        return out
    }
}
