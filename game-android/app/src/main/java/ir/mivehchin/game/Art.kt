package ir.mivehchin.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Palette {
    val BG_TOP = 0xFF2A6B47.toInt()
    val BG_MID = 0xFF16412D.toInt()
    val BG_BOTTOM = 0xFF081E15.toInt()
    val PANEL = 0xFF123A2E.toInt()
    val PANEL_SOFT = 0xFF1F5A45.toInt()
    val LINE = 0xFF2A6350.toInt()
    val CREAM = 0xFFFFF6E5.toInt()
    val MUTED = 0xFFA8CBBE.toInt()
    val LIME = 0xFFC6F36B.toInt()
    val LIME_DARK = 0xFF8ACB4A.toInt()
    val GREEN = 0xFF34C759.toInt()
    val CORAL = 0xFFFF7A59.toInt()
    val PURPLE = 0xFFA78BFA.toInt()
    val GOLD = 0xFFFFC94D.toInt()
    val GOLD_DARK = 0xFFD79A16.toInt()
    val DANGER = 0xFFE5455F.toInt()
    val SKY = 0xFF7DD3FC.toInt()
    val WOOD = 0xFF9C6234.toInt()
    val WOOD_LIGHT = 0xFFBE8148.toInt()
    val WOOD_DARK = 0xFF5E3818.toInt()
    val WELL = 0xFF3B2211.toInt()
    val LEAF = 0xFF3FA34D.toInt()
}

/* ---------------------------------------------------------------- produce */

/**
 * A produce written as a jewel: an outline polygon (100x100 space, light coming
 * from the top-left), one facet per outline edge and a small accent set that
 * gives the fruit its identity.
 */
private class Produce(
    val outline: FloatArray,
    val light: Int,
    val base: Int,
    val deep: Int,
    val accent: Int,
    val face: Int,
    val leaf: Int,
    val extras: FloatArray = floatArrayOf()
)

object Art {

    var regular: Typeface = Typeface.DEFAULT
    var bold: Typeface = Typeface.DEFAULT_BOLD
    var black: Typeface = Typeface.DEFAULT_BOLD

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val gemCache = HashMap<Int, Bitmap>()
    private val starCache = HashMap<Int, Bitmap>()

    private const val LX = -0.62f
    private const val LY = -0.78f

    fun init(ctx: Context) {
        try {
            regular = Typeface.createFromAsset(ctx.assets, "fonts/Vazirmatn-FD-Regular.ttf")
            bold = Typeface.createFromAsset(ctx.assets, "fonts/Vazirmatn-FD-Bold.ttf")
            black = Typeface.createFromAsset(ctx.assets, "fonts/Vazirmatn-FD-Black.ttf")
        } catch (e: Exception) {
            regular = Typeface.DEFAULT
            bold = Typeface.DEFAULT_BOLD
            black = Typeface.DEFAULT_BOLD
        }
    }

    /* ------------------------------------------------------------- text */

    fun text(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        align: Paint.Align = Paint.Align.CENTER,
        typeface: Typeface = regular,
        shadow: Boolean = false
    ) {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.typeface = typeface
        if (shadow) {
            textPaint.color = 0x77000000
            c.drawText(s, x, y + size * 0.09f, textPaint)
            textPaint.color = 0x33000000
            c.drawText(s, x, y + size * 0.18f, textPaint)
        }
        textPaint.color = color
        c.drawText(s, x, y, textPaint)
    }

    fun measure(s: String, size: Float, typeface: Typeface = regular): Float {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.textSize = size
        textPaint.typeface = typeface
        return textPaint.measureText(s)
    }

    /* ----------------------------------------------------------- shapes */

    fun panel(c: Canvas, rect: RectF, radius: Float, color: Int, stroke: Float = 0f, strokeColor: Int = 0) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawRoundRect(rect, radius, radius, shapePaint)
        if (Color.alpha(color) > 210 && rect.height() > radius * 1.6f) {
            shapePaint.color = 0x0FFFFFFF
            val top = RectF(
                rect.left + radius * 0.25f, rect.top + radius * 0.2f,
                rect.right - radius * 0.25f, rect.top + rect.height() * 0.42f
            )
            c.drawRoundRect(top, radius * 0.8f, radius * 0.8f, shapePaint)
            shapePaint.color = 0x14000000
            val bottom = RectF(
                rect.left + radius * 0.3f, rect.bottom - rect.height() * 0.06f,
                rect.right - radius * 0.3f, rect.bottom
            )
            c.drawRoundRect(bottom, radius * 0.5f, radius * 0.5f, shapePaint)
        }
        if (stroke > 0) {
            strokePaint.reset()
            strokePaint.isAntiAlias = true
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = stroke
            strokePaint.color = strokeColor
            c.drawRoundRect(rect, radius, radius, strokePaint)
        }
    }

    fun gradientPanel(c: Canvas, rect: RectF, radius: Float, top: Int, bottom: Int) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom, top, bottom, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, shapePaint)
        shapePaint.shader = null
        // glassy upper edge
        shapePaint.color = 0x2EFFFFFF
        val sheen = RectF(
            rect.left + radius * 0.3f, rect.top + radius * 0.18f,
            rect.right - radius * 0.3f, rect.top + rect.height() * 0.44f
        )
        c.drawRoundRect(sheen, radius * 0.7f, radius * 0.7f, shapePaint)
        shapePaint.color = 0x26000000
        val shade = RectF(
            rect.left + radius * 0.22f, rect.bottom - rect.height() * 0.16f,
            rect.right - radius * 0.22f, rect.bottom - radius * 0.1f
        )
        c.drawRoundRect(shade, radius * 0.6f, radius * 0.6f, shapePaint)
    }

    /** Chunky game button: gradient body, thick rim, inner gloss. */
    fun buttonSkin(c: Canvas, rect: RectF, radius: Float, top: Int, bottom: Int) {
        val rim = RectF(rect.left, rect.top, rect.right, rect.bottom + rect.height() * 0.09f)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = darken(bottom, 0.55f)
        c.drawRoundRect(rim, radius, radius, shapePaint)
        gradientPanel(c, rect, radius, top, bottom)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.4f, rect.height() * 0.035f)
        strokePaint.color = withAlpha(lighten(top, 0.35f), 0.75f)
        c.drawRoundRect(
            RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.bottom - 1f),
            radius, radius, strokePaint
        )
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawCircle(cx, cy, r, shapePaint)
    }

    fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
        if (r <= 0f || alpha <= 0f) return
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(withAlpha(color, alpha), withAlpha(color, alpha * 0.35f), 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, shapePaint)
        shapePaint.shader = null
    }

    fun progress(c: Canvas, rect: RectF, fraction: Float, fill: Int, track: Int) {
        val radius = rect.height() / 2f
        panel(c, rect, radius, track)
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped > 0f) {
            val f = RectF(rect.left, rect.top, rect.left + rect.width() * clamped, rect.bottom)
            if (f.width() < rect.height()) f.right = f.left + rect.height()
            gradientPanel(c, f, radius, lighten(fill, 0.3f), darken(fill, 0.82f))
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = 0x44FFFFFF
            c.drawRoundRect(
                RectF(f.left + radius * 0.4f, f.top + f.height() * 0.12f, f.right - radius * 0.4f, f.centerY()),
                radius * 0.5f, radius * 0.5f, shapePaint
            )
        }
    }

    /* ------------------------------------------------------- wood board */

    /** Wooden crate frame with a hole in the middle, like the reference board. */
    fun woodFrame(c: Canvas, outer: RectF, inner: RectF, radius: Float) {
        val p = Path()
        p.fillType = Path.FillType.EVEN_ODD
        p.addRoundRect(outer, radius, radius, Path.Direction.CW)
        p.addRoundRect(inner, radius * 0.72f, radius * 0.72f, Path.Direction.CW)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = LinearGradient(
            outer.left, outer.top, outer.left, outer.bottom,
            0xFFB87B44.toInt(), 0xFF6E4220.toInt(), Shader.TileMode.CLAMP
        )
        c.drawPath(p, shapePaint)
        shapePaint.shader = null

        // planks
        c.save()
        c.clipPath(p)
        val step = outer.height() / 5.5f
        var y = outer.top + step
        while (y < outer.bottom - step * 0.2f) {
            shapePaint.color = 0x22000000
            c.drawRect(outer.left, y, outer.right, y + outer.height() * 0.004f, shapePaint)
            shapePaint.color = 0x18FFFFFF
            c.drawRect(outer.left, y + outer.height() * 0.004f, outer.right, y + outer.height() * 0.008f, shapePaint)
            y += step
        }
        // grain
        shapePaint.color = 0x10000000
        for (i in 0 until 14) {
            val gy = outer.top + outer.height() * ((i * 37) % 100) / 100f
            val gx = outer.left + outer.width() * ((i * 53) % 80) / 100f
            c.drawOval(
                RectF(gx, gy, gx + outer.width() * 0.22f, gy + outer.height() * 0.012f),
                shapePaint
            )
        }
        // bevel light on top, shadow at the bottom
        shapePaint.shader = LinearGradient(
            outer.left, outer.top, outer.left, outer.top + outer.height() * 0.16f,
            0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRect(outer.left, outer.top, outer.right, outer.bottom, shapePaint)
        shapePaint.shader = LinearGradient(
            outer.left, outer.bottom - outer.height() * 0.18f, outer.left, outer.bottom,
            0x00000000, 0x44000000, Shader.TileMode.CLAMP
        )
        c.drawRect(outer.left, outer.top, outer.right, outer.bottom, shapePaint)
        shapePaint.shader = null

        // inner shadow around the hole
        shapePaint.shader = LinearGradient(
            inner.left, inner.top, inner.left, inner.top + outer.height() * 0.05f,
            0x66000000, 0x00000000, Shader.TileMode.CLAMP
        )
        c.drawRect(inner.left, inner.top, inner.right, inner.top + outer.height() * 0.05f, shapePaint)
        shapePaint.shader = null
        c.restore()

        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(2f, radius * 0.12f)
        strokePaint.color = 0x88260F02.toInt()
        c.drawRoundRect(outer, radius, radius, strokePaint)
        strokePaint.strokeWidth = Math.max(1.5f, radius * 0.06f)
        strokePaint.color = 0x55FFE0B8
        c.drawRoundRect(
            RectF(outer.left + 2f, outer.top + 2f, outer.right - 2f, outer.bottom - 2f),
            radius, radius, strokePaint
        )
    }

    /** Dark socket a gem sits in. */
    fun well(c: Canvas, rect: RectF, radius: Float) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = 0x55200F04
        c.drawRoundRect(rect, radius, radius, shapePaint)
        shapePaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.55f,
            0x55000000, 0x00000000, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, shapePaint)
        shapePaint.shader = null
        shapePaint.color = 0x12FFFFFF
        c.drawRoundRect(
            RectF(rect.left + 1.5f, rect.bottom - rect.height() * 0.14f, rect.right - 1.5f, rect.bottom - 2f),
            radius * 0.6f, radius * 0.6f, shapePaint
        )
    }

    /** Frozen cell overlay. */
    fun ice(c: Canvas, rect: RectF, strength: Float) {
        val r = rect.width() * 0.2f
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = withAlpha(0xFFB9E7F7.toInt(), 0.30f + 0.18f * strength)
        c.drawRoundRect(rect, r, r, shapePaint)
        shapePaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0x77FFFFFF, 0x228FD3F0, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, r, r, shapePaint)
        shapePaint.shader = null
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = Math.max(1.5f, rect.width() * 0.035f)
        strokePaint.color = 0xCCFFFFFF.toInt()
        val cx = rect.centerX()
        val cy = rect.centerY()
        c.drawLine(cx - rect.width() * 0.26f, cy - rect.height() * 0.3f, cx + rect.width() * 0.16f, cy + rect.height() * 0.06f, strokePaint)
        c.drawLine(cx + rect.width() * 0.16f, cy + rect.height() * 0.06f, cx + rect.width() * 0.02f, cy + rect.height() * 0.3f, strokePaint)
        c.drawLine(cx - rect.width() * 0.08f, cy - rect.height() * 0.36f, cx - rect.width() * 0.2f, cy + rect.height() * 0.12f, strokePaint)
        strokePaint.strokeWidth = Math.max(1f, rect.width() * 0.02f)
        strokePaint.color = 0x66FFFFFF
        c.drawArc(
            RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f),
            200f, 120f, false, strokePaint
        )
    }

    /* --------------------------------------------------------- backdrop */

    fun bgGarden(c: Canvas, w: Float, h: Float, t: Float) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Palette.BG_TOP, Palette.BG_MID, Palette.BG_BOTTOM),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, shapePaint)
        shapePaint.shader = null

        // sun glow
        glow(c, w * 0.22f, h * 0.08f, w * 0.75f, 0xFFFFE9A8.toInt(), 0.16f)
        // hills
        val hill = Path()
        hill.moveTo(0f, h * 0.86f)
        hill.cubicTo(w * 0.22f, h * 0.78f, w * 0.4f, h * 0.94f, w * 0.62f, h * 0.87f)
        hill.cubicTo(w * 0.78f, h * 0.82f, w * 0.9f, h * 0.93f, w, h * 0.88f)
        hill.lineTo(w, h)
        hill.lineTo(0f, h)
        hill.close()
        shapePaint.color = 0xFF0E3222.toInt()
        c.drawPath(hill, shapePaint)
        val hill2 = Path()
        hill2.moveTo(0f, h * 0.93f)
        hill2.cubicTo(w * 0.3f, h * 0.88f, w * 0.55f, h * 0.99f, w * 0.8f, h * 0.94f)
        hill2.cubicTo(w * 0.9f, h * 0.92f, w * 0.95f, h * 0.96f, w, h * 0.93f)
        hill2.lineTo(w, h)
        hill2.lineTo(0f, h)
        hill2.close()
        shapePaint.color = 0xFF092519.toInt()
        c.drawPath(hill2, shapePaint)

        // drifting leaves
        for (i in 0 until 9) {
            val seed = i * 7.13f
            val speed = 0.012f + (i % 3) * 0.004f
            val y = ((h * (0.12f + (i * 0.097f)) + t * h * speed) % (h * 1.1f)) - h * 0.05f
            val x = w * (0.08f + 0.11f * i) + sin(t * 0.6f + seed) * w * 0.03f
            val size = w * (0.028f + (i % 3) * 0.008f)
            leaf(c, x, y, size, 0xFFFFF6E5.toInt(), withAlpha(0xFFFFFFFF.toInt(), 0.10f), sin(t + seed) * 40f)
        }
        // vignette
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = RadialGradient(
            w * 0.5f, h * 0.45f, h * 0.78f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, shapePaint)
        shapePaint.shader = null
    }

    fun leaf(c: Canvas, cx: Float, cy: Float, size: Float, tint: Int, color: Int, rot: Float) {
        c.save()
        c.rotate(rot, cx, cy)
        val p = Path()
        p.moveTo(cx - size * 0.5f, cy)
        p.cubicTo(cx - size * 0.15f, cy - size * 0.55f, cx + size * 0.22f, cy - size * 0.5f, cx + size * 0.5f, cy)
        p.cubicTo(cx + size * 0.22f, cy + size * 0.5f, cx - size * 0.15f, cy + size * 0.55f, cx - size * 0.5f, cy)
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawPath(p, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, size * 0.05f)
        strokePaint.color = withAlpha(tint, 0.35f)
        c.drawLine(cx - size * 0.42f, cy, cx + size * 0.42f, cy, strokePaint)
        c.restore()
    }

    /* ----------------------------------------------------------- colour */

    fun darken(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun lighten(color: Int, amount: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun shade(color: Int, k: Float): Int {
        val r = (Color.red(color) * k).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * k).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * k).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    /* ------------------------------------------------------------ icons */

    fun sparkle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float, rot: Float = 0f) {
        if (r <= 0f || alpha <= 0f) return
        c.save()
        c.rotate(rot, cx, cy)
        val p = Path()
        p.moveTo(cx, cy - r)
        p.quadTo(cx + r * 0.16f, cy - r * 0.16f, cx + r, cy)
        p.quadTo(cx + r * 0.16f, cy + r * 0.16f, cx, cy + r)
        p.quadTo(cx - r * 0.16f, cy + r * 0.16f, cx - r, cy)
        p.quadTo(cx - r * 0.16f, cy - r * 0.16f, cx, cy - r)
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = withAlpha(color, alpha)
        c.drawPath(p, shapePaint)
        c.restore()
    }

    fun star(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean, color: Int = Palette.GOLD) {
        val key = (r.toInt() / 2) * 10 + (if (filled) 1 else 0)
        val bmp = starCache.getOrPut(key) { starBitmap(r.toInt().coerceAtLeast(12), filled, color) }
        bitmapPaint.alpha = 255
        c.drawBitmap(bmp, cx - bmp.width / 2f, cy - bmp.height / 2f, bitmapPaint)
    }

    private fun starBitmap(size: Int, filled: Boolean, color: Int): Bitmap {
        val pad = (size * 0.18f).toInt()
        val bmp = Bitmap.createBitmap(size + pad * 2, size + pad * 2, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = bmp.width / 2f
        val cy = bmp.height / 2f
        val r = size / 2f
        val p = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.46f
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        if (filled) {
            shapePaint.shader = LinearGradient(
                cx, cy - r, cx, cy + r,
                lighten(color, 0.45f), darken(color, 0.78f), Shader.TileMode.CLAMP
            )
            cv.drawPath(p, shapePaint)
            shapePaint.shader = null
            shapePaint.color = 0x55FFFFFF
            cv.save()
            cv.clipPath(p)
            cv.drawCircle(cx - r * 0.28f, cy - r * 0.3f, r * 0.42f, shapePaint)
            cv.restore()
            strokePaint.reset()
            strokePaint.isAntiAlias = true
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = Math.max(1.5f, r * 0.1f)
            strokePaint.color = darken(color, 0.55f)
            cv.drawPath(p, strokePaint)
        } else {
            shapePaint.color = 0x33000000
            cv.drawPath(p, shapePaint)
            strokePaint.reset()
            strokePaint.isAntiAlias = true
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = Math.max(1.4f, r * 0.11f)
            strokePaint.color = 0x88FFFFFF.toInt()
            cv.drawPath(p, strokePaint)
        }
        return bmp
    }

    fun coin(c: Canvas, cx: Float, cy: Float, r: Float) {
        glow(c, cx, cy, r * 1.5f, Palette.GOLD, 0.22f)
        circle(c, cx, cy, r, 0xFFB0741A.toInt())
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            0xFFFFE79A.toInt(), 0xFFE0A21C.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 0.86f, shapePaint)
        shapePaint.shader = null
        shapePaint.color = 0x66FFFFFF
        c.drawCircle(cx - r * 0.26f, cy - r * 0.3f, r * 0.24f, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, r * 0.13f)
        strokePaint.color = 0xFFB0741A.toInt()
        c.drawCircle(cx, cy, r * 0.56f, strokePaint)
        shapePaint.color = 0xFFB0741A.toInt()
        c.drawRoundRect(
            RectF(cx - r * 0.1f, cy - r * 0.3f, cx + r * 0.1f, cy + r * 0.3f),
            r * 0.08f, r * 0.08f, shapePaint
        )
    }

    fun heart(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean, color: Int = Palette.DANGER) {
        val p = Path()
        p.moveTo(cx, cy + r * 0.75f)
        p.cubicTo(cx - r * 1.4f, cy - r * 0.1f, cx - r * 0.7f, cy - r * 1.05f, cx, cy - r * 0.35f)
        p.cubicTo(cx + r * 0.7f, cy - r * 1.05f, cx + r * 1.4f, cy - r * 0.1f, cx, cy + r * 0.75f)
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        if (filled) {
            shapePaint.shader = LinearGradient(
                cx, cy - r, cx, cy + r * 0.8f,
                lighten(color, 0.28f), darken(color, 0.8f), Shader.TileMode.CLAMP
            )
            c.drawPath(p, shapePaint)
            shapePaint.shader = null
            shapePaint.color = 0x3DFFFFFF
            c.drawCircle(cx - r * 0.42f, cy - r * 0.42f, r * 0.3f, shapePaint)
        } else {
            shapePaint.color = 0x33000000
            c.drawPath(p, shapePaint)
        }
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, r * 0.18f)
        strokePaint.color = if (filled) darken(color, 0.62f) else 0x77FFFFFF
        c.drawPath(p, strokePaint)
    }

    fun lock(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(2f, r * 0.28f)
        strokePaint.color = color
        c.drawArc(RectF(cx - r * 0.55f, cy - r * 0.9f, cx + r * 0.55f, cy + r * 0.1f), 180f, 180f, false, strokePaint)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawRoundRect(RectF(cx - r * 0.8f, cy - r * 0.15f, cx + r * 0.8f, cy + r * 0.85f), r * 0.22f, r * 0.22f, shapePaint)
        shapePaint.color = 0x55000000
        c.drawCircle(cx, cy + r * 0.3f, r * 0.16f, shapePaint)
    }

    fun gear(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        repeat(8) { i ->
            c.save()
            c.rotate(i * 45f, cx, cy)
            c.drawRoundRect(
                RectF(cx - r * 0.18f, cy - r * 1.05f, cx + r * 0.18f, cy - r * 0.5f),
                r * 0.1f, r * 0.1f, shapePaint
            )
            c.restore()
        }
        c.drawCircle(cx, cy, r * 0.62f, shapePaint)
        shapePaint.color = Palette.PANEL
        c.drawCircle(cx, cy, r * 0.26f, shapePaint)
    }

    fun shuffleIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(2f, r * 0.22f)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = color
        val arcRect = RectF(cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy + r * 0.62f)
        c.drawArc(arcRect, 40f, 130f, false, strokePaint)
        c.drawArc(arcRect, 220f, 130f, false, strokePaint)
        val head = Path()
        head.moveTo(cx + r * 0.1f, cy - r * 0.95f)
        head.lineTo(cx + r * 0.72f, cy - r * 0.72f)
        head.lineTo(cx + r * 0.28f, cy - r * 0.2f)
        head.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawPath(head, shapePaint)
        val head2 = Path()
        head2.moveTo(cx - r * 0.1f, cy + r * 0.95f)
        head2.lineTo(cx - r * 0.72f, cy + r * 0.72f)
        head2.lineTo(cx - r * 0.28f, cy + r * 0.2f)
        head2.close()
        c.drawPath(head2, shapePaint)
    }

    fun chevronLeft(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = r * 0.28f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = color
        val p = Path()
        p.moveTo(cx + r * 0.35f, cy - r * 0.55f)
        p.lineTo(cx - r * 0.3f, cy)
        p.lineTo(cx + r * 0.35f, cy + r * 0.55f)
        c.drawPath(p, strokePaint)
    }

    fun close(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = r * 0.26f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = color
        c.drawLine(cx - r * 0.45f, cy - r * 0.45f, cx + r * 0.45f, cy + r * 0.45f, strokePaint)
        c.drawLine(cx + r * 0.45f, cy - r * 0.45f, cx - r * 0.45f, cy + r * 0.45f, strokePaint)
    }

    fun play(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Path()
        p.moveTo(cx - r * 0.45f, cy - r * 0.65f)
        p.lineTo(cx + r * 0.7f, cy)
        p.lineTo(cx - r * 0.45f, cy + r * 0.65f)
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawPath(p, shapePaint)
    }

    /* --------------------------------------------------------- produce */

    fun produce(c: Canvas, type: Int, cx: Float, cy: Float, size: Float, alpha: Int = 255, scale: Float = 1f) {
        if (type < 0) {
            rainbowOrb(c, cx, cy, size * scale, alpha, 0f)
            return
        }
        val px = ((size * scale) / 2f).toInt() * 2
        if (px < 8) return
        val key = type * 4096 + px.coerceAtMost(4095)
        val bmp = gemCache.getOrPut(key) { gemBitmap(type, px) }
        bitmapPaint.alpha = alpha.coerceIn(0, 255)
        c.drawBitmap(bmp, cx - bmp.width / 2f, cy - bmp.height / 2f, bitmapPaint)
    }

    private fun gemBitmap(type: Int, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val s = px / 100f
        cv.scale(s, s)
        val p = produceSpec(type)

        // contact shadow
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = RadialGradient(
            50f, 88f, 40f,
            intArrayOf(0x66000000, 0x33000000, 0x00000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawOval(RectF(12f, 76f, 88f, 100f), shapePaint)
        shapePaint.shader = null

        val body = Path()
        val pts = p.outline
        val n = pts.size / 2
        body.moveTo(pts[0], pts[1])
        for (i in 1 until n) body.lineTo(pts[i * 2], pts[i * 2 + 1])
        body.close()

        // base
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.color = p.base
        cv.drawPath(body, fillPaint)

        // facets
        cv.save()
        cv.clipPath(body)
        val cxp = 47f
        val cyp = 45f
        fillPaint.isAntiAlias = true
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x1 = pts[i * 2]
            val y1 = pts[i * 2 + 1]
            val x2 = pts[j * 2]
            val y2 = pts[j * 2 + 1]
            val mx = (x1 + x2) / 2f
            val my = (y1 + y2) / 2f
            var dx = mx - cxp
            var dy = my - cyp
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            dx /= len
            dy /= len
            val dot = dx * LX + dy * LY
            var k = 0.60f + 0.52f * (0.5f + 0.5f * dot)
            if (my > 62f) k *= 0.93f
            val f = Path()
            f.moveTo(cxp, cyp)
            f.lineTo(x1, y1)
            f.lineTo(x2, y2)
            f.close()
            fillPaint.color = shade(p.base, k)
            cv.drawPath(f, fillPaint)
        }

        // gem table (inner faceted plateau)
        val table = Path()
        for (i in 0 until n) {
            val qx = cxp + (pts[i * 2] - cxp) * 0.46f
            val qy = cyp + (pts[i * 2 + 1] - cyp) * 0.46f
            if (i == 0) table.moveTo(qx, qy) else table.lineTo(qx, qy)
        }
        table.close()
        fillPaint.color = shade(p.base, 1.16f)
        cv.drawPath(table, fillPaint)
        fillPaint.color = 0x1FFFFFFF
        cv.drawPath(table, fillPaint)

        // glossy light from the top-left, darkening to the bottom-right
        fillPaint.shader = RadialGradient(
            34f, 28f, 62f,
            intArrayOf(0x8CFFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawPath(body, fillPaint)
        fillPaint.shader = RadialGradient(
            74f, 82f, 52f,
            intArrayOf(0x00000000, 0x22000000, 0x66000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawPath(body, fillPaint)
        fillPaint.shader = null
        cv.restore()

        // rim light on the lit edge
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.2f
        strokePaint.shader = LinearGradient(
            14f, 12f, 86f, 92f,
            intArrayOf(0xFFFFFFFF.toInt(), 0x44FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawPath(body, strokePaint)
        strokePaint.shader = null

        // dark outline
        strokePaint.strokeWidth = 2f
        strokePaint.color = withAlpha(p.deep, 0.55f)
        cv.drawPath(body, strokePaint)

        // specular blob
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.shader = RadialGradient(
            36f, 30f, 13f,
            intArrayOf(0xE6FFFFFF.toInt(), 0x40FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawOval(RectF(24f, 20f, 52f, 40f), fillPaint)
        fillPaint.shader = null
        fillPaint.color = 0xCCFFFFFF.toInt()
        cv.drawCircle(31f, 26f, 2.6f, fillPaint)

        accents(cv, p)
        return bmp
    }

    private fun accents(cv: Canvas, p: Produce) {
        when (p.face) {
            0 -> { // strawberry seeds + crown
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = 0xE6FFE9A8.toInt()
                val seeds = floatArrayOf(38f, 46f, 50f, 42f, 62f, 46f, 32f, 56f, 44f, 58f, 56f, 57f, 68f, 57f, 37f, 67f, 49f, 70f, 61f, 69f, 44f, 79f)
                var i = 0
                while (i < seeds.size) {
                    cv.save()
                    cv.rotate(18f, seeds[i], seeds[i + 1])
                    cv.drawOval(
                        RectF(seeds[i] - 1.6f, seeds[i + 1] - 2.6f, seeds[i] + 1.6f, seeds[i + 1] + 2.6f),
                        fillPaint
                    )
                    cv.restore()
                    i += 2
                }
                // leaf crown
                val crown = Path()
                crown.moveTo(36f, 26f)
                crown.lineTo(30f, 14f)
                crown.lineTo(43f, 21f)
                crown.lineTo(50f, 10f)
                crown.lineTo(57f, 21f)
                crown.lineTo(70f, 14f)
                crown.lineTo(64f, 27f)
                crown.lineTo(50f, 31f)
                crown.close()
                fillPaint.color = Palette.LEAF
                cv.drawPath(crown, fillPaint)
                fillPaint.color = 0x33000000
                cv.drawPath(crown, fillPaint)
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.4f
                strokePaint.color = 0xFF2C7A38.toInt()
                cv.drawPath(crown, strokePaint)
            }
            1 -> { // orange pores + leaf
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = 0x2E8A4A00
                val dots = floatArrayOf(38f, 46f, 52f, 40f, 62f, 48f, 46f, 56f, 58f, 60f, 36f, 60f, 68f, 56f, 44f, 70f)
                var i = 0
                while (i < dots.size) {
                    cv.drawCircle(dots[i], dots[i + 1], 1.5f, fillPaint)
                    i += 2
                }
                val leafP = Path()
                leafP.moveTo(52f, 24f)
                leafP.cubicTo(60f, 12f, 76f, 14f, 80f, 20f)
                leafP.cubicTo(72f, 28f, 60f, 29f, 52f, 24f)
                leafP.close()
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafP, fillPaint)
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.2f
                strokePaint.color = 0xFF2C7A38.toInt()
                cv.drawPath(leafP, strokePaint)
            }
            2 -> { // lemon segments
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.3f
                strokePaint.color = 0x33A87C00
                val cx = 50f
                val cy = 50f
                for (a in intArrayOf(-55, -20, 20, 55)) {
                    val rad = Math.toRadians(a.toDouble())
                    cv.drawLine(
                        cx - (30 * cos(rad)).toFloat(), cy - (30 * sin(rad)).toFloat() * 0.55f,
                        cx + (30 * cos(rad)).toFloat(), cy + (30 * sin(rad)).toFloat() * 0.55f,
                        strokePaint
                    )
                }
                val leafP = Path()
                leafP.moveTo(56f, 28f)
                leafP.cubicTo(64f, 18f, 78f, 20f, 82f, 27f)
                leafP.cubicTo(72f, 33f, 62f, 33f, 56f, 28f)
                leafP.close()
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafP, fillPaint)
            }
            3 -> { // apple stem + leaf
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 3.2f
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.color = 0xFF6B4B2A.toInt()
                cv.drawLine(50f, 30f, 53f, 16f, strokePaint)
                val leafP = Path()
                leafP.moveTo(54f, 22f)
                leafP.cubicTo(62f, 12f, 76f, 15f, 79f, 21f)
                leafP.cubicTo(70f, 27f, 60f, 27f, 54f, 22f)
                leafP.close()
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafP, fillPaint)
            }
            4 -> { // grape bumps
                val balls = floatArrayOf(
                    44f, 38f, 12f, 62f, 42f, 12f, 53f, 52f, 12f,
                    36f, 54f, 11f, 67f, 58f, 11f, 50f, 66f, 11f, 40f, 72f, 10f, 62f, 72f, 10f
                )
                var i = 0
                while (i < balls.size) {
                    val cx = balls[i]
                    val cy = balls[i + 1]
                    val r = balls[i + 2]
                    fillPaint.reset()
                    fillPaint.isAntiAlias = true
                    fillPaint.shader = RadialGradient(
                        cx - r * 0.35f, cy - r * 0.4f, r * 1.25f,
                        intArrayOf(0xCCFFFFFF.toInt(), 0x22000000, 0x66000000),
                        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                    )
                    cv.drawCircle(cx, cy, r, fillPaint)
                    fillPaint.shader = null
                    i += 3
                }
            }
            5 -> { // eggplant calyx
                val calyx = Path()
                calyx.moveTo(50f, 34f)
                calyx.lineTo(36f, 26f)
                calyx.lineTo(43f, 22f)
                calyx.lineTo(40f, 12f)
                calyx.lineTo(50f, 18f)
                calyx.lineTo(60f, 12f)
                calyx.lineTo(57f, 22f)
                calyx.lineTo(64f, 26f)
                calyx.close()
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = Palette.LEAF
                cv.drawPath(calyx, fillPaint)
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.3f
                strokePaint.color = 0xFF2C7A38.toInt()
                cv.drawPath(calyx, strokePaint)
            }
            else -> { // cauliflower florets
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                val fl = floatArrayOf(38f, 40f, 13f, 58f, 44f, 13f, 48f, 30f, 12f, 32f, 56f, 12f, 66f, 56f, 12f, 50f, 58f, 15f)
                var i = 0
                while (i < fl.size) {
                    fillPaint.shader = RadialGradient(
                        fl[i] - 4f, fl[i + 1] - 4f, fl[i + 2] * 1.3f,
                        intArrayOf(0xFFFFFFFF.toInt(), 0xFFF3E9D2.toInt(), 0xFFC9B68F.toInt()),
                        floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                    )
                    cv.drawCircle(fl[i], fl[i + 1], fl[i + 2], fillPaint)
                    fillPaint.shader = null
                    i += 3
                }
                val leafL = Path()
                leafL.moveTo(36f, 74f)
                leafL.cubicTo(22f, 70f, 14f, 78f, 12f, 90f)
                leafL.cubicTo(26f, 92f, 36f, 84f, 40f, 76f)
                leafL.close()
                val leafR = Path()
                leafR.moveTo(64f, 74f)
                leafR.cubicTo(78f, 70f, 86f, 78f, 88f, 90f)
                leafR.cubicTo(74f, 92f, 64f, 84f, 60f, 76f)
                leafR.close()
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafL, fillPaint)
                fillPaint.color = 0xFF48B357.toInt()
                cv.drawPath(leafR, fillPaint)
            }
        }
    }

    fun special(c: Canvas, specialName: String, cx: Float, cy: Float, size: Float, alpha: Int = 255, phase: Float = 0f) {
        when (specialName) {
            "stripeH", "stripeV" -> {
                val r = size * 0.37f
                c.save()
                c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
                c.rotate(if (specialName == "stripeV") 90f else 0f, cx, cy)
                val off = ((phase * 2f) % 1f) * size * 0.5f
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                var x = cx - size * 0.6f + off
                while (x < cx + size * 0.6f) {
                    shapePaint.color = Color.argb((alpha * 0.5f).toInt(), 255, 255, 255)
                    c.drawRoundRect(RectF(x, cy - r, x + size * 0.1f, cy + r), size * 0.04f, size * 0.04f, shapePaint)
                    x += size * 0.2f
                }
                c.restore()
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = Math.max(1.5f, size * 0.035f)
                strokePaint.color = Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)
                c.drawCircle(cx, cy, r, strokePaint)
            }
            "bomb" -> {
                glow(c, cx, cy, size * 0.62f, 0xFFFF8A3D.toInt(), 0.35f * (alpha / 255f))
                val br = size * 0.17f
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                shapePaint.color = Color.argb(alpha, 32, 24, 44)
                c.drawCircle(cx + size * 0.22f, cy - size * 0.2f, br, shapePaint)
                shapePaint.color = Color.argb((alpha * 0.65f).toInt(), 255, 255, 255)
                c.drawCircle(cx + size * 0.16f, cy - size * 0.26f, br * 0.3f, shapePaint)
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = Math.max(1.5f, size * 0.03f)
                strokePaint.color = Color.argb(alpha, 255, 150, 60)
                c.drawCircle(cx + size * 0.22f, cy - size * 0.2f, br, strokePaint)
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeWidth = Math.max(1.5f, size * 0.035f)
                strokePaint.color = Color.argb(alpha, 255, 214, 120)
                val f = Path()
                f.moveTo(cx + size * 0.24f, cy - size * 0.36f)
                f.cubicTo(cx + size * 0.3f, cy - size * 0.48f, cx + size * 0.4f, cy - size * 0.46f, cx + size * 0.42f, cy - size * 0.38f)
                c.drawPath(f, strokePaint)
                sparkle(c, cx + size * 0.44f, cy - size * 0.36f, size * 0.07f, 0xFFFFD27A.toInt(), alpha / 255f * (0.6f + 0.4f * sin(phase * 6.28f)), phase * 90f)
            }
            "rainbow" -> rainbowOrb(c, cx, cy, size, alpha, phase)
        }
    }

    private fun rainbowOrb(c: Canvas, cx: Float, cy: Float, size: Float, alpha: Int, phase: Float) {
        val r = size * 0.38f
        glow(c, cx, cy, size * 0.6f, 0xFFFFF0B0.toInt(), 0.3f * (alpha / 255f))
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                0xFFFF5C7A.toInt(), 0xFFFFC94D.toInt(), 0xFFC6F36B.toInt(),
                0xFF34C759.toInt(), 0xFF7DD3FC.toInt(), 0xFFA78BFA.toInt(), 0xFFFF5C7A.toInt()
            ),
            null
        )
        c.drawCircle(cx, cy, r, shapePaint)
        shapePaint.shader = null
        shapePaint.color = Color.argb((alpha * 0.8f).toInt(), 255, 255, 255)
        c.drawCircle(cx, cy, r * 0.38f, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.5f, size * 0.03f)
        strokePaint.color = Color.argb((alpha * 0.75f).toInt(), 255, 255, 255)
        c.drawCircle(cx, cy, r, strokePaint)
        for (i in 0 until 3) {
            val a = phase * 6.28f + i * 2.09f
            sparkle(
                c, cx + (cos(a) * r * 1.24f), cy + (sin(a) * r * 1.24f),
                size * 0.055f, 0xFFFFFFFF.toInt(), alpha / 255f * 0.9f, phase * 200f
            )
        }
    }

    /* ------------------------------------------------------ produce spec */

    private val specs = HashMap<Int, Produce>()

    private fun produceSpec(type: Int): Produce = specs.getOrPut(type) {
        when (type) {
            0 -> Produce(
                outline = floatArrayOf(28f, 32f, 38f, 22f, 50f, 26f, 62f, 22f, 72f, 32f, 76f, 46f, 72f, 62f,
                    64f, 74f, 56f, 84f, 50f, 92f, 44f, 84f, 36f, 74f, 28f, 62f, 24f, 46f),
                light = 0xFFFF8391.toInt(), base = 0xFFE8334A.toInt(), deep = 0xFF8E0E24.toInt(),
                accent = 0xFFFFC2C9.toInt(), face = 0, leaf = Palette.LEAF
            )
            1 -> Produce(
                outline = floatArrayOf(50f, 16f, 63f, 19f, 74f, 27f, 81f, 39f, 82f, 52f, 76f, 66f, 64f, 76f,
                    52f, 81f, 40f, 78f, 29f, 71f, 21f, 59f, 19f, 45f, 25f, 30f, 37f, 21f),
                light = 0xFFFFC46B.toInt(), base = 0xFFF59316.toInt(), deep = 0xFF9A5200.toInt(),
                accent = 0xFFFFE2A8.toInt(), face = 1, leaf = Palette.LEAF
            )
            2 -> Produce(
                outline = floatArrayOf(11f, 50f, 20f, 41f, 31f, 33f, 44f, 29f, 56f, 29f, 69f, 33f, 80f, 41f,
                    89f, 50f, 80f, 59f, 69f, 67f, 56f, 71f, 44f, 71f, 31f, 67f, 20f, 59f),
                light = 0xFFFFF3A8.toInt(), base = 0xFFF5C518.toInt(), deep = 0xFFA87C00.toInt(),
                accent = 0xFFFFFBD8.toInt(), face = 2, leaf = Palette.LEAF
            )
            3 -> Produce(
                outline = floatArrayOf(50f, 30f, 58f, 23f, 68f, 21f, 77f, 27f, 82f, 39f, 81f, 55f, 74f, 69f,
                    62f, 79f, 50f, 82f, 38f, 79f, 26f, 69f, 19f, 55f, 18f, 39f, 23f, 27f, 32f, 21f, 42f, 23f),
                light = 0xFFC6F58A.toInt(), base = 0xFF74CC33.toInt(), deep = 0xFF2C7A20.toInt(),
                accent = 0xFFE6FFC2.toInt(), face = 3, leaf = Palette.LEAF
            )
            4 -> Produce(
                outline = floatArrayOf(50f, 22f, 62f, 25f, 72f, 33f, 78f, 45f, 79f, 57f, 73f, 69f, 62f, 77f,
                    50f, 81f, 38f, 77f, 27f, 69f, 21f, 57f, 22f, 45f, 28f, 33f, 38f, 25f),
                light = 0xFFD8B4FE.toInt(), base = 0xFF9A57E8.toInt(), deep = 0xFF5B21A8.toInt(),
                accent = 0xFFEBD9FF.toInt(), face = 4, leaf = Palette.LEAF
            )
            5 -> Produce(
                outline = floatArrayOf(50f, 15f, 58f, 21f, 63f, 31f, 72f, 41f, 78f, 55f, 76f, 69f, 66f, 79f,
                    54f, 85f, 42f, 84f, 30f, 76f, 22f, 64f, 21f, 50f, 27f, 37f, 37f, 27f, 44f, 19f),
                light = 0xFF9E93FF.toInt(), base = 0xFF5B4BE0.toInt(), deep = 0xFF2A1E8C.toInt(),
                accent = 0xFFCFC8FF.toInt(), face = 5, leaf = Palette.LEAF
            )
            else -> Produce(
                outline = floatArrayOf(50f, 17f, 62f, 21f, 72f, 29f, 80f, 39f, 82f, 51f, 77f, 63f, 67f, 73f,
                    55f, 79f, 43f, 79f, 31f, 73f, 21f, 63f, 17f, 51f, 19f, 39f, 27f, 29f, 37f, 21f),
                light = 0xFFFFFFFF.toInt(), base = 0xFFF2E8CF.toInt(), deep = 0xFFB49F76.toInt(),
                accent = 0xFFFFFFFF.toInt(), face = 6, leaf = Palette.LEAF
            )
        }
    }

    /** Colour used for particles and glows of each produce. */
    fun produceColor(type: Int): Int = when (type) {
        0 -> 0xFFFF5A73.toInt()
        1 -> 0xFFFFA22E.toInt()
        2 -> 0xFFFFD93D.toInt()
        3 -> 0xFF74CC33.toInt()
        4 -> 0xFF9A57E8.toInt()
        5 -> 0xFF5B4BE0.toInt()
        6 -> 0xFFF2E8CF.toInt()
        else -> 0xFFFFFFFF.toInt()
    }
}
