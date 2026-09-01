package ai.ebbflow.baseline.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.ebbflow.baseline.app.data.AppDatabase
import ai.ebbflow.baseline.app.data.SignalQualitySample
import ai.ebbflow.baseline.app.model.StreamHub
import ai.ebbflow.baseline.app.model.StreamState
import ai.ebbflow.baseline.app.service.Mw75StreamingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the live [StreamState] and recent persisted samples to the UI, and
 * forwards start/stop to the foreground service. State lives in [StreamHub] (set
 * by the service-owned controller), so the ViewModel survives the Activity and
 * never owns the Bluetooth session itself.
 */
class StreamViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).signalQualitySampleDao()

    val state: StateFlow<StreamState> = StreamHub.state

    val recent: StateFlow<List<SignalQualitySample>> = dao.recent(limit = 60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start() = Mw75StreamingService.start(getApplication())

    fun stop() = Mw75StreamingService.stop(getApplication())
}
