package ir.mivehchin.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import ir.mivehchin.game.GameView.Hotspot
import ir.mivehchin.game.GameView.Style
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun fmtCoins(v: Int): String = fa(v)

/** Persian digits, so numbers match the rest of the UI. */
internal fun faDigits(src: String): String {
    val out = StringBuilder(src.length)
    for (ch in src) out.append(if (ch in '0'..'9') ('\u06F0' + (ch - '0')) else ch)
    return out.toString()
}

internal fun fa(value: Int): String = faDigits(value.toString())

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return faDigits(m.toString() + ":" + (if (s < 10) "0$s" else "$s"))
}

/** App version name, shown on the about card. */
private fun GameView.appVersion(): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.0"
} catch (e: Exception) {
    "1.5.0"
}

private fun rrect(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

/** Four-value tuple used by the ornament loops. */
private data class Quad(val a: Float, val b: Float, val c: Float, val d: Int)

/** Vertical scroll position of the star-pass list. */
internal var passScroll = 0

private fun iconFor(kind: String, c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
    when {
        kind.startsWith("hammer") -> Art.hammer(c, cx, cy, r, color)
        kind.startsWith("shuffle") -> Art.shuffleIcon(c, cx, cy, r, color)
        kind.startsWith("moves") -> Art.plusMoves(c, cx, cy, r, color)
        kind.startsWith("life") || kind == "hearts" -> Art.heart(c, cx, cy, r, true, color)
        kind.startsWith("coin") -> Art.coin(c, cx, cy, r)
        else -> Art.star(c, cx, cy, r, true, color)
    }
}

/* ------------------------------------------------------------------ home */

/* --------------------------------------------------------------- entrances */

/** 0..1 progress for one block, staggered [delayMs] after the screen opened. */
private fun GameView.enterK(now: Long, delayMs: Float): Float {
    val age = (now - screenAt).toFloat()
    val k = ((age - delayMs) / 360f).coerceIn(0f, 1f)
    val u = 1f - k
    return 1f - u * u * u
}

private inline fun Canvas.enterGroup(k: Float, pivotX: Float, pivotY: Float, rise: Float, block: () -> Unit) {
    if (k >= 0.999f) {
        block()
        return
    }
    save()
    val sc = 0.90f + 0.10f * k
    translate(0f, (1f - k) * rise)
    scale(sc, sc, pivotX, pivotY)
    block()
    restore()
}

/** Slanted light bar that sweeps across a rounded rect now and then. */
internal fun Canvas.shine(rect: RectF, radius: Float, t: Float, speed: Float, phase: Float, strength: Float) {
    if (strength <= 0.01f) return
    val cycle = 2.8f
    val k = ((t * speed + phase) % cycle) / cycle
    if (k > 0.42f) return
    val span = k / 0.42f
    save()
    val clip = Path()
    clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
    clipPath(clip)
    val x = rect.left - rect.width() * 0.40f + span * rect.width() * 1.8f
    val barW = rect.width() * 0.15f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Art.withAlpha(0xFFFFFFFF.toInt(), strength * (1f - span))
    val wedge = Path()
    wedge.moveTo(x, rect.top)
    wedge.lineTo(x + barW, rect.top)
    wedge.lineTo(x + barW * 0.40f, rect.bottom)
    wedge.lineTo(x - barW * 0.60f, rect.bottom)
    wedge.close()
    drawPath(wedge, paint)
    restore()
}

/** Golden stud. */
internal fun Canvas.stud(x: Float, y: Float, r: Float) {
    Art.circle(this, x, y, r, 0xFF6E4220.toInt())
    Art.circle(this, x - r * 0.08f, y - r * 0.10f, r * 0.72f, Palette.GOLD)
    Art.circle(this, x - r * 0.22f, y - r * 0.28f, r * 0.26f, 0x99FFFFFF.toInt())
}

internal fun GameView.drawHome(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f
    val L = HomeLayout(w, h)
    val reduce = reduceMotion

    // --- sun haze behind the logo --------------------------------------
    if (!reduce) {
        c.save()
        c.rotate(t * 4.5f, w * 0.5f, h * 0.145f)
        val rays = Paint(Paint.ANTI_ALIAS_FLAG)
        rays.color = Art.withAlpha(Palette.GOLD, 0.10f)
        for (i in 0 until 12) {
            val a0 = Math.toRadians(i * 30.0)
            val a1 = Math.toRadians(i * 30.0 + 13.0)
            val p = Path()
            p.moveTo(w * 0.5f, h * 0.145f)
            p.lineTo(w * 0.5f + (cos(a0) * w * 0.9f).toFloat(), h * 0.145f + (sin(a0) * w * 0.9f).toFloat())
            p.lineTo(w * 0.5f + (cos(a1) * w * 0.9f).toFloat(), h * 0.145f + (sin(a1) * w * 0.9f).toFloat())
            p.close()
            c.drawPath(p, rays)
        }
        c.restore()
    }

    /* ------------------------------------------------------ resource chips */
    val chipH = L.settings.height
    val gear = RectF(L.settings.left, L.settings.top, L.settings.right, L.settings.bottom)
    c.enterGroup(enterK(now, 0f), gear.centerX(), gear.centerY(), h * 0.03f) {
        Art.iconButton(c, gear, pressActive && gear.contains(pressX, pressY)) { cc, cx, cy, r ->
            Art.gear(cc, cx, cy, r, Palette.CREAM)
        }
        hotspots.add(Hotspot(RectF(gear), { screen = Screen.SETTINGS }))
    }

    val coinRect = RectF(L.coin.left, L.coin.top, L.coin.right, L.coin.bottom)
    c.enterGroup(enterK(now, 40f), coinRect.centerX(), coinRect.centerY(), h * 0.03f) {
        Art.panel(c, RectF(coinRect.left + 2f, coinRect.top + chipH * 0.10f, coinRect.right + 2f, coinRect.bottom + chipH * 0.10f),
            chipH * 0.42f, 0x55000000)
        Art.gradRR(c, coinRect, chipH * 0.42f,
            intArrayOf(0xFF63AC7C.toInt(), 0xFF2B6B45.toInt(), 0xFF0B2C1F.toInt()))
        Art.bevel(c, coinRect, chipH * 0.42f, chipH * 0.06f, 0.18f, 0.28f)
        Art.gloss(c, coinRect, chipH * 0.42f, 0.50f, 0.24f)
        Art.panel(c, coinRect, chipH * 0.42f, 0x00000000, 1.6f, Art.withAlpha(Palette.GOLD, 0.75f))
        Art.coin(c, coinRect.left + chipH * 0.44f, coinRect.centerY(), chipH * 0.34f)
        val value = fmtCoins(save.coins)
        val size = Art.fit(value, chipH * 0.46f, coinRect.width() - chipH * 1.05f, Art.black)
        Art.text(c, value, coinRect.right - chipH * 0.24f, coinRect.centerY() + size * 0.36f,
            size, Palette.CREAM, Paint.Align.RIGHT, Art.black, true)
        c.shine(coinRect, chipH * 0.42f, t, 0.5f, 0f, 0.20f)
        hotspots.add(Hotspot(RectF(coinRect), { screen = Screen.SHOP }))
    }

    val heartRect = RectF(L.lives.left, L.lives.top, L.lives.right, L.lives.bottom)
    c.enterGroup(enterK(now, 70f), heartRect.centerX(), heartRect.centerY(), h * 0.03f) {
        Art.panel(c, RectF(heartRect.left + 2f, heartRect.top + chipH * 0.10f, heartRect.right + 2f, heartRect.bottom + chipH * 0.10f),
            chipH * 0.42f, 0x55000000)
        Art.gradRR(c, heartRect, chipH * 0.42f,
            intArrayOf(0xFFE2667C.toInt(), 0xFFB93A50.toInt(), 0xFF6E1424.toInt()))
        Art.bevel(c, heartRect, chipH * 0.42f, chipH * 0.06f, 0.18f, 0.28f)
        Art.gloss(c, heartRect, chipH * 0.42f, 0.50f, 0.24f)
        Art.panel(c, heartRect, chipH * 0.42f, 0x00000000, 1.6f, 0x66FF9DAE)
        Art.heart(c, heartRect.left + chipH * 0.44f, heartRect.centerY(), chipH * 0.30f, true, 0xFFFF8FA0.toInt())
        val value = "${fa(save.lives)}/${fa(eco.maxLives)}"
        val size = Art.fit(value, chipH * 0.42f, heartRect.width() - chipH * 1.05f, Art.black)
        Art.text(c, value, heartRect.right - chipH * 0.22f, heartRect.centerY() + size * 0.36f,
            size, Palette.CREAM, Paint.Align.RIGHT, Art.black, true)
        hotspots.add(Hotspot(RectF(heartRect), { buyLife() }))
    }

    /* --------------------------------------------------------------- logo */
    val bob = if (reduce) 0f else sin(t * 1.15f) * h * 0.004f
    val logo = RectF(L.logo.left, L.logo.top + bob, L.logo.right, L.logo.bottom + bob)
    c.enterGroup(enterK(now, 120f), w * 0.5f, logo.centerY(), h * 0.05f) {
        // wooden signboard with a gold crown, drawn like the level plaques
        Art.woodBody(c, logo, if (reduce) 0.1f else t, 0.34f)
        Art.sprout(c, logo.left + logo.height() * 0.30f, logo.bottom - logo.height() * 0.10f,
            logo.height() * 0.46f, 210f, Palette.LEAF_DARK, Palette.LEAF_LIGHT)
        Art.sprout(c, logo.right - logo.height() * 0.30f, logo.bottom - logo.height() * 0.10f,
            logo.height() * 0.46f, 330f, Palette.LEAF_DARK, Palette.LEAF_LIGHT)
        val titleSize = Art.fit("میوه‌چین", w * 0.112f, logo.width() * 0.60f, Art.black)
        Art.text3d(c, "میوه‌چین", logo.centerX(), logo.centerY() + titleSize * 0.36f, titleSize,
            0xFFFFD24A.toInt(), 0xFF4A2508.toInt(), Art.black)
        if (!reduce) {
            c.save()
            c.scale(1f + 0.02f * sin(t * 1.6f), 1f + 0.02f * sin(t * 1.6f),
                logo.centerX(), logo.top)
            c.crown(logo.centerX(), logo.top - logo.height() * 0.22f,
                logo.height() * 0.62f, logo.height() * 0.46f)
            c.restore()
        } else {
            c.crown(logo.centerX(), logo.top - logo.height() * 0.22f,
                logo.height() * 0.62f, logo.height() * 0.46f)
        }
    }

    c.enterGroup(enterK(now, 180f), w * 0.5f, L.taglineY, h * 0.03f) {
        val tag = "سه‌تا کنار هم بچین، باغ را پر کن!"
        val size = Art.fit(tag, w * 0.032f, w * 0.78f, Art.bold)
        Art.text(c, tag, w * 0.5f, L.taglineY, size, 0xFFF4E9D2.toInt(), Paint.Align.CENTER, Art.bold, true)
    }

    /* ------------------------------------------------------------- helper */
    val girlH = L.helperH
    val girlCx = w * 0.5f
    val girlFeet = L.hero.bottom - h * 0.006f
    c.enterGroup(enterK(now, 240f), girlCx, girlFeet - girlH * 0.5f, h * 0.05f) {
        Art.glow(c, girlCx, girlFeet - girlH * 0.42f, girlH * 0.72f, Palette.LIME, 0.16f)
        // a soft mound of grass she stands on
        Art.panel(c, RectF(girlCx - girlH * 0.46f, girlFeet - girlH * 0.045f,
            girlCx + girlH * 0.46f, girlFeet + girlH * 0.035f), girlH * 0.05f, 0x882B6B45.toInt())
        Helper.draw(c, girlCx, girlFeet, girlH, Helper.Mood.IDLE, if (reduce) 0f else t,
            if (reduce) 0f else sin(t * 1.6f) * girlH * 0.012f)

        // produce resting on the grass either side of her, never over her body
        val types = intArrayOf(0, 2, 4, 6)
        val spots = floatArrayOf(-0.36f, 0.36f, -0.44f, 0.44f)
        val lifts = floatArrayOf(0.30f, 0.30f, 0.44f, 0.44f)
        val sizes = floatArrayOf(0.20f, 0.20f, 0.15f, 0.15f)
        for (i in types.indices) {
            val x = girlCx + w * spots[i]
            val bob = if (reduce) 0f else sin(t * 1.5f + i) * girlH * 0.010f
            val y = girlFeet - girlH * lifts[i] + bob
            val r = girlH * sizes[i]
            Art.glow(c, x, y, r * 0.95f, Art.produceColor(types[i]), 0.22f)
            Art.produce(c, types[i], x, y, r)
            Art.sparkle(c, x + r * 0.86f, y - r * 0.86f, w * 0.011f, 0xFFFFFFFF.toInt(),
                if (reduce) 0.5f else 0.35f + 0.4f * sin(t * 2.4f + i * 1.3f), t * 70f)
        }
    }

    /* ------------------------------------------------------- primary action */
    val playRect = RectF(L.play.left, L.play.top, L.play.right, L.play.bottom)
    c.enterGroup(enterK(now, 300f), playRect.centerX(), playRect.centerY(), h * 0.05f) {
        button(c, playRect, "بازی", Style.PRIMARY,
            { cc, cx, cy, r -> Art.play(cc, cx + r * 0.1f, cy, r, Design.INK_ON_LIME) }) {
            if (save.level - 1 < levels.size) startLevel(save.level - 1) else screen = Screen.MAP
        }
    }
    c.enterGroup(enterK(now, 360f), w * 0.5f, L.captionY, h * 0.03f) {
        val next = levels.getOrNull(save.level - 1)
        val cap = if (next != null) "ادامه از سطح ${fa(next.id)}" else "همهٔ سطح‌ها تمام شد"
        val size = Art.fit(cap, w * 0.032f, w * 0.72f, Art.regular)
        Art.text(c, cap, w * 0.5f, L.captionY, size, Palette.CREAM_DIM, Paint.Align.CENTER, Art.regular, true)
    }

    /* ---------------------------------------------------------------- dock */
    val labels = listOf("نقشه", "فروشگاه", "گردونه", "جایزه‌ها")
    val dests = listOf(Screen.MAP, Screen.SHOP, Screen.WHEEL, Screen.PASS)
    val icons: List<(Canvas, Float, Float, Float) -> Unit> = listOf(
        { cc, x, y, r -> Art.mapIcon(cc, x, y, r, Palette.CREAM) },
        { cc, x, y, r -> Art.cart(cc, x, y, r, Palette.CREAM) },
        { cc, x, y, r -> Art.wheelIcon(cc, x, y, r, Palette.CREAM) },
        { cc, x, y, r -> Art.star(cc, x, y, r, true, Palette.GOLD) }
    )
    for (i in labels.indices) {
        val b = L.tiles[i]
        val rect = RectF(b.left, b.top, b.right, b.bottom)
        c.enterGroup(enterK(now, 420f + i * 60f), rect.centerX(), rect.centerY(), h * 0.05f) {
            val pressed = pressActive && rect.contains(pressX, pressY)
            val body = if (pressed) RectF(rect.left, rect.top + rect.height() * 0.05f, rect.right, rect.bottom) else rect
            val rad = rect.width() * 0.26f
            if (!pressed) {
                Art.panel(c, RectF(rect.left, rect.top + rect.height() * 0.07f, rect.right, rect.bottom + rect.height() * 0.07f),
                    rad, 0x55000000)
            }
            Art.gradRR(c, body, rad, intArrayOf(Design.GLASS_TOP, Design.GLASS_BOTTOM, 0xFF0A2A1E.toInt()))
            Art.bevel(c, body, rad, rect.width() * 0.022f, 0.20f, 0.26f)
            Art.gloss(c, body, rad, 0.48f, 0.20f)
            Art.panel(c, body, rad, 0x00000000, 1.8f, Art.withAlpha(Palette.GOLD, 0.55f))
            val plateR = rect.width() * 0.27f
            val cy = body.top + rect.height() * 0.40f
            Art.circle(c, body.centerX(), cy, plateR, 0x5532A05C)
            Art.gradRR(c, RectF(body.centerX() - plateR, cy - plateR, body.centerX() + plateR, cy + plateR),
                plateR, intArrayOf(0xFF63C08A.toInt(), 0xFF2F7A4E.toInt(), 0xFF14523A.toInt()))
            Art.panel(c, RectF(body.centerX() - plateR, cy - plateR, body.centerX() + plateR, cy + plateR),
                plateR, 0x00000000, Math.max(1.2f, plateR * 0.10f), 0x88FFE0A8.toInt())
            icons[i](c, body.centerX(), cy, plateR * 0.62f)
            val label = labels[i]
            val size = Art.fit(label, w * 0.030f, rect.width() * 0.80f, Art.bold)
            Art.text(c, label, body.centerX(), body.bottom - rect.height() * 0.13f, size,
                Palette.CREAM, Paint.Align.CENTER, Art.bold, true)
            hotspots.add(Hotspot(RectF(rect), { screen = dests[i] }))
        }
    }
}

internal fun GameView.drawMap(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f

    header(c, "نقشهٔ سطح‌ها")

    val top = h * 0.152f
    val bottom = h * 0.935f
    val cols = 4
    val gap = w * 0.032f
    val size = (w - gap * (cols + 1)) / cols
    val rows = ceil(levels.size.toFloat() / cols).toInt()
    val contentH = rows * (size + gap) + gap
    val viewH = bottom - top
    val maxScroll = max(0f, contentH - viewH)
    mapScroll = mapScroll.coerceIn(0f, maxScroll)

    c.save()
    c.clipRect(0f, top - h * 0.01f, w, bottom)

    // winding trail with travelling dashes
    if (levels.size > 1) {
        val pts = ArrayList<Float>()
        for (i in levels.indices) {
            val row = i / cols
            val col = i % cols
            val x = w - gap - (col + 1) * size - col * gap
            val y = top + gap + row * (size + gap) - mapScroll
            pts.add(x + size / 2f)
            pts.add(y + size / 2f)
        }
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 3 < pts.size) {
            val x1 = pts[i]
            val y1 = pts[i + 1]
            val x2 = pts[i + 2]
            val y2 = pts[i + 3]
            val mx = (x1 + x2) / 2f
            path.quadTo(mx, y1, mx, (y1 + y2) / 2f)
            path.quadTo(mx, y2, x2, y2)
            i += 2
        }
        val lp = Paint(Paint.ANTI_ALIAS_FLAG)
        lp.style = Paint.Style.STROKE
        lp.strokeCap = Paint.Cap.ROUND
        lp.strokeJoin = Paint.Join.ROUND
        lp.strokeWidth = size * 0.34f
        lp.color = 0x4410311F
        c.drawPath(path, lp)
        lp.strokeWidth = size * 0.13f
        lp.color = 0x99F6E7C4.toInt()
        lp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(size * 0.13f, size * 0.17f), -t * size * 0.32f)
        c.drawPath(path, lp)
        lp.pathEffect = null
    }

    // --- world milestones: a wooden sign behind the first row of a new garden
    val perWorld = 12
    for (i in levels.indices) {
        if (i == 0 || i % perWorld != 0) continue
        val row = i / cols
        val rowY = top + gap + row * (size + gap) - mapScroll
        if (rowY > bottom || rowY + size < top) continue
        val signH = h * 0.046f
        val sign = RectF(w * 0.30f, rowY + size * 0.30f, w * 0.70f, rowY + size * 0.30f + signH)
        val garden = i / perWorld + 1
        Art.woodTitle(c, sign, "باغ ${fa(garden)}", signH * 0.44f, if (reduceMotion) 0.1f else t, 0.20f)
    }

    for (i in levels.indices) {
        val row = i / cols
        val col = i % cols
        val x = w - gap - (col + 1) * size - col * gap
        val y = top + gap + row * (size + gap) - mapScroll
        if (y + size < top - size || y > bottom + size) continue

        val lvl = levels[i]
        val unlocked = lvl.id <= save.level
        val stars = save.starsFor(lvl.id)
        val rect = RectF(x, y, x + size, y + size)
        val current = lvl.id == save.level
        val passed = stars > 0
        val k = enterK(now, 40f + (i % cols) * 55f + (i / cols) * 20f)

        c.enterGroup(k, rect.centerX(), rect.centerY(), h * 0.04f) {
            val socket = RectF(rect.left + size * 0.05f, rect.top + size * 0.05f,
                rect.right - size * 0.05f, rect.bottom - size * 0.05f)
            when {
                current -> {
                    val pulse = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(t * 3.0f)
                    Art.glow(c, rect.centerX(), rect.centerY(), size * 1.02f, Palette.LIME, 0.24f + 0.18f * pulse)
                }
                passed -> Art.glow(c, rect.centerX(), rect.centerY(), size * 0.80f, Palette.GOLD, 0.14f)
            }

            if (!unlocked) {
                Art.panel(c, RectF(rect.left, rect.top + size * 0.07f, rect.right, rect.bottom + size * 0.07f),
                    size * 0.30f, 0x44000000)
                Art.panel(c, rect, size * 0.30f, 0x8C0E2A1E.toInt(), 1.6f, 0x33FFFFFF)
                Art.panel(c, socket, size * 0.26f, 0x33000000)
                Art.lock(c, rect.centerX(), rect.centerY() - size * 0.05f, size * 0.20f, 0x99FFF6E5.toInt())
                Art.text(c, fa(lvl.id), rect.centerX(), rect.bottom - size * 0.13f, size * 0.16f, 0x77FFF6E5)
                return@enterGroup
            }

            // carved wooden level badge
            Art.panel(c, RectF(rect.left, rect.top + size * 0.08f, rect.right, rect.bottom + size * 0.08f),
                size * 0.30f, 0x55000000)
            Art.nodeBadge(c, rect, size * 0.30f, current)
            Art.panel(c, socket, size * 0.25f, if (current) 0x332EA96A else 0x3310311F)
            Art.panel(c, socket, size * 0.25f, 0x00000000, 1.2f, 0x33FFF6E5)

            val numSize = Art.fit(fa(lvl.id), size * 0.38f, size * 0.60f, Art.black)
            Art.text(c, fa(lvl.id), rect.centerX(), rect.centerY() + numSize * 0.12f, numSize,
                Palette.CREAM, Paint.Align.CENTER, Art.black, true)

            for (sIdx in 0 until 3) {
                Art.starSocket(c, rect.centerX() + (sIdx - 1) * size * 0.225f, rect.bottom - size * 0.205f,
                    size * 0.082f, sIdx < stars)
            }
            if (current) {
                // Laleh marks where the player is
                Helper.portrait(c, rect.right - size * 0.06f, rect.top + size * 0.06f, size * 0.20f,
                    Helper.Mood.IDLE, if (reduceMotion) 0f else t)
            }
            hotspots.add(Hotspot(RectF(rect), { startLevel(i) }))
        }
    }
    c.restore()

    val footer = RectF(w * 0.28f, h * 0.946f, w * 0.72f, h * 0.986f)
    Art.panel(c, footer, footer.height() / 2f, 0x4D0A2418)
    Art.text(c, "بکش تا پایین بروی", w * 0.5f, h * 0.970f, w * 0.028f,
        Palette.CREAM_DIM, Paint.Align.CENTER, Art.regular, true)
}

internal fun GameView.drawGame(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val g = game ?: return
    val lvl = g.level
    val now = System.currentTimeMillis()
    val t = now / 1000f
    val hasGoals = lvl.collect.isNotEmpty() || g.iceLeft > 0
    val hud = HudGeometry(w, h, hasGoals)

    /* ----------------------------------------------------------- top chrome */
    val back = RectF(hud.back.left, hud.back.top, hud.back.right, hud.back.bottom)
    Art.iconButton(c, back, pressActive && back.contains(pressX, pressY)) { cc, cx, cy, r ->
        Art.chevronRight(cc, cx, cy, r * 1.1f, Palette.CREAM)
    }
    hotspots.add(Hotspot(RectF(back), { paused = true }))

    val plaque = RectF(hud.title.left, hud.title.top, hud.title.right, hud.title.bottom)
    Art.woodTitle(c, plaque, "سطح ${fa(lvl.id)}", plaque.height() * 0.40f, if (reduceMotion) 0.1f else t, 0.24f)
    val nameSize = Art.fit(lvl.name, plaque.height() * 0.22f, plaque.width() * 0.74f, Art.bold)
    Art.text(c, lvl.name, plaque.centerX(), plaque.bottom - plaque.height() * 0.13f, nameSize,
        0xFFFFF0D2.toInt(), Paint.Align.CENTER, Art.bold)

    Art.coinChip(c, RectF(hud.coin.left, hud.coin.top, hud.coin.right, hud.coin.bottom), save.coins, t)

    /* -------------------------------------------------------- moves + score */
    val movesRect = RectF(hud.moves.left, hud.moves.top, hud.moves.right, hud.moves.bottom)
    val lowMoves = g.moves in 1..5 && g.status == Status.PLAYING
    val movePulse = if (lowMoves && !reduceMotion) 0.5f + 0.5f * sin(now / 170f) else 0f
    Art.hudCard(
        c, movesRect, "حرکت", fa(g.moves),
        if (lowMoves) Palette.CORAL else Palette.LIME,
        if (g.moves <= 5) Palette.CORAL else Palette.CREAM,
        glowAmount = if (lowMoves) 0.20f + 0.18f * movePulse else 0f
    )

    val scoreRect = RectF(hud.score.left, hud.score.top, hud.score.right, hud.score.bottom)
    val rad = scoreRect.height() * 0.32f
    Art.panel(c, RectF(scoreRect.left + 2f, scoreRect.top + scoreRect.height() * 0.10f,
        scoreRect.right + 2f, scoreRect.bottom + scoreRect.height() * 0.10f), rad, 0x55000000)
    if (!Art.skinPanel(c, scoreRect, "card_glass", 52f)) {
        Art.gradRR(c, scoreRect, rad,
            intArrayOf(Art.lighten(Design.GLASS_TOP, 0.10f), Design.GLASS_TOP, Design.GLASS_BOTTOM))
        Art.bevel(c, scoreRect, rad, scoreRect.height() * 0.05f, 0.18f, 0.22f)
        Art.gloss(c, scoreRect, rad, 0.46f, 0.20f)
        Art.panel(c, scoreRect, rad, 0x00000000, 1.6f, Art.withAlpha(Palette.GOLD, 0.55f))
    }

    val bar = RectF(
        scoreRect.left + scoreRect.width() * 0.045f,
        scoreRect.bottom - scoreRect.height() * 0.26f,
        scoreRect.right - scoreRect.width() * 0.045f,
        scoreRect.bottom - scoreRect.height() * 0.11f
    )
    val frac = if (lvl.target > 0) g.score.toFloat() / lvl.target else 0f
    Art.progress(c, bar, frac, Palette.GREEN, 0x66081A12)

    // RTL: the running score hangs on the right (start), the target on the left (end)
    val scoreLabel = fa(g.score)
    val scoreSize = Art.fit(scoreLabel, scoreRect.height() * 0.34f, scoreRect.width() * 0.34f, Art.black)
    Art.text(c, scoreLabel, scoreRect.right - scoreRect.width() * 0.045f,
        scoreRect.top + scoreRect.height() * 0.40f, scoreSize, Palette.CREAM, Paint.Align.RIGHT, Art.black, true)
    val targetLabel = "هدف ${fa(lvl.target)}"
    val targetSize = Art.fit(targetLabel, scoreRect.height() * 0.24f, scoreRect.width() * 0.38f, Art.bold)
    Art.text(c, targetLabel, scoreRect.left + scoreRect.width() * 0.045f,
        scoreRect.top + scoreRect.height() * 0.38f, targetSize, Palette.CREAM_DIM, Paint.Align.LEFT, Art.bold)

    if (hasGoals) {
        drawObjectives(c, g, hud.goals.top, RectF(hud.goals.left, hud.goals.top, hud.goals.right, hud.goals.bottom))
    }

    /* ---------------------------------------------------------------- board */
    val shape = framePath
    c.save()
    val sh = shakeOffset(now)
    if (sh != 0f) c.translate(sh, 0f)

    if (shape != null) {
        Art.woodShape(c, shape, frameBounds, cellSize * 0.28f)
    } else {
        Art.panel(c, boardRect, cellSize * 0.22f, 0xFF8A5528.toInt())
    }

    for (r in 0 until g.rows) {
        for (col in 0 until g.cols) {
            if (!g.playable(r, col)) continue
            val x = boardRect.left + col * cellSize
            val y = boardRect.top + r * cellSize
            val cellRect = RectF(
                x + cellSize * 0.035f, y + cellSize * 0.035f,
                x + cellSize * 0.965f, y + cellSize * 0.965f
            )
            Art.well(c, cellRect, cellSize * 0.22f)
            if (g.ice[r][col] > 0) Art.ice(c, cellRect, 1f)
        }
    }

    val sel = selected
    if (sel != null && !busy) {
        val pulse = 0.5f + 0.5f * sin(now / 130f)
        val x = boardRect.left + sel.c * cellSize
        val y = boardRect.top + sel.r * cellSize
        val ringRect = RectF(x + cellSize * 0.015f, y + cellSize * 0.015f, x + cellSize * 0.985f, y + cellSize * 0.985f)
        Art.glow(c, ringRect.centerX(), ringRect.centerY(), cellSize * 0.86f, Palette.LIME, 0.18f + 0.16f * pulse)
        Art.panel(c, ringRect, cellSize * 0.24f, 0x00000000, 3f + pulse * 2f,
            Art.withAlpha(Palette.LIME, 0.62f + 0.38f * pulse))
    }

    val seconds = (now % 1000000L) / 1000f
    c.save()
    c.clipRect(frameBounds)
    for (sp in sprites.values) {
        val cx = boardRect.left + sp.x + cellSize / 2f
        val cy = boardRect.top + sp.y + cellSize / 2f
        var alpha = 255
        var scale = 1f
        var sx = 1f
        var sy = 1f

        if (sp.popStart > 0L) {
            val k = ((now - sp.popStart).toFloat() / 220f).coerceIn(0f, 1f)
            scale = 1f + 0.34f * sin(k * Math.PI.toFloat())
            alpha = (255 * (1f - k * k)).toInt().coerceIn(0, 255)
        }
        if (sp.spawnAt > 0L && now - sp.spawnAt < 260L) {
            val k = ((now - sp.spawnAt).toFloat() / 260f).coerceIn(0f, 1f)
            scale *= 0.84f + 0.16f * k
        }
        if (sp.landAt > 0L && now - sp.landAt < 240L) {
            val k = ((now - sp.landAt).toFloat() / 240f).coerceIn(0f, 1f)
            val squash = sin(k * Math.PI.toFloat()) * 0.14f
            sx = 1f + squash * 0.6f
            sy = 1f - squash
        }
        val idle = if (!busy && g.status == Status.PLAYING && sp.dur == 0L) {
            sin(seconds * 2f + sp.phase) * 0.012f
        } else 0f
        if (sel != null && sel.r == sp.r && sel.c == sp.c) scale *= 1.05f

        val type = sp.tile.type
        c.save()
        c.translate(cx, cy + idle * cellSize)
        if (type >= 0 && alpha > 30) {
            Art.produceHalo(c, type, 0f, 0f, cellSize * 0.98f)
        }
        if (alpha > 200) {
            Art.produceShadow(c, 0f, cellSize * 0.28f, cellSize * 0.60f)
        }
        c.scale(scale * sx, scale * sy)
        if (type >= 0) {
            Art.produce(c, type, 0f, -cellSize * 0.01f, cellSize * 0.88f, alpha)
        }
        val special = sp.tile.special
        if (special != null && alpha > 30) {
            Art.special(c, special, 0f, 0f, cellSize * 0.86f, alpha, seconds * 0.6f + sp.phase)
        }
        c.restore()
    }
    c.restore()

    drawEffects(c, now)
    c.restore()

    drawBoosters(c, g)
    drawCombo(c, now)
    drawIntro(c, now, lvl)

    val flashAge = now - flashAt
    if (flashAge in 0..230) {
        val k = flashAge / 230f
        val paint = Paint()
        paint.color = Art.withAlpha(flashColor, 0.30f * (1f - k))
        c.drawRect(0f, 0f, w, h, paint)
    }
}

/**
 * Combo medallion: a sunburst badge that pops over the board for a moment
 * whenever a cascade chains two or more clears.
 */
private fun GameView.drawCombo(c: Canvas, now: Long) {
    if (comboValue < 2) return
    val age = now - comboAt
    if (age < 0L || age > 980L) return
    val k = age / 980f
    val pop = if (k < 0.18f) 0.40f + (k / 0.18f) * 0.66f else 1.06f - 0.06f * ((k - 0.18f) / 0.82f)
    val alpha = if (k > 0.70f) 1f - (k - 0.70f) / 0.30f else 1f
    val cx = boardRect.centerX()
    val cy = boardRect.centerY() - cellSize * 0.10f
    val r = cellSize * 1.02f * pop
    val hot = comboValue >= 4
    val inner = if (hot) 0xFFFFC24A.toInt() else 0xFFD5F778.toInt()
    val outer = if (hot) 0xFFE5455F.toInt() else 0xFF2F9E5A.toInt()

    c.save()
    c.rotate(k * 26f - 13f, cx, cy)

    val points = 14
    val star = Path()
    for (i in 0 until points * 2) {
        val rr = if (i % 2 == 0) r else r * 0.74f
        val ang = Math.toRadians(i * 180.0 / points - 90.0)
        val px = cx + (cos(ang) * rr).toFloat()
        val py = cy + (sin(ang) * rr * 0.80f).toFloat()
        if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
    }
    star.close()

    Art.glow(c, cx, cy, r * 1.20f, outer, 0.34f * alpha)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Art.withAlpha(outer, alpha * 0.92f)
    c.drawPath(star, paint)
    paint.color = Art.withAlpha(inner, alpha)
    c.drawCircle(cx, cy, r * 0.60f, paint)
    paint.color = Art.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.28f)
    c.drawCircle(cx - r * 0.14f, cy - r * 0.18f, r * 0.24f, paint)
    c.restore()

    Art.text(c, "کمبو", cx, cy - cellSize * 0.18f, cellSize * 0.22f,
        Art.withAlpha(0xFF1B3A12.toInt(), alpha), Paint.Align.CENTER, Art.black)
    Art.text(c, "×${fa(comboValue)}", cx, cy + cellSize * 0.30f, cellSize * 0.42f,
        Art.withAlpha(0xFFFFFFFF.toInt(), alpha), Paint.Align.CENTER, Art.black, true)
    for (i in 0 until 4) {
        val ang = k * 6.28f + i * 1.57f
        Art.sparkle(c, cx + (cos(ang) * r * 0.95f).toFloat(), cy + (sin(ang) * r * 0.80f).toFloat(),
            cellSize * 0.09f, 0xFFFFFFFF.toInt(), alpha * 0.75f, k * 190f)
    }
}

private fun GameView.drawObjectives(c: Canvas, g: Game, y: Float, band: RectF) {
    val goals = g.level.collect
    val extra = if (g.iceLeft > 0) 1 else 0
    val count = goals.size + extra
    if (count == 0) return
    val gap = Design.gap(band.width(), 1.4f)
    val chipH = band.height()
    val chipW = min(band.width() * 0.30f, (band.width() - gap * (count - 1)) / count)
    val total = chipW * count + gap * (count - 1)
    var x = band.centerX() - total / 2f
    for (goal in goals) {
        val rect = RectF(x, y, x + chipW, y + chipH)
        val done = g.collected[goal.type] >= goal.count
        Art.goalChip(c, rect, goal.type, min(g.collected[goal.type], goal.count), goal.count, done)
        x += chipW + gap
    }
    if (g.iceLeft > 0) {
        val rect = RectF(x, y, x + chipW, y + chipH)
        val rad = chipH * 0.46f
        Art.panel(c, rect, rad, 0xB30A2418.toInt())
        Art.panel(c, rect, rad, 0x00000000, 1.4f, 0x33BFE9FF)
        val iceR = chipH * 0.34f
        Art.ice(c, RectF(rect.right - chipH * 0.82f, rect.centerY() - iceR,
            rect.right - chipH * 0.24f, rect.centerY() + iceR), 1f)
        val size = Art.fit(fa(g.iceLeft), chipH * 0.40f, chipW - chipH * 1.05f, Art.black)
        Art.text(c, fa(g.iceLeft), rect.left + chipH * 0.34f, rect.centerY() + size * 0.36f, size,
            Palette.CREAM, Paint.Align.LEFT, Art.black, true)
    }
}
private fun GameView.drawBoosters(c: Canvas, g: Game) {
    val w = widthPx
    val h = heightPx
    val t = System.currentTimeMillis() / 1000f
    val hud = HudGeometry(w, h, g.level.collect.isNotEmpty() || g.iceLeft > 0)

    /* ------------------------------------------------------- guide portrait */
    val guide = RectF(hud.guide.left, hud.guide.top, hud.guide.right, hud.guide.bottom)
    val mood = when {
        g.status != Status.PLAYING -> Helper.Mood.IDLE
        g.moves in 1..5 -> Helper.Mood.SAD
        else -> Helper.Mood.IDLE
    }
    c.enterGroup(1f, guide.centerX(), guide.centerY(), 0f) {
        val bob = if (reduceMotion) 0f else sin(t * 1.6f) * guide.height() * 0.015f
        Helper.portrait(c, guide.centerX(), guide.centerY() + bob, guide.height() * 0.40f, mood,
            if (reduceMotion) 0f else t)
        hotspots.add(Hotspot(RectF(guide), {
            val hint = if (g.level.collect.isNotEmpty()) "میوه‌های نشان‌داده‌شده را جمع کن" else "سه‌تای هم‌شکل را کنار هم بچین"
            toast(hint)
        }))
    }

    /* ------------------------------------------------------------- boosters */
    val kinds = listOf("hammer", "shuffle", "moves")
    val labels = listOf("چکش", "برهم‌زن", "حرکت")
    for (i in kinds.indices) {
        val kind = kinds[i]
        val b = hud.cards[i]
        val rect = RectF(b.left, b.top, b.right, b.bottom)
        val armed = boosterMode == kind
        val pressed = pressActive && rect.contains(pressX, pressY)
        val body = if (pressed) RectF(rect.left, rect.top + rect.height() * 0.05f, rect.right, rect.bottom) else rect
        if (armed) {
            val pulse = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(t * 3f)
            Art.glow(c, rect.centerX(), rect.centerY(), rect.width() * 0.78f, Palette.LIME, 0.28f + 0.10f * pulse)
        }
        if (!pressed) {
            Art.panel(c, RectF(rect.left, rect.top + rect.height() * 0.08f, rect.right, rect.bottom + rect.height() * 0.08f),
                rect.width() * 0.26f, 0x55000000)
        }
        val bodyRad = rect.width() * 0.26f
        Art.gradRR(c, body, bodyRad,
            if (armed) intArrayOf(0xFF54A876.toInt(), 0xFF2E6B48.toInt(), 0xFF12331F.toInt())
            else intArrayOf(Art.lighten(Design.GLASS_TOP, 0.10f), Design.GLASS_TOP, Design.GLASS_BOTTOM))
        Art.bevel(c, body, bodyRad, rect.width() * 0.022f, 0.18f, 0.24f)
        Art.gloss(c, body, bodyRad, 0.48f, if (armed) 0.26f else 0.18f)
        Art.panel(c, body, bodyRad, 0x00000000, 1.8f,
            if (armed) Palette.LIME else Art.withAlpha(Palette.GOLD, 0.45f))

        val plateR = rect.width() * 0.24f
        val cx = body.centerX()
        val cy = body.top + rect.height() * 0.37f
        Art.circle(c, cx, cy, plateR, if (armed) 0xFF2F9E5A.toInt() else 0xFF24422F.toInt())
        Art.circle(c, cx, cy - plateR * 0.07f, plateR * 0.84f,
            if (armed) 0xFF4CBE72.toInt() else 0xFF31583C.toInt())
        val ir = plateR * 0.54f
        when (kind) {
            "hammer" -> Art.hammer(c, cx, cy, ir, Palette.CREAM)
            "shuffle" -> Art.shuffleIcon(c, cx, cy, ir, Palette.CREAM)
            else -> Art.plusMoves(c, cx, cy, ir, Palette.CREAM)
        }
        val label = labels[i]
        val lsize = Art.fit(label, rect.width() * 0.20f, rect.width() * 0.74f, Art.bold)
        Art.text(c, label, cx, body.bottom - rect.height() * 0.11f, lsize,
            if (armed) Palette.LIME else Palette.CREAM_DIM, Paint.Align.CENTER, Art.bold)

        val owned = save.booster(kind)
        val bx = body.left + rect.width() * 0.14f
        val by = body.top + rect.width() * 0.14f
        Art.circle(c, bx, by, rect.width() * 0.145f, if (owned > 0) Palette.LIME else 0x66202830)
        val countSize = Art.fit(fa(owned), rect.width() * 0.17f, rect.width() * 0.20f, Art.black)
        Art.text(c, fa(owned), bx, by + countSize * 0.36f, countSize,
            if (owned > 0) 0xFF10331F.toInt() else Palette.CREAM_DIM, Paint.Align.CENTER, Art.black)
        hotspots.add(Hotspot(RectF(rect), { armBooster(kind) }))
    }
}
private fun GameView.drawEffects(c: Canvas, now: Long) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (p in particles) {
        val age = (now - p.start).toFloat()
        if (age < 0f) continue
        val k = (age / p.life).coerceIn(0f, 1f)
        if (k >= 1f) continue
        val t = age / 1000f
        when (p.kind) {
            GameView.SPARK -> {
                val x = p.x + p.vx * t
                val y = p.y + p.vy * t + 0.5f * p.gravity * t * t
                val fade = (1f - k) * (1f - k)
                Art.sparkle(c, x, y, p.radius * (1f + 0.4f * k), p.color, fade, p.rot + p.spin * t)
            }
            GameView.SHARD -> {
                val x = p.x + p.vx * t
                val y = p.y + p.vy * t + 0.5f * p.gravity * t * t
                c.save()
                c.rotate(p.rot + p.spin * t, x, y)
                paint.color = Art.withAlpha(p.color, 1f - k)
                c.drawRoundRect(
                    RectF(x - p.radius, y - p.radius * 0.55f, x + p.radius, y + p.radius * 0.55f),
                    p.radius * 0.3f, p.radius * 0.3f, paint
                )
                c.restore()
            }
            GameView.RING -> {
                val r = p.radius * (0.25f + 0.95f * k)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = Math.max(1.5f, p.radius * 0.16f * (1f - k))
                paint.color = Art.withAlpha(p.color, (1f - k) * 0.9f)
                c.drawCircle(p.x, p.y, r, paint)
                paint.style = Paint.Style.FILL
            }
            GameView.FLASH -> {
                val r = p.radius * (0.5f + 1.1f * k)
                Art.glow(c, p.x, p.y, r, p.color, (1f - k) * 0.6f)
                paint.color = Art.withAlpha(0xFFFFFFFF.toInt(), (1f - k) * 0.4f)
                c.drawCircle(p.x, p.y, r * 0.35f, paint)
            }
            GameView.BEAM -> {
                val grow = 0.35f + 0.9f * k
                val alpha = (1f - k) * 0.85f
                paint.color = Art.withAlpha(p.color, alpha * 0.35f)
                val half = p.radius * 0.34f * (1f - 0.4f * k)
                if (p.rot != 0f) {
                    c.save()
                    c.rotate(90f, p.x, p.y)
                    c.drawRoundRect(
                        RectF(p.x - p.radius * grow, p.y - half, p.x + p.radius * grow, p.y + half),
                        half, half, paint
                    )
                    paint.color = Art.withAlpha(0xFFFFFFFF.toInt(), alpha)
                    c.drawRoundRect(
                        RectF(p.x - p.radius * grow, p.y - half * 0.3f, p.x + p.radius * grow, p.y + half * 0.3f),
                        half * 0.3f, half * 0.3f, paint
                    )
                    c.restore()
                } else {
                    c.drawRoundRect(
                        RectF(p.x - p.radius * grow, p.y - half, p.x + p.radius * grow, p.y + half),
                        half, half, paint
                    )
                    paint.color = Art.withAlpha(0xFFFFFFFF.toInt(), alpha)
                    c.drawRoundRect(
                        RectF(p.x - p.radius * grow, p.y - half * 0.3f, p.x + p.radius * grow, p.y + half * 0.3f),
                        half * 0.3f, half * 0.3f, paint
                    )
                }
            }
            else -> {
                val r = p.radius * (0.12f + 1.05f * k)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = Math.max(2f, p.radius * 0.34f * (1f - k) * (1f - k))
                paint.color = Art.withAlpha(p.color, (1f - k) * 0.85f)
                c.drawCircle(p.x, p.y, r, paint)
                paint.style = Paint.Style.FILL
                Art.glow(c, p.x, p.y, r * 1.1f, p.color, (1f - k) * 0.45f)
                for (i in 0 until 8) {
                    val a = i * 0.785f + p.start * 0.001f
                    Art.sparkle(
                        c, p.x + cos(a) * r, p.y + sin(a) * r,
                        p.radius * 0.12f * (1f - k) + 2f, p.color, (1f - k) * 0.8f, a * 57f
                    )
                }
            }
        }
    }

    for (f in floats) {
        val k = ((now - f.start).toFloat() / 950f).coerceIn(0f, 1f)
        val alpha = (1f - k * k).coerceIn(0f, 1f)
        val pop = if (k < 0.18f) 0.6f + 2.2f * k else 1f
        val y = f.y - k * heightPx * 0.055f
        c.save()
        c.scale(pop, pop, f.x, y)
        Art.text(c, f.text, f.x, y, f.size, Art.withAlpha(f.color, alpha), Paint.Align.CENTER, Art.black, true)
        c.restore()
    }
}

private fun GameView.drawIntro(c: Canvas, now: Long, lvl: Level) {
    val age = now - introAt
    if (age < 0L || age > 2200L) return
    val w = widthPx
    val h = heightPx
    val k = (age / 2200f).coerceIn(0f, 1f)
    val slide = if (k < 0.16f) (1f - k / 0.16f) * -h * 0.10f else 0f
    val fade = when {
        k > 0.78f -> 1f - (k - 0.78f) / 0.22f
        k < 0.12f -> k / 0.12f
        else -> 1f
    }.coerceIn(0f, 1f)
    if (fade <= 0.01f) return

    val cx = w / 2f
    val cy = h * 0.345f + slide
    val bh = h * 0.086f
    val bw = w * 0.62f
    val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)

    c.save()
    c.scale(0.86f + 0.14f * min(1f, k * 5f), 0.86f + 0.14f * min(1f, k * 5f), cx, cy)
    Art.woodTitle(c, rect, "سطح ${fa(lvl.id)}", bh * 0.40f, age / 1000f, 0.30f * fade)
    val nameSize = Art.fit(lvl.name, bh * 0.25f, bw * 0.78f, Art.bold)
    Art.text(c, lvl.name, cx, cy + bh * 0.40f, nameSize, Art.withAlpha(0xFFFFF3D6.toInt(), fade),
        Paint.Align.CENTER, Art.bold)
    Art.sparkle(c, rect.left + bw * 0.07f, cy, bh * 0.15f, Palette.GOLD, fade * 0.9f, age / 6f)
    Art.sparkle(c, rect.right - bw * 0.07f, cy, bh * 0.15f, Palette.GOLD, fade * 0.9f, -age / 6f)
    c.restore()
}
internal fun GameView.drawShop(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f
    header(c, "فروشگاه")

    val balanceH = h * 0.060f
    val balance = RectF(w * 0.28f, h * 0.116f, w * 0.72f, h * 0.116f + balanceH)
    c.enterGroup(enterK(now, 60f), balance.centerX(), balance.centerY(), h * 0.03f) {
        Art.rewardCapsule(c, balance, fa(save.coins), if (reduceMotion) 1f else abs(cos(t * 2.2f)) * 0.7f + 0.3f)
        c.shine(balance, balanceH / 2f, t, 0.4f, 0.2f, 0.24f)
    }

    val top = h * 0.190f
    val cardH = h * 0.098f
    val gap = h * 0.014f
    val rows = eco.shop.size + 1

    for (idx in 0 until rows) {
        val y = top + idx * (cardH + gap)
        val rect = RectF(w * 0.045f, y, w * 0.955f, y + cardH)
        c.enterGroup(enterK(now, 120f + idx * 70f), rect.centerX(), rect.centerY(), h * 0.04f) {
            Art.frame(c, rect, Design.radiusCard(w), Design.rim(w) * 0.72f,
                Design.CREAM_TOP, Design.CREAM_BOTTOM)

            val row = RowGeometry(Box(rect.left, rect.top, rect.right, rect.bottom), cardH)
            val badgeR = row.icon.width / 2f
            val bx = row.icon.centerX
            val by = row.icon.centerY
            var title: String
            var desc: String
            var price: Int
            var owned: Int
            if (idx < eco.shop.size) {
                val item = eco.shop[idx]
                title = item.title
                desc = item.desc
                price = item.price
                owned = if (item.kind == "lifefull" || item.kind == "coins") 0 else save.booster(item.kind)
                Art.circle(c, bx, by, badgeR, 0xFF1F6B45.toInt())
                Art.circle(c, bx, by - badgeR * 0.06f, badgeR * 0.85f, 0xFF2F8A56.toInt())
                iconFor(item.kind, c, bx, by, badgeR * 0.56f, Palette.CREAM)
            } else {
                title = "یک جان"
                desc = "برای وقتی که جان کم آوردی"
                price = eco.lifePrice
                owned = 0
                Art.circle(c, bx, by, badgeR, 0xFFB93A50.toInt())
                Art.circle(c, bx, by - badgeR * 0.06f, badgeR * 0.85f, 0xFFE5536B.toInt())
                Art.heart(c, bx, by, badgeR * 0.56f, true, Palette.CREAM)
            }

            val textRight = row.text.right
            val textRoom = row.text.width
            val titleSize = Art.fit(title, cardH * 0.24f, textRoom, Art.black)
            Art.text(c, title, textRight, rect.centerY() - cardH * 0.06f, titleSize,
                Design.INK_ON_CREAM, Paint.Align.RIGHT, Art.black)
            val descSize = Art.fit(desc, cardH * 0.16f, textRoom, Art.regular)
            Art.text(c, desc, textRight, rect.centerY() + cardH * 0.20f, descSize,
                Design.INK_BODY, Paint.Align.RIGHT, Art.regular)

            if (owned > 0) {
                val chip = RectF(row.action.left, rect.top + cardH * 0.10f,
                    row.action.right, rect.top + cardH * 0.10f + cardH * 0.19f)
                Art.panel(c, chip, chip.height() / 2f, 0x331F6B45)
                val ownSize = Art.fit("دارید: ${fa(owned)}", chip.height() * 0.62f, chip.width() * 0.86f, Art.bold)
                Art.text(c, "دارید: ${fa(owned)}", chip.centerX(), chip.centerY() + ownSize * 0.36f, ownSize,
                    0xFF1F6B45.toInt(), Paint.Align.CENTER, Art.bold)
            }

            val canAfford = save.coins >= price
            val buy = RectF(row.action.left, row.action.top, row.action.right, row.action.bottom)
            button(c, buy, fa(price), if (canAfford) Style.GOLD else Style.SECONDARY,
                { cc, cx, cy, r -> Art.coin(cc, cx, cy, r) }) {
                if (idx < eco.shop.size) buyItem(eco.shop[idx]) else buyLife()
            }
            c.shine(rect, Design.radiusCard(w), t, 0.42f, idx * 0.22f, 0.18f)
        }
    }
}
internal fun GameView.drawWheel(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f
    header(c, "چرخ شانس")

    val cx = w / 2f
    val cy = h * 0.418f
    val radius = min(w * 0.40f, h * 0.215f)
    val slices = eco.wheel
    if (slices.isEmpty()) return
    val seg = 360f / slices.size
    val k = enterK(now, 80f)

    // halo behind the wheel
    val pulse = 0.5f + 0.5f * sin(t * 1.8f)
    Art.glow(c, cx, cy, radius * (1.35f + 0.05f * pulse), Palette.GOLD, 0.20f + 0.08f * pulse)

    c.enterGroup(k, cx, cy, h * 0.03f) {
        c.save()
        c.rotate(t * 8f, cx, cy)
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        rayPaint.color = Art.withAlpha(Palette.GOLD, 0.10f)
        for (i in 0 until 16) {
            val a0 = Math.toRadians(i * 22.5)
            val a1 = Math.toRadians(i * 22.5 + 8.0)
            val rp = Path()
            rp.moveTo(cx, cy)
            rp.lineTo(cx + (cos(a0) * radius * 1.9f).toFloat(), cy + (sin(a0) * radius * 1.9f).toFloat())
            rp.lineTo(cx + (cos(a1) * radius * 1.9f).toFloat(), cy + (sin(a1) * radius * 1.9f).toFloat())
            rp.close()
            c.drawPath(rp, rayPaint)
        }
        c.restore()

        val sliceColors = intArrayOf(
            0xFFF2545B.toInt(), 0xFFFF9F1C.toInt(), 0xFFFFD93D.toInt(), 0xFF7ED957.toInt(),
            0xFF34C759.toInt(), 0xFF41B8E0.toInt(), 0xFF8B5CF6.toInt(), 0xFFD96BD0.toInt()
        )

        Art.circle(c, cx, cy, radius * 1.13f, 0x66000000)
        Art.circle(c, cx, cy, radius * 1.11f, 0xFF5E3818.toInt())
        Art.circle(c, cx, cy, radius * 1.06f, 0xFFC08A52.toInt())
        Art.circle(c, cx, cy, radius * 1.01f, 0xFF8A5528.toInt())

        c.save()
        c.rotate(wheelAngle, cx, cy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val arc = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        for (i in slices.indices) {
            paint.color = sliceColors[i % sliceColors.size]
            c.drawArc(arc, i * seg, seg, true, paint)
            paint.color = 0x1A000000
            c.drawArc(arc, i * seg + seg * 0.92f, seg * 0.08f, true, paint)
            paint.color = 0x26FFFFFF
            c.drawArc(arc, i * seg, seg * 0.10f, true, paint)
        }
        for (i in slices.indices) {
            c.save()
            c.rotate(i * seg + seg / 2f, cx, cy)
            val ir = radius * 0.16f
            val iy = cy - radius * 0.62f
            Art.circle(c, cx, iy - ir * 0.25f, ir * 1.25f, 0x2E000000)
            iconFor(slices[i].kind, c, cx, iy - ir * 0.25f, ir * 0.8f, 0xFFFFFFFF.toInt())
            Art.text(c, slices[i].label, cx, iy + ir * 1.15f, radius * 0.10f, 0xFFFFFFFF.toInt(),
                Paint.Align.CENTER, Art.bold, true)
            c.restore()
        }
        c.restore()

        for (i in 0 until 16) {
            val a = Math.toRadians(i * 22.5)
            val sx = cx + (cos(a) * radius * 1.085f).toFloat()
            val sy = cy + (sin(a) * radius * 1.085f).toFloat()
            c.stud(sx, sy, radius * 0.030f)
        }

        Art.circle(c, cx, cy, radius * 0.22f, 0xFF5E3818.toInt())
        Art.circle(c, cx, cy, radius * 0.19f, Palette.GOLD_DARK)
        Art.circle(c, cx, cy, radius * 0.165f, Palette.GOLD)
        Art.text(c, "شانس", cx, cy + radius * 0.05f, radius * 0.115f, 0xFF6B4300.toInt(), Paint.Align.CENTER, Art.black)

        val p = Path()
        p.moveTo(cx, cy - radius * 0.96f)
        p.lineTo(cx - w * 0.042f, cy - radius * 1.20f)
        p.lineTo(cx + w * 0.042f, cy - radius * 1.20f)
        p.close()
        paint.color = 0xFFE5455F.toInt()
        c.drawPath(p, paint)
        paint.color = 0x88FFFFFF.toInt()
        c.drawCircle(cx, cy - radius * 1.13f, w * 0.011f, paint)
    }

    val ready = wheelReadyIn()
    val spinning = wheelSpinDur > 0L
    val btn = RectF(w * 0.16f, h * 0.700f, w * 0.84f, h * 0.780f)
    val label = when {
        spinning -> "می‌چرخد…"
        ready > 0 -> "شارژ مجدد: " + fmtTime(ready)
        else -> "بچرخان (رایگان)"
    }
    c.enterGroup(enterK(now, 220f), btn.centerX(), btn.centerY(), h * 0.04f) {
        if (ready == 0L && !spinning) {
            button(c, btn, label, Style.PRIMARY, { cc, ccx, ccy, r -> Art.wheelIcon(cc, ccx, ccy, r, 0xFF1B4410.toInt()) }) {
                spinWheel()
            }
        } else {
            Art.panel(c, btn, btn.height() * 0.40f, 0x8C0C2C21.toInt(), 1.6f, 0x44FFFFFF)
            Art.text(c, label, btn.centerX(), btn.centerY() + btn.height() * 0.17f, btn.height() * 0.36f,
                Palette.CREAM_DIM, Paint.Align.CENTER, Art.bold, true)
            hotspots.add(Hotspot(RectF(btn), { if (!spinning) spinWheel() }))
        }
    }

    val prize = wheelPrize
    if (prize != null && !spinning && wheelAwarded) {
        val card = RectF(w * 0.14f, h * 0.800f, w * 0.86f, h * 0.905f)
        c.enterGroup(enterK(now, 0f), card.centerX(), card.centerY(), h * 0.04f) {
            Art.glow(c, card.centerX(), card.centerY(), card.width() * 0.55f, Palette.GOLD, 0.22f)
            Art.frame(c, card, Design.radiusDialog(w), Design.rim(w), Design.CREAM_TOP, Design.CREAM_BOTTOM)
            Art.text(c, "جایزه", card.centerX(), card.top + card.height() * 0.26f, card.height() * 0.19f,
                Design.INK_BODY, Paint.Align.CENTER, Art.bold)
            val prizeSize = Art.fit(prize.label, card.height() * 0.34f, card.width() * 0.60f, Art.black)
            Art.text(c, prize.label, card.centerX(), card.centerY() + card.height() * 0.16f, prizeSize,
                0xFFB0740A.toInt(), Paint.Align.CENTER, Art.black)
            val ok = RectF(card.right - w * 0.26f, card.top + card.height() * 0.20f, card.right - w * 0.045f, card.bottom - card.height() * 0.20f)
            button(c, ok, "باشه", Style.PRIMARY) { wheelPrize = null }
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 + t * 40f)
                Art.sparkle(c, card.centerX() + (cos(a) * card.width() * 0.42f).toFloat(),
                    card.centerY() + (sin(a) * card.height() * 0.40f).toFloat(), card.height() * 0.05f,
                    0xFFFFFFFF.toInt(), 0.35f + 0.45f * sin(t * 3f + i), t * 120f)
            }
        }
    } else {
        Art.text(c, "هر ۴ ساعت یک چرخ رایگان", cx, h * 0.848f, w * 0.036f,
            Palette.CREAM_DIM, Paint.Align.CENTER, Art.regular, true)
    }
}

internal fun GameView.drawPass(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f
    header(c, "مسیر ستاره")

    val bar = RectF(w * 0.16f, h * 0.120f, w * 0.84f, h * 0.168f)
    c.enterGroup(enterK(now, 60f), bar.centerX(), bar.centerY(), h * 0.03f) {
        Art.panel(c, RectF(bar.left + 3f, bar.top + 7f, bar.right + 3f, bar.bottom + 7f), bar.height() / 2f, 0x55000000)
        Art.panel(c, bar, bar.height() / 2f, 0xCC0A2418.toInt())
        Art.progress(c, bar, (save.totalStars / 60f).coerceIn(0f, 1f), Palette.GOLD, 0x660A1F16)
        Art.panel(c, bar, bar.height() / 2f, 0x00000000, 1.8f, 0x88FFE9C8.toInt())
        Art.star(c, bar.left + bar.height() * 0.5f, bar.centerY(), bar.height() * 0.32f, true)
        val stars = "${fa(save.totalStars)} ستاره"
        val size = Art.fit(stars, bar.height() * 0.44f, bar.width() * 0.70f, Art.black)
        Art.text(c, stars, bar.centerX(), bar.centerY() + size * 0.36f, size,
            Palette.CREAM, Paint.Align.CENTER, Art.black, true)
        c.shine(bar, bar.height() / 2f, t, 0.4f, 0.1f, 0.28f)
    }
    Art.text(c, "با هر ۲ ستاره یک پله باز می‌شود", w * 0.5f, h * 0.196f, w * 0.034f, Palette.CREAM_DIM,
        Paint.Align.CENTER, Art.regular, true)

    val top = h * 0.222f
    val bottom = h * 0.932f
    val cardH = h * 0.098f
    val gap = h * 0.014f
    val visible = ((bottom - top) / (cardH + gap)).toInt()
    val start = max(0, min(passScroll, eco.pass.size - visible))
    for (k in 0 until visible) {
        val i = start + k
        if (i >= eco.pass.size) break
        val tier = eco.pass[i]
        val y = top + k * (cardH + gap)
        val rect = RectF(w * 0.045f, y, w * 0.955f, y + cardH)
        val unlocked = save.totalStars >= tier.stars
        val claimed = save.claimed.contains(tier.index)
        c.enterGroup(enterK(now, 110f + k * 45f), rect.centerX(), rect.centerY(), h * 0.035f) {
            // claimed tiers are gold-framed, open tiers cream, locked tiers muted glass
            if (unlocked) {
                Art.frame(c, rect, Design.radiusCard(w), Design.rim(w) * 0.72f,
                    Design.CREAM_TOP, Design.CREAM_BOTTOM)
            } else {
                Art.panel(c, RectF(rect.left, rect.top + cardH * 0.06f, rect.right, rect.bottom + cardH * 0.06f),
                    Design.radiusCard(w), 0x44000000)
                Art.panel(c, rect, Design.radiusCard(w), 0x99103122.toInt(), 1.6f, 0x33FFF6E5)
            }

            val row = RowGeometry(Box(rect.left, rect.top, rect.right, rect.bottom), cardH, cardH * 1.20f, cardH * 0.50f)
            val badgeR = row.icon.width / 2f
            val bx = row.icon.centerX
            val by = row.icon.centerY
            if (unlocked) {
                Art.circle(c, bx, by, badgeR, if (claimed) 0xFF9A6A0C.toInt() else 0xFF1F6B45.toInt())
                Art.circle(c, bx, by - badgeR * 0.06f, badgeR * 0.85f,
                    if (claimed) Palette.GOLD_DARK else 0xFF2F8A56.toInt())
                iconFor(tier.kind, c, bx, by, badgeR * 0.56f, if (claimed) Design.INK_ON_GOLD else Palette.CREAM)
            } else {
                Art.circle(c, bx, by, badgeR, 0x55243B33)
                Art.lock(c, bx, by, badgeR * 0.62f, 0x99FFF6E5.toInt())
            }

            val textRight = row.text.right
            val textRoom = row.text.width
            val titleSize = Art.fit(tier.label, cardH * 0.23f, textRoom, Art.black)
            Art.text(c, tier.label, textRight, rect.centerY() - cardH * 0.06f, titleSize,
                if (unlocked) Design.INK_ON_CREAM else Palette.CREAM_DIM, Paint.Align.RIGHT, Art.black)
            val sub = "پله ${fa(tier.index)} • ${fa(tier.stars)} ستاره"
            val subSize = Art.fit(sub, cardH * 0.155f, textRoom, Art.regular)
            Art.text(c, sub, textRight, rect.centerY() + cardH * 0.20f, subSize,
                if (unlocked) Design.INK_BODY else Palette.MUTED, Paint.Align.RIGHT, Art.regular)

            val btn = RectF(row.action.left, row.action.top, row.action.right, row.action.bottom)
            when {
                claimed -> {
                    Art.panel(c, btn, btn.height() * 0.42f, 0xFF1F6B45.toInt())
                    Art.panel(c, btn, btn.height() * 0.42f, 0x00000000, 1.4f, 0x88B6F08A.toInt())
                    val s2 = Art.fit("گرفته شد", btn.height() * 0.34f, btn.width() * 0.82f, Art.bold)
                    Art.text(c, "گرفته شد", btn.centerX(), btn.centerY() + s2 * 0.36f, s2,
                        0xFFB6F08A.toInt(), Paint.Align.CENTER, Art.bold)
                }
                !unlocked -> {
                    Art.panel(c, btn, btn.height() * 0.42f, 0x33FFFFFF)
                    val s2 = Art.fit("${fa(tier.stars)} ستاره", btn.height() * 0.32f, btn.width() * 0.82f, Art.bold)
                    Art.text(c, "${fa(tier.stars)} ستاره", btn.centerX(), btn.centerY() + s2 * 0.36f, s2,
                        Palette.CREAM_DIM, Paint.Align.CENTER, Art.bold)
                }
                else -> {
                    button(c, btn, "دریافت", Style.PRIMARY) { claimTier(tier) }
                    val pl = 0.5f + 0.5f * sin(t * 3.4f + k)
                    Art.glow(c, btn.centerX(), btn.centerY(), btn.width() * 0.62f, Palette.LIME, 0.10f + 0.10f * pl)
                }
            }
            if (unlocked) c.shine(rect, Design.radiusCard(w), t, 0.35f, k * 0.30f, 0.18f)
        }
    }
    Art.text(c, "بکش تا پله‌های بیشتر ببینی", w * 0.5f, h * 0.962f, w * 0.030f,
        Palette.CREAM_DIM, Paint.Align.CENTER, Art.regular, true)
}

internal fun GameView.drawSettings(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f
    header(c, "تنظیمات")

    val rowY = h * 0.150f
    val rowH = h * 0.092f

    /* --------------------------------------------------------- sound toggle */
    val soundRect = RectF(w * 0.045f, rowY, w * 0.955f, rowY + rowH)
    c.enterGroup(enterK(now, 70f), soundRect.centerX(), soundRect.centerY(), h * 0.035f) {
        Art.frame(c, soundRect, Design.radiusCard(w), Design.rim(w) * 0.72f, Design.CREAM_TOP, Design.CREAM_BOTTOM)
        val row = RowGeometry(
            Box(soundRect.left, soundRect.top, soundRect.right, soundRect.bottom), rowH,
            rowH * 0.80f, rowH * 0.42f, rowH * 0.52f
        )
        val badgeR = row.icon.width / 2f
        val bx = row.icon.centerX
        Art.circle(c, bx, row.icon.centerY, badgeR, 0xFF1F6B45.toInt())
        Art.circle(c, bx, row.icon.centerY - badgeR * 0.06f, badgeR * 0.85f, 0xFF2F8A56.toInt())
        Art.gear(c, bx, row.icon.centerY, badgeR * 0.56f, Palette.CREAM)

        val labelRight = row.text.right
        Art.text(c, "صدا و موسیقی", labelRight, soundRect.centerY() - rowH * 0.06f,
            Art.fit("صدا و موسیقی", rowH * 0.24f, row.text.width, Art.black),
            Design.INK_ON_CREAM, Paint.Align.RIGHT, Art.black)
        Art.text(c, if (save.sound) "روشن" else "خاموش", labelRight,
            soundRect.centerY() + rowH * 0.21f, rowH * 0.17f,
            if (save.sound) 0xFF1F6B45.toInt() else Design.INK_BODY, Paint.Align.RIGHT, Art.bold)

        val toggle = RectF(row.action.left, row.action.top, row.action.right, row.action.bottom)
        if (save.sound) Art.glow(c, toggle.centerX(), toggle.centerY(), toggle.width() * 0.62f, Palette.LIME, 0.18f)
        Art.panel(c, toggle, toggle.height() / 2f + 2f, if (save.sound) 0xFF2C7534.toInt() else 0xFF7C8A72.toInt())
        Art.panel(c, toggle, toggle.height() / 2f + 2f, 0x00000000, 1.6f,
            if (save.sound) 0x99D6FA8A.toInt() else 0x44FFFFFF)
        val knobX = if (save.sound) toggle.right - toggle.height() / 2f else toggle.left + toggle.height() / 2f
        Art.circle(c, knobX, toggle.centerY() + 2f, toggle.height() * 0.42f, 0x55000000)
        Art.circle(c, knobX, toggle.centerY(), toggle.height() * 0.40f, Palette.CREAM)
        Art.circle(c, knobX - toggle.height() * 0.10f, toggle.centerY() - toggle.height() * 0.13f,
            toggle.height() * 0.14f, 0xCCFFFFFF.toInt())
        hotspots.add(Hotspot(toggle, {
            save.sound = !save.sound
            save.persist()
            sound(R.raw.sfx_click)
        }))
        c.shine(soundRect, Design.radiusCard(w), t, 0.4f, 0.15f, 0.16f)
    }

    /* ------------------------------------------------------- motion readout */
    val motionRect = RectF(w * 0.045f, rowY + rowH + h * 0.014f, w * 0.955f, rowY + rowH * 2f + h * 0.014f)
    c.enterGroup(enterK(now, 130f), motionRect.centerX(), motionRect.centerY(), h * 0.03f) {
        Art.panel(c, motionRect, Design.radiusCard(w), 0x8C103122.toInt(), 1.4f, 0x33FFF6E5)
        Art.text(c, "انیمیشن‌ها", motionRect.right - rowH * 0.30f, motionRect.centerY() + rowH * 0.09f,
            rowH * 0.20f, Palette.CREAM, Paint.Align.RIGHT, Art.bold)
        val state = if (reduceMotion) "کم — طبق تنظیمات دستگاه" else "کامل"
        val stateSize = Art.fit(state, rowH * 0.16f, motionRect.width() * 0.52f, Art.regular)
        Art.text(c, state, motionRect.left + rowH * 0.30f, motionRect.centerY() + rowH * 0.09f,
            stateSize, Palette.MUTED, Paint.Align.LEFT, Art.regular)
    }

    /* ---------------------------------------------------------------- about */
    val resetH = h * 0.078f
    val resetTop = h * 0.958f - resetH
    val about = RectF(w * 0.06f, motionRect.bottom + h * 0.026f, w * 0.94f, resetTop - h * 0.024f)
    c.enterGroup(enterK(now, 250f), about.centerX(), about.centerY(), h * 0.04f) {
        Art.frame(c, about, Design.radiusDialog(w), Design.rim(w), Design.CREAM_TOP, Design.CREAM_BOTTOM)
        val fruitY = about.top + about.height() * 0.20f
        val step = about.width() * 0.155f
        var x = about.centerX() - step * 2f
        for (i in intArrayOf(0, 1, 2, 4, 6)) {
            val bob = if (reduceMotion) 0f else sin(t * 1.8f + i) * h * 0.004f
            Art.glow(c, x, fruitY + bob, about.width() * 0.06f, Art.produceColor(i), 0.16f)
            Art.produce(c, i, x, fruitY + bob, about.width() * 0.145f)
            x += step
        }
        val version = appVersion()
        val titleSize = Art.fit("میوه‌چین", about.width() * 0.076f, about.width() * 0.70f, Art.black)
        Art.text(c, "میوه‌چین", about.centerX(), about.top + about.height() * 0.52f, titleSize,
            Design.INK_ON_CREAM, Paint.Align.CENTER, Art.black)
        val verLabel = "نسخهٔ " + faDigits(version)
        val verSize = Art.fit(verLabel, about.width() * 0.046f, about.width() * 0.70f, Art.regular)
        Art.text(c, verLabel, about.centerX(), about.top + about.height() * 0.64f, verSize,
            Design.INK_BODY, Paint.Align.CENTER, Art.regular)
        val tagline = "هفت محصول، ۶۰ سطح، یک باغ."
        Art.text(c, tagline, about.centerX(), about.top + about.height() * 0.75f,
            Art.fit(tagline, about.width() * 0.042f, about.width() * 0.82f, Art.regular),
            Design.INK_BODY, Paint.Align.CENTER, Art.regular)

        // Laleh stands at the bottom-left of the card, small and friendly
        val girlH = min(about.height() * 0.44f, h * 0.150f)
        if (girlH > h * 0.05f) {
            Helper.draw(c, about.left + about.width() * 0.16f, about.bottom - about.height() * 0.08f,
                girlH, Helper.Mood.CHEER, if (reduceMotion) 0f else t)
        }
        c.shine(about, Design.radiusDialog(w), t, 0.3f, 0.5f, 0.18f)
    }

    /* ---------------------------------------------------------------- reset */
    val resetRect = RectF(w * 0.10f, resetTop, w * 0.90f, resetTop + resetH)
    c.enterGroup(enterK(now, 300f), resetRect.centerX(), resetRect.centerY(), h * 0.035f) {
        button(c, resetRect, "شروع دوباره از سطح ۱", Style.DANGER) {
            save.reset(eco)
            levelIndex = 0
            toast("پیشرفت پاک شد")
        }
    }
}
internal fun GameView.drawOverlays(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val res = result
    when {
        res != null -> drawResult(c, res)
        paused -> drawPause(c)
    }
    val hint = when {
        screen != Screen.GAME || res != null || paused -> null
        boosterMode == "hammer" -> "روی یک میوه بزن تا حذف شود"
        boosterMode == "shuffle" -> "روی تخته بزن تا همه جابه‌جا شوند"
        else -> null
    }
    if (hint != null) {
        val size = Art.fit(hint, Design.label(w), w * 0.80f, Art.bold)
        val tw = Art.measure(hint, size, Art.bold) + w * 0.10f
        val rect = RectF((w - tw) / 2f, h * 0.786f, (w + tw) / 2f, h * 0.786f + h * 0.046f)
        Art.panel(c, rect, rect.height() / 2f, 0xD90A2418.toInt(), 1.4f, 0x66C6F36B)
        Art.text(c, hint, rect.centerX(), rect.centerY() + size * 0.36f, size, Palette.LIME,
            Paint.Align.CENTER, Art.bold, true)
    }
}
private fun GameView.drawPause(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f
    val k = enterK(now, 0f)

    val dim = Paint()
    dim.color = Art.withAlpha(0xFF04150D.toInt(), 0.68f * k)
    c.drawRect(0f, 0f, w, h, dim)

    val panel = RectF(w * 0.075f, h * 0.252f, w * 0.925f, h * 0.706f)
    Art.glow(c, panel.centerX(), panel.centerY(), panel.width() * 0.62f, Palette.LIME, 0.12f + 0.05f * sin(t * 2f))

    c.save()
    val scale = 0.92f + 0.08f * k
    c.scale(scale, scale, panel.centerX(), panel.centerY())
    Art.frame(c, panel, Design.radiusDialog(w), Design.rim(w), Design.CREAM_TOP, Design.CREAM_BOTTOM)

    // corner sprigs, same language as the result panel
    val pw = panel.width()
    c.sprig(panel.left + pw * 0.05f, panel.top + pw * 0.055f, pw * 0.055f, 160f,
        Palette.LEAF_DARK, Palette.LEAF_LIGHT)
    c.sprig(panel.right - pw * 0.05f, panel.top + pw * 0.055f, pw * 0.055f, 20f,
        Palette.LEAF_DARK, Palette.LEAF_LIGHT)
    c.sprig(panel.left + pw * 0.05f, panel.bottom - pw * 0.045f, pw * 0.050f, 200f,
        Palette.LEAF_DARK, Palette.LEAF_LIGHT)
    c.sprig(panel.right - pw * 0.05f, panel.bottom - pw * 0.045f, pw * 0.050f, 340f,
        Palette.LEAF_DARK, Palette.LEAF_LIGHT)

    val plankH = h * 0.076f
    val plank = RectF(panel.centerX() - pw * 0.30f, panel.top - plankH * 0.46f,
        panel.centerX() + pw * 0.30f, panel.top - plankH * 0.46f + plankH)
    Art.woodTitle(c, plank, "مکث", plankH * 0.46f, if (reduceMotion) 0.1f else t, 0.30f)

    Art.text(c, "بازی متوقف شد", panel.centerX(), panel.top + h * 0.088f, Design.body(w),
        Design.INK_BODY, Paint.Align.CENTER, Art.bold)

    val bh = h * 0.064f
    val left = panel.left + w * 0.062f
    val right = panel.right - w * 0.062f
    val top = panel.top + h * 0.128f
    val gap = h * 0.018f
    button(c, RectF(left, top, right, top + bh), "ادامه", Style.PRIMARY,
        { cc, cx, cy, r -> Art.play(cc, cx, cy, r, Design.INK_ON_LIME) }) {
        paused = false
    }
    button(c, RectF(left, top + (bh + gap), right, top + (bh + gap) + bh), "شروع دوباره", Style.SECONDARY,
        { cc, cx, cy, r -> Art.shuffleIcon(cc, cx, cy, r, Palette.CREAM) }) {
        paused = false
        restartLevel()
    }
    button(c, RectF(left, top + (bh + gap) * 2f, right, top + (bh + gap) * 2f + bh), "خانه", Style.ROYAL,
        { cc, cx, cy, r -> Art.homeIcon(cc, cx, cy, r * 1.05f, 0xFFFFFDF5.toInt()) }) {
        paused = false
        result = null
        screen = Screen.HOME
    }
    c.restore()
}
private fun Canvas.crown(cx: Float, cy: Float, wd: Float, ht: Float) {
    if (Art.sprite(this, "crown", cx, cy, ht * 1.30f)) return
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG)
    line.style = Paint.Style.STROKE
    line.strokeWidth = Math.max(2f, wd * 0.026f)
    line.color = 0xFF7A4A05.toInt()

    val body = Path()
    body.moveTo(cx - wd * 0.50f, cy + ht * 0.34f)
    body.lineTo(cx - wd * 0.48f, cy - ht * 0.14f)
    body.lineTo(cx - wd * 0.24f, cy + ht * 0.12f)
    body.lineTo(cx, cy - ht * 0.46f)
    body.lineTo(cx + wd * 0.24f, cy + ht * 0.12f)
    body.lineTo(cx + wd * 0.48f, cy - ht * 0.14f)
    body.lineTo(cx + wd * 0.50f, cy + ht * 0.34f)
    body.close()

    fill.shader = android.graphics.LinearGradient(
        cx, cy - ht * 0.5f, cx, cy + ht * 0.4f,
        0xFFFFF3B8.toInt(), 0xFFD99A12.toInt(), android.graphics.Shader.TileMode.CLAMP
    )
    drawPath(body, fill)
    drawPath(body, line)
    fill.shader = null

    fill.color = 0xFFB06E06.toInt()
    drawRoundRect(RectF(cx - wd * 0.50f, cy + ht * 0.16f, cx + wd * 0.50f, cy + ht * 0.36f), ht * 0.08f, ht * 0.08f, fill)
    fill.color = 0xFFE5455F.toInt()
    drawCircle(cx, cy + ht * 0.06f, wd * 0.055f, fill)
    fill.color = 0xFF59C3FF.toInt()
    drawCircle(cx - wd * 0.255f, cy + ht * 0.02f, wd * 0.036f, fill)
    drawCircle(cx + wd * 0.255f, cy + ht * 0.02f, wd * 0.036f, fill)
    fill.color = 0x99FFFFFF.toInt()
    drawCircle(cx - wd * 0.02f, cy - ht * 0.06f, wd * 0.018f, fill)
}

/** Three leaves fanning out of a point, used on the panel corners. */
private fun Canvas.sprig(cx: Float, cy: Float, r: Float, rot: Float, dark: Int, light: Int) {
    for (i in 0 until 3) {
        val a = Math.toRadians((rot - 34f + i * 34f).toDouble())
        Art.leaf(
            this,
            cx + (cos(a) * r * 0.55f).toFloat(),
            cy + (sin(a) * r * 0.55f).toFloat(),
            r * (1f - 0.14f * abs(i - 1)),
            dark, light, rot - 34f + i * 34f
        )
    }
}

private fun GameView.drawResult(c: Canvas, res: GameView.Result) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    // reduced-motion: skip straight to the resting state
    val age = if (reduceMotion) 7000L else (now - resultAt).coerceAtLeast(0L)
    val t = age / 1000f
    val win = res.win

    fun easeOutCubic(k: Float): Float {
        val u = 1f - k
        return 1f - u * u * u
    }

    fun easeOutBack(k: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = k - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    fun k(delay: Float, dur: Float = 340f): Float = easeOutCubic(((age - delay) / dur).coerceIn(0f, 1f))

    /* ---------------------------------------------------------- palette */
    val creamTop = 0xFFFFFCF3.toInt()
    val creamBot = 0xFFFFE6BE.toInt()
    val goldTop = 0xFFFFECB4.toInt()
    val goldBot = 0xFFB8740C.toInt()
    val inkBrown = 0xFF5A2E05.toInt()
    val deepTop = 0xFF17744F.toInt()
    val deepBot = 0xFF052C1C.toInt()
    val royalTop = 0xFF2E6BE6.toInt()
    val royalBot = 0xFF14307F.toInt()

    /* ------------------------------------------------------------ scrim */
    val scrim = Paint()
    scrim.color = Art.withAlpha(0xFF04150D.toInt(), 0.70f * k(0f, 240f))
    c.drawRect(0f, 0f, w, h, scrim)
    Art.glow(c, w * 0.5f, h * 0.36f, w * 0.95f, 0xFFFFD98A.toInt(), 0.14f * k(0f, 240f))

    /* ------------------------------------------------ responsive geometry */
    // All placement lives in VictoryLayout so it can be unit tested on the JVM.
    val geo = VictoryGeometry(w, h, win)
    fun rect(b: Box) = RectF(b.left, b.top, b.right, b.bottom)

    val panel = rect(geo.panel)
    val inner = rect(geo.inner)
    val plaque = rect(geo.plaque)
    val chip = rect(geo.chip)
    val score = rect(geo.score)
    val capsule = rect(geo.capsule)
    val primary = rect(geo.primary)
    val secondary = rect(geo.secondary)

    val pw = panel.width()
    val radius = w * 0.060f
    val rim = w * 0.013f
    val gardenH = geo.gardenH
    val gardenTop = geo.gardenTop
    val avail = inner.height() - gardenH
    val plaqueH = plaque.height()
    val chipH = chip.height()
    val starR = geo.starR
    val socketR = geo.socketR
    val starCy = geo.starCy
    val scoreH = score.height()
    val capH = capsule.height()

    val openScale = 0.90f + 0.10f * easeOutBack(((age - 20f) / 420f).coerceIn(0f, 1f))
    val rise = (1f - k(20f, 420f)) * h * 0.018f

    c.save()
    c.translate(0f, rise)
    c.scale(openScale, openScale, panel.centerX(), panel.centerY())

    /* -------------------------------------------------------------- panel */
    Art.frame(c, panel, radius, rim, creamTop, creamBot)
    Art.panel(c, RectF(inner.left + rim * 0.8f, inner.top + rim * 0.8f, inner.right - rim * 0.8f, inner.bottom - rim * 0.8f),
        radius * 0.80f, 0x00000000, Math.max(1f, rim * 0.16f), Art.withAlpha(goldBot, 0.30f))
    Art.panel(c, RectF(inner.left + rim, gardenTop - avail * 0.02f, inner.right - rim, inner.bottom - rim * 0.4f),
        radius * 0.62f, 0x10000000)

    /* ------------------------------------------------- corner decorations */
    val cornerR = pw * 0.062f
    val leafD = Palette.LEAF_DARK
    val leafL = Palette.LEAF_LIGHT
    if (!Art.sprite(c, "sprig", inner.left + pw * 0.060f, inner.top + pw * 0.070f, pw * 0.115f, -18f)) {
        Art.sprout(c, inner.left + pw * 0.034f, inner.top + pw * 0.046f, cornerR, 148f, leafD, leafL)
    }
    if (!Art.sprite(c, "sprig", inner.right - pw * 0.060f, inner.top + pw * 0.070f, pw * 0.115f, 18f)) {
        Art.sprout(c, inner.right - pw * 0.034f, inner.top + pw * 0.046f, cornerR, 32f, leafD, leafL)
    }
    if (!Art.sprite(c, "sprig", inner.left + pw * 0.044f, inner.bottom - pw * 0.042f, cornerR * 1.7f, -156f)) {
        Art.sprout(c, inner.left + pw * 0.034f, inner.bottom - pw * 0.030f, cornerR * 0.88f, 212f, leafD, leafL)
    }
    if (!Art.sprite(c, "sprig", inner.right - pw * 0.044f, inner.bottom - pw * 0.042f, cornerR * 1.7f, 156f)) {
        Art.sprout(c, inner.right - pw * 0.034f, inner.bottom - pw * 0.030f, cornerR * 0.88f, 328f, leafD, leafL)
    }
    for ((fx, fy, pr, col) in listOf(
        Quad(inner.left + pw * 0.052f, inner.top + pw * 0.086f, pw * 0.020f, 0xFFFF8FA3.toInt()),
        Quad(inner.right - pw * 0.052f, inner.top + pw * 0.086f, pw * 0.020f, 0xFFB9A6FF.toInt()),
        Quad(inner.left + pw * 0.058f, inner.bottom - pw * 0.062f, pw * 0.018f, 0xFFFFD166.toInt()),
        Quad(inner.right - pw * 0.058f, inner.bottom - pw * 0.062f, pw * 0.018f, 0xFFFF8FA3.toInt())
    )) {
        val painted = if (col == 0xFFB9A6FF.toInt()) "flower_violet" else "flower_pink"
        if (!Art.sprite(c, painted, fx, fy, pr * 3.0f)) {
            Art.flower(c, fx, fy, pr, col, 0xFFFFF3C4.toInt())
        }
    }
    // fruit tucked against the bottom hedge instead of the title corners
    Art.strawberry(c, inner.left + pw * 0.140f, inner.bottom - pw * 0.020f, pw * 0.070f)
    Art.berries(c, inner.right - pw * 0.150f, inner.bottom - pw * 0.026f, pw * 0.016f)

    /* ------------------------------------------------- garden along the base */
    Art.bush(c, inner.left + pw * 0.14f, inner.bottom + avail * 0.010f, pw * 0.38f, avail * 0.085f,
        Palette.LEAF_DARK, Palette.LEAF, 0.32f)
    Art.bush(c, inner.right - pw * 0.14f, inner.bottom + avail * 0.010f, pw * 0.38f, avail * 0.088f,
        Palette.LEAF_DARK, Palette.LEAF, 0.32f)
    Art.bush(c, inner.centerX(), inner.bottom + avail * 0.014f, pw * 0.46f, avail * 0.070f,
        Palette.LEAF_DARK, Palette.LEAF, 0.42f)

    val flowerCols = intArrayOf(0xFFFF8FA3.toInt(), 0xFFFFD166.toInt(), 0xFFB9A6FF.toInt(), 0xFFFFB4E1.toInt())
    for (i in 0 until 9) {
        val fx = inner.left + pw * (0.09f + 0.82f * i / 8f)
        val fy = gardenTop + gardenH * (0.34f + 0.22f * (i % 3))
        val sway = if (reduceMotion) 0f else sin(t * 1.7f + i) * pw * 0.004f
        Art.flower(c, fx + sway, fy, pw * 0.016f, flowerCols[i % flowerCols.size], 0xFFFFF3C4.toInt())
    }

    // fruit resting on the hedge, outside the button column
    val fruitY = gardenTop + gardenH * 0.46f
    val bob = if (reduceMotion) 0f else sin(t * 2.1f) * avail * 0.004f
    val fs = pw * 0.135f
    Art.produceHalo(c, 0, inner.left + pw * 0.105f, fruitY + bob, fs * 0.95f)
    Art.produce(c, 0, inner.left + pw * 0.105f, fruitY + bob, fs)
    Art.produceHalo(c, 2, inner.right - pw * 0.105f, fruitY + bob, fs * 0.92f)
    Art.produce(c, 2, inner.right - pw * 0.105f, fruitY + bob, fs * 0.96f)
    Art.produceHalo(c, 4, inner.right - pw * 0.235f, fruitY + gardenH * 0.30f + bob, fs * 0.70f)
    Art.produce(c, 4, inner.right - pw * 0.235f, fruitY + gardenH * 0.30f + bob, fs * 0.74f)

    /* ------------------------------------------------------------- plaque */
    val plaqueK = easeOutBack(((age - 120f) / 400f).coerceIn(0f, 1f))
    c.save()
    c.translate(0f, (1f - plaqueK) * -h * 0.028f)
    if (plaqueK > 0.02f) {
        Art.woodBody(c, plaque, if (reduceMotion) 0.1f else t, 0.30f)
        Art.flower(c, plaque.left + plaqueH * 0.35f, plaque.centerY(), plaqueH * 0.090f,
            0xFFFF8FA3.toInt(), 0xFFFFF3C4.toInt())
        Art.flower(c, plaque.right - plaqueH * 0.35f, plaque.centerY(), plaqueH * 0.090f,
            0xFFB9A6FF.toInt(), 0xFFFFF3C4.toInt())
    }
    c.restore()

    val titleA = k(170f, 300f)
    if (titleA > 0.04f) {
        val text = if (win) "آفرین!" else "حرکات تمام شد"
        val maxW = plaque.width() * 0.58f
        var size = plaqueH * (if (win) 0.42f else 0.30f)
        val need = Art.measure(text, size, Art.black)
        if (need > maxW) size *= (maxW / need).coerceIn(0.5f, 1f)
        Art.text3d(
            c, text, plaque.centerX(), plaque.centerY() + size * 0.30f, size,
            if (win) 0xFFFFD24A.toInt() else Palette.CREAM,
            if (win) inkBrown else 0xFF5A0A18.toInt(),
            Art.black, titleA
        )
    }

    if (win && plaqueK > 0.45f) {
        val crownA = ((plaqueK - 0.45f) / 0.55f).coerceIn(0f, 1f)
        c.save()
        c.scale(crownA, crownA, plaque.centerX(), plaque.top)
        c.crown(plaque.centerX(), plaque.top - plaqueH * 0.02f, plaqueH * 0.62f, plaqueH * 0.34f)
        c.restore()
    }

    /* --------------------------------------------------------- level chip */
    val chipA = k(300f, 300f)
    if (chipA > 0.03f) {
        c.save()
        c.scale(0.92f + 0.08f * chipA, 0.92f + 0.08f * chipA, chip.centerX(), chip.centerY())
        if (!Art.skinPanel(c, chip, "pill_dark", 38f)) {
            Art.gradRR(c, chip, chipH / 2f,
                intArrayOf(0xFF9C6234.toInt(), 0xFF6B3F1C.toInt(), 0xFF3E2110.toInt()))
            Art.bevel(c, chip, chipH / 2f, chipH * 0.07f, 0.20f, 0.26f)
            Art.gloss(c, chip, chipH / 2f, 0.48f, 0.22f)
            Art.panel(c, chip, chipH / 2f, 0x00000000, Math.max(1.4f, chipH * 0.05f), Art.withAlpha(Palette.GOLD, 0.80f))
        }
        val lvlName = levels.getOrNull(res.levelId - 1)?.name ?: ""
        val label = if (lvlName.isBlank()) "سطح ${fa(res.levelId)}" else "سطح ${fa(res.levelId)} • $lvlName"
        var csize = chipH * 0.44f
        val cneed = Art.measure(label, csize, Art.bold)
        val croom = chip.width() * 0.86f
        if (cneed > croom) csize *= (croom / cneed).coerceIn(0.55f, 1f)
        Art.text(c, label, chip.centerX(), chip.centerY() + csize * 0.36f, csize,
            Art.withAlpha(Palette.CREAM, chipA), Paint.Align.CENTER, Art.bold, true)
        c.restore()
    }

    /* -------------------------------------------------------------- stars */
    var landed = 0
    if (win) {
        for (i in 0 until 3) {
            val sx = inner.centerX() + (i - 1) * pw * 0.205f
            val socketA = k(330f + i * 60f, 300f)
            if (socketA <= 0.03f) continue
            Art.socketPlate(c, sx, starCy, socketR, i < res.stars, socketA)

            if (i >= res.stars) {
                Art.star(c, sx, starCy, starR * 0.95f, false, Art.withAlpha(0xFFC9AE7C.toInt(), socketA))
                continue
            }

            val land = 520f + i * 250f
            val fly = ((age - (land - 320f)) / 320f).coerceIn(0f, 1f)
            if (fly <= 0f) {
                Art.star(c, sx, starCy, starR * 0.95f, false, 0x40C9AE7C)
                continue
            }
            if (age >= land) landed++

            val fall = fly * fly
            val cy = starCy - avail * 0.16f + avail * 0.16f * fall
            val sc = 1.65f - 0.65f * fly
            for (j in 1..3) {
                Art.star(c, sx, cy - starR * 0.75f * j * (1f - fly * 0.5f), starR * sc * (1f - 0.15f * j), true,
                    Art.withAlpha(Palette.GOLD, 0.22f * (1f - fly * 0.6f) / j))
            }
            c.save()
            c.scale(sc, sc, sx, cy)
            Art.star(c, sx, cy, starR, true)
            c.restore()

            val hit = (age - land).toInt()
            if (hit in 0..360) {
                val hk = hit / 360f
                val ringR = starR * (1.15f + 1.5f * hk)
                Art.panel(c, RectF(sx - ringR, cy - ringR, sx + ringR, cy + ringR), ringR, 0x00000000,
                    3f * (1f - hk) + 1f, Art.withAlpha(Palette.GOLD, 0.8f * (1f - hk)))
                Art.glow(c, sx, cy, starR * (1.3f + 1.4f * hk), Palette.GOLD, 0.45f * (1f - hk))
                for (j in 0 until 8) {
                    val a = j * (Math.PI / 4) + hk * 1.2f
                    val d = starR * (0.95f + 1.7f * hk)
                    Art.sparkle(c, sx + (cos(a) * d).toFloat(), cy + (sin(a) * d).toFloat(),
                        starR * 0.30f * (1f - hk), 0xFFFFFFFF.toInt(), 0.95f * (1f - hk), hk * 220f)
                }
            }
            if (hit > 360) {
                val idle = if (reduceMotion) 1f else 1f + 0.04f * sin(t * 3.4f + i * 1.3f)
                Art.glow(c, sx, cy, starR * 1.75f, Palette.GOLD, 0.20f + 0.06f * sin(t * 2.4f + i))
                c.save()
                c.scale(idle, idle, sx, cy)
                Art.star(c, sx, cy, starR, true)
                c.restore()
                Art.sparkle(c, sx + starR * 1.15f, cy - starR * 0.95f, starR * 0.24f, 0xFFFFFFFF.toInt(),
                    0.40f + 0.40f * sin(t * 3f + i), t * 90f)
            }
        }
        if (!reduceMotion && landed > resultStarSfx) {
            sound(R.raw.sfx_star, 1f + 0.07f * (landed - 1))
            flashAt = System.currentTimeMillis()
            flashColor = Palette.GOLD
            resultStarSfx = landed
        }
    } else {
        Art.text(c, "${fa(res.score)} امتیاز گرفتی", inner.centerX(), starCy, scoreH * 0.20f,
            Art.withAlpha(0xFF6B4A20.toInt(), k(320f, 300f)), Paint.Align.CENTER, Art.bold)
    }

    /* -------------------------------------------------------- score panel */
    val scoreA = k(1080f, 340f)
    if (scoreA > 0.03f) {
        c.save()
        c.scale(0.95f + 0.05f * scoreA, 0.95f + 0.05f * scoreA, score.centerX(), score.centerY())
        Art.panel(c, RectF(score.left + 3f, score.top + 8f, score.right + 3f, score.bottom + 8f),
            scoreH * 0.30f, 0x44031409)
        if (!Art.skinPanel(c, score, "plate_emerald", 52f)) {
            Art.gradRR(c, score, scoreH * 0.30f,
                intArrayOf(0xFF2E9C63.toInt(), deepTop, deepBot, 0xFF03200F.toInt()))
            Art.bevel(c, score, scoreH * 0.30f, scoreH * 0.05f, 0.22f, 0.20f)
            Art.gloss(c, score, scoreH * 0.30f, 0.42f, 0.16f)
            Art.panel(c, score, scoreH * 0.30f, 0x00000000, 2.4f, 0xCCFFD98A.toInt())
            Art.panel(c, RectF(score.left + 7f, score.top + 7f, score.right - 7f, score.bottom - 7f),
                scoreH * 0.24f, 0x00000000, 1.1f, 0x3CFFE9C8)
        }

        val labelY = score.top + scoreH * 0.28f
        Art.text(c, "امتیاز", score.centerX(), labelY, scoreH * 0.19f,
            Art.withAlpha(0xFFCFEEDC.toInt(), scoreA), Paint.Align.CENTER, Art.bold)
        Art.leaf(c, score.centerX() - pw * 0.115f, labelY - scoreH * 0.06f, pw * 0.026f,
            Palette.LEAF_DARK, Palette.LEAF_LIGHT, -60f)
        Art.leaf(c, score.centerX() + pw * 0.115f, labelY - scoreH * 0.06f, pw * 0.026f,
            Palette.LEAF_DARK, Palette.LEAF_LIGHT, 60f)

        val countK = easeOutCubic(((age - 1200f) / 700f).coerceIn(0f, 1f))
        val shown = if (reduceMotion) res.score else (res.score * countK).toInt()
        val bump = if (reduceMotion) 1f else 1f + 0.05f * sin(countK * Math.PI.toFloat())
        var numSize = scoreH * 0.42f
        val num = fa(shown)
        val nneed = Art.measure(num, numSize, Art.black)
        if (nneed > score.width() * 0.62f) numSize *= (score.width() * 0.62f / nneed)
        c.save()
        c.scale(bump, bump, score.centerX(), score.bottom - scoreH * 0.26f)
        Art.text(c, num, score.centerX(), score.bottom - scoreH * 0.16f, numSize,
            Art.withAlpha(0xFFFFFCF2.toInt(), scoreA), Paint.Align.CENTER, Art.black, true)
        c.restore()
        c.restore()
    }

    /* -------------------------------------------- reward capsule / extra moves */
    val capK = easeOutBack(((age - 1720f) / 420f).coerceIn(0f, 1f))
    if (capK > 0.03f) {
        c.save()
        c.scale(capK, capK, capsule.centerX(), capsule.centerY())
        if (win) {
            // real payout computed by the engine
            Art.rewardCapsule(
                c, capsule, "+${fa(res.coins)} سکه",
                if (reduceMotion) 1f else Math.abs(cos(t * 2.4f)) * 0.72f + 0.28f
            )
            if (!reduceMotion && !resultCoinSfx && age >= 1720f) {
                sound(R.raw.sfx_coin)
                resultCoinSfx = true
            }
        } else {
            // no reward on a loss — the only thing offered here is a real second chance
            button(c, capsule, "۵ حرکت با ${fa(80)} سکه", Style.GOLD,
                { cc, cx, cy, r -> Art.coin(cc, cx, cy, r) }) { buyMovesForCoins() }
        }
        c.restore()
    }

    /* ------------------------------------------------------------ buttons */
    val btnK = easeOutCubic(((age - 1980f) / 380f).coerceIn(0f, 1f))
    if (btnK > 0.05f) {
        if (win) {
            val isLast = res.levelId >= levels.size
            button(c, primary, if (isLast) "پایان مراحل" else "سطح بعد", Style.PRIMARY,
                { cc, cx, cy, r -> Art.chevronLeft(cc, cx, cy, r, 0xFF14330B.toInt()) }) {
                result = null
                if (isLast) {
                    screen = Screen.MAP
                } else {
                    nextLevel()
                }
            }
            button(c, secondary, "نقشه", Style.ROYAL,
                { cc, cx, cy, r -> Art.homeIcon(cc, cx, cy, r, 0xFFFFFDF5.toInt()) }) {
                result = null
                screen = Screen.MAP
            }
            c.shine(secondary, secondary.height() * 0.40f, if (reduceMotion) 0.1f else t, 0.36f, 0.25f, 0.20f)
        } else {
            // retry is the primary action and never grants a reward
            button(c, primary, "تلاش دوباره", Style.PRIMARY,
                { cc, cx, cy, r -> Art.shuffleIcon(cc, cx, cy, r, Design.INK_ON_LIME) }) {
                result = null
                restartLevel()
            }
            button(c, secondary, "نقشه", Style.ROYAL,
                { cc, cx, cy, r -> Art.homeIcon(cc, cx, cy, r, 0xFFFFFDF5.toInt()) }) {
                result = null
                screen = Screen.MAP
            }
            c.shine(secondary, secondary.height() * 0.40f, if (reduceMotion) 0.1f else t, 0.36f, 0.25f, 0.20f)
        }
    }

    /* --------------------------------------------------------------- لاله */
    if (btnK > 0.25f) {
        val girlH = avail * 0.225f
        val feetY = inner.bottom - gardenH * 0.12f
        val gx = inner.left + girlH * 0.30f
        val ga = ((btnK - 0.25f) / 0.75f).coerceIn(0f, 1f)
        c.save()
        c.translate(0f, (1f - ga) * girlH * 0.35f)
        Art.panel(c, RectF(gx - girlH * 0.26f, feetY - girlH * 0.03f, gx + girlH * 0.26f, feetY + girlH * 0.03f),
            girlH * 0.03f, Art.withAlpha(0xFF04150D.toInt(), 0.30f * ga))
        Helper.draw(
            c, gx, feetY, girlH,
            when {
                !win -> Helper.Mood.SAD
                res.stars >= 2 -> Helper.Mood.CHEER
                else -> Helper.Mood.IDLE
            },
            if (reduceMotion) 0f else t,
            if (reduceMotion) 0f else (0.012f + 0.010f * sin(t * 2.2f)) * girlH
        )
        c.restore()
    }

    /* ------------------------------------------------- celebration sparkles */
    if (win && !reduceMotion) {
        for (i in 0 until 7) {
            val kk = ((t * 0.8f + i * 0.9f) % 2.6f) / 2.6f
            if (kk > 0.5f) continue
            val a = 1f - kk / 0.5f
            val px = plaque.left + plaque.width() * (0.10f + 0.80f * ((i * 0.37f) % 1f))
            val py = plaque.bottom + avail * (0.02f + 0.30f * ((i * 0.61f) % 1f))
            Art.sparkle(c, px, py, pw * 0.015f, 0xFFFFFFFF.toInt(), a * 0.7f, t * 70f + i * 30f)
        }
    }

    c.restore()

    val flashAge = now - flashAt
    if (!reduceMotion && flashAge in 0..240) {
        val fk = flashAge / 240f
        val paint = Paint()
        paint.color = Art.withAlpha(flashColor, 0.26f * (1f - fk))
        c.drawRect(0f, 0f, w, h, paint)
    }
}

private fun GameView.header(c: Canvas, title: String) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val t = now / 1000f
    val margin = Design.margin(w)

    val plaque = RectF(w * 0.235f, h * 0.026f, w * 0.765f, h * 0.100f)
    c.enterGroup(enterK(now, 0f), plaque.centerX(), plaque.centerY(), h * 0.025f) {
        Art.woodTitle(c, plaque, title, plaque.height() * 0.46f, if (reduceMotion) 0.1f else t)
    }

    val backH = h * 0.072f
    val back = RectF(w - margin - backH, h * 0.027f, w - margin, h * 0.027f + backH)
    c.enterGroup(enterK(now, 40f), back.centerX(), back.centerY(), h * 0.02f) {
        val pressed = pressActive && back.contains(pressX, pressY)
        Art.iconButton(c, back, pressed) { cc, cx, cy, r -> Art.chevronRight(cc, cx, cy, r, Palette.CREAM) }
        hotspots.add(Hotspot(RectF(back), { screen = Screen.HOME }))
    }

    val chipH = h * 0.056f
    val chip = RectF(margin, h * 0.035f, margin + w * 0.140f, h * 0.035f + chipH)
    c.enterGroup(enterK(now, 40f), chip.centerX(), chip.centerY(), h * 0.02f) {
        Art.coinChip(c, chip, save.coins, t)
    }
}
