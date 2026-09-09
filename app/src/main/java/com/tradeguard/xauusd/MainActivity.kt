package com.tradeguard.xauusd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tradeguard.xauusd.notify.Notifier
import com.tradeguard.xauusd.scan.ScannerService
import com.tradeguard.xauusd.store.AppPrefs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Dashboard(
                    onStart = { startScanner() },
                    onStop = { ScannerService.stop(this) }
                )
            }
        }
    }

    private fun startScanner() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        ScannerService.start(this)
    }
}

private val Gold = Color(0xFFF5B301)
private val SurfaceDark = Color(0xFF1A1D24)
private val Muted = Color(0xFF9AA4B2)
private val Good = Color(0xFF4CAF50)
private val Bad = Color(0xFFE53935)

@Composable
private fun Dashboard(onStart: () -> Unit, onStop: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPrefs(context) }

    var running by remember { mutableStateOf(prefs.scannerRunning) }
    var lastScan by remember { mutableStateOf(prefs.lastScanTimeMs) }
    var lastSignal by remember { mutableStateOf(prefs.lastSignalTitle) }
    var feedError by remember { mutableStateOf(prefs.feedError) }

    LaunchedEffect(Unit) {
        while (true) {
            running = prefs.scannerRunning
            lastScan = prefs.lastScanTimeMs
            lastSignal = prefs.lastSignalTitle
            feedError = prefs.feedError
            delay(2000)
        }
    }

    Scaffold(containerColor = Color(0xFF0E1014)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "XAUUSD Setup Alerts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Gold
            )
            Text(
                text = "15-minute scanner — notification-only. No trades are placed.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )

            StatusCard(running, lastScan, lastSignal, feedError)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !running,
                    colors = ButtonDefaults.buttonColors(containerColor = Good),
                    modifier = Modifier.weight(1f)
                ) { Text("Start M15 Scanner") }
                OutlinedButton(
                    onClick = onStop,
                    enabled = running,
                    modifier = Modifier.weight(1f)
                ) { Text("Stop Scanner") }
            }

            InfoCard(
                title = "Setup 1 — Liquidity Sweep → MSS → FVG entry",
                body = "BUY after ALL of: (1) a prior swing low exists (2-candle fractal), " +
                    "(2) a candle wicks below it and closes back above, " +
                    "(3) a bullish displacement candle appears (body ≥ 1.5× the previous 14-candle average), " +
                    "(4) a bullish market-structure shift closes above the latest lower high, " +
                    "(5) a bullish 3-candle FVG forms (candle 1 high < candle 3 low), and " +
                    "(6) a later completed candle retraces into the FVG and closes back above its lower boundary.\n\n" +
                    "SELL is the exact inverse (sweep of a prior swing high, bearish displacement, " +
                    "bearish MSS, bearish FVG). Suggested SL sits just beyond the sweep with an ATR buffer; " +
                    "TP is the relevant previous high/low (BSL/SSL). Alerts only fire when R:R ≥ 1:2."
            )

            InfoCard(
                title = "Setup 2 — New York Opening Range Breakout",
                body = "The opening range is fixed to 5:30 PM–6:00 PM IST (Asia/Kolkata) all year " +
                    "and is built from the two 15-minute candles in that window.\n\n" +
                    "BUY: a completed candle closes above the range high, price later retests the broken " +
                    "high, and a later bullish continuation candle closes above the retest candle high — " +
                    "with EMA 20 > EMA 50 and price above EMA 20.\n\n" +
                    "SELL is the inverse (breakdown below the range low, retest, bearish continuation, " +
                    "EMA 20 < EMA 50). Minimum R:R is 1:2."
            )

            WarningCard(
                "Important — free data & Android limitations",
                "• The free Yahoo Finance feed can be delayed and rate-limited; every alert must be " +
                    "verified against your broker chart.\n" +
                    "• Android can pause the scanner if you Force Stop the app or if battery " +
                    "optimization is enabled. Exclude this app from battery optimization for reliable " +
                    "scanning.\n" +
                    "• On Android 13+ you must allow notifications when prompted."
            )
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, lastScan: Long, lastSignal: String, feedError: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Scanner: ${if (running) "ON" else "OFF"}",
                    fontWeight = FontWeight.Bold,
                    color = if (running) Good else Muted,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text("Last successful scan: ${formatTime(lastScan)}", color = Muted)
            Text(
                text = "Last signal: ${lastSignal.ifBlank { "—" }}",
                color = if (lastSignal.isNotBlank()) Gold else Muted
            )
            if (feedError.isNotBlank()) {
                Text(text = "Feed: $feedError", color = Bad)
            } else {
                Text(text = "Feed: OK", color = Muted)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Gold)
            Text(body, color = Color(0xFFE0E4EA), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WarningCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1B10))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFB74D))
            Text(body, color = Color(0xFFE0E4EA), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatTime(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date(ms))
