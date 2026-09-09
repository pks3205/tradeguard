package com.tradeguard.xauusd.data

import kotlin.math.abs

/** A single OHLC candle. All prices are plain numbers (XAUUSD spot-style). */
data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    /** Candle start time in milliseconds since epoch (UTC). */
    val timestampMs: Long
) {
    val body: Double get() = abs(close - open)
    val isBullish: Boolean get() = close > open
    val isBearish: Boolean get() = close < open
}
