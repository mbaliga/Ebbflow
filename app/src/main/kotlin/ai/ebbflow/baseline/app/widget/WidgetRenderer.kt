package ai.ebbflow.baseline.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import ai.ebbflow.baseline.app.R
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Draws [WidgetContent] into RemoteViews. On Android 12+ every size layout is handed to the
 * launcher at once, so it can switch layouts on resize without waking the app; below that
 * the layout is picked from the widget's reported size and redrawn when it changes.
 */
object WidgetRenderer {

    fun render(context: Context, kind: WidgetKind, inputs: WidgetInputs?, options: Bundle?, appWidgetId: Int): RemoteViews {
        if (inputs == null) return renderError(context, kind, options, appWidgetId)
        val text = Resolver(context, inputs.nowMs)
        return when (kind) {
            WidgetKind.HEADSET -> textWidget(context, text, kind, WidgetContent.headset(inputs.acquisition), options, appWidgetId)
            WidgetKind.QUALITY -> textWidget(context, text, kind, WidgetContent.quality(inputs.acquisition), options, appWidgetId)
            WidgetKind.LAST_CAPTURE ->
                textWidget(context, text, kind, WidgetContent.lastCapture(inputs.acquisition, inputs.capture), options, appWidgetId)
            WidgetKind.STORED -> {
                val facts = inputs.storage ?: return renderError(context, kind, options, appWidgetId)
                textWidget(context, text, kind, WidgetContent.stored(facts), options, appWidgetId)
            }
            WidgetKind.CAPTURE -> padsWidget(context, text, WidgetContent.captureShortcut(inputs.acquisition), options, appWidgetId)
            WidgetKind.HISTORY -> historyWidget(context, text, WidgetContent.history(inputs.acquisition, inputs.capture), options, appWidgetId)
        }
    }

    /** The error state: says in words that the data couldn't be read, and still opens the app. */
    fun renderError(context: Context, kind: WidgetKind, options: Bundle?, appWidgetId: Int): RemoteViews {
        val text = Resolver(context, System.currentTimeMillis())
        val card = TextCard(
            title = WText.Res(titleOf(kind)),
            glyph = Glyphs.TRIANGLE,
            value = WText.Res(WStr.ERROR_READ),
            smallCaption = WText.Res(WStr.ERROR_HINT),
            caption = WText.Res(WStr.ERROR_HINT),
            stats = emptyList(),
            details = emptyList(),
            note = WText.Res(WStr.INTEGRITY_NOTE),
            asOfMs = null,
        )
        return sized(WidgetTemplate.STAT, options) { size -> textViews(context, text, card, size, appWidgetId) }
    }

    private fun titleOf(kind: WidgetKind): WStr = when (kind) {
        WidgetKind.HEADSET -> WStr.TITLE_HEADSET
        WidgetKind.QUALITY -> WStr.TITLE_QUALITY
        WidgetKind.LAST_CAPTURE -> WStr.TITLE_LAST_CAPTURE
        WidgetKind.CAPTURE -> WStr.TITLE_CAPTURE
        WidgetKind.STORED -> WStr.TITLE_STORED
        WidgetKind.HISTORY -> WStr.TITLE_HISTORY
    }

    private fun sized(template: WidgetTemplate, options: Bundle?, build: (WidgetSize) -> RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val map = WidgetSizing.breakpoints(template).associate { (w, h, size) ->
                SizeF(w.toFloat(), h.toFloat()) to build(size)
            }
            return RemoteViews(map)
        }
        // Portrait-style reading: the width at its narrowest and the height at its tallest.
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val size = if (width <= 0 || height <= 0) {
            WidgetSize.MEDIUM // the default 4x2 placement, until the launcher reports a size
        } else {
            WidgetSizing.choose(template, width, height)
        }
        return build(size)
    }

    // ---- stat / card -------------------------------------------------------------------

    private fun textWidget(
        context: Context,
        text: Resolver,
        kind: WidgetKind,
        card: TextCard,
        options: Bundle?,
        appWidgetId: Int,
    ): RemoteViews = sized(kind.template, options) { size -> textViews(context, text, card, size, appWidgetId) }

    private val STAT_IDS = listOf(
        Triple(R.id.w_stat1, R.id.w_stat1_name, R.id.w_stat1_value),
        Triple(R.id.w_stat2, R.id.w_stat2_name, R.id.w_stat2_value),
        Triple(R.id.w_stat3, R.id.w_stat3_name, R.id.w_stat3_value),
    )
    private val DETAIL_IDS = listOf(
        Triple(R.id.w_detail1, R.id.w_detail1_name, R.id.w_detail1_value),
        Triple(R.id.w_detail2, R.id.w_detail2_name, R.id.w_detail2_value),
        Triple(R.id.w_detail3, R.id.w_detail3_name, R.id.w_detail3_value),
    )

    private fun textViews(context: Context, text: Resolver, card: TextCard, size: WidgetSize, appWidgetId: Int): RemoteViews {
        val layout = when (size) {
            WidgetSize.COLLAPSED -> R.layout.widget_text_line
            WidgetSize.SMALL -> R.layout.widget_text_small
            WidgetSize.MEDIUM -> R.layout.widget_text_medium
            WidgetSize.LARGE -> R.layout.widget_text_large
        }
        val rv = RemoteViews(context.packageName, layout)
        val spoken = mutableListOf<String>()
        val title = text.of(card.title)
        val value = text.of(card.value)
        spoken += title
        spoken += value
        setGlyph(rv, card.glyph)
        rv.setTextViewText(R.id.w_value, value)
        when (size) {
            WidgetSize.COLLAPSED, WidgetSize.SMALL -> {
                setOptional(rv, R.id.w_caption, card.smallCaption?.let(text::of))?.let { spoken += it }
            }
            WidgetSize.MEDIUM, WidgetSize.LARGE -> {
                rv.setTextViewText(R.id.w_title, title)
                setOptional(rv, R.id.w_as_of, card.asOfMs?.let { text.asOf(it) })?.let { spoken += it }
                if (size == WidgetSize.LARGE) {
                    setOptional(rv, R.id.w_caption, card.caption?.let(text::of))?.let { spoken += it }
                }
                bindRows(rv, STAT_IDS, card.stats, text, spoken)
                if (size == WidgetSize.LARGE) {
                    bindRows(rv, DETAIL_IDS, card.details, text, spoken)
                    setOptional(rv, R.id.w_note, card.note?.let(text::of))?.let { spoken += it }
                }
            }
        }
        rv.setOnClickPendingIntent(
            android.R.id.background,
            WidgetIntents.open(context, appWidgetId, WidgetRequestCodes.BODY, WidgetDestination.CAPTURE),
        )
        rv.setContentDescription(android.R.id.background, describe(context, spoken))
        return rv
    }

    private fun bindRows(
        rv: RemoteViews,
        ids: List<Triple<Int, Int, Int>>,
        rows: List<Stat>,
        text: Resolver,
        spoken: MutableList<String>,
    ) {
        ids.forEachIndexed { i, (container, nameId, valueId) ->
            val row = rows.getOrNull(i)
            if (row == null) {
                rv.setViewVisibility(container, View.GONE)
            } else {
                val name = text.of(row.name)
                val value = text.of(row.value)
                rv.setViewVisibility(container, View.VISIBLE)
                rv.setTextViewText(nameId, name)
                rv.setTextViewText(valueId, value)
                spoken += "$name $value"
            }
        }
    }

    // ---- pads --------------------------------------------------------------------------

    private val PAD_IDS = listOf(R.id.w_pad1, R.id.w_pad2)

    private fun padsWidget(context: Context, text: Resolver, view: PadsView, options: Bundle?, appWidgetId: Int): RemoteViews =
        sized(WidgetTemplate.PADS, options) { size ->
            val layout = when (size) {
                WidgetSize.COLLAPSED -> R.layout.widget_pads_collapsed
                WidgetSize.SMALL -> R.layout.widget_pads_small
                WidgetSize.MEDIUM -> R.layout.widget_pads_medium
                WidgetSize.LARGE -> R.layout.widget_pads_large
            }
            val rv = RemoteViews(context.packageName, layout)
            val status = text.of(view.status)
            setGlyph(rv, view.glyph)
            rv.setTextViewText(R.id.w_value, status)
            val spoken = mutableListOf(text.of(view.title), status)
            if (size == WidgetSize.COLLAPSED) {
                val hint = context.getString(R.string.widget_tap_to_open)
                rv.setTextViewText(R.id.w_caption, hint)
                spoken += hint
            }
            if (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE) {
                rv.setTextViewText(R.id.w_title, text.of(view.title))
                setOptional(rv, R.id.w_as_of, view.asOfMs?.let { text.asOf(it) })
            }
            if (size == WidgetSize.LARGE) setOptional(rv, R.id.w_note, text.of(view.hint))
            val pads = view.pads(size)
            val slots = when (size) {
                WidgetSize.COLLAPSED -> 0
                WidgetSize.SMALL -> 1
                WidgetSize.MEDIUM, WidgetSize.LARGE -> 2
            }
            PAD_IDS.take(slots).forEachIndexed { i, id ->
                val pad = pads.getOrNull(i)
                if (pad == null) {
                    rv.setViewVisibility(id, View.GONE)
                } else {
                    val label = context.getString(pad.label.res)
                    rv.setViewVisibility(id, View.VISIBLE)
                    rv.setTextViewText(id, label)
                    rv.setContentDescription(id, label)
                    rv.setOnClickPendingIntent(
                        id,
                        WidgetIntents.open(context, appWidgetId, WidgetRequestCodes.forPad(pad), pad.destination),
                    )
                }
            }
            rv.setOnClickPendingIntent(
                android.R.id.background,
                WidgetIntents.open(context, appWidgetId, WidgetRequestCodes.BODY, WidgetDestination.CAPTURE),
            )
            rv.setContentDescription(android.R.id.background, describe(context, spoken))
            rv
        }

    // ---- image -------------------------------------------------------------------------

    private fun historyWidget(context: Context, text: Resolver, view: HistoryView, options: Bundle?, appWidgetId: Int): RemoteViews =
        sized(WidgetTemplate.IMAGE, options) { size ->
            val layout = when (size) {
                WidgetSize.COLLAPSED, WidgetSize.SMALL -> R.layout.widget_image_small
                WidgetSize.MEDIUM -> R.layout.widget_image_medium
                WidgetSize.LARGE -> R.layout.widget_image_large
            }
            val rv = RemoteViews(context.packageName, layout)
            val alt = text.of(view.alt)
            val spoken = mutableListOf(text.of(view.title))
            if (view.isEmpty) {
                rv.setViewVisibility(R.id.w_image, View.GONE)
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                rv.setTextViewText(R.id.w_empty, alt)
                spoken += alt
            } else {
                val bitmap = SparklineBitmap.draw(
                    context = context,
                    values = Sparkline.buckets(view.values, view.bucketCount(size)),
                    size = size,
                )
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                rv.setViewVisibility(R.id.w_image, View.VISIBLE)
                rv.setImageViewBitmap(R.id.w_image, bitmap)
                rv.setContentDescription(R.id.w_image, alt)
                spoken += alt
            }
            if (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE) {
                rv.setTextViewText(R.id.w_title, text.of(view.title))
                setOptional(rv, R.id.w_as_of, view.asOfMs?.let { text.asOf(it) })
                rv.setTextViewText(R.id.w_subtitle, text.of(view.subtitle))
                setOptional(rv, R.id.w_caption, text.of(view.caption))
            }
            if (size == WidgetSize.LARGE) {
                setOptional(rv, R.id.w_note, view.legend?.let(text::of))
                setOptional(rv, R.id.w_span, view.span?.let(text::of))
            }
            rv.setOnClickPendingIntent(
                android.R.id.background,
                WidgetIntents.open(context, appWidgetId, WidgetRequestCodes.BODY, WidgetDestination.CAPTURE),
            )
            rv.setContentDescription(android.R.id.background, describe(context, spoken))
            rv
        }

    // ---- shared ------------------------------------------------------------------------

    private fun setGlyph(rv: RemoteViews, glyph: String?) {
        if (glyph == null) {
            rv.setViewVisibility(R.id.w_glyph, View.GONE)
        } else {
            rv.setViewVisibility(R.id.w_glyph, View.VISIBLE)
            rv.setTextViewText(R.id.w_glyph, glyph)
        }
    }

    /** Shows [value] in [id], or hides the view when there is nothing to say. */
    private fun setOptional(rv: RemoteViews, id: Int, value: String?): String? {
        if (value.isNullOrBlank()) {
            rv.setViewVisibility(id, View.GONE)
            return null
        }
        rv.setViewVisibility(id, View.VISIBLE)
        rv.setTextViewText(id, value)
        return value
    }

    /** One spoken sentence for the whole widget, ending with what a tap does. */
    private fun describe(context: Context, parts: List<String>): String =
        (parts.filter { it.isNotBlank() } + context.getString(R.string.widget_tap_to_open)).joinToString(". ")

    /** Resolves [WText] against the app's resources and the user's locale and clock style. */
    private class Resolver(private val context: Context, private val nowMs: Long) {
        private val locale: Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        private val zone: ZoneId = ZoneId.systemDefault()

        fun of(text: WText): String = when (text) {
            is WText.Res -> context.getString(text.key.res, *text.args.map(::arg).toTypedArray())
            is WText.Value -> arg(text.arg)
            is WText.Join -> text.parts.map(::of).filter { it.isNotBlank() }.joinToString(" · ")
        }

        fun asOf(epochMs: Long): String = context.getString(R.string.widget_as_of, time(epochMs))

        private fun arg(arg: WArg): String = when (arg) {
            is WArg.Percent -> WidgetFormat.percent(arg.fraction, arg.decimals, locale)
            is WArg.Time -> time(arg.epochMs)
            is WArg.Count -> NumberFormat.getIntegerInstance(locale).format(arg.value)
            is WArg.Duration -> when (val d = WidgetFormat.duration(arg.millis)) {
                WidgetFormat.DurationText.UnderAMinute -> context.getString(R.string.widget_duration_under_minute)
                is WidgetFormat.DurationText.Minutes -> context.getString(R.string.widget_duration_minutes, d.minutes)
                is WidgetFormat.DurationText.HoursMinutes ->
                    context.getString(R.string.widget_duration_hours_minutes, d.hours, d.minutes)
            }
            is WArg.Bytes -> Formatter.formatShortFileSize(context, arg.bytes)
            is WArg.Literal -> arg.text
            is WArg.Nested -> of(arg.text)
        }

        private fun time(epochMs: Long): String =
            if (WidgetFormat.isSameDay(epochMs, nowMs, zone)) {
                DateFormat.getTimeFormat(context).format(Date(epochMs))
            } else {
                DateUtils.formatDateTime(
                    context,
                    epochMs,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                )
            }
    }
}
