package com.tradeguard.xauusd.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Abstraction over the market-data source so the scanner is testable. */
interface MarketDataClient {
    fun fetchCandles(): Result<List<Candle>>
}

/**
 * Free/public prototype feed using the Yahoo Finance chart endpoint for XAUUSD.
 *
 * No API key is required. This feed can be delayed and is subject to rate
 * limiting; values must be verified against the user's broker chart.
 */
class YahooFinanceClient : MarketDataClient {

    override fun fetchCandles(): Result<List<Candle>> {
        return try {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (XAUUSD Setup Alerts)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    Result.failure(IOException("HTTP $code: ${body.take(160)}"))
                } else {
                    parse(body)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parse(body: String): Result<List<Candle>> {
        return try {
            val root = JSONObject(body)
            val chart = root.getJSONObject("chart")

            val errorObj = chart.optJSONObject("error")
            if (errorObj != null) {
                return Result.failure(IOException(errorObj.optString("description", "Yahoo Finance error")))
            }

            val resultArr = chart.optJSONArray("result")
            if (resultArr == null || resultArr.length() == 0) {
                return Result.failure(IOException("No chart result in response"))
            }

            val result = resultArr.getJSONObject(0)
            val timestamps = result.optJSONArray("timestamp")
                ?: return Result.failure(IOException("Missing timestamp array"))

            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
            val opens = quote.optJSONArray("open")
            val highs = quote.optJSONArray("high")
            val lows = quote.optJSONArray("low")
            val closes = quote.optJSONArray("close")

            val candles = ArrayList<Candle>(timestamps.length())
            for (i in 0 until timestamps.length()) {
                val t = timestamps.optLong(i, Long.MIN_VALUE)
                val o = opens?.optDouble(i, Double.NaN) ?: Double.NaN
                val h = highs?.optDouble(i, Double.NaN) ?: Double.NaN
                val l = lows?.optDouble(i, Double.NaN) ?: Double.NaN
                val c = closes?.optDouble(i, Double.NaN) ?: Double.NaN
                if (t == Long.MIN_VALUE || !allFinite(o, h, l, c)) continue
                candles.add(Candle(o, h, l, c, t * 1000L))
            }
            Result.success(candles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun allFinite(vararg values: Double): Boolean = values.all { it.isFinite() }

    companion object {
        const val ENDPOINT =
            "https://query1.finance.yahoo.com/v8/finance/chart/XAUUSD%3DX?interval=15m&range=5d&includePrePost=false"
    }
}
