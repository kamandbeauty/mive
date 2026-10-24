package ir.mivehchin.game

import android.content.Context
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

object Palette {
    val BG_TOP = 0xFF0E3A2C.toInt()
    val BG_BOTTOM = 0xFF061712.toInt()
    val PANEL = 0xFF123A2E.toInt()
    val PANEL_SOFT = 0xFF1A4A3A.toInt()
    val LINE = 0xFF2A6350.toInt()
    val CREAM = 0xFFFFF6E5.toInt()
    val MUTED = 0xFF9FC4B6.toInt()
    val LIME = 0xFFC6F36B.toInt()
    val GREEN = 0xFF34C759.toInt()
    val CORAL = 0xFFFF7A59.toInt()
    val PURPLE = 0xFFA78BFA.toInt()
    val GOLD = 0xFFFFC94D.toInt()
    val DANGER = 0xFFFF5C7A.toInt()
    val SKY = 0xFF7DD3FC.toInt()
}

private class FruitArt {
    lateinit var body: Path
    lateinit var shader: Shader
    val accents = ArrayList<Path>()
    val accentColors = ArrayList<Int>()
    var highlight: RectF? = null
}

object Art {

    var regular: Typeface = Typeface.DEFAULT
    var bold: Typeface = Typeface.DEFAULT_BOLD
    var black: Typeface = Typeface.DEFAULT_BOLD

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fruits = HashMap<Int, FruitArt>()

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
            textPaint.color = 0x66000000
            c.drawText(s, x, y + size * 0.07f, textPaint)
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

    /* ------------------------------------------------------------ shapes */

    fun panel(c: Canvas, rect: RectF, radius: Float, color: Int, stroke: Float = 0f, strokeColor: Int = 0) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawRoundRect(rect, radius, radius, shapePaint)
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
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = color
        c.drawCircle(cx, cy, r, shapePaint)
    }

    fun progress(c: Canvas, rect: RectF, fraction: Float, fill: Int, track: Int) {
        val radius = rect.height() / 2f
        panel(c, rect, radius, track)
        val clamped = Math.max(0f, Math.min(1f, fraction))
        if (clamped > 0f) {
            val f = RectF(rect.left, rect.top, rect.left + rect.width() * clamped, rect.bottom)
            if (f.width() < rect.height()) f.right = f.left + rect.height()
            gradientPanel(c, f, radius, fill, darken(fill, 0.75f))
        }
    }

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

    /* ------------------------------------------------------------- icons */

    fun star(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean, color: Int = Palette.GOLD) {
        val p = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.45f
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = if (filled) color else 0x33FFFFFF
        c.drawPath(p, shapePaint)
        if (filled) {
            strokePaint.reset()
            strokePaint.isAntiAlias = true
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = Math.max(1.5f, r * 0.12f)
            strokePaint.color = Art.darken(color, 0.7f)
            c.drawPath(p, strokePaint)
        } else {
            strokePaint.reset()
            strokePaint.isAntiAlias = true
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = Math.max(1.2f, r * 0.1f)
            strokePaint.color = 0x66FFFFFF
            c.drawPath(p, strokePaint)
        }
    }

    fun coin(c: Canvas, cx: Float, cy: Float, r: Float) {
        circle(c, cx, cy, r, 0xFFB8860B.toInt())
        circle(c, cx, cy, r * 0.86f, Palette.GOLD)
        circle(c, cx - r * 0.25f, cy - r * 0.28f, r * 0.22f, 0x66FFFFFF)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, r * 0.12f)
        strokePaint.color = 0xFFD89A18.toInt()
        c.drawCircle(cx, cy, r * 0.62f, strokePaint)
    }

    fun heart(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean, color: Int = Palette.DANGER) {
        val p = Path()
        p.moveTo(cx, cy + r * 0.75f)
        p.cubicTo(cx - r * 1.4f, cy - r * 0.1f, cx - r * 0.7f, cy - r * 1.0f, cx, cy - r * 0.35f)
        p.cubicTo(cx + r * 0.7f, cy - r * 1.0f, cx + r * 1.4f, cy - r * 0.1f, cx, cy + r * 0.75f)
        p.close()
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = if (filled) color else 0x33FFFFFF
        c.drawPath(p, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = Math.max(1f, r * 0.18f)
        strokePaint.color = if (filled) Art.darken(color, 0.7f) else 0x66FFFFFF
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

    /* ----------------------------------------------------------- produce */

    fun produce(c: Canvas, type: Int, cx: Float, cy: Float, size: Float, alpha: Int = 255, scale: Float = 1f) {
        if (type < 0) {
            rainbowOrb(c, cx, cy, size * scale, alpha)
            return
        }
        val art = fruits.getOrPut(type) { buildFruit(type) }
        val s = size * scale
        if (s <= 0f) return
        c.save()
        c.translate(cx - s / 2f, cy - s / 2f)
        c.scale(s / 100f, s / 100f)

        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.alpha = alpha
        shapePaint.shader = art.shader
        c.drawPath(art.body, shapePaint)
        shapePaint.shader = null

        for (i in art.accents.indices) {
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = art.accentColors[i]
            shapePaint.alpha = (alpha * (Color.alpha(art.accentColors[i]) / 255f)).toInt()
            c.drawPath(art.accents[i], shapePaint)
        }

        val hl = art.highlight
        if (hl != null) {
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = Color.WHITE
            shapePaint.alpha = (alpha * 0.22f).toInt()
            c.drawOval(hl, shapePaint)
        }
        c.restore()
    }

    fun special(c: Canvas, specialName: String, cx: Float, cy: Float, size: Float, alpha: Int = 255) {
        val s = size
        c.save()
        c.translate(cx - s / 2f, cy - s / 2f)
        c.scale(s / 100f, s / 100f)

        when (specialName) {
            "stripeH" -> {
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                shapePaint.color = Color.argb((alpha * 0.85f).toInt(), 255, 255, 255)
                c.drawRoundRect(RectF(14f, 44f, 86f, 56f), 6f, 6f, shapePaint)
                shapePaint.color = Color.argb((alpha * 0.35f).toInt(), 255, 255, 255)
                c.drawRoundRect(RectF(14f, 30f, 86f, 36f), 3f, 3f, shapePaint)
                c.drawRoundRect(RectF(14f, 64f, 86f, 70f), 3f, 3f, shapePaint)
            }
            "stripeV" -> {
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                shapePaint.color = Color.argb((alpha * 0.85f).toInt(), 255, 255, 255)
                c.drawRoundRect(RectF(44f, 14f, 56f, 86f), 6f, 6f, shapePaint)
                shapePaint.color = Color.argb((alpha * 0.35f).toInt(), 255, 255, 255)
                c.drawRoundRect(RectF(30f, 14f, 36f, 86f), 3f, 3f, shapePaint)
                c.drawRoundRect(RectF(64f, 14f, 70f, 86f), 3f, 3f, shapePaint)
            }
            "bomb" -> {
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                shapePaint.color = Color.argb((alpha * 0.85f).toInt(), 25, 20, 40)
                c.drawCircle(50f, 52f, 30f, shapePaint)
                strokePaint.reset()
                strokePaint.isAntiAlias = true
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 6f
                strokePaint.color = Color.argb(alpha, 255, 140, 60)
                c.drawCircle(50f, 52f, 30f, strokePaint)
                shapePaint.color = Color.argb(alpha, 255, 210, 120)
                c.drawCircle(50f, 52f, 7f, shapePaint)
                val spark = Path()
                spark.moveTo(50f, 12f)
                spark.lineTo(54f, 24f)
                spark.lineTo(63f, 28f)
                spark.lineTo(54f, 31f)
                spark.moveTo(50f, 12f)
                spark.lineTo(46f, 24f)
                spark.lineTo(37f, 28f)
                spark.lineTo(46f, 31f)
                strokePaint.strokeWidth = 4f
                strokePaint.color = Color.argb(alpha, 255, 200, 90)
                c.drawPath(spark, strokePaint)
            }
            "rainbow" -> rainbowOrb(c, 50f, 50f, 100f, alpha)
        }
        c.restore()
    }

    private fun rainbowOrb(c: Canvas, cx: Float, cy: Float, size: Float, alpha: Int) {
        val s = size
        c.save()
        c.translate(cx - s / 2f, cy - s / 2f)
        c.scale(s / 100f, s / 100f)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.shader = SweepGradient(
            50f, 50f,
            intArrayOf(
                0xFFFF5C7A.toInt(), 0xFFFFC94D.toInt(), 0xFFC6F36B.toInt(),
                0xFF34C759.toInt(), 0xFF7DD3FC.toInt(), 0xFFA78BFA.toInt(), 0xFFFF5C7A.toInt()
            ),
            null
        )
        c.drawCircle(50f, 50f, 34f, shapePaint)
        shapePaint.shader = null
        shapePaint.color = Color.argb((alpha * 0.85f).toInt(), 255, 255, 255)
        c.drawCircle(50f, 50f, 13f, shapePaint)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 3f
        strokePaint.color = Color.argb((alpha * 0.7f).toInt(), 255, 255, 255)
        c.drawCircle(50f, 50f, 42f, strokePaint)
        c.restore()
    }

    private fun buildFruit(type: Int): FruitArt {
        val art = FruitArt()
        when (type) {
            0 -> { // strawberry
                art.body = Path().apply {
                    moveTo(50f, 94f)
                    cubicTo(38f, 86f, 16f, 68f, 16f, 47f)
                    cubicTo(16f, 35f, 27f, 29f, 37f, 32f)
                    cubicTo(44f, 34f, 48f, 38f, 50f, 44f)
                    cubicTo(52f, 38f, 56f, 34f, 63f, 32f)
                    cubicTo(73f, 29f, 84f, 35f, 84f, 47f)
                    cubicTo(84f, 68f, 62f, 86f, 50f, 94f)
                    close()
                }
                art.shader = RadialGradient(
                    36f, 34f, 66f,
                    intArrayOf(0xFFFF9AA9.toInt(), 0xFFF43F5E.toInt(), 0xFFB31736.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                val seeds = floatArrayOf(
                    38f, 56f, 46f, 66f, 60f, 60f, 64f, 74f, 52f, 80f, 34f, 70f
                )
                var i = 0
                while (i < seeds.size) {
                    val p = Path()
                    p.addOval(RectF(seeds[i] - 2.2f, seeds[i + 1] - 3f, seeds[i] + 2.2f, seeds[i + 1] + 3f), Path.Direction.CW)
                    art.accents.add(p)
                    art.accentColors.add(0xCCFFE9A8.toInt())
                    i += 2
                }
                val leaf = Path().apply {
                    moveTo(50f, 40f)
                    cubicTo(43f, 39f, 40f, 33f, 42f, 27f)
                    cubicTo(47f, 29f, 50f, 32f, 50f, 36f)
                    cubicTo(50f, 32f, 53f, 29f, 58f, 27f)
                    cubicTo(60f, 33f, 57f, 39f, 50f, 40f)
                    close()
                }
                art.accents.add(leaf)
                art.accentColors.add(0xFF3FA34D.toInt())
                val stem = Path().apply {
                    moveTo(50f, 34f)
                    lineTo(50f, 24f)
                }
                val stemStroke = Path(stem)
                art.accents.add(stemStroke)
                art.accentColors.add(0xFF2F7A3A.toInt())
                art.highlight = RectF(24f, 36f, 40f, 48f)
            }
            1 -> { // orange
                art.body = Path().apply {
                    addCircle(50f, 55f, 31f, Path.Direction.CW)
                }
                art.shader = RadialGradient(
                    38f, 42f, 46f,
                    intArrayOf(0xFFFFD08A.toInt(), 0xFFFF9F1C.toInt(), 0xFFD96A06.toInt()),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                val dots = floatArrayOf(
                    36f, 44f, 54f, 40f, 64f, 50f, 44f, 60f, 60f, 68f, 38f, 68f, 52f, 52f, 46f, 74f
                )
                var i = 0
                while (i < dots.size) {
                    val p = Path()
                    p.addCircle(dots[i], dots[i + 1], 1.8f, Path.Direction.CW)
                    art.accents.add(p)
                    art.accentColors.add(0x40B35C00)
                    i += 2
                }
                val leaf = Path().apply {
                    moveTo(58f, 26f)
                    cubicTo(64f, 16f, 76f, 18f, 78f, 24f)
                    cubicTo(72f, 30f, 64f, 30f, 58f, 26f)
                    close()
                }
                art.accents.add(leaf)
                art.accentColors.add(0xFF3FA34D.toInt())
                art.highlight = RectF(28f, 34f, 46f, 48f)
            }
            2 -> { // lemon
                art.body = Path().apply {
                    moveTo(12f, 56f)
                    cubicTo(18f, 36f, 40f, 30f, 50f, 30f)
                    cubicTo(60f, 30f, 82f, 36f, 88f, 56f)
                    cubicTo(82f, 76f, 60f, 82f, 50f, 82f)
                    cubicTo(40f, 82f, 18f, 76f, 12f, 56f)
                    close()
                }
                art.shader = RadialGradient(
                    38f, 44f, 52f,
                    intArrayOf(0xFFFFF7B8.toInt(), 0xFFFFE04D.toInt(), 0xFFDBA400.toInt()),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                val line = Path().apply {
                    moveTo(30f, 48f)
                    cubicTo(42f, 42f, 58f, 42f, 70f, 48f)
                }
                art.accents.add(line)
                art.accentColors.add(0x33B8860B)
                art.highlight = RectF(26f, 38f, 46f, 50f)
            }
            3 -> { // green apple
                art.body = Path().apply {
                    moveTo(50f, 34f)
                    cubicTo(58f, 24f, 76f, 24f, 82f, 36f)
                    cubicTo(88f, 48f, 84f, 68f, 74f, 80f)
                    cubicTo(66f, 89f, 58f, 87f, 50f, 82f)
                    cubicTo(42f, 87f, 34f, 89f, 26f, 80f)
                    cubicTo(16f, 68f, 12f, 48f, 18f, 36f)
                    cubicTo(24f, 24f, 42f, 24f, 50f, 34f)
                    close()
                }
                art.shader = RadialGradient(
                    34f, 34f, 62f,
                    intArrayOf(0xFFD8F9A6.toInt(), 0xFF8FDD4F.toInt(), 0xFF2E9438.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                val stem = Path().apply {
                    moveTo(50f, 32f)
                    cubicTo(50f, 24f, 52f, 18f, 56f, 14f)
                }
                art.accents.add(stem)
                art.accentColors.add(0xFF6B4B2A.toInt())
                val leaf = Path().apply {
                    moveTo(56f, 20f)
                    cubicTo(64f, 12f, 74f, 16f, 76f, 22f)
                    cubicTo(68f, 26f, 60f, 25f, 56f, 20f)
                    close()
                }
                art.accents.add(leaf)
                art.accentColors.add(0xFF3FA34D.toInt())
                art.highlight = RectF(26f, 36f, 44f, 50f)
            }
            4 -> { // grapes
                art.body = Path()
                val balls = floatArrayOf(
                    50f, 40f, 9f, 37f, 48f, 9f, 63f, 48f, 9f, 50f, 57f, 9f,
                    30f, 58f, 8.5f, 70f, 58f, 8.5f, 43f, 67f, 8.5f, 57f, 67f, 8.5f, 50f, 77f, 8f
                )
                var i = 0
                while (i < balls.size) {
                    art.body.addCircle(balls[i], balls[i + 1], balls[i + 2], Path.Direction.CW)
                    i += 3
                }
                art.shader = RadialGradient(
                    40f, 38f, 58f,
                    intArrayOf(0xFFDCC7FF.toInt(), 0xFF8B5CF6.toInt(), 0xFF4C1D95.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                val stem = Path().apply {
                    moveTo(50f, 30f)
                    lineTo(50f, 20f)
                }
                art.accents.add(stem)
                art.accentColors.add(0xFF6B4B2A.toInt())
                val leaf = Path().apply {
                    moveTo(50f, 26f)
                    cubicTo(58f, 18f, 70f, 20f, 72f, 26f)
                    cubicTo(64f, 30f, 56f, 30f, 50f, 26f)
                    close()
                }
                art.accents.add(leaf)
                art.accentColors.add(0xFF3FA34D.toInt())
                art.highlight = RectF(34f, 34f, 50f, 46f)
            }
            5 -> { // eggplant
                art.body = Path().apply {
                    moveTo(50f, 28f)
                    cubicTo(64f, 28f, 76f, 40f, 76f, 57f)
                    cubicTo(76f, 75f, 64f, 89f, 50f, 89f)
                    cubicTo(36f, 89f, 24f, 75f, 24f, 57f)
                    cubicTo(24f, 40f, 36f, 28f, 50f, 28f)
                    close()
                }
                art.shader = RadialGradient(
                    38f, 40f, 58f,
                    intArrayOf(0xFFB7A8FF.toInt(), 0xFF6D5BFF.toInt(), 0xFF32239E.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                val calyx = Path().apply {
                    moveTo(50f, 34f)
                    lineTo(40f, 26f)
                    lineTo(46f, 24f)
                    lineTo(42f, 14f)
                    lineTo(50f, 20f)
                    lineTo(58f, 14f)
                    lineTo(54f, 24f)
                    lineTo(60f, 26f)
                    close()
                }
                art.accents.add(calyx)
                art.accentColors.add(0xFF3FA34D.toInt())
                val stem = Path().apply {
                    moveTo(50f, 18f)
                    lineTo(50f, 8f)
                }
                art.accents.add(stem)
                art.accentColors.add(0xFF2F7A3A.toInt())
                art.highlight = RectF(32f, 40f, 46f, 56f)
            }
            else -> { // cauliflower
                art.body = Path()
                val florets = floatArrayOf(
                    40f, 44f, 16f, 60f, 44f, 16f, 50f, 34f, 15f,
                    32f, 58f, 14f, 68f, 58f, 14f, 50f, 60f, 18f, 50f, 74f, 12f
                )
                var i = 0
                while (i < florets.size) {
                    art.body.addCircle(florets[i], florets[i + 1], florets[i + 2], Path.Direction.CW)
                    i += 3
                }
                art.shader = RadialGradient(
                    44f, 40f, 60f,
                    intArrayOf(0xFFFFFFFA.toInt(), 0xFFF6EBD2.toInt(), 0xFFD9C39A.toInt()),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                val leafL = Path().apply {
                    moveTo(38f, 76f)
                    cubicTo(26f, 74f, 18f, 80f, 16f, 90f)
                    cubicTo(28f, 92f, 38f, 86f, 42f, 78f)
                    close()
                }
                val leafR = Path().apply {
                    moveTo(62f, 76f)
                    cubicTo(74f, 74f, 82f, 80f, 84f, 90f)
                    cubicTo(72f, 92f, 62f, 86f, 58f, 78f)
                    close()
                }
                art.accents.add(leafL)
                art.accents.add(leafR)
                art.accentColors.add(0xFF3FA34D.toInt())
                art.accentColors.add(0xFF48B357.toInt())
                art.highlight = RectF(34f, 30f, 52f, 44f)
            }
        }
        return art
    }
}
