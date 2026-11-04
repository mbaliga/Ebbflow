package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.baseline.app.widget.Fixtures.T0
import ai.ebbflow.baseline.app.widget.Fixtures.snapshot
import ai.ebbflow.baseline.app.widget.Fixtures.streaming
import ai.ebbflow.eeg.SignalQualityLabel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishPolicyTest {
    private val published = snapshot(streaming())
    private val now = T0 + 700_000

    @Test
    fun theFirstSnapshotIsAlwaysPublished() {
        assertTrue(PublishPolicy.shouldPublish(null, published, 0, now))
    }

    @Test
    fun phaseSessionAndBatteryChangesGoOutAtOnce() {
        val justPublished = now - 10
        assertTrue(PublishPolicy.shouldPublish(published, published.copy(phase = StreamPhase.STOPPED), justPublished, now))
        assertTrue(PublishPolicy.shouldPublish(published, published.copy(sessionStartedMs = now), justPublished, now))
        assertTrue(PublishPolicy.shouldPublish(published, published.copy(batteryPercent = 71), justPublished, now))
        assertTrue(PublishPolicy.shouldPublish(published, published.copy(batteryReadMs = now), justPublished, now))
    }

    @Test
    fun shareChangesWaitForTheLiveInterval() {
        val next = published.copy(usableFraction = 0.91, asOfMs = now)
        assertFalse(PublishPolicy.shouldPublish(published, next, now - 29_999, now))
        assertTrue(PublishPolicy.shouldPublish(published, next, now - PublishPolicy.LIVE_INTERVAL_MS, now))
    }

    @Test
    fun aLabelChangeWaitsOnlyTheShortInterval() {
        val next = published.copy(qualityLabel = SignalQualityLabel.FAIR)
        assertFalse(PublishPolicy.shouldPublish(published, next, now - 4_999, now))
        assertTrue(PublishPolicy.shouldPublish(published, next, now - PublishPolicy.LABEL_INTERVAL_MS, now))
    }

    @Test
    fun nothingButTheTimestampChangedIsSkipped() {
        assertFalse(PublishPolicy.shouldPublish(published, published.copy(asOfMs = now), now - 3_600_000, now))
    }

    @Test
    fun aClockThatJumpedBackDoesNotHoldUpdates() {
        assertTrue(PublishPolicy.shouldPublish(published, published.copy(usableFraction = 0.5), now + 60_000, now))
    }
}
