package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.data.QualityRow
import ai.ebbflow.baseline.app.widget.Fixtures.T0
import ai.ebbflow.baseline.app.widget.Fixtures.row
import ai.ebbflow.baseline.app.widget.Fixtures.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHistoryTest {

    private fun newestFirst(vararg runs: List<QualityRow>) = runs.flatMap { it }.sortedByDescending { it.timestampMs }

    @Test
    fun migratedBringUpRowsCarryNoMeasurement() {
        assertFalse(CaptureHistory.isMeasured(QualityRow(T0, 0.0, 0.0, 0.0)))
        assertTrue(CaptureHistory.isMeasured(row(T0, 0.0, clipped = 0.2, flatline = 0.8)))
        assertTrue(CaptureHistory.isMeasured(row(T0, 1.0)))
        assertFalse(CaptureHistory.isMeasured(QualityRow(T0, Double.NaN, 0.0, 0.0)))
    }

    @Test
    fun noMeasuredRowsMeansNoCapture() {
        assertNull(CaptureHistory.latestCapture(emptyList()))
        assertNull(CaptureHistory.latestCapture(listOf(QualityRow(T0, 0.0, 0.0, 0.0))))
    }

    @Test
    fun aSilenceLongerThanTheGapSplitsCaptures() {
        val earlier = run(T0, List(10) { 0.5 })
        val later = run(T0 + 5_000 + CaptureHistory.GAP_MS + 1, List(6) { 0.95 })
        val capture = CaptureHistory.latestCapture(newestFirst(earlier, later))!!
        assertEquals(6, capture.rows.size)
        assertEquals(later.first().timestampMs, capture.startMs)
        assertEquals(later.last().timestampMs, capture.endMs)
        assertFalse(capture.truncated)
    }

    @Test
    fun aPauseWithinTheGapStaysOneCapture() {
        val first = run(T0, List(4) { 0.9 }) // last row at T0 + 1 500
        val second = run(T0 + 1_500 + CaptureHistory.GAP_MS, List(4) { 0.9 })
        assertEquals(8, CaptureHistory.latestCapture(newestFirst(first, second))!!.rows.size)
    }

    @Test
    fun legacyRowsInsideARunAreSkipped() {
        val rows = run(T0, List(5) { 0.9 }) + QualityRow(T0 + 2_600, 0.0, 0.0, 0.0)
        assertEquals(5, CaptureHistory.latestCapture(newestFirst(rows))!!.rows.size)
    }

    @Test
    fun aRunThatFillsTheReadIsMarkedTruncated() {
        val rows = run(T0, List(10) { 0.9 })
        assertTrue(CaptureHistory.latestCapture(newestFirst(rows), readLimit = 10)!!.truncated)
        assertFalse(CaptureHistory.latestCapture(newestFirst(rows), readLimit = 11)!!.truncated)
        // A full read whose newest run ends before the oldest row is complete.
        val split = run(T0, List(5) { 0.5 }) + run(T0 + 100_000, List(5) { 0.9 })
        assertFalse(CaptureHistory.latestCapture(newestFirst(split), readLimit = 10)!!.truncated)
    }

    @Test
    fun summaryAveragesAndClassifiesEachRowWithTheAppsRule() {
        val rows = listOf(
            row(T0, 0.95, clipped = 0.0), // GOOD
            row(T0 + 500, 0.92, clipped = 0.02), // FAIR (clipping)
            row(T0 + 1_000, 0.70, clipped = 0.0), // FAIR
            row(T0 + 1_500, 0.40, clipped = 0.1), // POOR
        )
        val sum = CaptureHistory.summarize(CaptureHistory.latestCapture(rows.reversed())!!)
        assertEquals(4, sum.summaries)
        assertEquals(1_500, sum.durationMs)
        assertEquals((0.95 + 0.92 + 0.70 + 0.40) / 4, sum.meanUsable, 1e-9)
        assertEquals((0.0 + 0.02 + 0.0 + 0.1) / 4, sum.meanClipped, 1e-9)
        assertEquals(0.40, sum.minUsable, 1e-9)
        assertEquals(0.40, sum.latestUsable, 1e-9)
        assertEquals(0.25, sum.goodShare, 1e-9)
        assertEquals(0.50, sum.fairShare, 1e-9)
        assertEquals(0.25, sum.poorShare, 1e-9)
    }

    @Test
    fun bucketsAverageConsecutiveGroups() {
        assertEquals(listOf(0.5, 0.7), Sparkline.buckets(listOf(0.5, 0.7), 24))
        assertEquals(listOf(0.15, 0.35, 0.55), Sparkline.buckets(listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6), 3).map { Math.round(it * 100) / 100.0 })
        assertEquals(24, Sparkline.buckets(List(1000) { it / 1000.0 }, 24).size)
        assertTrue(Sparkline.buckets(emptyList(), 24).isEmpty())
    }

    @Test
    fun pointsSpanTheBoxWithHigherShareHigherUp() {
        val pts = Sparkline.points(listOf(1.0, 0.0, 0.5), width = 104f, height = 54f, pad = 2f)
        assertEquals(2f, pts[0].first, 1e-4f)
        assertEquals(102f, pts[2].first, 1e-4f)
        assertEquals(2f, pts[0].second, 1e-4f) // 100% at the top
        assertEquals(52f, pts[1].second, 1e-4f) // 0% at the bottom
        assertEquals(27f, pts[2].second, 1e-4f)
        assertEquals(52f, Sparkline.yFor(Double.NaN, 54f, 2f), 1e-4f)
    }
}
