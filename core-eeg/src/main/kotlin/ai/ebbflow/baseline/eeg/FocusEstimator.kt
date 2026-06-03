package ai.ebbflow.baseline.eeg

import kotlin.math.ln

/**
 * A **placeholder** focus metric for the Android app bring-up.
 *
 * It maintains a sliding window of recent EEG samples (averaged across channels)
 * and, once a full window is available, reports a 0..1 "focus" index derived from
 * the theta/beta band-power ratio — a long-standing (if contested) attention proxy.
 * A lower theta/beta ratio maps to a higher focus score.
 *
 * Why a placeholder, and why it lives here:
 *  - PR #2 scoped exactly "a placeholder focus metric" for the app; the *real*
 *    state estimator is the Phase 2 sensing core (build_plan.md §2), which does
 *    proper filtering, artefact rejection, per-user baselining and deviation
 *    scoring. This class deliberately does none of that and makes no clinical claim.
 *  - Keeping it in pure-JVM `core-eeg` (no Android deps) means it is exercised by
 *    CI; the Android layer only feeds packets in and renders the result.
 *
 * This class is not thread-safe; feed it from a single stream-reader thread.
 *
 * @param sampleRateHz nominal sample rate (see [Mw75Constants.NOMINAL_SAMPLE_RATE_HZ];
 *        confirm empirically before trusting any frequency-domain output).
 * @param windowSeconds length of the analysis window in seconds.
 * @param hopSeconds how often to recompute. The window slides this far between
 *        recomputations, so the DFT runs at 1/hopSeconds Hz (≈2 Hz by default)
 *        rather than once per 500 Hz sample — essential on a phone.
 * @param smoothing exponential-smoothing factor in [0,1) applied to the output
 *        (0 = no smoothing, higher = smoother/slower).
 */
class FocusEstimator(
    private val sampleRateHz: Double = Mw75Constants.NOMINAL_SAMPLE_RATE_HZ,
    windowSeconds: Double = 2.0,
    hopSeconds: Double = 0.5,
    private val smoothing: Double = 0.8,
) {
    private val windowSize: Int = (sampleRateHz * windowSeconds).toInt().coerceAtLeast(8)
    private val hopSize: Long = (sampleRateHz * hopSeconds).toLong().coerceAtLeast(1)
    private val ring = DoubleArray(windowSize)
    private var count = 0
    private var head = 0

    /** Last smoothed focus score in [0,1], or null until the first full window. */
    var focus: Double? = null
        private set

    /** Most recent raw (unsmoothed) theta/beta ratio, for diagnostics; null until ready. */
    var lastThetaBetaRatio: Double? = null
        private set

    /** Number of samples seen so far. */
    val samplesSeen: Long
        get() = totalSamples

    private var totalSamples = 0L

    /**
     * Add one parsed packet (its channels are averaged into a single value).
     * Returns the current focus score if a full window is now available, else null.
     */
    fun add(packet: EegPacket): Double? {
        if (packet.channels.isEmpty()) return focus
        return addSample(packet.channels.average())
    }

    /** Add a single pre-averaged µV sample. Exposed for testing with synthetic signals. */
    fun addSample(sample: Double): Double? {
        ring[head] = sample
        head = (head + 1) % windowSize
        if (count < windowSize) count++
        totalSamples++

        // Only recompute once a full window exists and we've advanced one hop, so
        // the DFT runs at ~1/hopSeconds Hz instead of on every incoming sample.
        if (count < windowSize || totalSamples % hopSize != 0L) return null
        return recompute()
    }

    private fun recompute(): Double {
        // Copy the ring into chronological order for the DFT.
        val window = DoubleArray(windowSize)
        for (i in 0 until windowSize) {
            window[i] = ring[(head + i) % windowSize]
        }

        val spectrum = BandPower.spectrum(window, sampleRateHz)
        val theta = BandPower.bandPower(spectrum, sampleRateHz, BandPower.THETA)
        val beta = BandPower.bandPower(spectrum, sampleRateHz, BandPower.BETA)

        // theta/beta ratio; guard against divide-by-zero on a flat signal.
        val ratio = if (beta > EPS) theta / beta else theta / EPS
        lastThetaBetaRatio = ratio

        // Map ratio -> 0..1 focus via a smooth, bounded transform. Lower ratio =>
        // higher focus. This curve is arbitrary (a placeholder); only relative
        // movement is meaningful, never absolute values.
        val raw = 1.0 / (1.0 + ln(1.0 + ratio.coerceAtLeast(0.0)))

        val prev = focus
        val smoothed = if (prev == null) raw else smoothing * prev + (1 - smoothing) * raw
        focus = smoothed.coerceIn(0.0, 1.0)
        return focus!!
    }

    /** Reset all state (e.g. on reconnect). */
    fun reset() {
        ring.fill(0.0)
        count = 0
        head = 0
        totalSamples = 0
        focus = null
        lastThetaBetaRatio = null
    }

    private companion object {
        const val EPS = 1e-9
    }
}
