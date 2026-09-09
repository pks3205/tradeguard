package com.tradeguard.xauusd.scan

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.tradeguard.xauusd.data.YahooFinanceClient
import com.tradeguard.xauusd.notify.Notifier
import com.tradeguard.xauusd.store.AppPrefs
import com.tradeguard.xauusd.store.PrefsIdStore
import com.tradeguard.xauusd.store.SentSignalRegistry
import com.tradeguard.xauusd.widget.ScannerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground scanner service. Polls the free Yahoo Finance XAUUSD feed every
 * 60 seconds and evaluates only completed 15-minute candles. Runs with a
 * visible, persistent notification even when the app UI is closed.
 */
class ScannerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: ScannerEngine
    private lateinit var prefs: AppPrefs
    private lateinit var registry: SentSignalRegistry

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        prefs = AppPrefs(this)
        registry = SentSignalRegistry(PrefsIdStore(this))
        engine = ScannerEngine(YahooFinanceClient())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            // System restarted a previously-running service (START_STICKY):
            // re-enter foreground state and resume polling.
            null -> if (prefs.scannerRunning) start()
        }
        return START_STICKY
    }

    private fun start() {
        prefs.scannerRunning = true
        startAsForeground()
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            while (isActive) {
                runScan()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun startAsForeground() {
        val notification = Notifier.buildStatusNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                Notifier.STATUS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifier.STATUS_NOTIFICATION_ID, notification)
        }
    }

    private fun runScan() {
        val result = engine.scan(System.currentTimeMillis())
        if (result.error != null) {
            prefs.feedError = result.error
        } else {
            prefs.feedError = ""
            prefs.lastScanTimeMs = System.currentTimeMillis()
            val signal = result.signal
            if (signal != null && registry.tryMark(signal.id)) {
                prefs.lastSignalTitle = Notifier.titleFor(signal)
                Notifier.showAlert(this, signal)
            }
        }
        ScannerWidgetProvider.refresh(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.scannerRunning = false
        scanJob?.cancel()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.tradeguard.xauusd.action.START_SCANNER"
        const val ACTION_STOP = "com.tradeguard.xauusd.action.STOP_SCANNER"
        private const val POLL_INTERVAL_MS = 60_000L

        @Volatile
        private var scanJob: Job? = null

        fun start(context: android.content.Context) {
            val intent = Intent(context, ScannerService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, ScannerService::class.java))
        }
    }
}
