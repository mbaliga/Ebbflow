package ai.ebbflow.eeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalQualityEstimatorTest {
    private fun packet(index: Int, channels: List<Double>) = EegPacket(
        timestampMs = index.toLong(),
        eventId = Mw75Constants.EEG_EVENT_ID,
        counter = index,
        ref = 0f,
        drl = 0f,
        channels = channels,
        featureStatus = 0,
    )

    @Test
    fun varyingUnclippedChannelsAreGood() {
        val estimator = SignalQualityEstimator(windowPackets = 4, emitEveryPackets = 1)
        var result: SignalQualitySummary? = null
        repeat(4) { index ->
            result = estimator.add(packet(index, List(12) { channel -> index + channel * 0.2 }))
        }
        assertEquals(SignalQualityLabel.GOOD, result?.label)
        assertEquals(1.0, result!!.usableChannelsFraction, 1e-9)
    }

    @Test
    fun flatlineAndClippingAreReportedWithoutStateInference() {
        val estimator = SignalQualityEstimator(windowPackets = 4, emitEveryPackets = 1)
        var result: SignalQualitySummary? = null
        repeat(4) { index ->
            result = estimator.add(packet(index, List(12) { channel ->
                when {
                    channel < 6 -> 1.0
                    channel == 6 -> 900.0
                    else -> index.toDouble()
                }
            }))
        }
        assertTrue(result!!.flatlineChannelsFraction >= 0.5)
        assertTrue(result!!.clippedSamplesFraction > 0.0)
        assertEquals(SignalQualityLabel.POOR, result!!.label)
    }
}
