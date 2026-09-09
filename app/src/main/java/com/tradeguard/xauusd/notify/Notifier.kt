package com.tradeguard.xauusd.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tradeguard.xauusd.MainActivity
import com.tradeguard.xauusd.R
import com.tradeguard.xauusd.detect.SetupType
import com.tradeguard.xauusd.detect.Signal
import java.util.Locale

object Notifier {

    const val CHANNEL_ALERTS = "xauusd_alerts"
    const val CHANNEL_STATUS = "scanner_status"
    const val STATUS_NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_BASE = 2001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.channel_alerts_desc) }
        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = context.getString(R.string.channel_status_desc) }
        nm.createNotificationChannels(listOf(alerts, status))
    }

    fun buildStatusNotification(context: Context): android.app.Notification {
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.status_notif_title))
            .setContentText(context.getString(R.string.status_notif_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showAlert(context: Context, signal: Signal) {
        ensureChannels(context)
        val title = titleFor(signal)
        val body = bodyFor(signal)
        val pi = PendingIntent.getActivity(
            context, signal.id.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(ALERT_NOTIFICATION_BASE + (signal.entryCandleTimeMs % 1000).toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+; scanning continues silently.
        }
    }

    fun titleFor(signal: Signal): String = when (signal.type) {
        SetupType.SWEEP_FVG_BUY -> "XAUUSD 15M bullish FVG entry"
        SetupType.SWEEP_FVG_SELL -> "XAUUSD 15M bearish FVG entry"
        SetupType.ORB_BUY -> "XAUUSD 15M NY range BUY"
        SetupType.ORB_SELL -> "XAUUSD 15M NY range SELL"
    }

    fun bodyFor(signal: Signal): String = String.format(
        Locale.US,
        "Final setup detected — manually verify your broker chart.\n" +
            "Setup: %s\nEntry: %.2f\nSL: %.2f\nTP: %.2f\nR:R = 1:%.2f",
        signal.type.name, signal.entry, signal.stopLoss, signal.takeProfit, signal.riskReward
    )
}
