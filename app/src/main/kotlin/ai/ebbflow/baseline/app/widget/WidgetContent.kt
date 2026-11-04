package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.widget.WArg.Count
import ai.ebbflow.baseline.app.widget.WArg.Duration
import ai.ebbflow.baseline.app.widget.WArg.Literal
import ai.ebbflow.baseline.app.widget.WArg.Nested
import ai.ebbflow.baseline.app.widget.WArg.Percent
import ai.ebbflow.baseline.app.widget.WArg.Time
import ai.ebbflow.baseline.app.widget.WText.Res
import ai.ebbflow.baseline.app.widget.WText.Value
import ai.ebbflow.eeg.SignalQualityLabel

/** The home-screen widgets, each with the strategy template it is laid out as. */
enum class WidgetKind(val template: WidgetTemplate, val readsHistory: Boolean, val readsStorage: Boolean) {
    HEADSET(WidgetTemplate.STAT, readsHistory = false, readsStorage = false),
    QUALITY(WidgetTemplate.STAT, readsHistory = false, readsStorage = false),
    LAST_CAPTURE(WidgetTemplate.CARD, readsHistory = true, readsStorage = false),
    CAPTURE(WidgetTemplate.PADS, readsHistory = false, readsStorage = false),
    STORED(WidgetTemplate.STAT, readsHistory = false, readsStorage = true),
    HISTORY(WidgetTemplate.IMAGE, readsHistory = true, readsStorage = false),
}

/** Where a tap lands. The app has one acquisition screen, so most taps land there. */
enum class WidgetDestination { CAPTURE, BLUETOOTH_SETTINGS }

/** What the app keeps on the phone, read from the database files and the manifest. */
data class StorageFacts(
    val summaryCount: Long,
    val oldestMs: Long?,
    val newestMs: Long?,
    val databaseBytes: Long,
    /** Whether raw EEG is being written. This build has no raw recorder, so it is false. */
    val rawEegStored: Boolean,
    val internetPermission: Boolean,
)

data class Stat(val name: WText, val value: WText)

/**
 * A `stat` or `card` widget. SMALL shows [glyph] + [value] and [smallCaption]; MEDIUM adds
 * [title], the as-of time and [stats]; LARGE adds [caption], [details] and [note].
 */
data class TextCard(
    val title: WText,
    val glyph: String?,
    val value: WText,
    val smallCaption: WText?,
    val caption: WText?,
    val stats: List<Stat>,
    val details: List<Stat>,
    val note: WText?,
    val asOfMs: Long?,
)

/** A pad on the capture shortcut. Every pad opens the app; none starts or stops a capture. */
enum class Pad(val label: WStr, val destination: WidgetDestination) {
    CONNECT(WStr.PAD_CONNECT, WidgetDestination.CAPTURE),
    LIVE(WStr.PAD_LIVE, WidgetDestination.CAPTURE),
    BLUETOOTH(WStr.PAD_BLUETOOTH, WidgetDestination.BLUETOOTH_SETTINGS),
}

data class PadsView(
    val title: WText,
    val glyph: String,
    val status: WText,
    val asOfMs: Long?,
    val live: Boolean,
    val hint: WText,
) {
    /**
     * The pads per size. Connecting and stopping both happen inside the app, where the user
     * sees the session: no pad starts Bluetooth, starts a capture or ends one.
     */
    fun pads(size: WidgetSize): List<Pad> {
        val open = if (live) Pad.LIVE else Pad.CONNECT
        return when (size) {
            WidgetSize.COLLAPSED -> emptyList()
            WidgetSize.SMALL -> listOf(open)
            WidgetSize.MEDIUM, WidgetSize.LARGE -> listOf(open, Pad.BLUETOOTH)
        }
    }
}

/** The `image` widget: a line the app draws itself, with alt text carrying its numbers. */
data class HistoryView(
    val title: WText,
    val subtitle: WText,
    /** Usable-channel shares, oldest first; empty when there is nothing to draw. */
    val values: List<Double>,
    val caption: WText,
    val legend: WText?,
    val span: WText?,
    val alt: WText,
    val asOfMs: Long?,
) {
    val isEmpty: Boolean get() = values.size < 2

    fun bucketCount(size: WidgetSize): Int = when (size) {
        WidgetSize.COLLAPSED, WidgetSize.SMALL -> 24
        WidgetSize.MEDIUM -> 48
        WidgetSize.LARGE -> 64
    }
}

/**
 * Turns the app's data into what each widget shows. Every widget reports acquisition
 * integrity only: no focus, attention or other person-state wording, no device name, no
 * signal values.
 */
object WidgetContent {

    fun headset(acq: Acquisition): TextCard {
        val status = StatusWord.of(acq)
        val title = Res(WStr.TITLE_HEADSET)
        val s = acq.snapshot
        if (s == null) {
            return TextCard(
                title = title,
                glyph = status.glyph,
                value = Res(status.word),
                smallCaption = Res(WStr.OPEN_TO_CONNECT),
                caption = Res(WStr.OPEN_TO_CONNECT),
                stats = emptyList(),
                details = emptyList(),
                note = Res(WStr.INTEGRITY_NOTE),
                asOfMs = null,
            )
        }
        val battery: WText = s.batteryPercent?.let { Value(Percent(it / 100.0)) } ?: Res(WStr.NOT_READ)
        // The battery is read once, at activation, and carries over into the next session
        // until that one reads it again, so it is always stamped with when it was read.
        val readMs = s.batteryReadMs.takeIf { s.batteryPercent != null && it > 0 }
        val batteryRead: WText = readMs?.let { Value(Time(it)) } ?: Res(WStr.DASH)
        val session: Stat? = when (acq.liveness) {
            Liveness.LIVE ->
                if (s.sessionStartedMs > 0) Stat(Res(WStr.STARTED), Value(Time(s.sessionStartedMs))) else null
            Liveness.LAST_KNOWN -> Stat(Res(WStr.ENDED), Value(Time(s.asOfMs)))
            Liveness.INTERRUPTED -> Stat(Res(WStr.LAST_HEARD), Value(Time(s.asOfMs)))
            Liveness.NONE -> null
        }
        val caption: WText? = when (status) {
            StatusWord.ERROR -> Res(WStr.ERROR_HINT)
            StatusWord.NOT_RUNNING -> Res(WStr.LAST_KNOWN_PHASE, Nested(Res(StatusWord.ofPhase(s.phase).word)))
            else -> null
        }
        val details = if (s.hasQualityThisSession && s.validPackets + s.invalidPackets > 0) {
            listOf(
                Stat(Res(WStr.VALID_PACKETS), Value(Count(s.validPackets))),
                Stat(Res(WStr.INVALID_PACKETS), Value(Count(s.invalidPackets))),
                Stat(Res(WStr.PACKET_ERRORS), Value(Percent(s.errorRatePercent / 100.0, decimals = 2))),
            )
        } else {
            val none = if (acq.liveness == Liveness.LIVE) WStr.NO_PACKETS_YET else WStr.NO_PACKETS_LAST
            listOf(Stat(Res(WStr.PACKETS), Res(none)))
        }
        return TextCard(
            title = title,
            glyph = status.glyph,
            value = Res(status.word),
            smallCaption = if (s.batteryPercent != null && readMs != null) {
                join(Res(WStr.BATTERY_SHORT, Percent(s.batteryPercent / 100.0)), Value(Time(readMs)))
            } else {
                join(Res(WStr.BATTERY_UNREAD), Value(Time(s.asOfMs)))
            },
            caption = caption,
            stats = listOfNotNull(
                Stat(Res(WStr.BATTERY), battery),
                Stat(Res(WStr.BATTERY_READ), batteryRead),
                session,
            ),
            details = details,
            note = Res(WStr.INTEGRITY_NOTE),
            asOfMs = s.asOfMs,
        )
    }

    /** The label rule, so the word on the widget can be checked against its numbers. */
    fun labelRules(): List<Stat> = listOf(
        Stat(
            labeled(SignalQualityLabel.GOOD),
            Res(
                WStr.RULE_GOOD,
                Percent(SignalQualityLabel.GOOD_MIN_USABLE),
                Percent(SignalQualityLabel.GOOD_MAX_CLIPPED),
            ),
        ),
        Stat(
            labeled(SignalQualityLabel.FAIR),
            Res(
                WStr.RULE_FAIR,
                Percent(SignalQualityLabel.FAIR_MIN_USABLE),
                Percent(SignalQualityLabel.FAIR_MAX_CLIPPED),
            ),
        ),
        Stat(labeled(SignalQualityLabel.POOR), Res(WStr.RULE_POOR)),
    )

    private fun labeled(label: SignalQualityLabel): WText =
        Res(WStr.LABELED, Literal(Glyphs.of(label)), Nested(Res(Glyphs.word(label))))

    fun quality(acq: Acquisition): TextCard {
        val title = Res(WStr.TITLE_QUALITY)
        val s = acq.snapshot
        val label = s?.qualityLabel
        if (s == null || label == null || s.qualityUpdatedMs <= 0) {
            return TextCard(
                title = title,
                glyph = null,
                value = Res(WStr.QUALITY_NONE),
                smallCaption = Res(WStr.QUALITY_NONE_HINT),
                caption = Res(WStr.QUALITY_NONE_HINT),
                stats = emptyList(),
                details = labelRules(),
                note = Res(WStr.INTEGRITY_NOTE),
                asOfMs = null,
            )
        }
        if (acq.liveness == Liveness.LIVE && !s.hasQualityThisSession) {
            val status = StatusWord.of(acq)
            return TextCard(
                title = title,
                glyph = status.glyph,
                value = Res(WStr.QUALITY_WAITING),
                smallCaption = Res(status.word),
                caption = Res(status.word),
                stats = emptyList(),
                details = labelRules(),
                note = Res(WStr.INTEGRITY_NOTE),
                asOfMs = s.asOfMs,
            )
        }
        val stats = listOfNotNull(
            s.usableFraction?.let { Stat(Res(WStr.USABLE), Value(Percent(it))) },
            s.clippedFraction?.let { Stat(Res(WStr.CLIPPED), Value(Percent(it, decimals = 1))) },
            s.flatlineFraction?.let { Stat(Res(WStr.FLATLINE), Value(Percent(it))) },
        )
        return TextCard(
            title = title,
            glyph = Glyphs.of(label),
            // "Good signal", not a bare "Good": the small size has no title to qualify it.
            value = Res(Glyphs.signalWord(label)),
            smallCaption = join(
                s.usableFraction?.let { Res(WStr.USABLE_SHORT, Percent(it)) },
                Value(Time(s.qualityUpdatedMs)),
            ),
            caption = if (acq.liveness == Liveness.LIVE) {
                Res(WStr.QUALITY_LIVE)
            } else {
                Res(WStr.QUALITY_LAST_KNOWN, Time(s.qualityUpdatedMs))
            },
            stats = stats,
            details = labelRules(),
            note = Res(WStr.INTEGRITY_NOTE),
            asOfMs = s.qualityUpdatedMs,
        )
    }

    /** True when [summary] is the capture the running session is writing now. */
    fun isLiveCapture(acq: Acquisition, summary: CaptureSummary): Boolean {
        val s = acq.snapshot ?: return false
        return acq.liveness == Liveness.LIVE && s.sessionStartedMs > 0 && summary.endMs >= s.sessionStartedMs
    }

    fun lastCapture(acq: Acquisition, capture: Capture?): TextCard {
        if (capture == null) {
            return TextCard(
                title = Res(WStr.TITLE_LAST_CAPTURE),
                glyph = null,
                value = Res(WStr.NO_CAPTURES),
                smallCaption = Res(WStr.NO_CAPTURES_HINT),
                caption = Res(WStr.NO_CAPTURES_HINT),
                stats = emptyList(),
                details = emptyList(),
                note = Res(WStr.INTEGRITY_NOTE),
                asOfMs = null,
            )
        }
        val sum = CaptureHistory.summarize(capture)
        val live = isLiveCapture(acq, sum)
        val end: WText = if (live) Res(WStr.SO_FAR) else Value(Time(sum.endMs))
        return TextCard(
            title = Res(if (live) WStr.TITLE_THIS_CAPTURE else WStr.TITLE_LAST_CAPTURE),
            glyph = null,
            value = Value(Duration(sum.durationMs)),
            smallCaption = join(Res(WStr.USABLE_AVG_SHORT, Percent(sum.meanUsable)), end),
            caption = WText.Join(
                listOf(
                    share(SignalQualityLabel.GOOD, sum.goodShare),
                    share(SignalQualityLabel.FAIR, sum.fairShare),
                    share(SignalQualityLabel.POOR, sum.poorShare),
                ),
            ),
            stats = listOf(
                Stat(Res(WStr.USABLE_AVG), Value(Percent(sum.meanUsable))),
                Stat(Res(WStr.CLIPPED_AVG), Value(Percent(sum.meanClipped, decimals = 1))),
                Stat(Res(WStr.FLATLINE_AVG), Value(Percent(sum.meanFlatline))),
            ),
            details = listOf(
                Stat(Res(WStr.STARTED), Value(Time(sum.startMs))),
                if (live) {
                    Stat(Res(WStr.UPDATED), Value(Time(sum.endMs)))
                } else {
                    Stat(Res(WStr.ENDED), Value(Time(sum.endMs)))
                },
                Stat(Res(WStr.SUMMARIES), Value(Count(sum.summaries.toLong()))),
            ),
            note = if (sum.truncated) {
                Res(WStr.TRUNCATED_NOTE, Count(sum.summaries.toLong()))
            } else {
                Res(WStr.INTEGRITY_NOTE)
            },
            asOfMs = sum.endMs,
        )
    }

    private fun share(label: SignalQualityLabel, fraction: Double): WText =
        Res(WStr.LABELED_VALUE, Literal(Glyphs.of(label)), Nested(Res(Glyphs.word(label))), Percent(fraction))

    fun stored(facts: StorageFacts): TextCard {
        val rawWord = Res(if (facts.rawEegStored) WStr.ON else WStr.OFF)
        val time: (Long?) -> WText = { ms -> if (ms != null && ms > 0) Value(Time(ms)) else Res(WStr.DASH) }
        return TextCard(
            title = Res(WStr.TITLE_STORED),
            glyph = if (facts.rawEegStored) Glyphs.FILLED else Glyphs.EMPTY,
            value = Res(if (facts.rawEegStored) WStr.RAW_ON else WStr.RAW_OFF),
            smallCaption = if (facts.summaryCount <= 0) {
                Res(WStr.NOTHING_STORED)
            } else {
                Res(WStr.SUMMARIES_COUNT, Count(facts.summaryCount))
            },
            caption = if (facts.rawEegStored) null else Res(WStr.RAW_OFF_DETAIL),
            stats = listOf(
                Stat(Res(WStr.RAW_EEG), rawWord),
                Stat(Res(WStr.SUMMARIES), Value(Count(facts.summaryCount.coerceAtLeast(0)))),
                Stat(Res(WStr.SIZE), Value(WArg.Bytes(facts.databaseBytes.coerceAtLeast(0)))),
            ),
            details = listOf(
                Stat(Res(WStr.OLDEST), time(facts.oldestMs)),
                Stat(Res(WStr.NEWEST), time(facts.newestMs)),
                Stat(
                    Res(WStr.NETWORK),
                    Res(if (facts.internetPermission) WStr.NETWORK_REQUESTED else WStr.NETWORK_NONE),
                ),
            ),
            note = null,
            asOfMs = null,
        )
    }

    fun captureShortcut(acq: Acquisition): PadsView {
        val status = StatusWord.of(acq)
        return PadsView(
            title = Res(WStr.TITLE_CAPTURE),
            glyph = status.glyph,
            status = Res(status.word),
            asOfMs = acq.snapshot?.asOfMs,
            live = acq.liveness == Liveness.LIVE,
            hint = Res(WStr.CAPTURE_HINT),
        )
    }

    fun history(acq: Acquisition, capture: Capture?): HistoryView {
        if (capture == null || capture.rows.size < 2) {
            return HistoryView(
                title = Res(WStr.TITLE_HISTORY),
                subtitle = Res(WStr.HISTORY_SUBTITLE),
                values = emptyList(),
                caption = Res(WStr.HISTORY_EMPTY_HINT),
                legend = null,
                span = null,
                alt = Res(WStr.HISTORY_EMPTY),
                asOfMs = null,
            )
        }
        val sum = CaptureHistory.summarize(capture)
        val live = isLiveCapture(acq, sum)
        val end: WArg = if (live) Nested(Res(WStr.SO_FAR)) else Time(sum.endMs)
        val low = Percent(sum.minUsable)
        val mean = Percent(sum.meanUsable)
        val last = Percent(sum.latestUsable)
        return HistoryView(
            title = Res(WStr.TITLE_HISTORY),
            subtitle = Res(if (live) WStr.HISTORY_SUBTITLE_LIVE else WStr.HISTORY_SUBTITLE),
            values = capture.rows.map { it.usableChannelsFraction },
            caption = Res(WStr.HISTORY_CAPTION, low, mean, last),
            legend = Res(
                WStr.HISTORY_LEGEND,
                Percent(SignalQualityLabel.GOOD_MIN_USABLE),
                Percent(SignalQualityLabel.FAIR_MIN_USABLE),
            ),
            span = Res(WStr.HISTORY_SPAN, Time(sum.startMs), end),
            alt = Res(WStr.HISTORY_ALT, Time(sum.startMs), end, low, mean, last),
            asOfMs = sum.endMs,
        )
    }
}

/** Everything one refresh reads, gathered once and shared by every widget it redraws. */
data class WidgetInputs(
    val nowMs: Long,
    val acquisition: Acquisition,
    /** The newest capture, when a widget that shows history is placed. */
    val capture: Capture?,
    /** Storage facts, when the "Stored on this phone" widget is placed. */
    val storage: StorageFacts?,
)
