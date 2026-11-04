package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.data.QualityRow
import ai.ebbflow.eeg.SignalQualityLabel

/** One capture: an unbroken run of stored quality summaries, oldest first. */
data class Capture(
    val rows: List<QualityRow>,
    /** True when the run reached the read limit, so it may have started earlier. */
    val truncated: Boolean,
) {
    val startMs: Long get() = rows.first().timestampMs
    val endMs: Long get() = rows.last().timestampMs
}

/** The integrity summary of one [Capture]. Every value describes the recording only. */
data class CaptureSummary(
    val startMs: Long,
    val endMs: Long,
    val summaries: Int,
    val meanUsable: Double,
    val meanClipped: Double,
    val meanFlatline: Double,
    val minUsable: Double,
    val latestUsable: Double,
    val goodShare: Double,
    val fairShare: Double,
    val poorShare: Double,
    val truncated: Boolean,
) {
    val durationMs: Long get() = endMs - startMs
}

object CaptureHistory {
    /**
     * Summaries arrive every 125 packets (well under a second at the MW75's rate) while a
     * stream runs, so a silence this long means the stream stopped. Two captures closer
     * together than this read as one.
     */
    const val GAP_MS = 30_000L

    /** Newest rows read per refresh: hours of streaming, bounded for the widget's time budget. */
    const val READ_LIMIT = 20_000

    /**
     * Rows migrated from the retired bring-up table have all three shares set to 0.0. A real
     * summary can't look like that (usable = 1 − flatline − clipped), so those rows carry no
     * measurement and are left out.
     */
    fun isMeasured(row: QualityRow): Boolean =
        !(row.usableChannelsFraction == 0.0 && row.clippedSamplesFraction == 0.0 && row.flatlineChannelsFraction == 0.0) &&
            row.usableChannelsFraction.isFinite() && row.clippedSamplesFraction.isFinite() &&
            row.flatlineChannelsFraction.isFinite()

    /**
     * The newest capture in [newestFirst] (as the DAO returns it). [readLimit] is the limit the
     * rows were read with: a run that reaches the end of a full read is marked truncated.
     */
    fun latestCapture(newestFirst: List<QualityRow>, readLimit: Int = READ_LIMIT): Capture? {
        val measured = newestFirst.filter(::isMeasured)
        if (measured.isEmpty()) return null
        var end = 1
        while (end < measured.size && measured[end - 1].timestampMs - measured[end].timestampMs <= GAP_MS) end++
        val reachedOldestRead = end == measured.size
        val truncated = reachedOldestRead && newestFirst.size >= readLimit
        return Capture(measured.subList(0, end).asReversed().toList(), truncated)
    }

    fun summarize(capture: Capture): CaptureSummary {
        val rows = capture.rows
        val labels = rows.map { SignalQualityLabel.classify(it.usableChannelsFraction, it.clippedSamplesFraction) }
        val n = rows.size.toDouble()
        return CaptureSummary(
            startMs = capture.startMs,
            endMs = capture.endMs,
            summaries = rows.size,
            meanUsable = rows.sumOf { it.usableChannelsFraction } / n,
            meanClipped = rows.sumOf { it.clippedSamplesFraction } / n,
            meanFlatline = rows.sumOf { it.flatlineChannelsFraction } / n,
            minUsable = rows.minOf { it.usableChannelsFraction },
            latestUsable = rows.last().usableChannelsFraction,
            goodShare = labels.count { it == SignalQualityLabel.GOOD } / n,
            fairShare = labels.count { it == SignalQualityLabel.FAIR } / n,
            poorShare = labels.count { it == SignalQualityLabel.POOR } / n,
            truncated = capture.truncated,
        )
    }
}

/** The quality-history line: usable-channel share over one capture. */
object Sparkline {
    /**
     * Averages [values] (oldest first) into at most [count] consecutive groups of near-equal
     * size. Summaries arrive at a steady cadence, so equal-count groups are equal-time groups.
     */
    fun buckets(values: List<Double>, count: Int): List<Double> {
        if (values.isEmpty() || count <= 0) return emptyList()
        if (values.size <= count) return values.toList()
        return List(count) { i ->
            val from = (i.toLong() * values.size / count).toInt()
            val to = ((i + 1).toLong() * values.size / count).toInt()
            values.subList(from, to).average()
        }
    }

    /**
     * Pixel positions for [values] (0..1, higher is better) inside a [width] × [height] box
     * with [pad] on every side; y grows downwards as on a Canvas.
     */
    fun points(values: List<Double>, width: Float, height: Float, pad: Float): List<Pair<Float, Float>> {
        if (values.isEmpty()) return emptyList()
        val innerW = (width - 2 * pad).coerceAtLeast(0f)
        val step = if (values.size > 1) innerW / (values.size - 1) else 0f
        return values.mapIndexed { i, v ->
            val x = if (values.size > 1) pad + i * step else pad + innerW / 2f
            x to yFor(v, height, pad)
        }
    }

    fun yFor(value: Double, height: Float, pad: Float): Float {
        val innerH = (height - 2 * pad).coerceAtLeast(0f)
        val clamped = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0
        return pad + ((1.0 - clamped) * innerH).toFloat()
    }
}
