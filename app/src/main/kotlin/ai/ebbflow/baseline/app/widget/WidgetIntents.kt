package ai.ebbflow.baseline.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ai.ebbflow.baseline.app.ui.MainActivity

/**
 * Taps from the widgets. Every tap opens the app's own activity by explicit component; the
 * only broadcast is the self-scheduled redraw, to the app's non-exported
 * [WidgetActionReceiver]. No custom URL schemes.
 */
object WidgetIntents {
    const val EXTRA_DESTINATION = "ai.ebbflow.baseline.app.widget.extra.DESTINATION"
    const val ACTION_REFRESH = "ai.ebbflow.baseline.app.widget.action.REFRESH"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** The destination a widget tap asked for, or null for anything else (a launcher start). */
    fun destinationOf(intent: Intent?): WidgetDestination? =
        intent?.getStringExtra(EXTRA_DESTINATION)?.let { name -> WidgetDestination.entries.firstOrNull { it.name == name } }

    fun open(context: Context, appWidgetId: Int, slot: Int, destination: WidgetDestination): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_DESTINATION, destination.name)
        return PendingIntent.getActivity(context, WidgetRequestCodes.of(appWidgetId, slot), intent, FLAGS)
    }

    fun refresh(context: Context): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 0, intent, FLAGS)
    }
}
