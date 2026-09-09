package com.tradeguard.xauusd.store

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed persistence for scanner state, the last scan
 * result, and feed errors.
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tradeguard_prefs", Context.MODE_PRIVATE)

    var scannerRunning: Boolean
        get() = prefs.getBoolean(KEY_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_RUNNING, value).apply()

    var lastScanTimeMs: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN, value).apply()

    var lastSignalTitle: String
        get() = prefs.getString(KEY_LAST_SIGNAL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_SIGNAL, value).apply()

    var feedError: String
        get() = prefs.getString(KEY_FEED_ERROR, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_FEED_ERROR, value).apply()

    companion object {
        private const val KEY_RUNNING = "scanner_running"
        private const val KEY_LAST_SCAN = "last_scan_time"
        private const val KEY_LAST_SIGNAL = "last_signal_title"
        private const val KEY_FEED_ERROR = "feed_error"
    }
}
