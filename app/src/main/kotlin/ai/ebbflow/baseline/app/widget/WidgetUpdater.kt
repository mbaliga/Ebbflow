package ai.ebbflow.baseline.app.widget

import android.Manifest
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import ai.ebbflow.baseline.app.data.AppDatabase
import ai.ebbflow.baseline.app.data.RawEegStorage
import ai.ebbflow.baseline.app.model.StreamHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.ZoneId

/**
 * Reads the app's data once per refresh and redraws the placed widgets. All reads happen
 * off the main thread; receivers hand over with [BroadcastReceiver.goAsync] and finish
 * within [BUDGET_MS].
 */
object WidgetUpdater {
    private const val TAG = "EbbflowWidgets"
    private const val BUDGET_MS = 8_000L

    /** Process-wide scope for widget work; IO because every refresh reads files and Room. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun providerFor(kind: WidgetKind): Class<out AppWidgetProvider> = when (kind) {
        WidgetKind.HEADSET -> HeadsetWidgetProvider::class.java
        WidgetKind.QUALITY -> QualityWidgetProvider::class.java
        WidgetKind.LAST_CAPTURE -> LastCaptureWidgetProvider::class.java
        WidgetKind.CAPTURE -> CaptureWidgetProvider::class.java
        WidgetKind.STORED -> StoredWidgetProvider::class.java
        WidgetKind.HISTORY -> HistoryWidgetProvider::class.java
    }

    /** The placed widgets, by kind; kinds with none placed are left out. */
    fun placed(context: Context): Map<WidgetKind, IntArray> {
        val manager = AppWidgetManager.getInstance(context) ?: return emptyMap()
        return WidgetKind.entries
            .associateWith { manager.getAppWidgetIds(ComponentName(context, providerFor(it))) ?: IntArray(0) }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * Redraws every placed widget. Called when the app's data changes, when the app opens
     * and when the self-scheduled refresh fires.
     */
    suspend fun updateAll(context: Context) {
        val targets = placed(context)
        if (targets.isEmpty()) {
            cancelRefresh(context)
            return
        }
        update(context, targets)
    }

    suspend fun update(context: Context, targets: Map<WidgetKind, IntArray>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val inputs = runCatching { load(context, targets.keys) }
            .onFailure { Log.w(TAG, "Widget data could not be read", it) }
            .getOrNull()
        for ((kind, ids) in targets) {
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val views = runCatching { WidgetRenderer.render(context, kind, inputs, options, id) }
                    .onFailure { Log.w(TAG, "Widget $kind could not be drawn", it) }
                    .getOrElse { WidgetRenderer.renderError(context, kind, options, id) }
                manager.updateAppWidget(id, views)
            }
        }
        scheduleRefresh(
            context,
            nowMs = inputs?.nowMs ?: System.currentTimeMillis(),
            live = AcquisitionStatus.isLive(StreamHub.state.value),
        )
    }

    private suspend fun load(context: Context, kinds: Set<WidgetKind>): WidgetInputs {
        val acquisition = AcquisitionStatus.resolve(StreamHub.state.value, WidgetSnapshotStore.read(context))
        val needsHistory = kinds.any { it.readsHistory }
        val needsStorage = kinds.any { it.readsStorage }
        val dao = if (needsHistory || needsStorage) AppDatabase.get(context).signalQualitySampleDao() else null
        val capture = if (needsHistory && dao != null) {
            CaptureHistory.latestCapture(dao.latestQuality(CaptureHistory.READ_LIMIT), CaptureHistory.READ_LIMIT)
        } else {
            null
        }
        val storage = if (needsStorage && dao != null) {
            StorageFacts(
                summaryCount = dao.count(),
                oldestMs = dao.oldestTimestampMs(),
                newestMs = dao.newestTimestampMs(),
                databaseBytes = databaseBytes(context),
                rawEegStored = RawEegStorage.ENABLED,
                internetPermission = requestsInternet(context),
            )
        } else {
            null
        }
        return WidgetInputs(System.currentTimeMillis(), acquisition, capture, storage)
    }

    private fun databaseBytes(context: Context): Long {
        val db = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        return listOf("", "-wal", "-shm", "-journal")
            .map { File(db.path + it) }
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun requestsInternet(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
    }.getOrDefault(false)

    /**
     * The widgets' one self-scheduled redraw (see [WidgetFormat.nextRefresh]): just after
     * midnight, so "14:02" becomes "3 Nov, 14:02", and while a session is live also a check
     * that the session still runs, pushed back by every publish. Setting the same
     * PendingIntent replaces the previous alarm. Inexact and non-waking: it runs when the
     * phone is next awake. A force-stop cancels it; then the widgets correct themselves when
     * ebbflow is next opened (MainActivity redraws them) or the phone restarts.
     */
    private fun scheduleRefresh(context: Context, nowMs: Long, live: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = WidgetFormat.nextRefresh(nowMs, live, ZoneId.systemDefault())
        runCatching { alarms.set(AlarmManager.RTC, at, WidgetIntents.refresh(context)) }
    }

    fun cancelRefresh(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarms.cancel(WidgetIntents.refresh(context)) }
    }

    /**
     * Redraws every placed widget off the main thread, from outside a receiver. The app calls
     * it when it opens: after a force-stop (which cancels the app's alarms) that is the first
     * chance to replace a "Streaming" the dead process left on the home screen.
     */
    fun refreshInBackground(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MS) { updateAll(app) }
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            }
        }
    }

    /** Runs [block] for a receiver without blocking its thread, within the broadcast budget. */
    fun runAsync(receiver: BroadcastReceiver, context: Context, block: suspend (Context) -> Unit) {
        val app = context.applicationContext
        val pending = receiver.goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MS) { block(app) }
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
