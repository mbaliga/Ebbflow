package ai.ebbflow.baseline.eeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FocusEstimatorTest {

    private val fs = 250.0

    /** Feed [seconds] of a pure sine at [freqHz] and return the final focus score. */
    private fun focusForSine(freqHz: Double, seconds: Double = 4.0): Double {
        val est = FocusEstimator(sampleRateHz = fs, windowSeconds = 2.0, smoothing = 0.0)
        val n = (fs * seconds).toInt()
        var last: Double? = null
        for (i in 0 until n) {
            val s = 50.0 * sin(2.0 * PI * freqHz * i / fs)
            est.addSample(s)?.let { last = it }
        }
        assertNotNull("estimator should produce a score after a full window", last)
        return last!!
    }

    @Test
    fun noScoreUntilWindowFull() {
        val est = FocusEstimator(sampleRateHz = fs, windowSeconds = 2.0)
        // windowSize = 500 samples; nothing before that.
        repeat(499) { assertNull(est.addSample(1.0)) }
        assertNotNull(est.addSample(1.0))
    }

    @Test
    fun bandPowerPeaksInTheCorrectBand() {
        // A 6 Hz sine should have far more theta power than beta power.
        val n = (fs * 2).toInt()
        val sig = DoubleArray(n) { 50.0 * sin(2.0 * PI * 6.0 * it / fs) }
        val spec = BandPower.spectrum(sig, fs)
        val theta = BandPower.bandPower(spec, fs, BandPower.THETA)
        val beta = BandPower.bandPower(spec, fs, BandPower.BETA)
        assertTrue("theta ($theta) should dominate beta ($beta) for a 6 Hz tone", theta > beta * 10)
    }

    @Test
    fun thetaHeavySignalScoresHigherThanBetaHeavy() {
        // Lower theta/beta ratio => higher focus. A beta tone has a high beta
        // band power (low ratio => high focus); a theta tone is the opposite.
        val betaFocus = focusForSine(20.0)
        val thetaFocus = focusForSine(6.0)
        assertTrue(
            "beta-dominant focus ($betaFocus) should exceed theta-dominant focus ($thetaFocus)",
            betaFocus > thetaFocus,
        )
    }

    @Test
    fun focusIsBoundedZeroToOne() {
        val f = focusForSine(10.0)
        assertTrue("focus in [0,1] but was $f", f in 0.0..1.0)
    }

    @Test
    fun resetClearsState() {
        val est = FocusEstimator(sampleRateHz = fs, windowSeconds = 2.0)
        repeat(600) { est.addSample(sin(it.toDouble())) }
        assertNotNull(est.focus)
        est.reset()
        assertNull(est.focus)
        assertEquals(0L, est.samplesSeen)
    }

    @Test
    fun averagesChannelsFromPacket() {
        // add(packet) should average channels; a 6-channel constant packet is fine.
        val est = FocusEstimator(sampleRateHz = fs, windowSeconds = 2.0)
        val pkt = EegPacket(
            timestampMs = 0,
            eventId = Mw75Constants.EEG_EVENT_ID,
            counter = 0,
            ref = 0f,
            drl = 0f,
            channels = List(Mw75Constants.NUM_EEG_CHANNELS) { 1.0 },
            featureStatus = 0,
        )
        var last: Double? = null
        repeat(600) { est.add(pkt)?.let { last = it } }
        assertNotNull(last)
    }
}
