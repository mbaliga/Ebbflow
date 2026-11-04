package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.baseline.app.model.StreamState
import ai.ebbflow.baseline.app.widget.Fixtures.T0
import ai.ebbflow.baseline.app.widget.Fixtures.run
import ai.ebbflow.baseline.app.widget.Fixtures.snapshot
import ai.ebbflow.baseline.app.widget.Fixtures.streaming
import ai.ebbflow.baseline.app.widget.WArg.Percent
import ai.ebbflow.baseline.app.widget.WArg.Time
import ai.ebbflow.baseline.app.widget.WText.Res
import ai.ebbflow.baseline.app.widget.WText.Value
import ai.ebbflow.eeg.SignalQualityLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentTest {
    private val none = Acquisition(null, Liveness.NONE)

    /** Every string key and literal a widget would print, flattened for assertions. */
    private fun keys(text: WText?): List<String> = when (text) {
        null -> emptyList()
        is Res -> listOf(text.key.name) + text.args.flatMap(::argKeys)
        is Value -> argKeys(text.arg)
        is WText.Join -> text.parts.flatMap(::keys)
    }

    private fun argKeys(arg: WArg): List<String> = when (arg) {
        is WArg.Nested -> keys(arg.text)
        is WArg.Literal -> listOf(arg.text)
        else -> emptyList()
    }

    private fun allKeys(card: TextCard): List<String> =
        listOf(card.title, card.value, card.smallCaption, card.caption, card.note).flatMap(::keys) +
            (card.stats + card.details).flatMap { keys(it.name) + keys(it.value) }

    // ---- Headset ----

    @Test
    fun headsetFirstRunAsksToConnect() {
        val card = WidgetContent.headset(none)
        assertEquals(Res(WStr.STATUS_NOT_CONNECTED), card.value)
        assertEquals(Glyphs.EMPTY, card.glyph)
        assertEquals(Res(WStr.OPEN_TO_CONNECT), card.smallCaption)
        assertNull(card.asOfMs)
    }

    @Test
    fun headsetLiveShowsPhaseBatteryAndWhenItWasRead() {
        val card = WidgetContent.headset(Fixtures.live())
        assertEquals(Res(WStr.STATUS_STREAMING), card.value)
        assertEquals(Glyphs.FILLED, card.glyph)
        assertEquals(Stat(Res(WStr.BATTERY), Value(Percent(0.72))), card.stats[0])
        assertEquals(Stat(Res(WStr.BATTERY_READ), Value(Time(T0 + 2_000))), card.stats[1])
        assertEquals(Stat(Res(WStr.STARTED), Value(Time(T0))), card.stats[2])
        assertEquals(T0 + 600_000, card.asOfMs)
        assertEquals(WStr.VALID_PACKETS, (card.details[0].name as Res).key)
        assertEquals(Value(Percent(0.001, decimals = 2)), card.details[2].value)
    }

    @Test
    fun headsetSmallStampsTheBatteryWithWhenItWasReadNotTheLatestUpdate() {
        // Three hours into a capture: the battery was read once, at activation.
        val card = WidgetContent.headset(Fixtures.live(streaming(at = T0 + 3 * 3_600_000L)))
        assertEquals(
            WText.Join(listOf(Res(WStr.BATTERY_SHORT, Percent(0.72)), Value(Time(T0 + 2_000)))),
            card.smallCaption,
        )
    }

    @Test
    fun aNewSessionShowsTheCarriedOverBatteryWithItsOldReadTime() {
        // Scanning again the next day; the hub still holds yesterday's reading.
        val nextDay = T0 + 86_400_000L
        val scanning = streaming(at = nextDay + 5_000, sessionStarted = nextDay)
            .copy(phase = StreamPhase.SCANNING, batteryReadMs = T0 + 2_000)
        val card = WidgetContent.headset(Fixtures.live(scanning))
        assertEquals(
            WText.Join(listOf(Res(WStr.BATTERY_SHORT, Percent(0.72)), Value(Time(T0 + 2_000)))),
            card.smallCaption,
        )
        assertEquals(Stat(Res(WStr.BATTERY_READ), Value(Time(T0 + 2_000))), card.stats[1])
    }

    @Test
    fun headsetWithoutABatteryReadingSaysSo() {
        val card = WidgetContent.headset(Fixtures.live(streaming(battery = null)))
        assertTrue(keys(card.smallCaption).contains(WStr.BATTERY_UNREAD.name))
        assertEquals(Res(WStr.NOT_READ), card.stats[0].value)
        assertEquals(Res(WStr.DASH), card.stats[1].value)
    }

    @Test
    fun headsetInterruptedSaysNotRunningAndWhatWasLastKnown() {
        val acq = AcquisitionStatus.resolve(StreamState(), snapshot(streaming()))
        val card = WidgetContent.headset(acq)
        assertEquals(Res(WStr.STATUS_NOT_RUNNING), card.value)
        assertEquals(Glyphs.OPEN_TRIANGLE, card.glyph)
        assertEquals(Res(WStr.LAST_KNOWN_PHASE, WArg.Nested(Res(WStr.STATUS_STREAMING))), card.caption)
        assertEquals(Stat(Res(WStr.LAST_HEARD), Value(Time(T0 + 600_000))), card.stats[2])
    }

    @Test
    fun headsetErrorPointsIntoTheAppWithoutItsMessage() {
        val failed = streaming().copy(phase = StreamPhase.ERROR, message = "Alice's MW75 not found")
        val card = WidgetContent.headset(Acquisition(snapshot(failed), Liveness.LAST_KNOWN))
        assertEquals(Res(WStr.STATUS_ERROR), card.value)
        assertEquals(Res(WStr.ERROR_HINT), card.caption)
        assertFalse(allKeys(card).any { it.contains("Alice") })
    }

    @Test
    fun packetCountsFromAnEarlierSessionAreNotShownAsThisOnes() {
        val activating = streaming(sessionStarted = T0 + 1_000_000, qualityAt = T0 + 500_000, at = T0 + 1_000_100)
            .copy(phase = StreamPhase.ACTIVATING)
        val card = WidgetContent.headset(Fixtures.live(activating))
        assertEquals(listOf(Stat(Res(WStr.PACKETS), Res(WStr.NO_PACKETS_YET))), card.details)
    }

    // ---- Signal quality ----

    @Test
    fun qualityEmptyBeforeAnySummary() {
        val card = WidgetContent.quality(none)
        assertEquals(Res(WStr.QUALITY_NONE), card.value)
        assertNull(card.glyph)
        assertTrue(card.stats.isEmpty())
        assertEquals(3, card.details.size) // the label rule is always explained
    }

    @Test
    fun qualityWaitsForThisSessionsFirstSummary() {
        val connecting = streaming(sessionStarted = T0 + 1_000_000, qualityAt = T0 + 500_000, at = T0 + 1_000_100)
            .copy(phase = StreamPhase.CONNECTING_RFCOMM)
        val card = WidgetContent.quality(Fixtures.live(connecting))
        assertEquals(Res(WStr.QUALITY_WAITING), card.value)
        assertTrue(card.stats.isEmpty())
    }

    @Test
    fun qualityLiveUsesTheAppsLabelAsShapeAndWord() {
        for (label in SignalQualityLabel.entries) {
            val card = WidgetContent.quality(Fixtures.live(streaming(label = label)))
            assertEquals(Glyphs.of(label), card.glyph)
            // Qualified as the signal's label, even at the small size where no title shows.
            assertEquals(Res(Glyphs.signalWord(label)), card.value)
            assertTrue((card.value as Res).key.name.startsWith("SIGNAL_"))
            assertEquals(Res(WStr.QUALITY_LIVE), card.caption)
        }
        val card = WidgetContent.quality(Fixtures.live())
        assertEquals(
            listOf(
                Stat(Res(WStr.USABLE), Value(Percent(0.93))),
                Stat(Res(WStr.CLIPPED), Value(Percent(0.004, decimals = 1))),
                Stat(Res(WStr.FLATLINE), Value(Percent(0.0833))),
            ),
            card.stats,
        )
    }

    @Test
    fun qualityAfterTheSessionIsLastKnownWithItsTime() {
        val stopped = streaming().copy(phase = StreamPhase.STOPPED)
        val card = WidgetContent.quality(AcquisitionStatus.resolve(StreamState(), snapshot(stopped)))
        assertEquals(Res(WStr.QUALITY_LAST_KNOWN, Time(T0 + 600_000)), card.caption)
        assertEquals(T0 + 600_000, card.asOfMs)
    }

    @Test
    fun labelRuleUsesTheCoreThresholds() {
        val good = WidgetContent.labelRules()[0].value as Res
        assertEquals(
            listOf(Percent(SignalQualityLabel.GOOD_MIN_USABLE), Percent(SignalQualityLabel.GOOD_MAX_CLIPPED)),
            good.args,
        )
    }

    // ---- Last capture ----

    @Test
    fun lastCaptureEmptyState() {
        val card = WidgetContent.lastCapture(none, capture = null)
        assertEquals(Res(WStr.NO_CAPTURES), card.value)
        assertNull(card.asOfMs)
    }

    @Test
    fun lastCaptureEndedShowsDurationMeansAndLabelShares() {
        val rows = run(T0, List(121) { 0.9 }) // 60 s of summaries
        val capture = CaptureHistory.latestCapture(rows.reversed())!!
        val stopped = Acquisition(snapshot(streaming(at = T0 + 70_000).copy(phase = StreamPhase.STOPPED)), Liveness.LAST_KNOWN)
        val card = WidgetContent.lastCapture(stopped, capture)
        assertEquals(Res(WStr.TITLE_LAST_CAPTURE), card.title)
        assertEquals(Value(WArg.Duration(60_000)), card.value)
        assertEquals(Stat(Res(WStr.ENDED), Value(Time(T0 + 60_000))), card.details[1])
        assertEquals(Res(WStr.INTEGRITY_NOTE), card.note)
        val shares = (card.caption as WText.Join).parts.map { (it as Res).args.last() }
        assertEquals(listOf(Percent(1.0), Percent(0.0), Percent(0.0)), shares)
    }

    @Test
    fun theRunningCaptureReadsAsSoFar() {
        val rows = run(T0 + 5_000, List(20) { 0.95 })
        val capture = CaptureHistory.latestCapture(rows.reversed())!!
        val card = WidgetContent.lastCapture(Fixtures.live(streaming(sessionStarted = T0)), capture)
        assertEquals(Res(WStr.TITLE_THIS_CAPTURE), card.title)
        assertTrue(keys(card.smallCaption).contains(WStr.SO_FAR.name))
        assertEquals(WStr.UPDATED, (card.details[1].name as Res).key)
    }

    @Test
    fun aTruncatedCaptureSaysHowMuchItCovers() {
        val rows = run(T0, List(10) { 0.9 })
        val capture = CaptureHistory.latestCapture(rows.reversed(), readLimit = 10)!!
        val card = WidgetContent.lastCapture(none, capture)
        assertEquals(Res(WStr.TRUNCATED_NOTE, WArg.Count(10)), card.note)
    }

    // ---- Stored on this phone ----

    @Test
    fun storedReportsRawOffCountsAndNetwork() {
        val facts = StorageFacts(5_040, T0, T0 + 9_000, 1_234_567, rawEegStored = false, internetPermission = false)
        val card = WidgetContent.stored(facts)
        assertEquals(Glyphs.EMPTY, card.glyph)
        assertEquals(Res(WStr.RAW_OFF), card.value)
        assertEquals(Res(WStr.SUMMARIES_COUNT, WArg.Count(5_040)), card.smallCaption)
        assertEquals(Res(WStr.RAW_OFF_DETAIL), card.caption)
        assertEquals(Stat(Res(WStr.RAW_EEG), Res(WStr.OFF)), card.stats[0])
        assertEquals(Value(WArg.Bytes(1_234_567)), card.stats[2].value)
        assertEquals(Res(WStr.NETWORK_NONE), card.details[2].value)
    }

    @Test
    fun storedFirstRunAndRawOn() {
        val empty = WidgetContent.stored(StorageFacts(0, null, null, 0, rawEegStored = false, internetPermission = true))
        assertEquals(Res(WStr.NOTHING_STORED), empty.smallCaption)
        assertEquals(Res(WStr.DASH), empty.details[0].value)
        assertEquals(Res(WStr.NETWORK_REQUESTED), empty.details[2].value)
        val on = WidgetContent.stored(StorageFacts(1, T0, T0, 10, rawEegStored = true, internetPermission = false))
        assertEquals(Glyphs.FILLED, on.glyph)
        assertEquals(Res(WStr.RAW_ON), on.value)
        assertNull(on.caption)
    }

    // ---- Capture shortcut ----

    @Test
    fun everyPadOnlyOpensTheApp() {
        // No pad starts Bluetooth, starts a capture or ends one: there are only open pads.
        assertEquals(setOf(Pad.CONNECT, Pad.LIVE, Pad.BLUETOOTH), Pad.entries.toSet())
        val idle = WidgetContent.captureShortcut(none)
        val live = WidgetContent.captureShortcut(Fixtures.live())
        assertTrue(idle.pads(WidgetSize.COLLAPSED).isEmpty())
        assertTrue(live.pads(WidgetSize.COLLAPSED).isEmpty())
        assertEquals(listOf(Pad.CONNECT), idle.pads(WidgetSize.SMALL))
        assertEquals(listOf(Pad.LIVE), live.pads(WidgetSize.SMALL))
        for (size in listOf(WidgetSize.MEDIUM, WidgetSize.LARGE)) {
            assertEquals(listOf(Pad.CONNECT, Pad.BLUETOOTH), idle.pads(size))
            assertEquals(listOf(Pad.LIVE, Pad.BLUETOOTH), live.pads(size))
        }
    }

    @Test
    fun padCountGrowsWithSizeWithinTheLayoutsSlots() {
        val live = WidgetContent.captureShortcut(Fixtures.live())
        val counts = listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE).map { live.pads(it).size }
        assertEquals(listOf(1, 2, 2), counts)
    }

    // ---- Quality history ----

    @Test
    fun historyNeedsTwoSummariesToDrawALine() {
        assertTrue(WidgetContent.history(none, null).isEmpty)
        val one = CaptureHistory.latestCapture(run(T0, listOf(0.9)))
        assertTrue(WidgetContent.history(none, one).isEmpty)
        assertEquals(Res(WStr.HISTORY_EMPTY), WidgetContent.history(none, null).alt)
    }

    @Test
    fun historyAltTextCarriesTheNumbersItDraws() {
        val rows = run(T0, listOf(0.8, 0.6, 1.0, 0.9))
        val view = WidgetContent.history(none, CaptureHistory.latestCapture(rows.reversed()))
        assertFalse(view.isEmpty)
        assertEquals(listOf(0.8, 0.6, 1.0, 0.9), view.values)
        val alt = view.alt as Res
        assertEquals(WStr.HISTORY_ALT, alt.key)
        assertEquals(
            listOf(Time(T0), Time(T0 + 1_500), Percent(0.6), Percent(0.825), Percent(0.9)),
            alt.args.map { if (it is Percent) Percent(Math.round(it.fraction * 1000) / 1000.0) else it },
        )
        assertEquals(24, view.bucketCount(WidgetSize.SMALL))
        assertTrue(view.bucketCount(WidgetSize.LARGE) > view.bucketCount(WidgetSize.MEDIUM))
    }

    // ---- Wording law ----

    @Test
    fun noWidgetSaysAnythingAboutThePersonsState() {
        val cards = listOf(
            WidgetContent.headset(Fixtures.live()),
            WidgetContent.quality(Fixtures.live()),
            WidgetContent.lastCapture(Fixtures.live(), CaptureHistory.latestCapture(run(T0, List(5) { 0.9 }).reversed())),
            WidgetContent.stored(StorageFacts(1, T0, T0, 1, rawEegStored = false, internetPermission = false)),
        )
        val forbidden = listOf("FOCUS", "ATTENTION", "MOOD", "CALM", "STRESS", "RELAX")
        cards.flatMap(::allKeys).forEach { key -> forbidden.forEach { assertFalse(key, key.contains(it)) } }
    }
}
