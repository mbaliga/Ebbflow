package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.baseline.app.model.StreamState
import ai.ebbflow.eeg.SignalQualityLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The part of [StreamState] the widgets may show, kept so they can draw a last-known
 * value after the process ends. Deliberately leaves out the device name (a user can
 * rename a headset after themselves), the latest per-channel µV values (signal, not
 * integrity), the stored-sample count and free-text messages.
 */
data class AcquisitionSnapshot(
    val phase: StreamPhase,
    val asOfMs: Long,
    val sessionStartedMs: Long,
    val batteryPercent: Int?,
    val batteryReadMs: Long,
    val usableFraction: Double?,
    val clippedFraction: Double?,
    val flatlineFraction: Double?,
    val qualityLabel: SignalQualityLabel?,
    val qualityUpdatedMs: Long,
    val validPackets: Long,
    val invalidPackets: Long,
    val errorRatePercent: Double,
) {
    /** True once this session has produced a quality summary of its own. */
    val hasQualityThisSession: Boolean
        get() = qualityLabel != null && qualityUpdatedMs > 0 && qualityUpdatedMs >= sessionStartedMs

    companion object {
        /** Null for the in-memory default, which never came from a session. */
        fun of(state: StreamState): AcquisitionSnapshot? {
            if (state.lastUpdateMs <= 0L) return null
            return AcquisitionSnapshot(
                phase = state.phase,
                asOfMs = state.lastUpdateMs,
                sessionStartedMs = state.sessionStartedMs,
                batteryPercent = state.batteryPercent?.coerceIn(0, 100),
                batteryReadMs = state.batteryReadMs,
                usableFraction = state.usableChannelsFraction,
                clippedFraction = state.clippedSamplesFraction,
                flatlineFraction = state.flatlineChannelsFraction,
                qualityLabel = SignalQualityLabel.entries.firstOrNull { it.name == state.qualityLabel },
                qualityUpdatedMs = state.qualityUpdatedMs,
                validPackets = state.validPackets,
                invalidPackets = state.invalidPackets,
                errorRatePercent = state.errorRatePercent,
            )
        }
    }
}

/** A tiny line format for the snapshot file, so no JSON library is needed. */
object SnapshotCodec {
    private const val VERSION = "1"

    fun encode(s: AcquisitionSnapshot): String = buildString {
        fun put(key: String, value: Any?) {
            if (value != null) append(key).append('=').append(value).append('\n')
        }
        put("v", VERSION)
        put("phase", s.phase.name)
        put("asOf", s.asOfMs)
        put("sessionStarted", s.sessionStartedMs)
        put("battery", s.batteryPercent)
        put("batteryRead", s.batteryReadMs)
        put("usable", s.usableFraction)
        put("clipped", s.clippedFraction)
        put("flatline", s.flatlineFraction)
        put("label", s.qualityLabel?.name)
        put("qualityUpdated", s.qualityUpdatedMs)
        put("valid", s.validPackets)
        put("invalid", s.invalidPackets)
        put("errorRate", s.errorRatePercent)
    }

    /** Null for anything unreadable or from another format version. */
    fun decode(text: String): AcquisitionSnapshot? = runCatching {
        val map = text.lineSequence()
            .mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
            .toMap()
        if (map["v"] != VERSION) return null
        AcquisitionSnapshot(
            phase = StreamPhase.valueOf(map.getValue("phase")),
            asOfMs = map.getValue("asOf").toLong(),
            sessionStartedMs = map["sessionStarted"]?.toLong() ?: 0L,
            batteryPercent = map["battery"]?.toInt(),
            batteryReadMs = map["batteryRead"]?.toLong() ?: 0L,
            usableFraction = map["usable"]?.toDouble(),
            clippedFraction = map["clipped"]?.toDouble(),
            flatlineFraction = map["flatline"]?.toDouble(),
            qualityLabel = map["label"]?.let { name -> SignalQualityLabel.entries.firstOrNull { it.name == name } },
            qualityUpdatedMs = map["qualityUpdated"]?.toLong() ?: 0L,
            validPackets = map["valid"]?.toLong() ?: 0L,
            invalidPackets = map["invalid"]?.toLong() ?: 0L,
            errorRatePercent = map["errorRate"]?.toDouble() ?: 0.0,
        ).takeIf { it.asOfMs > 0L }
    }.getOrNull()
}

/** Whether what a widget shows is happening now or is the last thing the app saw. */
enum class Liveness {
    /** No session has ever been seen. */
    NONE,

    /** A session is running in this process now. */
    LIVE,

    /** The last session ended normally (stopped or failed). */
    LAST_KNOWN,

    /** The saved state says a session was running, but no process holds it any more. */
    INTERRUPTED,
}

data class Acquisition(val snapshot: AcquisitionSnapshot?, val liveness: Liveness)

object AcquisitionStatus {
    val ACTIVE_PHASES = setOf(
        StreamPhase.SCANNING,
        StreamPhase.ACTIVATING,
        StreamPhase.CONNECTING_RFCOMM,
        StreamPhase.STREAMING,
    )

    /** True when this process holds a running session (the service is in charge of it). */
    fun isLive(state: StreamState): Boolean = state.lastUpdateMs > 0L && state.phase in ACTIVE_PHASES

    /**
     * Combines the in-memory hub (authoritative while the service runs) with the saved
     * snapshot (all that is left after the process ends).
     */
    fun resolve(inMemory: StreamState, saved: AcquisitionSnapshot?): Acquisition {
        val memory = AcquisitionSnapshot.of(inMemory)
        if (memory != null && isLive(inMemory)) return Acquisition(memory, Liveness.LIVE)
        val newest = listOfNotNull(memory, saved).maxByOrNull { it.asOfMs }
            ?: return Acquisition(null, Liveness.NONE)
        val liveness = if (newest.phase in ACTIVE_PHASES) Liveness.INTERRUPTED else Liveness.LAST_KNOWN
        return Acquisition(newest, liveness)
    }
}

/** The stream phase as the widgets say it: always a shape and a word, never a colour. */
enum class StatusWord(val glyph: String, val word: WStr) {
    NOT_CONNECTED(Glyphs.EMPTY, WStr.STATUS_NOT_CONNECTED),
    SCANNING(Glyphs.HALF, WStr.STATUS_SCANNING),
    ACTIVATING(Glyphs.HALF, WStr.STATUS_ACTIVATING),
    CONNECTING(Glyphs.HALF, WStr.STATUS_CONNECTING),
    STREAMING(Glyphs.FILLED, WStr.STATUS_STREAMING),
    STOPPED(Glyphs.SQUARE, WStr.STATUS_STOPPED),
    ERROR(Glyphs.TRIANGLE, WStr.STATUS_ERROR),
    NOT_RUNNING(Glyphs.OPEN_TRIANGLE, WStr.STATUS_NOT_RUNNING),
    ;

    companion object {
        fun ofPhase(phase: StreamPhase): StatusWord = when (phase) {
            StreamPhase.IDLE -> NOT_CONNECTED
            StreamPhase.SCANNING -> SCANNING
            StreamPhase.ACTIVATING -> ACTIVATING
            StreamPhase.CONNECTING_RFCOMM -> CONNECTING
            StreamPhase.STREAMING -> STREAMING
            StreamPhase.STOPPED -> STOPPED
            StreamPhase.ERROR -> ERROR
        }

        fun of(acquisition: Acquisition): StatusWord {
            val snapshot = acquisition.snapshot ?: return NOT_CONNECTED
            return when (acquisition.liveness) {
                Liveness.NONE -> NOT_CONNECTED
                Liveness.INTERRUPTED -> NOT_RUNNING
                Liveness.LIVE, Liveness.LAST_KNOWN -> ofPhase(snapshot.phase)
            }
        }
    }
}

object Glyphs {
    const val FILLED = "●"
    const val HALF = "◐"
    const val EMPTY = "○"
    const val SQUARE = "■"
    const val TRIANGLE = "▲"
    const val OPEN_TRIANGLE = "△"

    /** The app's own quality label as a shape: full, half, empty. */
    fun of(label: SignalQualityLabel): String = when (label) {
        SignalQualityLabel.GOOD -> FILLED
        SignalQualityLabel.FAIR -> HALF
        SignalQualityLabel.POOR -> EMPTY
    }

    fun word(label: SignalQualityLabel): WStr = when (label) {
        SignalQualityLabel.GOOD -> WStr.LABEL_GOOD
        SignalQualityLabel.FAIR -> WStr.LABEL_FAIR
        SignalQualityLabel.POOR -> WStr.LABEL_POOR
    }

    /** The label said about the signal ("Good signal"), for where no title qualifies it. */
    fun signalWord(label: SignalQualityLabel): WStr = when (label) {
        SignalQualityLabel.GOOD -> WStr.SIGNAL_GOOD
        SignalQualityLabel.FAIR -> WStr.SIGNAL_FAIR
        SignalQualityLabel.POOR -> WStr.SIGNAL_POOR
    }
}

/**
 * When a live session's changes reach the home screen. Phase, session and battery changes
 * go out at once; a label change after [LABEL_INTERVAL_MS]; anything else (the shares,
 * packet counts) at most every [LIVE_INTERVAL_MS], which also bounds how often the
 * widgets re-read the quality history while streaming.
 */
object PublishPolicy {
    const val LIVE_INTERVAL_MS = 30_000L
    const val LABEL_INTERVAL_MS = 5_000L

    fun shouldPublish(
        published: AcquisitionSnapshot?,
        next: AcquisitionSnapshot,
        lastPublishMs: Long,
        nowMs: Long,
    ): Boolean {
        if (published == null) return true
        if (next.phase != published.phase ||
            next.sessionStartedMs != published.sessionStartedMs ||
            next.batteryReadMs != published.batteryReadMs ||
            next.batteryPercent != published.batteryPercent
        ) {
            return true
        }
        val sinceLast = nowMs - lastPublishMs
        if (sinceLast < 0) return true // the clock moved back; don't sit on the update
        if (next.qualityLabel != published.qualityLabel) return sinceLast >= LABEL_INTERVAL_MS
        if (next == published.copy(asOfMs = next.asOfMs)) return false
        return sinceLast >= LIVE_INTERVAL_MS
    }
}

/**
 * The size buckets every widget lays out for. COLLAPSED is a single tappable summary: one
 * line for `stat` and `card`, one tap target with no pads for `pads`.
 */
enum class WidgetSize { COLLAPSED, SMALL, MEDIUM, LARGE }

/**
 * The strategy's layout patterns used here. [smallHeightDp] is the height its full small
 * layout needs; below that (or narrower than [WidgetSizing.MIN_W]) it draws [collapsed].
 */
enum class WidgetTemplate(val smallHeightDp: Int, val collapsed: WidgetSize) {
    STAT(WidgetSizing.TWO_LINES_H, WidgetSize.COLLAPSED),
    CARD(WidgetSizing.TWO_LINES_H, WidgetSize.COLLAPSED),
    IMAGE(WidgetSizing.MIN_H, WidgetSize.SMALL),
    PADS(WidgetSizing.PADS_SMALL_H, WidgetSize.COLLAPSED),
}

/**
 * Size thresholds in dp, measured on the whole widget (12 dp padding on each side included).
 *
 * Calibrated to what Android 12+ launchers report for a cell grid: about (73n − 16) ×
 * (118m − 16) dp in portrait and (142n − 15) × (66m − 15) dp in landscape, so 2x1 ≈ 130 × 102,
 * 4x1 ≈ 276 × 102, 4x2 ≈ 276 × 220 and 4x3 ≈ 276 × 338 in portrait, and 2x1 ≈ 269 × 51,
 * 4x2 ≈ 553 × 117 and 4x3 ≈ 553 × 184 in landscape. Older launchers report at least
 * 70n − 30 per side. Each threshold is where its layout still fits at 1.3× font scale.
 */
object WidgetSizing {
    /** Two cells wide on any grid. */
    const val MIN_W = 110

    /** One row on the oldest grids: the smallest the provider XML allows. */
    const val MIN_H = 40

    /** The small `stat`/`card` layout: a value line over a caption line. */
    const val TWO_LINES_H = 56

    /** The small `pads` layout: a status line over one 48 dp pad. */
    const val PADS_SMALL_H = 110

    /** The medium layouts: title, value line and a row of stats or 48 dp pads. */
    const val MEDIUM_W = 250
    const val MEDIUM_H = 130

    /**
     * The large layouts. Between 4x2 (≈ 220) and 4x3 (≈ 338) in portrait, so the default 4x2
     * placement gets MEDIUM and 4x3 or taller gets LARGE; landscape 4x2 and 4x3 stay MEDIUM.
     */
    const val LARGE_W = 250
    const val LARGE_H = 280

    /**
     * The layout for a widget of [widthDp] × [heightDp]. Below the template's small size the
     * widget collapses to one tappable summary instead of squeezing its content or controls.
     */
    fun choose(template: WidgetTemplate, widthDp: Int, heightDp: Int): WidgetSize = when {
        widthDp < MIN_W || heightDp < template.smallHeightDp -> template.collapsed
        widthDp >= LARGE_W && heightDp >= LARGE_H -> WidgetSize.LARGE
        widthDp >= MEDIUM_W && heightDp >= MEDIUM_H -> WidgetSize.MEDIUM
        else -> WidgetSize.SMALL
    }

    /**
     * The sizes (dp) handed to the launcher on Android 12+, smallest first. Each one is no
     * smaller than the one before in either direction, so the launcher's pick (the closest
     * size that fits) is always the largest that fits, the same answer as [choose].
     */
    fun breakpoints(template: WidgetTemplate): List<Triple<Int, Int, WidgetSize>> = buildList {
        if (template.collapsed != WidgetSize.SMALL) add(Triple(MIN_W, MIN_H, template.collapsed))
        add(Triple(MIN_W, template.smallHeightDp, WidgetSize.SMALL))
        add(Triple(MEDIUM_W, MEDIUM_H, WidgetSize.MEDIUM))
        add(Triple(LARGE_W, LARGE_H, WidgetSize.LARGE))
    }
}

/** Formatting decisions that don't need Android. */
object WidgetFormat {
    fun percent(fraction: Double, decimals: Int, locale: Locale): String {
        val value = if (fraction.isFinite()) fraction.coerceIn(0.0, 1.0) * 100.0 else 0.0
        return String.format(locale, "%.${decimals.coerceIn(0, 3)}f%%", value)
    }

    sealed interface DurationText {
        data object UnderAMinute : DurationText
        data class Minutes(val minutes: Long) : DurationText
        data class HoursMinutes(val hours: Long, val minutes: Long) : DurationText
    }

    fun duration(millis: Long): DurationText {
        val totalMinutes = millis.coerceAtLeast(0L) / 60_000L
        return when {
            totalMinutes < 1 -> DurationText.UnderAMinute
            totalMinutes < 60 -> DurationText.Minutes(totalMinutes)
            else -> DurationText.HoursMinutes(totalMinutes / 60, totalMinutes % 60)
        }
    }

    /** True when [epochMs] falls on the same local day as [nowMs]; then the time alone is shown. */
    fun isSameDay(epochMs: Long, nowMs: Long, zone: ZoneId): Boolean =
        localDate(epochMs, zone) == localDate(nowMs, zone)

    /**
     * The next local midnight after [nowMs], when "14:02" must become "3 Nov, 14:02". One
     * minute late on purpose so an inexact alarm never lands just before the date flips.
     */
    fun nextDayBoundary(nowMs: Long, zone: ZoneId): Long =
        localDate(nowMs, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60_000L

    /**
     * How long a live widget may go without a push before the app checks whether the session
     * is still running. Pushes come at most [PublishPolicy.LIVE_INTERVAL_MS] apart while
     * streaming and each one moves this check later, so it only fires once pushes stop.
     */
    const val LIVE_CHECK_MS = 10 * 60_000L

    /**
     * When the widgets next redraw on their own. Normally just after local midnight. While a
     * session is live, also no later than [LIVE_CHECK_MS] from now: if the process dies mid-
     * stream (low memory, a crash, the Task Manager's Stop), that redraw runs in a fresh
     * process, finds no session in memory and shows "Not running" instead of "Streaming".
     */
    fun nextRefresh(nowMs: Long, live: Boolean, zone: ZoneId): Long {
        val midnight = nextDayBoundary(nowMs, zone)
        return if (live) minOf(midnight, nowMs + LIVE_CHECK_MS) else midnight
    }

    private fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}

/** PendingIntent request codes: unique per widget id and per action within it. */
object WidgetRequestCodes {
    private const val SLOTS = 8
    const val BODY = 0

    fun of(appWidgetId: Int, slot: Int): Int = appWidgetId * SLOTS + slot.coerceIn(0, SLOTS - 1)

    fun forPad(pad: Pad): Int = 1 + pad.ordinal
}
