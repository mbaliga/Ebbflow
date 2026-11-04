package ai.ebbflow.baseline.app.widget

import androidx.annotation.StringRes
import ai.ebbflow.baseline.app.R

/**
 * Text a widget shows, kept as data so the choice of words is unit-testable on the JVM.
 * The renderer resolves it against the app's string resources and the user's locale.
 */
sealed interface WText {
    /** A string resource, formatted with [args]. */
    data class Res(val key: WStr, val args: List<WArg> = emptyList()) : WText {
        constructor(key: WStr, vararg args: WArg) : this(key, args.toList())
    }

    /** A bare formatted value, such as "72%" or a time. */
    data class Value(val arg: WArg) : WText

    /** Parts shown on one line, separated by " · ". */
    data class Join(val parts: List<WText>) : WText
}

/** A value inside a [WText.Res]. Formatting (digits, clock style) is the renderer's job. */
sealed interface WArg {
    /** A 0..1 fraction shown as a percentage with [decimals] decimals. */
    data class Percent(val fraction: Double, val decimals: Int = 0) : WArg

    /** A wall-clock time: the time alone if it is today, else the date and time. */
    data class Time(val epochMs: Long) : WArg

    /** A grouped whole number. */
    data class Count(val value: Long) : WArg

    /** An elapsed duration, shown in minutes or hours and minutes. */
    data class Duration(val millis: Long) : WArg

    /** A storage size. */
    data class Bytes(val bytes: Long) : WArg

    /** Language-independent text, such as a shape glyph. */
    data class Literal(val text: String) : WArg

    /** Another resolved text, such as a status word inside a sentence. */
    data class Nested(val text: WText) : WArg
}

fun join(vararg parts: WText?): WText = WText.Join(parts.filterNotNull())

/** Every string a widget can show. Each one is a `widget_*` string resource. */
enum class WStr(@StringRes val res: Int) {
    TITLE_HEADSET(R.string.widget_title_headset),
    TITLE_QUALITY(R.string.widget_title_quality),
    TITLE_LAST_CAPTURE(R.string.widget_title_last_capture),
    TITLE_THIS_CAPTURE(R.string.widget_title_this_capture),
    TITLE_CAPTURE(R.string.widget_title_capture),
    TITLE_STORED(R.string.widget_title_stored),
    TITLE_HISTORY(R.string.widget_title_history),

    STATUS_NOT_CONNECTED(R.string.widget_status_not_connected),
    STATUS_SCANNING(R.string.widget_status_scanning),
    STATUS_ACTIVATING(R.string.widget_status_activating),
    STATUS_CONNECTING(R.string.widget_status_connecting),
    STATUS_STREAMING(R.string.widget_status_streaming),
    STATUS_STOPPED(R.string.widget_status_stopped),
    STATUS_ERROR(R.string.widget_status_error),
    STATUS_NOT_RUNNING(R.string.widget_status_not_running),

    OPEN_TO_CONNECT(R.string.widget_open_to_connect),
    ERROR_HINT(R.string.widget_error_hint),
    ERROR_READ(R.string.widget_error_read),
    LAST_KNOWN_PHASE(R.string.widget_last_known_phase),
    AS_OF(R.string.widget_as_of),
    DASH(R.string.widget_dash),
    INTEGRITY_NOTE(R.string.widget_integrity_note),

    BATTERY(R.string.widget_battery),
    BATTERY_SHORT(R.string.widget_battery_short),
    BATTERY_UNREAD(R.string.widget_battery_unread),
    BATTERY_READ(R.string.widget_battery_read),
    NOT_READ(R.string.widget_not_read),
    STARTED(R.string.widget_started),
    ENDED(R.string.widget_ended),
    LAST_HEARD(R.string.widget_last_heard),
    VALID_PACKETS(R.string.widget_valid_packets),
    INVALID_PACKETS(R.string.widget_invalid_packets),
    PACKET_ERRORS(R.string.widget_packet_errors),
    PACKETS(R.string.widget_packets),
    NO_PACKETS_YET(R.string.widget_no_packets_yet),
    NO_PACKETS_LAST(R.string.widget_no_packets_last),

    QUALITY_NONE(R.string.widget_quality_none),
    QUALITY_NONE_HINT(R.string.widget_quality_none_hint),
    QUALITY_WAITING(R.string.widget_quality_waiting),
    QUALITY_LIVE(R.string.widget_quality_live),
    QUALITY_LAST_KNOWN(R.string.widget_quality_last_known),
    LABEL_GOOD(R.string.widget_label_good),
    LABEL_FAIR(R.string.widget_label_fair),
    LABEL_POOR(R.string.widget_label_poor),
    SIGNAL_GOOD(R.string.widget_signal_good),
    SIGNAL_FAIR(R.string.widget_signal_fair),
    SIGNAL_POOR(R.string.widget_signal_poor),
    USABLE(R.string.widget_usable),
    CLIPPED(R.string.widget_clipped),
    FLATLINE(R.string.widget_flatline),
    USABLE_SHORT(R.string.widget_usable_short),
    RULE_GOOD(R.string.widget_rule_good),
    RULE_FAIR(R.string.widget_rule_fair),
    RULE_POOR(R.string.widget_rule_poor),

    NO_CAPTURES(R.string.widget_no_captures),
    NO_CAPTURES_HINT(R.string.widget_no_captures_hint),
    USABLE_AVG_SHORT(R.string.widget_usable_avg_short),
    SO_FAR(R.string.widget_so_far),
    USABLE_AVG(R.string.widget_usable_avg),
    CLIPPED_AVG(R.string.widget_clipped_avg),
    FLATLINE_AVG(R.string.widget_flatline_avg),
    LABELED(R.string.widget_labeled),
    LABELED_VALUE(R.string.widget_labeled_value),
    UPDATED(R.string.widget_updated),
    SUMMARIES(R.string.widget_summaries),
    TRUNCATED_NOTE(R.string.widget_truncated_note),

    RAW_OFF(R.string.widget_raw_off),
    RAW_ON(R.string.widget_raw_on),
    RAW_EEG(R.string.widget_raw_eeg),
    OFF(R.string.widget_off),
    ON(R.string.widget_on),
    RAW_OFF_DETAIL(R.string.widget_raw_off_detail),
    NOTHING_STORED(R.string.widget_nothing_stored),
    SUMMARIES_COUNT(R.string.widget_summaries_count),
    SIZE(R.string.widget_size),
    OLDEST(R.string.widget_oldest),
    NEWEST(R.string.widget_newest),
    NETWORK(R.string.widget_network),
    NETWORK_NONE(R.string.widget_network_none),
    NETWORK_REQUESTED(R.string.widget_network_requested),

    PAD_CONNECT(R.string.widget_pad_connect),
    PAD_LIVE(R.string.widget_pad_live),
    PAD_BLUETOOTH(R.string.widget_pad_bluetooth),
    CAPTURE_HINT(R.string.widget_capture_hint),
    TAP_TO_OPEN(R.string.widget_tap_to_open),

    HISTORY_SUBTITLE(R.string.widget_history_subtitle),
    HISTORY_SUBTITLE_LIVE(R.string.widget_history_subtitle_live),
    HISTORY_EMPTY(R.string.widget_history_empty),
    HISTORY_EMPTY_HINT(R.string.widget_history_empty_hint),
    HISTORY_CAPTION(R.string.widget_history_caption),
    HISTORY_LEGEND(R.string.widget_history_legend),
    HISTORY_SPAN(R.string.widget_history_span),
    HISTORY_ALT(R.string.widget_history_alt),
}
