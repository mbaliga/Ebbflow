package ai.ebbflow.baseline.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide bridge for the current [StreamState].
 *
 * The foreground [ai.ebbflow.baseline.app.service.Mw75StreamingService] owns the
 * controller and pushes state here; the UI (a `ViewModel`) only reads it. A simple
 * singleton is sufficient for a single-session bring-up app — there is at most one
 * MW75 stream at a time — and avoids binding the Activity to the Service just to
 * share a flow. If the app later grows multiple concurrent sessions, replace this
 * with a DI-scoped holder.
 */
object StreamHub {
    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    fun update(transform: (StreamState) -> StreamState) {
        _state.update { transform(it).copy(lastUpdateMs = System.currentTimeMillis()) }
    }

    fun set(state: StreamState) {
        _state.value = state.copy(lastUpdateMs = System.currentTimeMillis())
    }
}
