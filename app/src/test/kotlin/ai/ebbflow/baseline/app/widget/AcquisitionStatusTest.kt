package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.baseline.app.model.StreamState
import ai.ebbflow.baseline.app.widget.Fixtures.T0
import ai.ebbflow.baseline.app.widget.Fixtures.streaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionStatusTest {

    @Test
    fun nothingSeenIsNone() {
        val acq = AcquisitionStatus.resolve(StreamState(), saved = null)
        assertEquals(Liveness.NONE, acq.liveness)
        assertNull(acq.snapshot)
        assertEquals(StatusWord.NOT_CONNECTED, StatusWord.of(acq))
    }

    @Test
    fun aRunningSessionInThisProcessIsLive() {
        val acq = AcquisitionStatus.resolve(streaming(), saved = null)
        assertEquals(Liveness.LIVE, acq.liveness)
        assertEquals(StatusWord.STREAMING, StatusWord.of(acq))
    }

    @Test
    fun aSavedRunningSessionWithNoProcessIsInterrupted() {
        val saved = Fixtures.snapshot(streaming())
        val acq = AcquisitionStatus.resolve(StreamState(), saved)
        assertEquals(Liveness.INTERRUPTED, acq.liveness)
        assertEquals(StatusWord.NOT_RUNNING, StatusWord.of(acq))
    }

    @Test
    fun aSavedStoppedSessionIsLastKnown() {
        val saved = Fixtures.snapshot(streaming().copy(phase = StreamPhase.STOPPED))
        val acq = AcquisitionStatus.resolve(StreamState(), saved)
        assertEquals(Liveness.LAST_KNOWN, acq.liveness)
        assertEquals(StatusWord.STOPPED, StatusWord.of(acq))
    }

    @Test
    fun theNewerOfMemoryAndFileWins() {
        val saved = Fixtures.snapshot(streaming(at = T0 + 1_000))
        val memory = streaming(at = T0 + 9_000).copy(phase = StreamPhase.ERROR)
        val acq = AcquisitionStatus.resolve(memory, saved)
        assertEquals(Liveness.LAST_KNOWN, acq.liveness)
        assertEquals(StatusWord.ERROR, StatusWord.of(acq))
        assertEquals(T0 + 9_000, acq.snapshot?.asOfMs)
    }

    @Test
    fun everyStatusHasAShapeAndAWord() {
        StatusWord.entries.forEach { assertTrue(it.name, it.glyph.isNotBlank()) }
        // Running, idle, stopped, failed and not-running are told apart by shape alone too.
        val distinct = listOf(StatusWord.STREAMING, StatusWord.NOT_CONNECTED, StatusWord.STOPPED, StatusWord.ERROR, StatusWord.NOT_RUNNING)
            .map { it.glyph }.toSet()
        assertEquals(5, distinct.size)
        assertEquals(StatusWord.entries.size, StatusWord.entries.map { it.word }.toSet().size)
    }

    @Test
    fun isLiveNeedsARealUpdate() {
        assertEquals(false, AcquisitionStatus.isLive(StreamState(phase = StreamPhase.STREAMING)))
        assertEquals(true, AcquisitionStatus.isLive(streaming()))
    }
}
