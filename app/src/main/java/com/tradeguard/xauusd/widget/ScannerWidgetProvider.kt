package com.tradeguard.xauusd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tradeguard.xauusd.MainActivity
import com.tradeguard.xauusd.R
import com.tradeguard.xauusd.store.AppPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small home-screen widget that shows scanner status and the last alert.
 * It only displays state persisted by the scanner/service and simply opens
 * the app when tapped — it contains no scanner logic itself.
 */
class ScannerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, manager, id)
    }

    companion object {

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ScannerWidgetProvider::class.java)
            for (id in manager.getAppWidgetIds(component)) {
                update(context, manager, id)
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = AppPrefs(context)
            val views = RemoteViews(context.packageName, R.layout.scanner_widget)

            views.setTextViewText(
                R.id.widget_status,
                if (prefs.scannerRunning) "Scanner: ON" else "Scanner: OFF"
            )
            views.setTextViewText(
                R.id.widget_last,
                if (prefs.lastScanTimeMs > 0L) {
                    "Last scan: " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(prefs.lastScanTimeMs))
                } else {
                    "Last scan: --"
                }
            )
            views.setTextViewText(
                R.id.widget_signal,
                prefs.lastSignalTitle.ifBlank { "No alerts yet" }
            )

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            manager.updateAppWidget(id, views)
        }
    }
}
