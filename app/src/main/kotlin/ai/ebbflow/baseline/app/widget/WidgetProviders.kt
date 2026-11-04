package ai.ebbflow.baseline.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Shared behaviour of the six home-screen widgets: draw on update, redraw on resize below
 * Android 12 (12+ gets every size layout up front), and redraw when the clock, time zone or
 * language changes, since times and words are printed into the widget.
 */
abstract class EbbflowWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        WidgetUpdater.runAsync(this, context) { app -> WidgetUpdater.update(app, mapOf(kind to appWidgetIds)) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        WidgetUpdater.runAsync(this, context) { app -> WidgetUpdater.update(app, mapOf(kind to intArrayOf(appWidgetId))) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                val ids = AppWidgetManager.getInstance(context)
                    ?.getAppWidgetIds(ComponentName(context, javaClass))
                    ?: return
                if (ids.isEmpty()) return
                WidgetUpdater.runAsync(this, context) { app -> WidgetUpdater.update(app, mapOf(kind to ids)) }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        // The last widget of this kind went; drop the self-scheduled redraw if no other kind is left.
        if (WidgetUpdater.placed(context).isEmpty()) WidgetUpdater.cancelRefresh(context)
    }
}

/** Stream phase and headset battery (`stat`). */
class HeadsetWidgetProvider : EbbflowWidgetProvider(WidgetKind.HEADSET)

/** The app's quality label with usable, clipped and flatline shares (`stat`). */
class QualityWidgetProvider : EbbflowWidgetProvider(WidgetKind.QUALITY)

/** Integrity summary of the most recent capture (`card`). */
class LastCaptureWidgetProvider : EbbflowWidgetProvider(WidgetKind.LAST_CAPTURE)

/** Opens the app to connect or to watch a live capture; never starts or stops one (`pads`). */
class CaptureWidgetProvider : EbbflowWidgetProvider(WidgetKind.CAPTURE)

/** Raw-EEG storage state and what is kept on the phone (`stat`). */
class StoredWidgetProvider : EbbflowWidgetProvider(WidgetKind.STORED)

/** Usable-channel share across the last capture, drawn by the app (`image`). */
class HistoryWidgetProvider : EbbflowWidgetProvider(WidgetKind.HISTORY)

/**
 * The app's own, non-exported receiver for the widgets' self-scheduled redraw (midnight, and
 * the live-session check). It only redraws: no widget action starts or stops a capture.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetIntents.ACTION_REFRESH) {
            WidgetUpdater.runAsync(this, context) { app -> WidgetUpdater.updateAll(app) }
        }
    }
}
