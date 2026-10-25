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
    val BG_TOP = 0xFF3E8A5C.toInt()
    val BG_MID = 0xFF1C5138.toInt()
    val BG_BOTTOM = 0xFF071C14.toInt()
    val PANEL = 0xFF123A2E.toInt()
    val PANEL_SOFT = 0xFF1F5A45.toInt()
    val PANEL_DEEP = 0xFF0A241A.toInt()
    val LINE = 0xFF2A6350.toInt()
    val CREAM = 0xFFFFF6E5.toInt()
    val CREAM_DIM = 0xFFE7D9BE.toInt()
    val MUTED = 0xFFA8CBBE.toInt()
    val LIME = 0xFFC6F36B.toInt()
    val LIME_DARK = 0xFF8ACB4A.toInt()
    val GREEN = 0xFF34C759.toInt()
    val CORAL = 0xFFFF7A59.toInt()
    val PURPLE = 0xFFA78BFA.toInt()
    val GOLD = 0xFFFFC94D.toInt()
    val GOLD_DARK = 0xFFD79A16.toInt()
    val GOLD_DEEP = 0xFF9A6A0C.toInt()
    val DANGER = 0xFFE5455F.toInt()
    val SKY = 0xFF7DD3FC.toInt()
    val WOOD = 0xFF9C6234.toInt()
    val WOOD_LIGHT = 0xFFC08A52.toInt()
    val WOOD_MID = 0xFF8A5528.toInt()
    val WOOD_DARK = 0xFF5E3818.toInt()
    val WOOD_DEEP = 0xFF2E1A08.toInt()
    val WELL = 0xFF3B2211.toInt()
    val LEAF = 0xFF3FA34D.toInt()
    val LEAF_DARK = 0xFF25702F.toInt()
    val LEAF_LIGHT = 0xFF79D06A.toInt()
}

/* --------------------------------------------------------------- produce */

private class Produce(
    val outline: FloatArray,
    val base: Int,
    val deep: Int,
    val light: Int,
    val glow: Int,
    val face: Int
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
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val gemCache = HashMap<Int, Bitmap>()
    private val starCache = HashMap<Int, Bitmap>()
    private var gardenCache: Bitmap? = null
    private var gardenW = 0
    private var gardenH = 0

    private const val LX = -0.60f
    private const val LY = -0.80f
    private const val CX = 50f
    private const val CY = 48f

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
        gardenCache = null
    }

    /* ---------------------------------------------------------------- text */

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
            textPaint.color = 0x88000000
            c.drawText(s, x, y + size * 0.1f, textPaint)
            textPaint.color = 0x3D000000
            c.drawText(s, x, y + size * 0.2f, textPaint)
        }
        textPaint.color = color
        c.drawText(s, x, y, textPaint)
    }

    /** Chunky title with a hard outline, like a game logo. */
    fun title(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        fill: Int,
        outline: Int,
        typeface: Typeface = black
    ) {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.textSize = size
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = typeface
        val o = size * 0.075f
        textPaint.color = 0x66000000
        c.drawText(s, x + o * 0.7f, y + o * 1.7f, textPaint)
        textPaint.color = outline
        val steps = 12
        for (i in 0 until steps) {
            val a = (i * 2.0 * Math.PI / steps).toFloat()
            c.drawText(s, x + cos(a) * o, y + sin(a) * o, textPaint)
        }
        textPaint.color = fill
        c.drawText(s, x, y, textPaint)
        textPaint.color = 0x4DFFFFFF
        c.drawText(s, x - o * 0.25f, y - o * 0.5f, textPaint)
        textPaint.color = fill
        c.drawText(s, x, y, textPaint)
    }

    fun measure(s: String, size: Float, typeface: Typeface = regular): Float {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.textSize = size
        textPaint.typeface = typeface
        return textPaint.measureText(s)
    }

    /* -------------------------------------------------------------- shapes */

    fun panel(c: Canvas, rect: RectF, radius: Float, color: Int, stroke: Float = 0f, strokeColor: Int = 0) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawRoundRect(rect, radius, radius, shapePaint)
        if (Color.alpha(color) > 210 && rect.height() > radius * 1.6f) {
            shapePaint.color = 0x0FFFFFFF
            c.drawRoundRect(
                RectF(
                    rect.left + radius * 0.25f, rect.top + radius * 0.2f,
                    rect.right - radius * 0.25f, rect.top + rect.height() * 0.42f
                ),
                radius * 0.8f, radius * 0.8f, shapePaint
            )
            shapePaint.color = 0x14000000
            c.drawRoundRect(
                RectF(
                    rect.left + radius * 0.3f, rect.bottom - rect.height() * 0.06f,
                    rect.right - radius * 0.3f, rect.bottom
                ),
                radius * 0.5f, radius * 0.5f, shapePaint
            )
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
        shapePaint.color = 0x1CFFFFFF
        c.drawRoundRect(
            RectF(
                rect.left + radius * 0.34f, rect.top + radius * 0.16f,
                rect.right - radius * 0.34f, rect.top + rect.height() * 0.34f
            ),
            radius * 0.7f, radius * 0.7f, shapePaint
        )
        shapePaint.color = 0x26000000
        c.drawRoundRect(
            RectF(
                rect.left + radius * 0.22f, rect.bottom - rect.height() * 0.16f,
                rect.right - radius * 0.22f, rect.bottom - radius * 0.1f
            ),
            radius * 0.6f, radius * 0.6f, shapePaint
        )
    }

    /** Glass card: deep green body, cream stitched border, gold studs. */
    fun card(c: Canvas, rect: RectF, radius: Float, accent: Int = Palette.LIME) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = 0x66000000
        c.drawRoundRect(
            RectF(rect.left + radius * 0.06f, rect.top + radius * 0.16f, rect.right + radius * 0.06f, rect.bottom + radius * 0.16f),
            radius, radius, shapePaint
        )
        gradientPanel(c, rect, radius, 0xFF1C5440.toInt(), 0xFF0C2C21.toInt())
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.6f, radius * 0.055f)
        strokePaint.color = withAlpha(Palette.CREAM, 0.28f)
        c.drawRoundRect(
            RectF(rect.left + radius * 0.18f, rect.top + radius * 0.18f, rect.right - radius * 0.18f, rect.bottom - radius * 0.18f),
            radius * 0.82f, radius * 0.82f, strokePaint
        )
        strokePaint.strokeWidth = Math.max(1.2f, radius * 0.035f)
        strokePaint.color = withAlpha(accent, 0.30f)
        c.drawRoundRect(
            RectF(rect.left + radius * 0.36f, rect.top + radius * 0.36f, rect.right - radius * 0.36f, rect.bottom - radius * 0.36f),
            radius * 0.66f, radius * 0.66f, strokePaint
        )
        // corner studs
        val s = radius * 0.26f
        val inset = radius * 0.30f
        for ((px, py) in listOf(
            Pair(rect.left + inset, rect.top + inset),
            Pair(rect.right - inset, rect.top + inset),
            Pair(rect.left + inset, rect.bottom - inset),
            Pair(rect.right - inset, rect.bottom - inset)
        )) {
            circlePaint.reset()
            circlePaint.isAntiAlias = true
            circlePaint.color = Palette.GOLD_DARK
            c.drawCircle(px, py, s, circlePaint)
            circlePaint.color = Palette.GOLD
            c.drawCircle(px, py, s * 0.72f, circlePaint)
            circlePaint.color = 0x88FFFFFF.toInt()
            c.drawCircle(px - s * 0.2f, py - s * 0.25f, s * 0.26f, circlePaint)
        }
    }

    /** Wooden plaque for headers and prices. */
    fun plaque(c: Canvas, rect: RectF, radius: Float) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = 0x66000000
        c.drawRoundRect(
            RectF(rect.left + radius * 0.08f, rect.bottom - radius * 0.1f, rect.right + radius * 0.08f, rect.bottom + radius * 0.34f),
            radius, radius, shapePaint
        )
        shapePaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            0xFFC08A52.toInt(), 0xFF7A4721.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, shapePaint)
        shapePaint.shader = null
        c.save()
        c.clipRect(rect)
        shapePaint.color = 0x1A000000
        var y = rect.top + rect.height() * 0.2f
        while (y < rect.bottom) {
            c.drawRect(rect.left, y, rect.right, y + rect.height() * 0.02f, shapePaint)
            y += rect.height() * 0.22f
        }
        shapePaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.4f,
            0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRect(rect, shapePaint)
        shapePaint.shader = null
        c.restore()
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.6f, radius * 0.09f)
        strokePaint.color = 0x99260F02.toInt()
        c.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.strokeWidth = Math.max(1f, radius * 0.05f)
        strokePaint.color = 0x66FFE0B8
        c.drawRoundRect(
            RectF(rect.left + radius * 0.12f, rect.top + radius * 0.12f, rect.right - radius * 0.12f, rect.bottom - radius * 0.12f),
            radius * 0.85f, radius * 0.85f, strokePaint
        )
    }

    /** Chunky game button with rim, gloss and a bottom lip. */
    fun buttonSkin(c: Canvas, rect: RectF, radius: Float, top: Int, bottom: Int) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = darken(bottom, 0.5f)
        c.drawRoundRect(
            RectF(rect.left, rect.top + rect.height() * 0.10f, rect.right, rect.bottom + rect.height() * 0.11f),
            radius, radius, shapePaint
        )
        gradientPanel(c, rect, radius, top, bottom)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.6f, rect.height() * 0.04f)
        strokePaint.color = withAlpha(lighten(top, 0.45f), 0.8f)
        c.drawRoundRect(
            RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.bottom - 1f),
            radius, radius, strokePaint
        )
        strokePaint.strokeWidth = Math.max(1f, rect.height() * 0.02f)
        strokePaint.color = 0x40000000
        c.drawRoundRect(rect, radius, radius, strokePaint)
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        circlePaint.reset()
        circlePaint.isAntiAlias = true
        circlePaint.color = color
        c.drawCircle(cx, cy, r, circlePaint)
    }

    /** Round gold-rimmed badge with a caller-drawn icon inside. */
    fun badge(
        c: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        top: Int,
        bottom: Int,
        rim: Int = Palette.GOLD,
        icon: (Canvas, Float, Float, Float) -> Unit
    ) {
        glow(c, cx, cy, r * 1.5f, rim, 0.18f)
        circle(c, cx, cy, r, rim)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.35f, r * 1.35f,
            intArrayOf(lighten(top, 0.25f), top, bottom),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 0.86f, shapePaint)
        shapePaint.shader = null
        shapePaint.color = 0x3DFFFFFF
        c.drawOval(
            RectF(cx - r * 0.6f, cy - r * 0.72f, cx + r * 0.1f, cy - r * 0.2f),
            shapePaint
        )
        icon(c, cx, cy, r * 0.5f)
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
            gradientPanel(c, f, radius, lighten(fill, 0.32f), darken(fill, 0.8f))
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = 0x55FFFFFF
            c.drawRoundRect(
                RectF(f.left + radius * 0.4f, f.top + f.height() * 0.14f, f.right - radius * 0.4f, f.centerY() - f.height() * 0.05f),
                radius * 0.5f, radius * 0.5f, shapePaint
            )
        }
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.4f
        strokePaint.color = 0x33FFFFFF
        c.drawRoundRect(rect, radius, radius, strokePaint)
    }

    /* ----------------------------------------------------------- the board */

    fun boardShape(g: Game, ox: Float, oy: Float, cell: Float, pad: Float): Path {
        val out = Path()
        val tmp = Path()
        for (r in 0 until g.rows) {
            for (col in 0 until g.cols) {
                if (!g.playable(r, col)) continue
                val x = ox + col * cell
                val y = oy + r * cell
                tmp.reset()
                tmp.addRoundRect(
                    RectF(x - pad, y - pad, x + cell + pad, y + cell + pad),
                    pad + cell * 0.15f, pad + cell * 0.15f, Path.Direction.CW
                )
                out.op(tmp, Path.Op.UNION)
            }
        }
        return out
    }

    fun woodShape(c: Canvas, shape: Path, bounds: RectF, radius: Float) {
        c.save()
        c.translate(bounds.width() * 0.008f, radius * 0.36f)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = 0x66000000
        c.drawPath(shape, shapePaint)
        c.restore()

        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = LinearGradient(
            bounds.left, bounds.top, bounds.left, bounds.bottom,
            0xFFCE9860.toInt(), 0xFF6B3F1C.toInt(), Shader.TileMode.CLAMP
        )
        c.drawPath(shape, shapePaint)
        shapePaint.shader = null

        c.save()
        c.clipPath(shape)
        val step = bounds.height() / 5.6f
        var y = bounds.top + step
        while (y < bounds.bottom) {
            shapePaint.color = 0x25000000
            c.drawRect(bounds.left, y, bounds.right, y + bounds.height() * 0.004f, shapePaint)
            shapePaint.color = 0x22FFE9C8
            c.drawRect(bounds.left, y + bounds.height() * 0.004f, bounds.right, y + bounds.height() * 0.009f, shapePaint)
            y += step
        }
        shapePaint.color = 0x12000000
        var i = 0
        while (i < 18) {
            val gy = bounds.top + bounds.height() * ((i * 37) % 100) / 100f
            val gx = bounds.left + bounds.width() * ((i * 53) % 80) / 100f
            c.drawOval(
                RectF(gx, gy, gx + bounds.width() * 0.2f, gy + bounds.height() * 0.011f),
                shapePaint
            )
            i++
        }
        shapePaint.shader = LinearGradient(
            bounds.left, bounds.top, bounds.left, bounds.top + bounds.height() * 0.15f,
            0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRect(bounds, shapePaint)
        shapePaint.shader = LinearGradient(
            bounds.left, bounds.bottom - bounds.height() * 0.18f, bounds.left, bounds.bottom,
            0x00000000, 0x59000000, Shader.TileMode.CLAMP
        )
        c.drawRect(bounds, shapePaint)
        shapePaint.shader = null
        c.restore()

        // carved band along the rim
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.strokeWidth = Math.max(3f, radius * 0.16f)
        strokePaint.color = 0xAA2A1204.toInt()
        c.drawPath(shape, strokePaint)
        strokePaint.strokeWidth = Math.max(1.4f, radius * 0.055f)
        strokePaint.color = 0x66FFE2BC.toInt()
        c.drawPath(shape, strokePaint)
    }

    fun woodenLegs(c: Canvas, bounds: RectF, radius: Float) {
        val legH = bounds.height() * 0.05f
        val legW = bounds.width() * 0.06f
        for (lx in listOf(bounds.left + bounds.width() * 0.16f, bounds.right - bounds.width() * 0.16f)) {
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = 0xFF4A2A10.toInt()
            c.drawRoundRect(
                RectF(lx - legW / 2f, bounds.bottom - legH * 0.4f, lx + legW / 2f, bounds.bottom + legH),
                legW * 0.3f, legW * 0.3f, shapePaint
            )
        }
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(2f, radius * 0.06f)
        strokePaint.color = 0x551E0D02
        c.drawLine(bounds.left + radius * 0.3f, bounds.bottom + 1f, bounds.right - radius * 0.3f, bounds.bottom + 1f, strokePaint)
    }

    /** Dark socket a gem sits in, with an inner shadow and a light lip. */
    fun well(c: Canvas, rect: RectF, radius: Float) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = 0xFF3A2010.toInt()
        c.drawRoundRect(rect, radius, radius, shapePaint)
        shapePaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.6f,
            0x77000000, 0x11000000, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, shapePaint)
        shapePaint.shader = null
        shapePaint.color = 0x1FFFD9A0
        c.drawRoundRect(
            RectF(rect.left + 1.5f, rect.bottom - rect.height() * 0.14f, rect.right - 1.5f, rect.bottom - 1.5f),
            radius * 0.65f, radius * 0.65f, shapePaint
        )
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, rect.width() * 0.02f)
        strokePaint.color = 0x331A0A02
        c.drawRoundRect(rect, radius, radius, strokePaint)
    }

    fun ice(c: Canvas, rect: RectF, strength: Float) {
        val r = rect.width() * 0.2f
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = withAlpha(0xFFB9E7F7.toInt(), 0.28f + 0.16f * strength)
        c.drawRoundRect(rect, r, r, shapePaint)
        shapePaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0x88FFFFFF.toInt(), 0x228FD3F0, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, r, r, shapePaint)
        shapePaint.shader = null
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = Math.max(1.5f, rect.width() * 0.04f)
        strokePaint.color = 0xD9FFFFFF.toInt()
        val cx = rect.centerX()
        val cy = rect.centerY()
        c.drawLine(cx - rect.width() * 0.26f, cy - rect.height() * 0.3f, cx + rect.width() * 0.16f, cy + rect.height() * 0.06f, strokePaint)
        c.drawLine(cx + rect.width() * 0.16f, cy + rect.height() * 0.06f, cx + rect.width() * 0.02f, cy + rect.height() * 0.3f, strokePaint)
        c.drawLine(cx - rect.width() * 0.08f, cy - rect.height() * 0.36f, cx - rect.width() * 0.2f, cy + rect.height() * 0.12f, strokePaint)
        strokePaint.strokeWidth = Math.max(1f, rect.width() * 0.02f)
        strokePaint.color = 0x77FFFFFF
        c.drawArc(
            RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f),
            195f, 130f, false, strokePaint
        )
    }

    /* ------------------------------------------------------------ backdrop */

    /** Static garden scenery, cached; animated layers are drawn on top. */
    fun bgGarden(c: Canvas, w: Float, h: Float, t: Float) {
        if (gardenCache == null || gardenW != w.toInt() || gardenH != h.toInt()) {
            gardenCache = buildGarden(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
            gardenW = w.toInt()
            gardenH = h.toInt()
        }
        val g = gardenCache
        if (g != null) c.drawBitmap(g, 0f, 0f, bitmapPaint)

        // rotating sun rays
        c.save()
        c.rotate(t * 4f, w * 0.22f, h * 0.055f)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = 0x12FFF3C0
        var i = 0
        while (i < 10) {
            c.save()
            c.rotate(i * 36f, w * 0.22f, h * 0.055f)
            val ray = Path()
            ray.moveTo(w * 0.22f, h * 0.055f)
            ray.lineTo(w * 0.22f - w * 0.05f, h * 0.55f)
            ray.lineTo(w * 0.22f + w * 0.05f, h * 0.55f)
            ray.close()
            c.drawPath(ray, shapePaint)
            c.restore()
            i++
        }
        c.restore()

        // drifting leaves
        for (k in 0 until 8) {
            val seed = k * 7.13f
            val speed = 0.012f + (k % 3) * 0.004f
            val y = ((h * (0.12f + (k * 0.097f)) + t * h * speed) % (h * 1.1f)) - h * 0.05f
            val x = w * (0.08f + 0.11f * k) + sin(t * 0.6f + seed) * w * 0.03f
            val size = w * (0.026f + (k % 3) * 0.008f)
            leaf(c, x, y, size, 0xFFFFF6E5.toInt(), 0x1AFFFFFF, sin(t + seed) * 40f)
        }
    }

    private fun buildGarden(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val fw = w.toFloat()
        val fh = h.toFloat()

        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = LinearGradient(
            0f, 0f, 0f, fh,
            intArrayOf(0xFF63B77E.toInt(), 0xFF3E8A5C.toInt(), 0xFF1C5138.toInt(), 0xFF071C14.toInt()),
            floatArrayOf(0f, 0.32f, 0.66f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, fw, fh, shapePaint)
        shapePaint.shader = null

        // sun
        glow(cv, fw * 0.22f, fh * 0.05f, fw * 0.5f, 0xFFFFF0B8.toInt(), 0.35f)
        circle(cv, fw * 0.22f, fh * 0.05f, fw * 0.055f, 0xCCFFF6D8.toInt())

        // clouds
        cloud(cv, fw * 0.74f, fh * 0.085f, fw * 0.13f, 0.22f)
        cloud(cv, fw * 0.34f, fh * 0.145f, fw * 0.1f, 0.16f)
        cloud(cv, fw * 0.88f, fh * 0.18f, fw * 0.09f, 0.13f)

        // distant hills
        val hill1 = Path()
        hill1.moveTo(0f, fh * 0.62f)
        hill1.cubicTo(fw * 0.18f, fh * 0.55f, fw * 0.34f, fh * 0.70f, fw * 0.52f, fh * 0.63f)
        hill1.cubicTo(fw * 0.7f, fh * 0.57f, fw * 0.86f, fh * 0.68f, fw, fh * 0.61f)
        hill1.lineTo(fw, fh)
        hill1.lineTo(0f, fh)
        hill1.close()
        shapePaint.color = 0xFF2E7A4E.toInt()
        cv.drawPath(hill1, shapePaint)

        val hill2 = Path()
        hill2.moveTo(0f, fh * 0.72f)
        hill2.cubicTo(fw * 0.22f, fh * 0.66f, fw * 0.46f, fh * 0.78f, fw * 0.68f, fh * 0.71f)
        hill2.cubicTo(fw * 0.84f, fh * 0.66f, fw * 0.94f, fh * 0.74f, fw, fh * 0.70f)
        hill2.lineTo(fw, fh)
        hill2.lineTo(0f, fh)
        hill2.close()
        shapePaint.color = 0xFF226440.toInt()
        cv.drawPath(hill2, shapePaint)

        val hill3 = Path()
        hill3.moveTo(0f, fh * 0.84f)
        hill3.cubicTo(fw * 0.26f, fh * 0.78f, fw * 0.5f, fh * 0.9f, fw * 0.74f, fh * 0.83f)
        hill3.cubicTo(fw * 0.88f, fh * 0.79f, fw * 0.95f, fh * 0.86f, fw, fh * 0.84f)
        hill3.lineTo(fw, fh)
        hill3.lineTo(0f, fh)
        hill3.close()
        shapePaint.color = 0xFF164A2E.toInt()
        cv.drawPath(hill3, shapePaint)

        // back bushes along the horizon
        var i = 0
        while (i < 9) {
            val bx = fw * (0.02f + i * 0.12f)
            bush(cv, bx, fh * 0.855f, fw * 0.11f, fw * 0.075f, 0xFF1D5A38.toInt(), 0xFF2C7448.toInt(), 0f)
            i++
        }

        // wooden fence
        fence(cv, 0f, fh * 0.865f, fw, fh * 0.045f, 0.85f)

        // foreground bushes + flowers
        bush(cv, fw * 0.03f, fh * 0.965f, fw * 0.24f, fw * 0.16f, 0xFF0E3A24.toInt(), 0xFF1B5636.toInt(), 1f)
        bush(cv, fw * 0.97f, fh * 0.975f, fw * 0.22f, fw * 0.15f, 0xFF0E3A24.toInt(), 0xFF1B5636.toInt(), 1f)
        bush(cv, fw * 0.52f, fh * 0.995f, fw * 0.3f, fw * 0.12f, 0xFF0B2E1C.toInt(), 0xFF17482C.toInt(), 1f)

        for (k in 0 until 10) {
            val fx = fw * (0.06f + k * 0.098f)
            val fy = fh * (0.9f + ((k * 37) % 9) * 0.008f)
            flower(cv, fx, fy, fw * 0.018f, if (k % 3 == 0) 0xFFFFC2D0.toInt() else 0xFFFFF0C0.toInt(), Palette.GOLD)
        }

        // vignette
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = RadialGradient(
            fw * 0.5f, fh * 0.42f, fh * 0.8f,
            intArrayOf(0x00000000, 0x00000000, 0x77000000),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, fw, fh, shapePaint)
        shapePaint.shader = null
        return bmp
    }

    fun cloud(c: Canvas, cx: Float, cy: Float, r: Float, alpha: Float) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = withAlpha(0xFFFFFFFF.toInt(), alpha)
        c.drawCircle(cx - r * 0.7f, cy + r * 0.16f, r * 0.5f, shapePaint)
        c.drawCircle(cx, cy, r * 0.66f, shapePaint)
        c.drawCircle(cx + r * 0.72f, cy + r * 0.2f, r * 0.46f, shapePaint)
        c.drawRoundRect(
            RectF(cx - r * 1.1f, cy + r * 0.02f, cx + r * 1.1f, cy + r * 0.66f),
            r * 0.32f, r * 0.32f, shapePaint
        )
    }

    fun bush(c: Canvas, cx: Float, baseY: Float, w: Float, h: Float, dark: Int, light: Int, shade: Float) {
        val blobs = floatArrayOf(
            -0.34f, -0.1f, 0.34f, -0.22f, -0.18f, 0.62f, -0.02f, 0.30f, 0.36f, 0.0f, 0.12f, 0.58f, -0.42f, 0.30f
        )
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        var i = 0
        while (i < blobs.size) {
            shapePaint.color = dark
            c.drawCircle(cx + blobs[i] * w, baseY + blobs[i + 1] * h * 0.5f, w * 0.26f, shapePaint)
            i += 2
        }
        i = 0
        while (i < blobs.size) {
            shapePaint.color = light
            c.drawCircle(
                cx + blobs[i] * w - w * 0.04f,
                baseY + blobs[i + 1] * h * 0.5f - h * 0.1f,
                w * 0.15f, shapePaint
            )
            i += 2
        }
        if (shade > 0f) {
            shapePaint.shader = LinearGradient(
                cx, baseY - h * 0.6f, cx, baseY + h * 0.4f,
                0x00000000, 0x55000000, Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, baseY, w * 0.42f, shapePaint)
            shapePaint.shader = null
        }
    }

    fun flower(c: Canvas, cx: Float, cy: Float, r: Float, petal: Int, center: Int) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = petal
        for (i in 0 until 5) {
            val a = i * (2.0 * Math.PI / 5)
            c.drawCircle(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), r * 0.62f, shapePaint)
        }
        shapePaint.color = center
        c.drawCircle(cx, cy, r * 0.5f, shapePaint)
        shapePaint.color = 0x66FFFFFF
        c.drawCircle(cx - r * 0.14f, cy - r * 0.18f, r * 0.16f, shapePaint)
    }

    fun fence(c: Canvas, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        val count = 9
        val gap = w / count
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        for (i in 0 until count) {
            val px = x + gap * (i + 0.5f)
            shapePaint.color = withAlpha(0xFF8A5A2E.toInt(), alpha)
            c.drawRoundRect(
                RectF(px - gap * 0.09f, y - h * 0.1f, px + gap * 0.09f, y + h),
                gap * 0.05f, gap * 0.05f, shapePaint
            )
            shapePaint.color = withAlpha(0xFFC08A52.toInt(), alpha)
            c.drawRoundRect(
                RectF(px - gap * 0.09f, y - h * 0.1f, px + gap * 0.02f, y + h), gap * 0.05f, gap * 0.05f, shapePaint
            )
        }
        shapePaint.color = withAlpha(0xFF7A4721.toInt(), alpha)
        c.drawRect(x, y + h * 0.18f, x + w, y + h * 0.34f, shapePaint)
        shapePaint.color = withAlpha(0xFFC08A52.toInt(), alpha)
        c.drawRect(x, y + h * 0.18f, x + w, y + h * 0.24f, shapePaint)
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
        strokePaint.color = withAlpha(tint, 0.3f)
        c.drawLine(cx - size * 0.42f, cy, cx + size * 0.42f, cy, strokePaint)
        c.restore()
    }

    /* -------------------------------------------------------------- colour */

    fun darken(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        return Color.argb(
            a,
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }

    fun lighten(color: Int, amount: Float): Int {
        val a = Color.alpha(color)
        return Color.argb(
            a,
            (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255),
            (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255),
            (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
        )
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun shade(color: Int, k: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * k).toInt().coerceIn(0, 255),
        (Color.green(color) * k).toInt().coerceIn(0, 255),
        (Color.blue(color) * k).toInt().coerceIn(0, 255)
    )

    /* --------------------------------------------------------------- icons */

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
        val pad = (size * 0.22f).toInt()
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
                lighten(color, 0.5f), darken(color, 0.72f), Shader.TileMode.CLAMP
            )
            cv.drawPath(p, shapePaint)
            shapePaint.shader = null
            shapePaint.color = 0x66FFFFFF
            cv.save()
            cv.clipPath(p)
            cv.drawCircle(cx - r * 0.3f, cy - r * 0.34f, r * 0.4f, shapePaint)
            cv.restore()
            strokePaint.reset()
            strokePaint.isAntiAlias = true
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = Math.max(1.8f, r * 0.12f)
            strokePaint.color = darken(color, 0.5f)
            cv.drawPath(p, strokePaint)
        } else {
            shapePaint.color = 0x40000000
            cv.drawPath(p, shapePaint)
            strokePaint.reset()
            strokePaint.isAntiAlias = true
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = Math.max(1.6f, r * 0.13f)
            strokePaint.color = 0x99FFFFFF.toInt()
            cv.drawPath(p, strokePaint)
        }
        return bmp
    }

    fun coin(c: Canvas, cx: Float, cy: Float, r: Float) {
        glow(c, cx, cy, r * 1.6f, Palette.GOLD, 0.22f)
        circle(c, cx, cy, r, 0xFFA96C12.toInt())
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            0xFFFFF0B8.toInt(), 0xFFE0A21C.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 0.86f, shapePaint)
        shapePaint.shader = null
        shapePaint.color = 0x77FFFFFF
        c.drawCircle(cx - r * 0.26f, cy - r * 0.3f, r * 0.24f, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, r * 0.14f)
        strokePaint.color = 0xFFA96C12.toInt()
        c.drawCircle(cx, cy, r * 0.56f, strokePaint)
        shapePaint.color = 0xFFA96C12.toInt()
        c.drawRoundRect(
            RectF(cx - r * 0.1f, cy - r * 0.3f, cx + r * 0.1f, cy + r * 0.3f),
            r * 0.08f, r * 0.08f, shapePaint
        )
        sparkle(c, cx + r * 0.5f, cy - r * 0.56f, r * 0.3f, 0xFFFFFFFF.toInt(), 0.75f, 20f)
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
                lighten(color, 0.32f), darken(color, 0.78f), Shader.TileMode.CLAMP
            )
            c.drawPath(p, shapePaint)
            shapePaint.shader = null
            shapePaint.color = 0x4DFFFFFF
            c.drawCircle(cx - r * 0.42f, cy - r * 0.42f, r * 0.26f, shapePaint)
        } else {
            shapePaint.color = 0x40000000
            c.drawPath(p, shapePaint)
        }
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, r * 0.18f)
        strokePaint.color = if (filled) darken(color, 0.6f) else 0x77FFFFFF
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
        shapePaint.color = 0x66000000
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
        shapePaint.color = Palette.PANEL_DEEP
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
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        val head = Path()
        head.moveTo(cx + r * 0.1f, cy - r * 0.95f)
        head.lineTo(cx + r * 0.72f, cy - r * 0.72f)
        head.lineTo(cx + r * 0.28f, cy - r * 0.2f)
        head.close()
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

    fun hammer(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        c.save()
        c.rotate(-22f, cx, cy)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawRoundRect(
            RectF(cx - r * 0.16f, cy - r * 0.1f, cx + r * 0.16f, cy + r * 0.95f),
            r * 0.14f, r * 0.14f, shapePaint
        )
        shapePaint.color = lighten(color, 0.15f)
        c.drawRoundRect(
            RectF(cx - r * 0.72f, cy - r * 0.78f, cx + r * 0.72f, cy - r * 0.22f),
            r * 0.2f, r * 0.2f, shapePaint
        )
        shapePaint.color = withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        c.drawRoundRect(
            RectF(cx - r * 0.6f, cy - r * 0.7f, cx + r * 0.3f, cy - r * 0.52f),
            r * 0.1f, r * 0.1f, shapePaint
        )
        c.restore()
    }

    fun plusMoves(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = Math.max(2f, r * 0.3f)
        strokePaint.color = color
        c.drawLine(cx - r * 0.6f, cy, cx + r * 0.6f, cy, strokePaint)
        c.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.6f, strokePaint)
    }

    fun cart(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(2f, r * 0.16f)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = color
        val p = Path()
        p.moveTo(cx - r * 0.85f, cy - r * 0.62f)
        p.lineTo(cx - r * 0.55f, cy - r * 0.62f)
        p.lineTo(cx - r * 0.32f, cy + r * 0.22f)
        p.lineTo(cx + r * 0.7f, cy + r * 0.22f)
        c.drawPath(p, strokePaint)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawCircle(cx - r * 0.12f, cy + r * 0.58f, r * 0.16f, shapePaint)
        c.drawCircle(cx + r * 0.54f, cy + r * 0.58f, r * 0.16f, shapePaint)
        c.drawRoundRect(
            RectF(cx - r * 0.42f, cy - r * 0.5f, cx + r * 0.62f, cy + r * 0.1f),
            r * 0.1f, r * 0.1f, shapePaint
        )
    }

    fun wheelIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.6f, r * 0.16f)
        strokePaint.color = color
        c.drawCircle(cx, cy, r * 0.86f, strokePaint)
        strokePaint.strokeWidth = Math.max(1.2f, r * 0.1f)
        for (i in 0 until 4) {
            val a = i * Math.PI / 4.0
            c.drawLine(
                cx - (cos(a) * r * 0.8f).toFloat(), cy - (sin(a) * r * 0.8f).toFloat(),
                cx + (cos(a) * r * 0.8f).toFloat(), cy + (sin(a) * r * 0.8f).toFloat(),
                strokePaint
            )
        }
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawCircle(cx, cy, r * 0.2f, shapePaint)
    }

    fun mapIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Path()
        p.moveTo(cx - r * 0.9f, cy - r * 0.62f)
        p.lineTo(cx - r * 0.3f, cy - r * 0.82f)
        p.lineTo(cx + r * 0.3f, cy - r * 0.6f)
        p.lineTo(cx + r * 0.9f, cy - r * 0.8f)
        p.lineTo(cx + r * 0.9f, cy + r * 0.64f)
        p.lineTo(cx + r * 0.3f, cy + r * 0.84f)
        p.lineTo(cx - r * 0.3f, cy + r * 0.62f)
        p.lineTo(cx - r * 0.9f, cy + r * 0.82f)
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawPath(p, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.2f, r * 0.1f)
        strokePaint.color = withAlpha(Palette.WOOD_DEEP, 0.35f)
        c.drawLine(cx - r * 0.3f, cy - r * 0.8f, cx - r * 0.3f, cy + r * 0.62f, strokePaint)
        c.drawLine(cx + r * 0.3f, cy - r * 0.6f, cx + r * 0.3f, cy + r * 0.82f, strokePaint)
    }

    fun ticketIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawRoundRect(
            RectF(cx - r * 0.92f, cy - r * 0.56f, cx + r * 0.92f, cy + r * 0.56f),
            r * 0.16f, r * 0.16f, shapePaint
        )
        shapePaint.color = Palette.PANEL_DEEP
        c.drawCircle(cx - r * 0.92f, cy, r * 0.18f, shapePaint)
        c.drawCircle(cx + r * 0.92f, cy, r * 0.18f, shapePaint)
    }

    /* ------------------------------------------------------------ produce */

    fun produce(c: Canvas, type: Int, cx: Float, cy: Float, size: Float, alpha: Int = 255, scale: Float = 1f) {
        if (type < 0) return
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
        cv.scale(px / 100f, px / 100f)
        val p = produceSpec(type)
        val pts = p.outline
        val n = pts.size / 2

        // ground shadow
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = RadialGradient(
            CX, 94f, 40f,
            intArrayOf(0x66000000, 0x2E000000, 0x00000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawOval(RectF(10f, 80f, 90f, 100f), shapePaint)

        // coloured halo makes the fruit pop off a dark socket
        shapePaint.shader = RadialGradient(
            CX, CY, 52f,
            intArrayOf(withAlpha(p.glow, 0.34f), withAlpha(p.glow, 0.12f), 0x00FFFFFF),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(CX, CY, 52f, shapePaint)
        shapePaint.shader = null

        val body = Path()
        body.moveTo(pts[0], pts[1])
        for (i in 1 until n) body.lineTo(pts[i * 2], pts[i * 2 + 1])
        body.close()

        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.color = p.base
        cv.drawPath(body, fillPaint)

        // table ring (inner plateau)
        val tableXs = FloatArray(n)
        val tableYs = FloatArray(n)
        for (i in 0 until n) {
            tableXs[i] = CX + (pts[i * 2] - CX) * 0.52f
            tableYs[i] = CY + (pts[i * 2 + 1] - CY) * 0.52f
        }
        val table = Path()
        table.moveTo(tableXs[0], tableYs[0])
        for (i in 1 until n) table.lineTo(tableXs[i], tableYs[i])
        table.close()

        cv.save()
        cv.clipPath(body)

        // crown facets
        for (i in 0 until n) {
            val j = (i + 1) % n
            val mx = (pts[i * 2] + pts[j * 2]) / 2f
            val my = (pts[i * 2 + 1] + pts[j * 2 + 1]) / 2f
            var dx = mx - CX
            var dy = my - CY
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            dx /= len
            dy /= len
            val dot = dx * LX + dy * LY
            var k = 0.50f + 0.80f * (0.5f + 0.5f * dot)
            if (my > 66f) k *= 0.88f
            if (i % 2 == 0) k *= 1.10f else k *= 0.95f
            val f = Path()
            f.moveTo(pts[i * 2], pts[i * 2 + 1])
            f.lineTo(pts[j * 2], pts[j * 2 + 1])
            f.lineTo(tableXs[j], tableYs[j])
            f.lineTo(tableXs[i], tableYs[i])
            f.close()
            fillPaint.color = shade(p.base, k)
            cv.drawPath(f, fillPaint)
        }

        // table facets, brighter
        for (i in 0 until n) {
            val j = (i + 1) % n
            val mx = (tableXs[i] + tableXs[j]) / 2f
            val my = (tableYs[i] + tableYs[j]) / 2f
            var dx = mx - CX
            var dy = my - CY
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            dx /= len
            dy /= len
            val dot = dx * LX + dy * LY
            val k = 1.02f + 0.42f * (0.5f + 0.5f * dot)
            val f = Path()
            f.moveTo(tableXs[i], tableYs[i])
            f.lineTo(tableXs[j], tableYs[j])
            f.lineTo(CX, CY - 2f)
            f.close()
            fillPaint.color = shade(p.base, k)
            cv.drawPath(f, fillPaint)
        }

        // specular wash
        fillPaint.shader = RadialGradient(
            33f, 26f, 66f,
            intArrayOf(0xA6FFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawPath(body, fillPaint)
        // bottom falloff
        fillPaint.shader = RadialGradient(
            76f, 86f, 56f,
            intArrayOf(0x00000000, 0x26000000, 0x77000000),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawPath(body, fillPaint)
        fillPaint.shader = null
        cv.restore()

        // bevel on the table edge
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.1f
        strokePaint.color = withAlpha(p.light, 0.55f)
        cv.drawPath(table, strokePaint)

        // rim light along the lit edge
        strokePaint.strokeWidth = 1.7f
        strokePaint.shader = LinearGradient(
            12f, 10f, 88f, 94f,
            intArrayOf(0xFFFFFFFF.toInt(), 0x55FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawPath(body, strokePaint)
        strokePaint.shader = null

        // dark outline
        strokePaint.strokeWidth = 2.1f
        strokePaint.color = withAlpha(p.deep, 0.6f)
        cv.drawPath(body, strokePaint)

        // specular blob + catchlight
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.shader = RadialGradient(
            34f, 28f, 15f,
            intArrayOf(0xF2FFFFFF.toInt(), 0x44FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawOval(RectF(21f, 17f, 51f, 39f), fillPaint)
        fillPaint.shader = null
        fillPaint.color = 0xE6FFFFFF.toInt()
        cv.drawCircle(29f, 24f, 2.9f, fillPaint)
        cv.drawCircle(38f, 21f, 1.5f, fillPaint)

        accents(cv, p)
        return bmp
    }

    private fun accents(cv: Canvas, p: Produce) {
        when (p.face) {
            0 -> { // strawberry: seeds + leafy crown
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = 0xE6FFE9A8.toInt()
                val seeds = floatArrayOf(
                    38f, 48f, 50f, 44f, 62f, 49f, 32f, 58f, 44f, 60f, 56f, 59f,
                    68f, 59f, 37f, 69f, 49f, 72f, 61f, 71f, 44f, 81f, 55f, 81f
                )
                var i = 0
                while (i < seeds.size) {
                    cv.save()
                    cv.rotate(16f, seeds[i], seeds[i + 1])
                    cv.drawOval(
                        RectF(seeds[i] - 1.9f, seeds[i + 1] - 3f, seeds[i] + 1.9f, seeds[i + 1] + 3f),
                        fillPaint
                    )
                    cv.restore()
                    i += 2
                }
                val crown = Path()
                crown.moveTo(33f, 30f)
                crown.lineTo(27f, 15f)
                crown.lineTo(42f, 23f)
                crown.lineTo(50f, 9f)
                crown.lineTo(58f, 23f)
                crown.lineTo(73f, 15f)
                crown.lineTo(67f, 31f)
                crown.lineTo(50f, 36f)
                crown.close()
                fillPaint.color = Palette.LEAF
                cv.drawPath(crown, fillPaint)
                fillPaint.color = 0x33000000
                cv.drawPath(crown, fillPaint)
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.6f
                strokePaint.color = Palette.LEAF_DARK
                cv.drawPath(crown, strokePaint)
            }
            1 -> { // orange: pores, leaf, split light
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = 0x2E8A4A00
                val dots = floatArrayOf(
                    36f, 46f, 52f, 40f, 63f, 47f, 45f, 56f, 58f, 61f, 34f, 61f, 69f, 55f, 42f, 71f, 56f, 72f
                )
                var i = 0
                while (i < dots.size) {
                    cv.drawCircle(dots[i], dots[i + 1], 1.6f, fillPaint)
                    i += 2
                }
                val leafP = Path()
                leafP.moveTo(50f, 24f)
                leafP.cubicTo(60f, 10f, 78f, 12f, 83f, 19f)
                leafP.cubicTo(72f, 29f, 58f, 30f, 50f, 24f)
                leafP.close()
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafP, fillPaint)
                fillPaint.color = 0x33000000
                cv.drawPath(leafP, fillPaint)
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.4f
                strokePaint.color = Palette.LEAF_DARK
                cv.drawPath(leafP, strokePaint)
            }
            2 -> { // lemon: pith arcs + leaf
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.4f
                strokePaint.color = 0x33A87C00
                for (a in intArrayOf(-58, -22, 22, 58)) {
                    val rad = Math.toRadians(a.toDouble())
                    cv.drawLine(
                        CX - (30 * cos(rad)).toFloat(), CY - (26 * sin(rad)).toFloat(),
                        CX + (30 * cos(rad)).toFloat(), CY + (26 * sin(rad)).toFloat(),
                        strokePaint
                    )
                }
                val leafP = Path()
                leafP.moveTo(54f, 30f)
                leafP.cubicTo(64f, 18f, 80f, 20f, 85f, 28f)
                leafP.cubicTo(73f, 35f, 61f, 35f, 54f, 30f)
                leafP.close()
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafP, fillPaint)
                fillPaint.color = 0x33000000
                cv.drawPath(leafP, fillPaint)
            }
            3 -> { // apple: stem + leaf + blush
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.shader = RadialGradient(
                    70f, 66f, 30f,
                    intArrayOf(0x55FF6B57, 0x00FF6B57),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawCircle(66f, 62f, 30f, fillPaint)
                fillPaint.shader = null
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 3.4f
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.color = 0xFF6B4B2A.toInt()
                cv.drawLine(50f, 30f, 54f, 15f, strokePaint)
                val leafP = Path()
                leafP.moveTo(55f, 22f)
                leafP.cubicTo(64f, 11f, 79f, 14f, 83f, 21f)
                leafP.cubicTo(72f, 28f, 61f, 28f, 55f, 22f)
                leafP.close()
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafP, fillPaint)
                strokePaint.strokeWidth = 1.4f
                strokePaint.color = Palette.LEAF_DARK
                cv.drawPath(leafP, strokePaint)
            }
            4 -> { // grapes: individual berries catching light
                val balls = floatArrayOf(
                    43f, 39f, 12.5f, 60f, 43f, 12.5f, 51f, 53f, 12.5f,
                    35f, 55f, 11.5f, 66f, 58f, 11.5f, 49f, 66f, 11.5f,
                    39f, 73f, 10.5f, 61f, 72f, 10.5f
                )
                var i = 0
                while (i < balls.size) {
                    val bx = balls[i]
                    val by = balls[i + 1]
                    val br = balls[i + 2]
                    fillPaint.reset()
                    fillPaint.isAntiAlias = true
                    fillPaint.shader = RadialGradient(
                        bx - br * 0.35f, by - br * 0.42f, br * 1.3f,
                        intArrayOf(0xD9FFFFFF.toInt(), 0x1A000000, 0x70000000),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                    )
                    cv.drawCircle(bx, by, br, fillPaint)
                    fillPaint.shader = null
                    strokePaint.reset()
                    strokePaint.isAntiAlias = true
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = 1f
                    strokePaint.color = 0x33000000
                    cv.drawCircle(bx, by, br, strokePaint)
                    i += 3
                }
            }
            5 -> { // eggplant: calyx + highlight streak
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeWidth = 3.4f
                strokePaint.color = withAlpha(0xFFFFFFFF.toInt(), 0.35f)
                val streak = Path()
                streak.moveTo(34f, 50f)
                streak.cubicTo(33f, 62f, 37f, 72f, 44f, 78f)
                cv.drawPath(streak, strokePaint)
                val calyx = Path()
                calyx.moveTo(50f, 36f)
                calyx.lineTo(33f, 28f)
                calyx.lineTo(41f, 23f)
                calyx.lineTo(37f, 11f)
                calyx.lineTo(50f, 19f)
                calyx.lineTo(63f, 11f)
                calyx.lineTo(59f, 23f)
                calyx.lineTo(67f, 28f)
                calyx.close()
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                fillPaint.color = Palette.LEAF
                cv.drawPath(calyx, fillPaint)
                fillPaint.color = 0x33000000
                cv.drawPath(calyx, fillPaint)
                strokePaint.strokeWidth = 1.5f
                strokePaint.color = Palette.LEAF_DARK
                cv.drawPath(calyx, strokePaint)
            }
            else -> { // cauliflower: florets + leaves
                fillPaint.reset()
                fillPaint.isAntiAlias = true
                val fl = floatArrayOf(
                    37f, 41f, 14f, 59f, 45f, 14f, 48f, 30f, 13f,
                    30f, 58f, 13f, 68f, 58f, 13f, 50f, 60f, 16f
                )
                var i = 0
                while (i < fl.size) {
                    fillPaint.shader = RadialGradient(
                        fl[i] - 4f, fl[i + 1] - 5f, fl[i + 2] * 1.35f,
                        intArrayOf(0xFFFFFFFF.toInt(), 0xFFF6EDD8.toInt(), 0xFFC7B48C.toInt()),
                        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                    )
                    cv.drawCircle(fl[i], fl[i + 1], fl[i + 2], fillPaint)
                    fillPaint.shader = null
                    i += 3
                }
                val leafL = Path()
                leafL.moveTo(35f, 76f)
                leafL.cubicTo(20f, 70f, 11f, 79f, 9f, 92f)
                leafL.cubicTo(24f, 94f, 35f, 85f, 40f, 78f)
                leafL.close()
                val leafR = Path()
                leafR.moveTo(65f, 76f)
                leafR.cubicTo(80f, 70f, 89f, 79f, 91f, 92f)
                leafR.cubicTo(76f, 94f, 65f, 85f, 60f, 78f)
                leafR.close()
                fillPaint.color = Palette.LEAF
                cv.drawPath(leafL, fillPaint)
                fillPaint.color = Palette.LEAF_LIGHT
                cv.drawPath(leafR, fillPaint)
            }
        }
    }

    fun special(c: Canvas, specialName: String, cx: Float, cy: Float, size: Float, alpha: Int = 255, phase: Float = 0f) {
        when (specialName) {
            "stripeH", "stripeV" -> {
                val r = size * 0.38f
                c.save()
                c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
                c.rotate(if (specialName == "stripeV") 90f else 0f, cx, cy)
                val off = ((phase * 2f) % 1f) * size * 0.4f
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                var x = cx - size * 0.7f + off
                while (x < cx + size * 0.7f) {
                    shapePaint.color = Color.argb((alpha * 0.55f).toInt(), 255, 255, 255)
                    c.drawRoundRect(RectF(x, cy - r, x + size * 0.11f, cy + r), size * 0.05f, size * 0.05f, shapePaint)
                    shapePaint.color = Color.argb((alpha * 0.25f).toInt(), 255, 255, 255)
                    c.drawRoundRect(RectF(x + size * 0.02f, cy - r, x + size * 0.07f, cy + r), size * 0.03f, size * 0.03f, shapePaint)
                    x += size * 0.24f
                }
                c.restore()
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = Math.max(1.8f, size * 0.04f)
                strokePaint.color = Color.argb((alpha * 0.95f).toInt(), 255, 255, 255)
                c.drawCircle(cx, cy, r, strokePaint)
                strokePaint.strokeWidth = Math.max(1f, size * 0.02f)
                strokePaint.color = Color.argb((alpha * 0.6f).toInt(), 255, 240, 190)
                c.drawCircle(cx, cy, r * 0.82f, strokePaint)
            }
            "bomb" -> {
                glow(c, cx, cy, size * 0.6f, 0xFFFF8A3D.toInt(), 0.4f * (alpha / 255f))
                val br = size * 0.17f
                val bx = cx + size * 0.2f
                val by = cy - size * 0.18f
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                shapePaint.shader = RadialGradient(
                    bx - br * 0.4f, by - br * 0.4f, br * 1.5f,
                    intArrayOf(0xFF5A4A72.toInt(), 0xFF241A34.toInt(), 0xFF100A18.toInt()),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                c.drawCircle(bx, by, br, shapePaint)
                shapePaint.shader = null
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = Math.max(1.6f, size * 0.028f)
                strokePaint.color = Color.argb(alpha, 255, 150, 60)
                c.drawCircle(bx, by, br, strokePaint)
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeWidth = Math.max(1.8f, size * 0.038f)
                strokePaint.color = Color.argb(alpha, 255, 218, 128)
                val fuse = Path()
                fuse.moveTo(bx + br * 0.5f, by - br * 0.86f)
                fuse.cubicTo(bx + br * 0.9f, by - br * 1.7f, bx + br * 2f, by - br * 1.6f, bx + br * 2.1f, by - br * 0.9f)
                c.drawPath(fuse, strokePaint)
                val flick = 0.7f + 0.3f * sin(phase * 12f)
                sparkle(c, bx + br * 2.1f, by - br * 0.95f, size * 0.075f * flick, 0xFFFFE08A.toInt(), (alpha / 255f) * flick, phase * 120f)
                sparkle(c, bx + br * 2.1f, by - br * 0.95f, size * 0.035f, 0xFFFFFFFF.toInt(), alpha / 255f, -phase * 90f)
            }
            "rainbow" -> rainbowOrb(c, cx, cy, size, alpha, phase)
        }
    }

    private fun rainbowOrb(c: Canvas, cx: Float, cy: Float, size: Float, alpha: Int, phase: Float) {
        val r = size * 0.4f
        glow(c, cx, cy, size * 0.66f, 0xFFFFF0B0.toInt(), 0.36f * (alpha / 255f))
        c.save()
        c.rotate(phase * 90f, cx, cy)
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
        shapePaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.35f, r * 1.2f,
            intArrayOf(0x99FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, shapePaint)
        shapePaint.shader = null
        c.restore()
        shapePaint.color = Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)
        c.drawCircle(cx, cy, r * 0.34f, shapePaint)
        shapePaint.color = Color.argb((alpha * 0.35f).toInt(), 255, 255, 255)
        c.drawCircle(cx - r * 0.14f, cy - r * 0.16f, r * 0.13f, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1.6f, size * 0.028f)
        strokePaint.color = Color.argb((alpha * 0.8f).toInt(), 255, 255, 255)
        c.drawCircle(cx, cy, r, strokePaint)
        for (i in 0 until 3) {
            val a = phase * 6.28f + i * 2.09f
            sparkle(
                c, cx + (cos(a) * r * 1.28f), cy + (sin(a) * r * 1.28f),
                size * 0.06f, 0xFFFFFFFF.toInt(), alpha / 255f * 0.9f, phase * 200f
            )
        }
    }

    /* -------------------------------------------------------- produce spec */

    private val specs = HashMap<Int, Produce>()

    private fun produceSpec(type: Int): Produce = specs.getOrPut(type) {
        when (type) {
            0 -> Produce(
                outline = floatArrayOf(
                    24f, 36f, 30f, 26f, 41f, 22f, 50f, 27f, 59f, 22f, 70f, 26f, 76f, 36f, 78f, 50f,
                    73f, 62f, 64f, 74f, 56f, 83f, 50f, 92f, 44f, 83f, 36f, 74f, 27f, 62f, 22f, 50f
                ),
                base = 0xFFE5324A.toInt(), deep = 0xFF8E0E24.toInt(), light = 0xFFFF9AA8.toInt(),
                glow = 0xFFFF5A73.toInt(), face = 0
            )
            1 -> Produce(
                outline = floatArrayOf(
                    50f, 15f, 63f, 18f, 74f, 25f, 82f, 36f, 85f, 50f, 80f, 64f, 69f, 75f, 57f, 82f,
                    44f, 82f, 32f, 76f, 22f, 65f, 17f, 51f, 20f, 36f, 30f, 24f, 40f, 18f
                ),
                base = 0xFFF59316.toInt(), deep = 0xFF9A5200.toInt(), light = 0xFFFFC46B.toInt(),
                glow = 0xFFFFA22E.toInt(), face = 1
            )
            2 -> Produce(
                outline = floatArrayOf(
                    9f, 50f, 16f, 40f, 27f, 32f, 39f, 28f, 50f, 27f, 61f, 28f, 73f, 32f, 84f, 40f,
                    91f, 50f, 84f, 60f, 73f, 68f, 61f, 72f, 50f, 73f, 39f, 72f, 27f, 68f, 16f, 60f
                ),
                base = 0xFFF5C518.toInt(), deep = 0xFFA87C00.toInt(), light = 0xFFFFF3A8.toInt(),
                glow = 0xFFFFD93D.toInt(), face = 2
            )
            3 -> Produce(
                outline = floatArrayOf(
                    50f, 29f, 58f, 23f, 69f, 21f, 78f, 27f, 84f, 38f, 85f, 53f, 80f, 66f, 71f, 76f,
                    61f, 82f, 50f, 84f, 39f, 82f, 29f, 76f, 20f, 66f, 15f, 53f, 16f, 38f, 22f, 27f, 31f, 21f, 42f, 23f
                ),
                base = 0xFF74CC33.toInt(), deep = 0xFF2C7A20.toInt(), light = 0xFFC6F58A.toInt(),
                glow = 0xFF7ED957.toInt(), face = 3
            )
            4 -> Produce(
                outline = floatArrayOf(
                    50f, 21f, 62f, 24f, 72f, 32f, 79f, 44f, 80f, 57f, 74f, 69f, 63f, 78f, 50f, 83f,
                    37f, 78f, 26f, 69f, 20f, 57f, 21f, 44f, 28f, 32f, 38f, 24f
                ),
                base = 0xFF9A57E8.toInt(), deep = 0xFF5B21A8.toInt(), light = 0xFFD8B4FE.toInt(),
                glow = 0xFFA855F7.toInt(), face = 4
            )
            5 -> Produce(
                outline = floatArrayOf(
                    50f, 14f, 58f, 20f, 64f, 30f, 73f, 40f, 79f, 54f, 77f, 69f, 67f, 80f, 55f, 86f,
                    43f, 85f, 31f, 77f, 22f, 65f, 20f, 50f, 26f, 36f, 36f, 26f, 44f, 18f
                ),
                base = 0xFF5B4BE0.toInt(), deep = 0xFF2A1E8C.toInt(), light = 0xFF9E93FF.toInt(),
                glow = 0xFF7C6BFF.toInt(), face = 5
            )
            else -> Produce(
                outline = floatArrayOf(
                    50f, 16f, 62f, 20f, 73f, 27f, 81f, 38f, 84f, 51f, 79f, 63f, 69f, 74f, 57f, 80f,
                    43f, 80f, 31f, 74f, 21f, 63f, 16f, 51f, 19f, 38f, 27f, 27f, 38f, 20f
                ),
                base = 0xFFF2E8CF.toInt(), deep = 0xFFB49F76.toInt(), light = 0xFFFFFFFF.toInt(),
                glow = 0xFFFFF3D6.toInt(), face = 6
            )
        }
    }

    fun produceColor(type: Int): Int = when (type) {
        0 -> 0xFFFF5A73.toInt()
        1 -> 0xFFFFA22E.toInt()
        2 -> 0xFFFFD93D.toInt()
        3 -> 0xFF7ED957.toInt()
        4 -> 0xFFA855F7.toInt()
        5 -> 0xFF7C6BFF.toInt()
        6 -> 0xFFFFF3D6.toInt()
        else -> 0xFFFFFFFF.toInt()
    }
}
