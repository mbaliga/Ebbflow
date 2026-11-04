package ai.ebbflow.baseline.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WidgetFormatTest {

    @Test
    fun percentagesRoundAndClamp() {
        assertEquals("93%", WidgetFormat.percent(0.934, 0, Locale.US))
        assertEquals("0.4%", WidgetFormat.percent(0.004, 1, Locale.US))
        assertEquals("100%", WidgetFormat.percent(1.7, 0, Locale.US))
        assertEquals("0%", WidgetFormat.percent(-0.2, 0, Locale.US))
        assertEquals("0%", WidgetFormat.percent(Double.NaN, 0, Locale.US))
        assertEquals("0,4%", WidgetFormat.percent(0.004, 1, Locale.GERMANY))
    }

    @Test
    fun durationsAreMinutesOrHoursAndMinutes() {
        assertEquals(WidgetFormat.DurationText.UnderAMinute, WidgetFormat.duration(59_999))
        assertEquals(WidgetFormat.DurationText.UnderAMinute, WidgetFormat.duration(-5))
        assertEquals(WidgetFormat.DurationText.Minutes(42), WidgetFormat.duration(42 * 60_000L + 59_000))
        assertEquals(WidgetFormat.DurationText.HoursMinutes(1, 5), WidgetFormat.duration(65 * 60_000L))
    }

    @Test
    fun timesAreTodayOnlyOnTheSameLocalDay() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 11, 4, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        val lateYesterday = ZonedDateTime.of(2026, 11, 3, 23, 50, 0, 0, zone).toInstant().toEpochMilli()
        val earlyToday = ZonedDateTime.of(2026, 11, 4, 0, 1, 0, 0, zone).toInstant().toEpochMilli()
        assertFalse(WidgetFormat.isSameDay(lateYesterday, now, zone))
        assertTrue(WidgetFormat.isSameDay(earlyToday, now, zone))
    }

    @Test
    fun theNextRedrawIsJustAfterLocalMidnight() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 25, 0, 1, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, WidgetFormat.nextDayBoundary(now, zone))
        // Across the autumn clock change the boundary is still local midnight.
        val beforeShift = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val afterShift = ZonedDateTime.of(2026, 10, 26, 0, 1, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(afterShift, WidgetFormat.nextDayBoundary(beforeShift, zone))
    }

    @Test
    fun aLiveSessionIsCheckedWellBeforeMidnight() {
        val zone = ZoneId.of("Europe/Berlin")
        val afternoon = ZonedDateTime.of(2026, 11, 4, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val midnight = ZonedDateTime.of(2026, 11, 5, 0, 1, 0, 0, zone).toInstant().toEpochMilli()
        // Not live: only the date boundary.
        assertEquals(midnight, WidgetFormat.nextRefresh(afternoon, live = false, zone = zone))
        // Live: a check soon after pushes stop, so a dead process can't leave "Streaming" up.
        assertEquals(afternoon + WidgetFormat.LIVE_CHECK_MS, WidgetFormat.nextRefresh(afternoon, live = true, zone = zone))
        assertTrue(WidgetFormat.LIVE_CHECK_MS > PublishPolicy.LIVE_INTERVAL_MS * 4)
        // Just before midnight the date boundary comes first.
        val lateEvening = ZonedDateTime.of(2026, 11, 4, 23, 55, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(midnight, WidgetFormat.nextRefresh(lateEvening, live = true, zone = zone))
    }
}
