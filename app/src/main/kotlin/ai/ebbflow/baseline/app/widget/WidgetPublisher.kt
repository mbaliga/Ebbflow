package ai.ebbflow.baseline.app.widget

import android.content.Context
import ai.ebbflow.baseline.app.model.StreamHub
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pushes acquisition changes to the home screen. Started by the streaming service (the only
 * writer of [StreamHub]); watches the hub for the rest of the process, saves the snapshot and
 * redraws the widgets as [PublishPolicy] allows. Never starts or stops anything itself.
 */
object WidgetPublisher {
    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        WidgetUpdater.scope.launch {
            var published: AcquisitionSnapshot? = null
            var lastPublishMs = 0L
            StreamHub.state.collect { state ->
                val next = AcquisitionSnapshot.of(state) ?: return@collect
                val now = System.currentTimeMillis()
                if (!PublishPolicy.shouldPublish(published, next, lastPublishMs, now)) return@collect
                published = next
                lastPublishMs = now
                runCatching { WidgetSnapshotStore.write(app, next) }
                runCatching { WidgetUpdater.updateAll(app) }
            }
        }
    }
}
