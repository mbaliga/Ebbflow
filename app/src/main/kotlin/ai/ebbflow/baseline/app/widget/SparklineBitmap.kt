package ai.ebbflow.baseline.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import ai.ebbflow.eeg.SignalQualityLabel

/**
 * Draws the quality-history line as an alpha mask. The layout tints it with the widget
 * accent (day and night colours from resources), so the picture follows the launcher's
 * theme without the app redrawing it. One byte per pixel keeps the update small.
 */
object SparklineBitmap {
    // Nominal drawing sizes in dp per layout; the ImageView stretches the mask to fit.
    private fun sizeDp(size: WidgetSize): Pair<Float, Float> = when (size) {
        WidgetSize.COLLAPSED, WidgetSize.SMALL -> 160f to 44f
        WidgetSize.MEDIUM -> 300f to 64f
        WidgetSize.LARGE -> 300f to 110f
    }

    fun draw(context: Context, values: List<Double>, size: WidgetSize): Bitmap {
        val scale = context.resources.displayMetrics.density.coerceIn(1f, 2f)
        val (wDp, hDp) = sizeDp(size)
        val width = (wDp * scale).toInt().coerceAtLeast(2)
        val height = (hDp * scale).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val stroke = 2f * scale
        val pad = 2.5f * stroke
        val w = width.toFloat()
        val h = height.toFloat()

        if (size == WidgetSize.LARGE) {
            // The label thresholds, told apart by dash shape (the legend names them).
            val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = scale
                alpha = 140
            }
            guide.pathEffect = DashPathEffect(floatArrayOf(8f * scale, 5f * scale), 0f)
            val good = Sparkline.yFor(SignalQualityLabel.GOOD_MIN_USABLE, h, pad)
            canvas.drawLine(pad, good, w - pad, good, guide)
            guide.pathEffect = DashPathEffect(floatArrayOf(1.5f * scale, 4f * scale), 0f)
            val fair = Sparkline.yFor(SignalQualityLabel.FAIR_MIN_USABLE, h, pad)
            canvas.drawLine(pad, fair, w - pad, fair, guide)
        }

        val points = Sparkline.points(values, w, h, pad)
        if (points.size < 2) return bitmap
        val line = Path().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        val area = Path(line).apply {
            lineTo(points.last().first, h - pad)
            lineTo(points.first().first, h - pad)
            close()
        }
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; alpha = 46 })
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                alpha = 255
            },
        )
        val (lastX, lastY) = points.last()
        canvas.drawCircle(lastX, lastY, stroke * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 255 })
        return bitmap
    }
}
