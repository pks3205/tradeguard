package com.tradeguard.xauusd.scan

import com.tradeguard.xauusd.data.Candle
import com.tradeguard.xauusd.data.MarketDataClient
import com.tradeguard.xauusd.detect.SetupDetector
import com.tradeguard.xauusd.detect.Signal
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Orchestrates a single scan: fetch the feed, keep only COMPLETED 15-minute
 * candles, then run the pure [SetupDetector].
 */
class ScannerEngine(
    private val client: MarketDataClient,
    private val detector: SetupDetector = SetupDetector
) {

    data class ScanResult(
        val signal: Signal?,
        val completedCandleCount: Int,
        val error: String?
    )

    fun scan(nowMs: Long): ScanResult {
        val result = client.fetchCandles()
        val candles = result.getOrElse { e ->
            return ScanResult(null, 0, friendlyError(e))
        }
        if (candles.isEmpty()) return ScanResult(null, 0, "No candles returned by feed")

        val completed = candles.filter { it.timestampMs + CANDLE_MS <= nowMs }
        if (completed.isEmpty()) return ScanResult(null, 0, "No completed candles yet")

        val signal = detector.detect(completed)
        return ScanResult(signal, completed.size, null)
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "Feed timeout (free data is slow) — will retry"
        is IOException -> "Feed unavailable (${e.message}) — will retry"
        else -> "Feed error (${e.message ?: e.javaClass.simpleName})"
    }

    companion object {
        const val CANDLE_MS = 15 * 60 * 1000L
    }
}
