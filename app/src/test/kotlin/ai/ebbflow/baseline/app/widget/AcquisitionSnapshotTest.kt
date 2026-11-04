package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.baseline.app.model.StreamState
import ai.ebbflow.baseline.app.widget.Fixtures.T0
import ai.ebbflow.baseline.app.widget.Fixtures.streaming
import ai.ebbflow.eeg.SignalQualityLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionSnapshotTest {

    @Test
    fun theInMemoryDefaultIsNotASnapshot() {
        assertNull(AcquisitionSnapshot.of(StreamState()))
    }

    @Test
    fun snapshotKeepsIntegrityFieldsOnly() {
        val encoded = SnapshotCodec.encode(Fixtures.snapshot(streaming()))
        // The device name can identify a person; µV values are signal, not integrity.
        assertFalse(encoded.contains("Alice"))
        assertFalse(encoded.contains("12.5"))
        assertFalse(encoded.contains("-3.25"))
        assertFalse(encoded.contains("RFCOMM"))
        assertTrue(encoded.contains("label=GOOD"))
    }

    @Test
    fun codecRoundTrips() {
        val snapshot = Fixtures.snapshot(streaming(battery = null))
        assertEquals(snapshot, SnapshotCodec.decode(SnapshotCodec.encode(snapshot)))
    }

    @Test
    fun unreadableOrForeignFilesDecodeToNull() {
        assertNull(SnapshotCodec.decode(""))
        assertNull(SnapshotCodec.decode("garbage"))
        assertNull(SnapshotCodec.decode("v=2\nphase=STREAMING\nasOf=5\n"))
        assertNull(SnapshotCodec.decode("v=1\nphase=NOT_A_PHASE\nasOf=5\n"))
        assertNull(SnapshotCodec.decode("v=1\nphase=STOPPED\nasOf=0\n"))
    }

    @Test
    fun unknownLabelIsDroppedNotGuessed() {
        val state = streaming().copy(qualityLabel = "FOCUSED")
        assertNull(Fixtures.snapshot(state).qualityLabel)
    }

    @Test
    fun qualityFromAPreviousSessionIsNotThisSessions() {
        val carried = streaming(sessionStarted = T0 + 1_000_000, qualityAt = T0 + 500_000)
            .copy(phase = StreamPhase.ACTIVATING)
        assertFalse(Fixtures.snapshot(carried).hasQualityThisSession)
        assertTrue(Fixtures.snapshot(streaming()).hasQualityThisSession)
    }

    @Test
    fun batteryIsClampedToAPercentage() {
        assertEquals(100, Fixtures.snapshot(streaming(battery = 140)).batteryPercent)
    }

    @Test
    fun labelsComeFromTheCoreEnum() {
        assertEquals(SignalQualityLabel.FAIR, Fixtures.snapshot(streaming(label = SignalQualityLabel.FAIR)).qualityLabel)
    }
}
