package com.align.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.res.ResourcesCompat
import com.align.app.R
import com.align.app.data.AppCache
import com.align.app.data.AppHelper
import com.align.app.ui.barLabel
import com.align.app.ui.shortDuration
import com.align.app.ui.weekLabels
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws the widget pages with Canvas in the same look as the app (red #FF2541 gradient, white cards, the
 * custom bold font, donut / bar charts). The pages are shown by a ViewFlipper that slides + fades between them.
 */
object WidgetRenderer {
    private const val PRIMARY = 0xFFFF2541.toInt()
    private const val LIGHT = 0xFFFF5C73.toInt()
    private const val DARK = 0xFFC4112D.toInt()
    private const val PALE = 0xFFFFA3AF.toInt()
    private const val SOFT = 0xFFFFE6EA.toInt()
    private const val BLACK = 0xFF0B0B0D.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val TRACK = 0xFFECECF0.toInt()
    private const val TEXT2 = 0xFF6E6E78.toInt()
    private const val GREEN = 0xFF22C55E.toInt()
    private const val RED = 0xFFE11D2E.toInt()
    private const val WHITE90 = 0xE6FFFFFF.toInt()

    /**
     * 3 pages drawn at the widget's exact pixel size ([wPx] x [hPx]) so nothing is stretched. Sizes are
     * designed in dp and scale with the widget; wide widgets and compact (square / tall) ones get
     * different layouts.
     */
    fun render(context: Context, data: WidgetData, wPx: Int, hPx: Int): List<Bitmap> {
        val density = context.resources.displayMetrics.density
        val wDp = wPx / density
        val hDp = hPx / density
        // RemoteViews has a bitmap memory budget (about 1.5 screens for all pages), so cap each page.
        val f = min(1f, sqrt(450_000f / (wPx.toFloat() * hPx)))
        val w = (wPx * f).toInt().coerceAtLeast(2)
        val h = (hPx * f).toInt().coerceAtLeast(2)
        val wide = wDp / hDp >= 1.5f
        // One-row strips (short height) get their own dense layout, designed for ~56dp of height.
        val short = wide && hDp < 90f
        val s = when {
            short -> min(hDp / 56f, wDp / 200f).coerceIn(0.8f, 2.0f)
            wide -> min(wDp / 250f, hDp / 110f).coerceIn(0.8f, 2.2f)
            else -> (min(wDp, hDp) / 150f).coerceIn(0.6f, 2.2f)
        }
        val unit = density * f * s
        return if (wide) {
            listOf(
                Painter(context, w, h, unit).also { if (short) stripToday(it, data) else wideToday(it, data) }.bmp,
                Painter(context, w, h, unit).also { wideWeek(it, data, short) }.bmp,
                Painter(context, w, h, unit).also { wideLimits(it, data, short) }.bmp
            )
        } else {
            listOf(
                Painter(context, w, h, unit).also { compactToday(it, data) }.bmp,
                Painter(context, w, h, unit).also { compactFocus(it, data) }.bmp,
                Painter(context, w, h, unit).also { compactWeek(it, data) }.bmp
            )
        }
    }

    // ------------------------------------------------------------------ wide pages

    private fun wideToday(g: Painter, d: WidgetData) {
        val p = g.d(14f)
        g.background(gradient = true)
        if (!d.hasUsage) return g.needsAccess(white = true)

        val dia = min(g.h - 2 * p, g.w * 0.42f)
        g.text("Screen time today", p, p + g.d(11f), g.d(11f), WHITE90, bold = false)
        g.text(shortDuration(d.total), p, p + g.d(49f), g.d(34f), WHITE, maxW = g.w - dia - 3 * p)
        val y = g.h - p - g.d(17f)
        val x = g.pill("${d.limits.size} limited", p, y, g.d(10f), WHITE, PRIMARY) + g.d(6f)
        g.pill("${d.reachedCount} reached", x, y, g.d(10f), BLACK, WHITE)
        donut(g, g.w - p - dia / 2, g.h / 2f, dia, d, centerTotal = false)
    }

    /** One-row version of "Today": small label, big total, and the limit counts on the right if they fit. */
    private fun stripToday(g: Painter, d: WidgetData) {
        val p = g.d(8f)
        g.background(gradient = true)
        if (!d.hasUsage) return g.needsAccess(white = true)
        g.text("Screen time today", p + g.d(2f), p + g.d(9f), g.d(9f), WHITE90, bold = false)
        g.text(shortDuration(d.total), p + g.d(2f), g.h - p, g.d(26f), WHITE, maxW = g.w * 0.55f)
        val info = "${d.limits.size} limited · ${d.reachedCount} reached"
        if (g.measure(info, g.d(10f)) + g.d(130f) < g.w) {
            g.text(info, g.w - p - g.d(2f), g.h / 2f + g.d(4f), g.d(10f), WHITE90, right = true, bold = false)
        }
    }

    private fun wideWeek(g: Painter, d: WidgetData, short: Boolean = false) {
        val p = if (short) g.d(8f) else g.d(14f)
        g.background(gradient = false)
        if (short) {
            bars(g, d.week, p, p - g.d(12f), g.w - p, g.h - p)
            return
        }
        g.text("Last 7 days", p, p + g.d(15f), g.d(15f), BLACK)
        g.pillRight("avg ${shortDuration(d.week.sum() / d.week.size)}", g.w - p, p - g.d(1f), g.d(10f), PRIMARY, WHITE)
        bars(g, d.week, p, p + g.d(26f), g.w - p, g.h - p)
    }

    private fun wideLimits(g: Painter, d: WidgetData, short: Boolean = false) {
        val p = if (short) g.d(8f) else g.d(14f)
        g.background(gradient = false)
        if (!short) g.text("Limits", p, p + g.d(15f), g.d(15f), BLACK)
        if (d.limits.isEmpty()) {
            g.text("No limits yet", g.w / 2f, g.h / 2f, g.d(if (short) 13f else 16f), BLACK, center = true)
            if (!short) g.text("Open Align to add one", g.w / 2f, g.h / 2f + g.d(18f), g.d(11f), TEXT2, center = true, bold = false)
            return
        }
        val top = if (short) p else p + g.d(26f)
        val slots = ((g.h - p - top) / g.d(28f)).toInt().coerceIn(1, 3)
        val rowH = min((g.h - p - top) / slots, g.d(46f))
        d.limits.take(slots).forEachIndexed { i, l ->
            val y = top + i * rowH
            val icon = min(rowH * 0.7f, g.d(32f))
            g.icon(l.pkg, p, y + (rowH - icon) / 2f, icon)
            val tx = p + icon + g.d(10f)
            val right = g.w - p
            val base = y + rowH * 0.46f
            g.text(l.label, tx, base, g.d(11.5f), BLACK, maxW = right - tx - g.d(70f))
            g.text(
                "${shortDuration(l.usedMs)} / ${AppHelper.formatMinutes(l.limitMin)}",
                right, base, g.d(9.5f), if (l.reached) RED else TEXT2, right = true, bold = false
            )
            g.progress(tx, y + rowH * 0.62f, right, g.d(5.5f), l.fraction, if (l.reached) RED else null)
        }
    }

    // ------------------------------------------------------------------ compact pages (square / tall)

    private fun compactToday(g: Painter, d: WidgetData) {
        val p = g.d(14f)
        g.background(gradient = true)
        if (!d.hasUsage) return g.needsAccess(white = true)
        g.text("Today", p, p + g.d(11f), g.d(11f), WHITE90, bold = false)
        val topY = p + g.d(18f)
        val avail = g.h - topY - p - g.d(16f)
        val dia = min(g.w - 2 * p, avail).coerceAtLeast(g.d(40f))
        donut(g, g.w / 2f, topY + avail / 2f, dia, d, centerTotal = true)
        g.text(
            "${d.limits.size} limited · ${d.reachedCount} reached",
            g.w / 2f, g.h - p, g.d(10f), WHITE90, center = true, bold = false, maxW = g.w - 2 * p
        )
    }

    private fun compactFocus(g: Painter, d: WidgetData) {
        val p = g.d(14f)
        g.background(gradient = false)
        if (!d.hasUsage) return g.needsAccess(white = false)
        val focus = d.limits.firstOrNull()
        val pkg = focus?.pkg ?: d.top.firstOrNull()?.pkg
        val label = focus?.label ?: d.top.firstOrNull()?.label
        val dia = min(g.w * 0.62f, g.h - 2 * p - g.d(40f)).coerceAtLeast(g.d(40f))
        val cx = g.w / 2f
        val cy = p + dia / 2f
        g.ring(cx, cy, dia, dia * 0.09f, focus?.fraction ?: 0f, focus?.reached == true)
        if (pkg != null) g.icon(pkg, cx - dia * 0.28f, cy - dia * 0.28f, dia * 0.56f)
        if (label == null) {
            g.text("No data yet", cx, g.h - p - g.d(12f), g.d(13f), BLACK, center = true)
            return
        }
        g.text(label, cx, g.h - p - g.d(17f), g.d(13f), BLACK, center = true, maxW = g.w - 2 * p)
        val status = when {
            focus == null -> "Most used today"
            focus.reached -> "Limit reached"
            else -> "${shortDuration(focus.limitMin * 60_000L - focus.usedMs)} left"
        }
        g.text(status, cx, g.h - p - g.d(2f), g.d(11f), if (focus?.reached == true) RED else GREEN, center = true)
    }

    private fun compactWeek(g: Painter, d: WidgetData) {
        val p = g.d(14f)
        g.background(gradient = false)
        g.text("7 days", p, p + g.d(14f), g.d(14f), BLACK)
        if (g.w >= g.d(150f)) {
            g.pillRight("avg ${shortDuration(d.week.sum() / d.week.size)}", g.w - p, p - g.d(1f), g.d(9.5f), PRIMARY, WHITE)
        }
        bars(g, d.week, p, p + g.d(24f), g.w - p, g.h - p)
    }

    // ------------------------------------------------------------------ shared charts

    private fun donut(g: Painter, cx: Float, cy: Float, dia: Float, d: WidgetData, centerTotal: Boolean) {
        val stroke = dia * 0.15f
        val sum = d.top.sumOf { it.ms }.toFloat()
        val shades = intArrayOf(WHITE, 0xE0FFFFFF.toInt(), 0xB8FFFFFF.toInt(), 0x90FFFFFF.toInt(), 0x66FFFFFF.toInt())
        g.arcTrack(cx, cy, dia, stroke, 0x33FFFFFF)
        if (sum > 0f) {
            var start = -90f
            d.top.forEachIndexed { i, a ->
                val sweep = a.ms / sum * 360f
                g.arc(cx, cy, dia, stroke, start + 1.5f, (sweep - 3f).coerceAtLeast(0.5f), shades[i % shades.size])
                start += sweep
            }
        }
        val inner = dia - stroke * 2
        if (centerTotal) {
            g.text(shortDuration(d.total), cx, cy + inner * 0.08f, inner * 0.30f, WHITE, center = true, maxW = inner * 0.9f)
            g.text("screen time", cx, cy + inner * 0.30f, inner * 0.12f, WHITE90, center = true, bold = false, maxW = inner * 0.9f)
        } else {
            val top = d.top.firstOrNull()
            if (top != null && sum > 0f) {
                g.text("${(top.ms * 100f / sum).toInt()}%", cx, cy + inner * 0.06f, inner * 0.34f, WHITE, center = true, maxW = inner * 0.9f)
                g.text(top.label, cx, cy + inner * 0.30f, inner * 0.13f, WHITE90, center = true, bold = false, maxW = inner * 0.9f)
            } else {
                g.text("–", cx, cy + inner * 0.12f, inner * 0.34f, WHITE, center = true)
            }
        }
    }

    private fun bars(g: Painter, week: List<Long>, l: Float, t: Float, r: Float, b: Float) {
        val labels = weekLabels()
        val labelH = g.d(14f)
        val chartTop = t + g.d(12f)
        val chartBottom = b - labelH
        val maxV = maxOf(week.maxOrNull() ?: 0L, 60_000L).toFloat()
        val slot = (r - l) / week.size
        val barW = min(slot * 0.5f, g.d(26f))
        g.line(l, chartBottom, r, chartBottom, TRACK, g.d(1f))
        week.forEachIndexed { i, v ->
            val cx = l + slot * i + slot / 2f
            val today = i == week.lastIndex
            val bh = if (v <= 0L) g.d(2f) else maxOf(g.d(4f), (chartBottom - chartTop) * (v / maxV))
            g.barRect(cx - barW / 2f, chartBottom - bh, cx + barW / 2f, chartBottom, today, v <= 0L)
            if (v > 0L) {
                g.text(barLabel(v), cx, chartBottom - bh - g.d(3f), g.d(9f), if (today) PRIMARY else TEXT2, center = true, bold = today, maxW = slot)
            }
            g.text(labels[i], cx, b - g.d(2f), g.d(9.5f), if (today) BLACK else TEXT2, center = true, bold = today, maxW = slot)
        }
    }

    // ------------------------------------------------------------------ painter

    private class Painter(private val context: Context, val w: Int, val h: Int, private val unit: Float) {
        /** dp -> pixels of this bitmap */
        fun d(v: Float) = v * unit

        val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        private val c = Canvas(bmp)
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val font: Typeface = ResourcesCompat.getFont(context, R.font.bold_font) ?: Typeface.DEFAULT_BOLD
        private val radius = min(18f * unit, min(w, h) * 0.22f)

        fun background(gradient: Boolean) {
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            if (gradient) {
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(LIGHT, PRIMARY, DARK), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, radius, radius, p)
                p.shader = null
                // soft decorative circles, only where the card already has pixels
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                p.color = 0x16FFFFFF
                c.drawCircle(w * 0.92f, h * -0.05f, h * 0.55f, p)
                c.drawCircle(w * 0.05f, h * 1.08f, h * 0.45f, p)
                p.xfermode = null
            } else {
                p.color = WHITE
                c.drawRoundRect(rect, radius, radius, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                p.color = 0x14000000
                c.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), radius, radius, p)
                p.style = Paint.Style.FILL
            }
        }

        fun needsAccess(white: Boolean) {
            val fg = if (white) WHITE else BLACK
            text("Allow usage access", w / 2f, h / 2f, d(15f), fg, center = true, maxW = w - d(24f))
            text("Open Align to finish setup", w / 2f, h / 2f + d(17f), d(10.5f), if (white) WHITE90 else TEXT2, center = true, bold = false, maxW = w - d(24f))
        }

        fun text(
            s: String, x: Float, y: Float, size: Float, color: Int,
            center: Boolean = false, right: Boolean = false, bold: Boolean = true, maxW: Float = 0f
        ) {
            p.typeface = if (bold) font else Typeface.DEFAULT
            p.textSize = size
            p.color = color
            p.textAlign = when {
                center -> Paint.Align.CENTER
                right -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }
            var str = s
            if (maxW > 0f) {
                // shrink a little first, then cut with an ellipsis
                while (p.measureText(str) > maxW && p.textSize > size * 0.72f) p.textSize -= size * 0.04f
                while (p.measureText(str) > maxW && str.length > 2) str = str.dropLast(2).trimEnd() + "…"
            }
            c.drawText(str, x, y, p)
        }

        fun measure(s: String, size: Float): Float {
            p.typeface = font
            p.textSize = size
            return p.measureText(s)
        }

        /** Rounded pill; (x, y) is its left-top. Returns the right edge. */
        fun pill(s: String, x: Float, y: Float, size: Float, bg: Int, fg: Int): Float {
            val padX = size * 0.75f
            val hgt = size * 1.7f
            val wid = measure(s, size) + padX * 2
            p.color = bg
            c.drawRoundRect(RectF(x, y, x + wid, y + hgt), hgt / 2, hgt / 2, p)
            text(s, x + padX, y + hgt * 0.5f + size * 0.36f, size, fg)
            return x + wid
        }

        fun pillRight(s: String, right: Float, y: Float, size: Float, bg: Int, fg: Int) {
            val wid = measure(s, size) + size * 1.5f
            pill(s, right - wid, y, size, bg, fg)
        }

        fun icon(pkg: String, x: Float, y: Float, size: Float) {
            val bitmap = AppCache.icon(context, pkg)?.asAndroidBitmap() ?: return
            c.drawBitmap(bitmap, null, RectF(x, y, x + size, y + size), p)
        }

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
            p.color = color
            p.strokeWidth = width
            c.drawLine(x1, y1, x2, y2, p)
        }

        fun barRect(l: Float, t: Float, r: Float, b: Float, strong: Boolean, empty: Boolean) {
            p.shader = when {
                empty -> null
                strong -> LinearGradient(0f, t, 0f, b, LIGHT, PRIMARY, Shader.TileMode.CLAMP)
                else -> LinearGradient(0f, t, 0f, b, PALE, SOFT, Shader.TileMode.CLAMP)
            }
            p.color = TRACK
            val rad = (r - l) / 2.6f
            c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
            p.shader = null
        }

        fun progress(x: Float, y: Float, right: Float, hgt: Float, fraction: Float, solid: Int?) {
            p.color = TRACK
            c.drawRoundRect(RectF(x, y, right, y + hgt), hgt / 2, hgt / 2, p)
            val fillRight = x + (right - x) * fraction.coerceAtLeast(0.03f)
            if (solid != null) p.color = solid
            else p.shader = LinearGradient(x, 0f, right, 0f, LIGHT, PRIMARY, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(x, y, fillRight, y + hgt), hgt / 2, hgt / 2, p)
            p.shader = null
        }

        private fun oval(cx: Float, cy: Float, dia: Float, stroke: Float) =
            RectF(cx - dia / 2 + stroke / 2, cy - dia / 2 + stroke / 2, cx + dia / 2 - stroke / 2, cy + dia / 2 - stroke / 2)

        private fun strokePaint(stroke: Float, color: Int, round: Boolean = false) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke
            p.color = color
            p.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        }

        private fun fillPaint() {
            p.style = Paint.Style.FILL
            p.strokeCap = Paint.Cap.BUTT
        }

        fun arcTrack(cx: Float, cy: Float, dia: Float, stroke: Float, color: Int) {
            strokePaint(stroke, color)
            c.drawArc(oval(cx, cy, dia, stroke), 0f, 360f, false, p)
            fillPaint()
        }

        fun arc(cx: Float, cy: Float, dia: Float, stroke: Float, start: Float, sweep: Float, color: Int) {
            strokePaint(stroke, color)
            c.drawArc(oval(cx, cy, dia, stroke), start, sweep, false, p)
            fillPaint()
        }

        fun ring(cx: Float, cy: Float, dia: Float, stroke: Float, fraction: Float, reached: Boolean) {
            arcTrack(cx, cy, dia, stroke, TRACK)
            if (fraction <= 0f) return
            strokePaint(stroke, if (reached) RED else PRIMARY, round = true)
            if (!reached) {
                p.shader = LinearGradient(cx - dia / 2, cy - dia / 2, cx + dia / 2, cy + dia / 2, LIGHT, PRIMARY, Shader.TileMode.CLAMP)
            }
            c.drawArc(oval(cx, cy, dia, stroke), -90f, 360f * fraction, false, p)
            p.shader = null
            fillPaint()
        }
    }
}
