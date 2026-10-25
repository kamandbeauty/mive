package ir.mivehchin.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * "لاله" — the garden helper. She is the game's mascot: she appears on the home
 * screen, as a small guide portrait during play, celebrates on the victory panel
 * and sympathises on the failure panel. Everything is drawn with primitives so
 * she stays crisp at any density and needs no asset files.
 *
 * The whole character is defined inside a box of [height] pixels; [cy] is her
 * feet. Proportions are fixed so she never changes identity between screens.
 */
internal object Helper {

    enum class Mood { IDLE, CHEER, SAD }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private const val SKIN = 0xFFFFDDBB.toInt()
    private const val SKIN_SHADE = 0xFFE8B98F.toInt()
    private const val HAIR = 0xFF6B3E1E.toInt()
    private const val HAIR_LIGHT = 0xFF9A5F32.toInt()
    private const val STRAW = 0xFFF2C25B.toInt()
    private const val STRAW_DARK = 0xFFC08B1E.toInt()
    private const val APRON = 0xFFFFF6E5.toInt()
    private const val DRESS = 0xFF4FA64A.toInt()
    private const val DRESS_DARK = 0xFF2C7534.toInt()
    private const val EYE = 0xFF3A2415.toInt()
    private const val BLUSH = 0x66FF7B96

    /**
     * Draws Laleh with her feet at ([cx], [cy]).
     *
     * @param height total pixel height from the top of her hat to the soles.
     * @param bob vertical breathing offset in pixels (0 for none).
     */
    fun draw(c: Canvas, cx: Float, cy: Float, height: Float, mood: Mood, t: Float, bob: Float = 0f) {
        if (height <= 4f) return
        val h = height
        val u = h / 100f          // one unit = 1% of her height
        val feet = cy - bob
        val headR = 15f * u
        val headCy = feet - 58f * u
        val blink = if (mood == Mood.IDLE && (t % 3.6f) < 0.12f) 0.12f else 1f

        /* ------------------------------------------------------------- legs */
        p.color = SKIN_SHADE
        c.drawRoundRect(
            RectF(cx - 7.2f * u, feet - 22f * u, cx - 1.6f * u, feet - 2f * u),
            3f * u, 3f * u, p
        )
        c.drawRoundRect(
            RectF(cx + 1.6f * u, feet - 22f * u, cx + 7.2f * u, feet - 2f * u),
            3f * u, 3f * u, p
        )
        // boots
        p.color = STRAW_DARK
        c.drawRoundRect(RectF(cx - 8.6f * u, feet - 6f * u, cx - 0.8f * u, feet), 2.6f * u, 2.6f * u, p)
        c.drawRoundRect(RectF(cx + 0.8f * u, feet - 6f * u, cx + 8.6f * u, feet), 2.6f * u, 2.6f * u, p)

        /* ------------------------------------------------------------ dress */
        val skirt = Path()
        skirt.moveTo(cx - 11f * u, feet - 34f * u)
        skirt.lineTo(cx + 11f * u, feet - 34f * u)
        skirt.lineTo(cx + 19f * u, feet - 17f * u)
        skirt.quadTo(cx, feet - 11f * u, cx - 19f * u, feet - 17f * u)
        skirt.close()
        p.color = DRESS
        c.drawPath(skirt, p)

        p.color = DRESS_DARK
        c.drawRoundRect(RectF(cx - 11.4f * u, feet - 40f * u, cx + 11.4f * u, feet - 26f * u), 6f * u, 6f * u, p)

        // apron
        p.color = APRON
        val apron = Path()
        apron.moveTo(cx - 7f * u, feet - 33f * u)
        apron.lineTo(cx + 7f * u, feet - 33f * u)
        apron.lineTo(cx + 10f * u, feet - 15f * u)
        apron.quadTo(cx, feet - 11.5f * u, cx - 10f * u, feet - 15f * u)
        apron.close()
        c.drawPath(apron, p)

        // leaf pocket
        p.color = 0x33407A2C
        c.drawCircle(cx, feet - 24f * u, 4.6f * u, p)

        /* ------------------------------------------------------------- arms */
        stroke.strokeWidth = 5.4f * u
        stroke.color = SKIN
        val armLift = when (mood) {
            Mood.CHEER -> -34f
            Mood.SAD -> 8f
            else -> 0f
        }
        val swing = if (mood == Mood.IDLE) sin(t * 1.6f) * 2f else 0f
        c.drawLine(cx - 10f * u, feet - 38f * u, cx - 17f * u, feet - (20f + armLift + swing) * u, stroke)
        c.drawLine(cx + 10f * u, feet - 38f * u, cx + 17f * u, feet - (20f + armLift - swing) * u, stroke)
        p.color = SKIN
        c.drawCircle(cx - 17f * u, feet - (19.4f + armLift + swing) * u, 3.1f * u, p)
        c.drawCircle(cx + 17f * u, feet - (19.4f + armLift - swing) * u, 3.1f * u, p)

        /* ------------------------------------------------------- neck + head */
        p.color = SKIN_SHADE
        c.drawRoundRect(RectF(cx - 3.4f * u, headCy + 10f * u, cx + 3.4f * u, feet - 38f * u), 2f * u, 2f * u, p)

        // hair back
        p.color = HAIR
        c.drawCircle(cx, headCy, headR * 1.06f, p)
        c.drawRoundRect(
            RectF(cx - headR * 0.92f, headCy, cx + headR * 0.92f, headCy + headR * 1.15f),
            headR * 0.7f, headR * 0.7f, p
        )

        // face
        p.color = SKIN
        c.drawCircle(cx, headCy + 1.2f * u, headR * 0.92f, p)

        // fringe
        p.color = HAIR
        val fringe = Path()
        fringe.moveTo(cx - headR * 0.96f, headCy - headR * 0.10f)
        fringe.quadTo(cx - headR * 0.45f, headCy - headR * 0.92f, cx + headR * 0.10f, headCy - headR * 0.62f)
        fringe.quadTo(cx + headR * 0.55f, headCy - headR * 0.42f, cx + headR * 0.96f, headCy - headR * 0.24f)
        fringe.quadTo(cx, headCy - headR * 0.10f, cx - headR * 0.96f, headCy - headR * 0.10f)
        fringe.close()
        c.drawPath(fringe, p)
        p.color = HAIR_LIGHT
        c.drawCircle(cx - headR * 0.34f, headCy - headR * 0.52f, headR * 0.12f, p)

        // side locks
        p.color = HAIR
        c.drawCircle(cx - headR * 0.98f, headCy + headR * 0.30f, headR * 0.28f, p)
        c.drawCircle(cx + headR * 0.98f, headCy + headR * 0.30f, headR * 0.28f, p)

        /* ----------------------------------------------------------- face */
        val eyeY = headCy + 1.6f * u
        val eyeDx = headR * 0.40f
        when (mood) {
            Mood.CHEER -> {
                // happy arcs
                stroke.strokeWidth = 2.4f * u
                stroke.color = EYE
                c.drawArc(
                    RectF(cx - eyeDx - 2.6f * u, eyeY - 2.4f * u, cx - eyeDx + 2.6f * u, eyeY + 2.0f * u),
                    200f, 140f, false, stroke
                )
                c.drawArc(
                    RectF(cx + eyeDx - 2.6f * u, eyeY - 2.4f * u, cx + eyeDx + 2.6f * u, eyeY + 2.0f * u),
                    200f, 140f, false, stroke
                )
            }
            Mood.SAD -> {
                p.color = EYE
                c.drawCircle(cx - eyeDx, eyeY + 0.6f * u, 2.2f * u, p)
                c.drawCircle(cx + eyeDx, eyeY + 0.6f * u, 2.2f * u, p)
                // droopy lids
                stroke.strokeWidth = 1.8f * u
                stroke.color = HAIR
                c.drawLine(cx - eyeDx - 3f * u, eyeY - 3f * u, cx - eyeDx + 3f * u, eyeY - 4.2f * u, stroke)
                c.drawLine(cx + eyeDx - 3f * u, eyeY - 4.2f * u, cx + eyeDx + 3f * u, eyeY - 3f * u, stroke)
            }
            else -> {
                p.color = EYE
                c.drawOval(
                    RectF(cx - eyeDx - 2.1f * u, eyeY - 3.0f * u * blink, cx - eyeDx + 2.1f * u, eyeY + 3.0f * u * blink),
                    p
                )
                c.drawOval(
                    RectF(cx + eyeDx - 2.1f * u, eyeY - 3.0f * u * blink, cx + eyeDx + 2.1f * u, eyeY + 3.0f * u * blink),
                    p
                )
                if (blink > 0.5f) {
                    p.color = 0xCCFFFFFF.toInt()
                    c.drawCircle(cx - eyeDx - 0.7f * u, eyeY - 1.2f * u, 0.75f * u, p)
                    c.drawCircle(cx + eyeDx - 0.7f * u, eyeY - 1.2f * u, 0.75f * u, p)
                }
            }
        }

        // blush
        p.color = BLUSH
        c.drawCircle(cx - headR * 0.62f, eyeY + 3.2f * u, 2.5f * u, p)
        c.drawCircle(cx + headR * 0.62f, eyeY + 3.2f * u, 2.5f * u, p)

        // mouth
        stroke.strokeWidth = 1.9f * u
        stroke.color = 0xFF8A3325.toInt()
        when (mood) {
            Mood.CHEER -> c.drawArc(
                RectF(cx - 3.8f * u, eyeY + 3.2f * u, cx + 3.8f * u, eyeY + 8.4f * u), 10f, 160f, false, stroke
            )
            Mood.SAD -> c.drawArc(
                RectF(cx - 3.4f * u, eyeY + 5.4f * u, cx + 3.4f * u, eyeY + 9.6f * u), 200f, 140f, false, stroke
            )
            else -> c.drawArc(
                RectF(cx - 3.0f * u, eyeY + 3.6f * u, cx + 3.0f * u, eyeY + 7.4f * u), 20f, 140f, false, stroke
            )
        }

        /* -------------------------------------------------------------- hat */
        val hatY = headCy - headR * 0.72f
        p.color = STRAW
        c.drawOval(
            RectF(cx - headR * 1.55f, hatY - 3.4f * u, cx + headR * 1.55f, hatY + 4.6f * u), p
        )
        p.color = STRAW_DARK
        c.drawOval(
            RectF(cx - headR * 1.55f, hatY + 1.2f * u, cx + headR * 1.55f, hatY + 4.6f * u), p
        )
        p.color = STRAW
        c.drawRoundRect(
            RectF(cx - headR * 0.80f, hatY - headR * 0.72f, cx + headR * 0.80f, hatY + 1.6f * u),
            headR * 0.46f, headR * 0.46f, p
        )
        p.color = 0xFFE8546B.toInt()
        c.drawRoundRect(
            RectF(cx - headR * 0.82f, hatY - headR * 0.20f, cx + headR * 0.82f, hatY + 1.2f * u),
            headR * 0.18f, headR * 0.18f, p
        )
        Art.leaf(c, cx + headR * 0.62f, hatY - headR * 0.30f, headR * 0.52f,
            Palette.LEAF_DARK, Palette.LEAF_LIGHT, 32f)

        // cheer sparkles
        if (mood == Mood.CHEER) {
            for (i in 0 until 3) {
                val a = t * 2.2f + i * 2.09f
                Art.sparkle(
                    c, cx + (kotlin.math.cos(a) * h * 0.30f), headCy + (sin(a) * h * 0.18f),
                    h * 0.028f, Palette.GOLD, 0.55f + 0.35f * sin(t * 5f + i), t * 90f
                )
            }
        }
    }

    /** Bust-only portrait for HUD chips: circle framing, no legs. */
    fun portrait(c: Canvas, cx: Float, cy: Float, r: Float, mood: Mood, t: Float) {
        p.color = 0x5510311F
        c.drawCircle(cx + r * 0.05f, cy + r * 0.07f, r, p)
        p.color = 0xFF2B6B49.toInt()
        c.drawCircle(cx, cy, r, p)
        Art.circle(c, cx, cy - r * 0.04f, r * 0.92f, 0xFF3E8A5C.toInt())
        c.save()
        val clip = Path()
        clip.addCircle(cx, cy, r * 0.94f, Path.Direction.CW)
        c.clipPath(clip)
        draw(c, cx, cy + r * 1.34f, r * 2.5f, mood, t)
        c.restore()
        stroke.strokeWidth = Math.max(1.6f, r * 0.10f)
        stroke.color = Art.withAlpha(Palette.GOLD, 0.85f)
        c.drawCircle(cx, cy, r * 0.97f, stroke)
    }
}
