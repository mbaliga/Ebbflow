package ai.ebbflow.baseline.eeg

import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal frequency-domain helpers for the on-device *placeholder* focus metric.
 *
 * This is intentionally small and dependency-free (a naive O(n²) DFT) — the
 * windows are short (a few hundred samples) and run at most a couple of times a
 * second, so the cost is negligible and it keeps [FocusEstimator] pure-JVM and
 * unit-testable in CI. The Phase 2 "sensing core" (see build_plan.md §2) replaces
 * this with a real Welch-PSD / band-power pipeline; nothing here claims to be a
 * validated EEG measure.
 */
object BandPower {

    /** Classic EEG bands, in Hz. */
    val DELTA = 1.0..4.0
    val THETA = 4.0..8.0
    val ALPHA = 8.0..13.0
    val BETA = 13.0..30.0

    /**
     * Power spectral estimate of [signal] at sampling rate [sampleRateHz], as an
     * array indexed by frequency bin k (frequency = k * sampleRateHz / N) up to
     * Nyquist. A Hann window is applied to reduce spectral leakage. Values are
     * |X(k)|² of the windowed signal (arbitrary units — only ratios are used).
     */
    fun spectrum(signal: DoubleArray, sampleRateHz: Double): DoubleArray {
        val n = signal.size
        if (n == 0) return DoubleArray(0)

        // Hann window, and remove the DC offset so band ratios aren't dominated by it.
        val mean = signal.average()
        val windowed = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 - 0.5 * cos(2.0 * Math.PI * i / (n - 1).coerceAtLeast(1))
            windowed[i] = (signal[i] - mean) * w
        }

        val bins = n / 2 + 1
        val power = DoubleArray(bins)
        for (k in 0 until bins) {
            var re = 0.0
            var im = 0.0
            val coef = 2.0 * Math.PI * k / n
            for (i in 0 until n) {
                val angle = coef * i
                re += windowed[i] * cos(angle)
                im -= windowed[i] * sin(angle)
            }
            power[k] = re * re + im * im
        }
        return power
    }

    /** Sum of spectral power in the half-open frequency [band], from a [spectrum]. */
    fun bandPower(spectrum: DoubleArray, sampleRateHz: Double, band: ClosedFloatingPointRange<Double>): Double {
        if (spectrum.isEmpty()) return 0.0
        val n = (spectrum.size - 1) * 2 // original window length
        val binHz = sampleRateHz / n
        var sum = 0.0
        for (k in spectrum.indices) {
            val f = k * binHz
            if (f >= band.start && f < band.endInclusive) sum += spectrum[k]
        }
        return sum
    }
}
