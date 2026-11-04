package ai.ebbflow.eeg

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalQualityLabelTest {

    @Test
    fun boundariesMatchTheEstimatorRule() {
        assertEquals(SignalQualityLabel.GOOD, SignalQualityLabel.classify(0.9, 0.0))
        assertEquals(SignalQualityLabel.FAIR, SignalQualityLabel.classify(0.9, 0.01))
        assertEquals(SignalQualityLabel.FAIR, SignalQualityLabel.classify(0.89, 0.0))
        assertEquals(SignalQualityLabel.FAIR, SignalQualityLabel.classify(0.65, 0.049))
        assertEquals(SignalQualityLabel.POOR, SignalQualityLabel.classify(0.65, 0.05))
        assertEquals(SignalQualityLabel.POOR, SignalQualityLabel.classify(0.64, 0.0))
    }

    @Test
    fun estimatorUsesTheSameRule() {
        val estimator = SignalQualityEstimator(windowPackets = 4, emitEveryPackets = 1)
        var result: SignalQualitySummary? = null
        repeat(4) { index ->
            result = estimator.add(
                EegPacket(
                    timestampMs = index.toLong(),
                    eventId = Mw75Constants.EEG_EVENT_ID,
                    counter = index,
                    ref = 0f,
                    drl = 0f,
                    channels = List(12) { channel -> if (channel < 2) 1.0 else index + channel * 0.1 },
                    featureStatus = 0,
                ),
            )
        }
        val summary = result!!
        assertEquals(
            SignalQualityLabel.classify(summary.usableChannelsFraction, summary.clippedSamplesFraction),
            summary.label,
        )
    }
}
