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
internal fun fa(value: Int): String {
    val src = value.toString()
    val out = StringBuilder(src.length)
    for (ch in src) out.append(if (ch in '0'..'9') ('\u06F0' + (ch - '0')) else ch)
    return out.toString()
}

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return "$m:" + (if (s < 10) "0$s" else "$s")
}

private fun rrect(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

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

internal fun GameView.drawHome(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()

    // --- resource pills -------------------------------------------------
    val pillH = h * 0.05f
    val coinRect = rrect(w * 0.05f, h * 0.028f, w * 0.4f, pillH)
    Art.card(c, coinRect, pillH * 0.44f)
    Art.badge(c, coinRect.left + pillH * 0.52f, coinRect.centerY(), pillH * 0.34f,
        Palette.GOLD, Palette.GOLD_DARK, Palette.GOLD_DEEP) { cc, cx, cy, r -> Art.coin(cc, cx, cy, r * 0.95f) }
    Art.text(c, fmtCoins(save.coins), coinRect.right - w * 0.06f, coinRect.centerY() + pillH * 0.14f,
        pillH * 0.46f, Palette.CREAM, Paint.Align.RIGHT, Art.black, true)
    hotspots.add(Hotspot(RectF(coinRect), { screen = Screen.SHOP }))

    val heartRect = rrect(w * 0.55f, h * 0.028f, w * 0.4f, pillH)
    Art.card(c, heartRect, pillH * 0.44f, Palette.DANGER)
    Art.badge(c, heartRect.left + pillH * 0.52f, heartRect.centerY(), pillH * 0.34f,
        0xFFFF7D8F.toInt(), 0xFFC22B44.toInt(), 0xFF7A1226.toInt()) { cc, cx, cy, r -> Art.heart(cc, cx, cy, r, true) }
    val lifeText = "${save.lives}/${eco.maxLives}"
    Art.text(c, lifeText, heartRect.right - w * 0.06f, heartRect.centerY() + pillH * 0.14f,
        pillH * 0.46f, Palette.CREAM, Paint.Align.RIGHT, Art.black, true)
    hotspots.add(Hotspot(RectF(heartRect), { buyLife() }))

    // --- logo ribbon ----------------------------------------------------
    val ribbon = RectF(w * 0.06f, h * 0.095f, w * 0.94f, h * 0.19f)
    val tailL = Path()
    tailL.moveTo(ribbon.left, ribbon.top + ribbon.height() * 0.18f)
    tailL.lineTo(ribbon.left - w * 0.05f, ribbon.top + ribbon.height() * 0.36f)
    tailL.lineTo(ribbon.left, ribbon.top + ribbon.height() * 0.54f)
    tailL.lineTo(ribbon.left, ribbon.bottom - ribbon.height() * 0.18f)
    tailL.close()
    val tailR = Path()
    tailR.moveTo(ribbon.right, ribbon.top + ribbon.height() * 0.18f)
    tailR.lineTo(ribbon.right + w * 0.05f, ribbon.top + ribbon.height() * 0.36f)
    tailR.lineTo(ribbon.right, ribbon.top + ribbon.height() * 0.54f)
    tailR.lineTo(ribbon.right, ribbon.bottom - ribbon.height() * 0.18f)
    tailR.close()
    val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    tailPaint.color = 0xFF6E4220.toInt()
    c.drawPath(tailL, tailPaint)
    c.drawPath(tailR, tailPaint)

    Art.gradientPanel(c, ribbon, ribbon.height() * 0.3f, 0xFFF3C34A.toInt(), 0xFFCf8A16.toInt())
    val inner = RectF(ribbon.left + 4f, ribbon.top + 4f, ribbon.right - 4f, ribbon.bottom - 4f)
    Art.panel(c, inner, inner.height() * 0.28f, 0x00000000, 2.2f, 0x88FFF6DC.toInt())
    Art.title(c, "میوه‌چین", ribbon.centerX(), ribbon.centerY() + ribbon.height() * 0.19f,
        w * 0.115f, Palette.CREAM, 0xFF4A2508.toInt())
    Art.leaf(c, ribbon.left + w * 0.045f, ribbon.centerY(), w * 0.06f, Palette.LEAF_DARK, Palette.LEAF, -20f)
    Art.leaf(c, ribbon.right - w * 0.045f, ribbon.centerY(), w * 0.06f, Palette.LEAF_DARK, Palette.LEAF, 20f)

    Art.text(c, "سه‌تا کنار هم بچین، باغ را پر کن!", w * 0.5f, h * 0.222f, w * 0.036f, Palette.CREAM_DIM, Paint.Align.CENTER, Art.regular, true)

    // --- produce parade on a shelf --------------------------------------
    val secs = (now % 100000L) / 1000f
    val types = intArrayOf(0, 1, 2, 4, 6)
    for (i in types.indices) {
        val x = w * (0.16f + 0.17f * i)
        val bob = sin(secs * 1.7f + i * 0.9f) * h * 0.009f
        val y = h * 0.268f + bob + (if (i % 2 == 0) 0f else h * 0.012f)
        Art.glow(c, x, y + w * 0.055f, w * 0.11f, Art.produceColor(types[i]), 0.22f)
        Art.produce(c, types[i], x, y, w * 0.165f)
    }
    val shelf = RectF(w * 0.1f, h * 0.315f, w * 0.9f, h * 0.348f)
    Art.plaque(c, shelf, shelf.height() * 0.35f)
    Art.text(c, "پنج محصول تازه، یک باغ", shelf.centerX(), shelf.centerY() + shelf.height() * 0.16f,
        shelf.height() * 0.34f, 0xFF4A2508.toInt(), Paint.Align.CENTER, Art.bold)

    // --- big play button ------------------------------------------------
    val playRect = RectF(w * 0.12f, h * 0.375f, w * 0.88f, h * 0.458f)
    Art.buttonSkin(c, playRect, playRect.height() * 0.36f, Palette.LIME, 0xFF6FAF30.toInt())
    Art.badge(c, playRect.left + playRect.height() * 0.56f, playRect.centerY(), playRect.height() * 0.33f,
        0xFFFFFFFF.toInt(), 0xFFE9FBD0.toInt(), 0xFF6FAF30.toInt()) { cc, cx, cy, r -> Art.play(cc, cx + r * 0.12f, cy, r * 0.95f, 0xFF2F6B14.toInt()) }
    Art.text(c, "بازی", playRect.centerX() + w * 0.02f, playRect.centerY() + playRect.height() * 0.17f,
        playRect.height() * 0.44f, 0xFF17330A.toInt(), Paint.Align.CENTER, Art.black, true)
    hotspots.add(Hotspot(RectF(playRect), {
        if (save.level - 1 < levels.size) startLevel(save.level - 1) else screen = Screen.MAP
    }))
    Art.text(c, "ادامه از سطح ${fa(save.level)}", w * 0.5f, h * 0.492f, w * 0.036f, Palette.CREAM_DIM, Paint.Align.CENTER, Art.regular, true)

    // --- 2x2 tiles ------------------------------------------------------
    val tiles = listOf(
        Triple("نقشهٔ سطح‌ها", "۶۰ مرحله", Screen.MAP),
        Triple("فروشگاه", "بوستر و جان", Screen.SHOP),
        Triple("چرخ شانس", "هر ۴ ساعت", Screen.WHEEL),
        Triple("مسیر ستاره", "${save.totalStars} ستاره", Screen.PASS)
    )
    val tw = w * 0.435f
    val th = h * 0.108f
    for (i in tiles.indices) {
        val col = i % 2
        val row = i / 2
        val x = if (col == 0) w * 0.05f else w * 0.515f
        val y = h * 0.525f + row * (th + h * 0.016f)
        val rect = RectF(x, y, x + tw, y + th)
        Art.card(c, rect, th * 0.28f)
        val badgeR = th * 0.29f
        val bx = x + th * 0.36f + badgeR
        Art.badge(c, bx, rect.centerY(), badgeR, 0xFF2F7A4E.toInt(), 0xFF14402C.toInt(), Palette.LIME) { cc, cx, cy, r ->
            when (i) {
                0 -> Art.mapIcon(cc, cx, cy, r, Palette.CREAM)
                1 -> Art.cart(cc, cx, cy, r, Palette.CREAM)
                2 -> Art.wheelIcon(cc, cx, cy, r * 1.05f, Palette.CREAM)
                else -> Art.star(cc, cx, cy, r * 1.1f, true, Palette.GOLD)
            }
        }
        Art.text(c, tiles[i].first, rect.right - w * 0.03f, rect.centerY() - th * 0.08f,
            th * 0.26f, Palette.CREAM, Paint.Align.RIGHT, Art.bold, true)
        Art.text(c, tiles[i].second, rect.right - w * 0.03f, rect.centerY() + th * 0.22f,
            th * 0.19f, Palette.MUTED, Paint.Align.RIGHT)
        val target = tiles[i].third
        hotspots.add(Hotspot(RectF(rect), { screen = target }))
    }

    // --- footer ---------------------------------------------------------
    val settingsRect = RectF(w * 0.33f, h * 0.828f, w * 0.67f, h * 0.884f)
    Art.panel(c, settingsRect, settingsRect.height() / 2f, 0x4D0A1F16, 1.4f, 0x33FFFFFF)
    Art.gear(c, settingsRect.left + settingsRect.height() * 0.5f, settingsRect.centerY(),
        settingsRect.height() * 0.28f, Palette.CREAM_DIM)
    Art.text(c, "تنظیمات", settingsRect.centerX() + settingsRect.height() * 0.24f, settingsRect.centerY() + settingsRect.height() * 0.15f,
        settingsRect.height() * 0.36f, Palette.CREAM, Paint.Align.CENTER, Art.bold)
    hotspots.add(Hotspot(RectF(settingsRect), { screen = Screen.SETTINGS }))

    Art.text(c, "ستاره‌ها: ${save.totalStars}  •  آخرین سطح: ${save.level}", w * 0.5f, h * 0.918f,
        w * 0.036f, 0xAAFFF6E5.toInt(), Paint.Align.CENTER, Art.regular, true)
}

/* ------------------------------------------------------------------- map */

internal fun GameView.drawMap(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()

    header(c, "نقشهٔ سطح‌ها")

    val top = h * 0.15f
    val bottom = h * 0.94f
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

    // winding trail
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
        lp.strokeWidth = size * 0.26f
        lp.color = 0x5510311F
        c.drawPath(path, lp)
        lp.strokeWidth = size * 0.13f
        lp.color = 0x66FFF6E5.toInt()
        lp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(size * 0.13f, size * 0.15f), 0f)
        c.drawPath(path, lp)
        lp.pathEffect = null
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

        if (current) {
            val pulse = 0.5f + 0.5f * sin(now / 190f).toFloat()
            Art.glow(c, rect.centerX(), rect.centerY(), size * 0.85f, Palette.LIME, 0.20f + 0.18f * pulse)
        }

        if (unlocked) {
            Art.plaque(c, rect, size * 0.3f)
            if (current) {
                Art.panel(c, rect, size * 0.3f, 0x00000000, 3.5f, Palette.LIME)
            }
            Art.text(c, "${lvl.id}", rect.centerX(), rect.centerY() - size * 0.04f, size * 0.38f,
                Palette.CREAM, Paint.Align.CENTER, Art.black, true)
            for (s in 0 until 3) {
                Art.star(
                    c,
                    rect.centerX() + (s - 1.2f) * size * 0.2f,
                    rect.bottom - size * 0.17f,
                    size * 0.075f,
                    s < stars
                )
            }
            hotspots.add(Hotspot(RectF(rect), { startLevel(i) }))
        } else {
            Art.panel(c, rect, size * 0.3f, 0x33103121, 1.4f, 0x33FFFFFF)
            Art.lock(c, rect.centerX(), rect.centerY() - size * 0.03f, size * 0.2f, 0x77FFFFFF)
            Art.text(c, "${lvl.id}", rect.centerX(), rect.bottom - size * 0.1f, size * 0.16f, 0x66FFFFFF)
        }
    }
    c.restore()

    Art.text(c, "بکش تا پایین بروی", w * 0.5f, h * 0.968f, w * 0.032f, 0x88FFF6E5.toInt(), Paint.Align.CENTER, Art.regular, true)
}

/* ------------------------------------------------------------------ game */

internal fun GameView.drawGame(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val g = game ?: return
    val lvl = g.level
    val now = System.currentTimeMillis()

    // ---------------- top chrome ----------------
    val back = RectF(w * 0.045f, h * 0.021f, w * 0.116f, h * 0.073f)
    Art.plaque(c, back, back.height() * 0.32f)
    Art.chevronLeft(c, back.centerX(), back.centerY(), back.height() * 0.24f, Palette.CREAM)
    hotspots.add(Hotspot(RectF(back), { paused = true }))

    val plaque = RectF(w * 0.305f, h * 0.019f, w * 0.695f, h * 0.077f)
    Art.plaque(c, plaque, plaque.height() * 0.34f)
    Art.text(c, "سطح ${fa(lvl.id)}", plaque.centerX(), plaque.top + plaque.height() * 0.46f,
        plaque.height() * 0.32f, Palette.CREAM, Paint.Align.CENTER, Art.black, true)
    Art.text(c, lvl.name, plaque.centerX(), plaque.top + plaque.height() * 0.81f,
        plaque.height() * 0.20f, 0xFFFFE9C8.toInt(), Paint.Align.CENTER, Art.bold)

    val wallet = RectF(w * 0.792f, h * 0.027f, w * 0.955f, h * 0.071f)
    Art.panel(c, wallet, wallet.height() / 2f, 0x59081710, 1.4f, 0x40FFE9C8)
    Art.coin(c, wallet.left + wallet.height() * 0.46f, wallet.centerY(), wallet.height() * 0.27f)
    Art.text(c, fa(save.coins), wallet.right - wallet.height() * 0.18f, wallet.centerY() + wallet.height() * 0.15f,
        wallet.height() * 0.33f, Palette.CREAM, Paint.Align.RIGHT, Art.bold, true)

    // ---------------- moves + score ----------------
    val hudTop = h * 0.090f
    val hudH = h * 0.064f

    val movesRect = RectF(w * 0.045f, hudTop, w * 0.232f, hudTop + hudH)
    val lowMoves = g.moves in 1..5 && g.status == Status.PLAYING
    if (lowMoves) {
        val pulse = 0.5f + 0.5f * sin(now / 170f)
        Art.glow(c, movesRect.centerX(), movesRect.centerY(), movesRect.height() * (1.05f + 0.3f * pulse),
            Palette.CORAL, 0.20f + 0.18f * pulse)
    }
    Art.card(c, movesRect, movesRect.height() * 0.34f, if (lowMoves) Palette.CORAL else Palette.LIME)
    Art.text(c, "حرکت", movesRect.centerX(), movesRect.top + movesRect.height() * 0.34f,
        movesRect.height() * 0.23f, Palette.MUTED, Paint.Align.CENTER, Art.regular)
    Art.text(c, fa(g.moves), movesRect.centerX(), movesRect.top + movesRect.height() * 0.79f,
        movesRect.height() * 0.40f, if (g.moves <= 5) Palette.CORAL else Palette.CREAM,
        Paint.Align.CENTER, Art.black, true)

    val scoreRect = RectF(w * 0.252f, hudTop, w * 0.955f, hudTop + hudH)
    Art.card(c, scoreRect, scoreRect.height() * 0.34f)
    val bar = RectF(
        scoreRect.left + scoreRect.width() * 0.055f, scoreRect.bottom - scoreRect.height() * 0.30f,
        scoreRect.right - scoreRect.width() * 0.055f, scoreRect.bottom - scoreRect.height() * 0.13f
    )
    val frac = if (lvl.target > 0) g.score.toFloat() / lvl.target else 0f
    Art.progress(c, bar, frac, Palette.GREEN, 0x66081A12)
    Art.text(c, fa(g.score), scoreRect.left + scoreRect.width() * 0.055f, scoreRect.top + scoreRect.height() * 0.47f,
        scoreRect.height() * 0.33f, Palette.CREAM, Paint.Align.LEFT, Art.black, true)
    Art.text(c, "هدف ${fa(lvl.target)}", scoreRect.right - scoreRect.width() * 0.055f,
        scoreRect.top + scoreRect.height() * 0.45f,
        scoreRect.height() * 0.24f, Palette.CREAM_DIM, Paint.Align.RIGHT, Art.bold)

    if (lvl.collect.isNotEmpty() || g.iceLeft > 0) {
        drawObjectives(c, g, hudTop + hudH + h * 0.010f)
    }

    // ---------------- board ----------------
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

private fun GameView.drawObjectives(c: Canvas, g: Game, y: Float) {
    val w = widthPx
    val h = heightPx
    val chipH = h * 0.042f
    val count = g.level.collect.size + (if (g.iceLeft > 0) 1 else 0)
    if (count == 0) return
    val gap = w * 0.02f
    val chipW = min(w * 0.24f, (w * 0.90f - gap * (count - 1)) / count)
    val totalW = chipW * count + gap * (count - 1)
    var x = (w - totalW) / 2f

    for (goal in g.level.collect) {
        val rect = RectF(x, y, x + chipW, y + chipH)
        val done = g.collected[goal.type] >= goal.count
        Art.panel(c, rect, chipH * 0.46f, if (done) 0xCC2F7A46.toInt() else 0x99081710.toInt(), 1.4f,
            if (done) 0x66B6F08A else 0x33FFE9C8)
        Art.produce(c, goal.type, rect.right - chipH * 0.56f, rect.centerY(), chipH * 1.02f)
        val have = min(g.collected[goal.type], goal.count)
        Art.text(c, "${fa(have)}/${fa(goal.count)}", rect.left + chipH * 0.34f, rect.centerY() + chipH * 0.16f,
            chipH * 0.40f, if (done) 0xFFB6F08A.toInt() else Palette.CREAM, Paint.Align.LEFT, Art.black, true)
        x += chipW + gap
    }
    if (g.iceLeft > 0) {
        val rect = RectF(x, y, x + chipW, y + chipH)
        Art.panel(c, rect, chipH * 0.46f, 0x99081710.toInt(), 1.4f, 0x33BFE9FF)
        Art.ice(c, RectF(rect.right - chipH * 0.82f, rect.centerY() - chipH * 0.34f,
            rect.right - chipH * 0.28f, rect.centerY() + chipH * 0.34f), 1f)
        Art.text(c, fa(g.iceLeft), rect.left + chipH * 0.34f, rect.centerY() + chipH * 0.16f,
            chipH * 0.40f, Palette.CREAM, Paint.Align.LEFT, Art.black, true)
    }
}

private fun GameView.drawBoosters(c: Canvas, g: Game) {
    val w = widthPx
    val h = heightPx
    val size = w * 0.196f
    val gap = w * 0.034f
    val total = size * 3 + gap * 2
    var x = (w - total) / 2f
    val y = h * 0.848f
    val cardH = size * 0.92f

    val kinds = listOf("hammer", "shuffle", "moves")
    val labels = listOf("چکش", "برهم‌زن", "حرکت")
    for (i in kinds.indices) {
        val kind = kinds[i]
        val armed = boosterMode == kind
        val lift = if (armed) size * 0.05f else 0f
        val rect = RectF(x, y - lift, x + size, y + cardH - lift)
        if (armed) {
            Art.glow(c, rect.centerX(), rect.centerY(), size * 0.80f, Palette.LIME, 0.32f)
        }
        Art.panel(c, RectF(rect.left, rect.top + size * 0.03f, rect.right, rect.bottom + size * 0.03f),
            size * 0.26f, 0x66000000)
        Art.panel(c, rect, size * 0.26f, if (armed) 0xDD1E3A22.toInt() else 0xCC14261C.toInt(), 2f,
            if (armed) Palette.LIME else 0x66FFE9C8)
        val plateR = size * 0.26f
        Art.circle(c, rect.centerX(), rect.top + cardH * 0.36f, plateR,
            if (armed) 0xFF2F9E5A.toInt() else 0xFF24422F.toInt())
        Art.circle(c, rect.centerX(), rect.top + cardH * 0.34f, plateR * 0.86f,
            if (armed) 0xFF4CBE72.toInt() else 0xFF31583C.toInt())
        val ir = plateR * 0.56f
        val icx = rect.centerX()
        val icy = rect.top + cardH * 0.34f
        when (kind) {
            "hammer" -> Art.hammer(c, icx, icy, ir, Palette.CREAM)
            "shuffle" -> Art.shuffleIcon(c, icx, icy, ir, Palette.CREAM)
            else -> Art.plusMoves(c, icx, icy, ir, Palette.CREAM)
        }
        Art.text(c, labels[i], rect.centerX(), rect.bottom - cardH * 0.12f, size * 0.16f,
            if (armed) Palette.LIME else Palette.CREAM_DIM, Paint.Align.CENTER, Art.bold)

        val owned = save.booster(kind)
        val bx = rect.right - size * 0.10f
        val by = rect.top + size * 0.10f
        Art.circle(c, bx, by, size * 0.135f, if (owned > 0) Palette.LIME else 0x66202830)
        Art.text(c, fa(owned), bx, by + size * 0.055f, size * 0.16f,
            if (owned > 0) 0xFF10331F.toInt() else Palette.CREAM_DIM, Paint.Align.CENTER, Art.black)
        hotspots.add(Hotspot(RectF(rect), { armBooster(kind) }))
        x += size + gap
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
    val slide = if (k < 0.16f) (1f - k / 0.16f) * -h * 0.1f else 0f
    val fade = when {
        k > 0.78f -> 1f - (k - 0.78f) / 0.22f
        k < 0.12f -> k / 0.12f
        else -> 1f
    }.coerceIn(0f, 1f)

    val cx = w / 2f
    val cy = h * 0.36f + slide
    val bh = h * 0.082f
    val bw = w * 0.56f
    val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
    c.save()
    c.scale(0.85f + 0.15f * min(1f, k * 5f), 0.85f + 0.15f * min(1f, k * 5f), cx, cy)
    Art.plaque(c, rect, bh * 0.3f)
    Art.text(c, "سطح ${fa(lvl.id)}", cx, cy - bh * 0.02f, bh * 0.38f,
        Art.withAlpha(Palette.CREAM, fade), Paint.Align.CENTER, Art.black, true)
    Art.text(c, lvl.name, cx, cy + bh * 0.33f, bh * 0.24f,
        Art.withAlpha(0xFFFFE9C8.toInt(), fade), Paint.Align.CENTER, Art.bold)
    Art.sparkle(c, rect.left + bw * 0.08f, cy, bh * 0.16f, Palette.GOLD, fade * 0.9f, age / 6f)
    Art.sparkle(c, rect.right - bw * 0.08f, cy, bh * 0.16f, Palette.GOLD, fade * 0.9f, -age / 6f)
    c.restore()
}

/* ------------------------------------------------------------------ shop */

internal fun GameView.drawShop(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "فروشگاه")

    val balance = RectF(w * 0.28f, h * 0.118f, w * 0.72f, h * 0.168f)
    Art.card(c, balance, balance.height() * 0.46f)
    Art.coin(c, balance.left + balance.height() * 0.52f, balance.centerY(), balance.height() * 0.32f)
    Art.text(c, "${save.coins}", balance.centerX() + balance.height() * 0.2f, balance.centerY() + balance.height() * 0.17f,
        balance.height() * 0.5f, Palette.CREAM, Paint.Align.CENTER, Art.black, true)

    val top = h * 0.185f
    val cardH = h * 0.1f
    val gap = h * 0.014f
    for (i in eco.shop.indices) {
        val item = eco.shop[i]
        val y = top + i * (cardH + gap)
        val rect = RectF(w * 0.045f, y, w * 0.955f, y + cardH)
        Art.card(c, rect, cardH * 0.26f)
        val badgeR = cardH * 0.3f
        Art.badge(c, rect.left + cardH * 0.34f + badgeR, rect.centerY(), badgeR,
            0xFF2F7A4E.toInt(), 0xFF14402C.toInt(), Palette.GOLD) { cc, cx, cy, r ->
            iconFor(item.kind, cc, cx, cy, r, Palette.CREAM)
        }
        Art.text(c, item.title, rect.right - cardH * 0.28f, rect.centerY() - cardH * 0.1f,
            cardH * 0.24f, Palette.CREAM, Paint.Align.RIGHT, Art.bold, true)
        Art.text(c, item.desc, rect.right - cardH * 0.28f, rect.centerY() + cardH * 0.17f,
            cardH * 0.17f, Palette.MUTED, Paint.Align.RIGHT)
        val buy = RectF(rect.left + cardH * 1.11f, rect.top + cardH * 0.2f, rect.left + cardH * 2.1f, rect.bottom - cardH * 0.2f)
        Art.buttonSkin(c, buy, buy.height() * 0.34f, Palette.LIME, 0xFF6FAF30.toInt())
        Art.coin(c, buy.left + buy.height() * 0.34f, buy.centerY(), buy.height() * 0.22f)
        Art.text(c, "${item.price}", buy.centerX() + buy.height() * 0.22f, buy.centerY() + buy.height() * 0.16f,
            buy.height() * 0.4f, 0xFF17330A.toInt(), Paint.Align.CENTER, Art.black)
        hotspots.add(Hotspot(RectF(buy), { buyItem(item) }))
    }

    val y = top + eco.shop.size * (cardH + gap)
    val rect = RectF(w * 0.045f, y, w * 0.955f, y + cardH)
    Art.card(c, rect, cardH * 0.26f, Palette.DANGER)
    val badgeR = cardH * 0.3f
    Art.badge(c, rect.left + cardH * 0.34f + badgeR, rect.centerY(), badgeR,
        0xFFFF7D8F.toInt(), 0xFFC22B44.toInt(), 0xFF7A1226.toInt()) { cc, cx, cy, r -> Art.heart(cc, cx, cy, r, true) }
    Art.text(c, "یک جان", rect.right - cardH * 0.28f, rect.centerY() - cardH * 0.1f,
        cardH * 0.24f, Palette.CREAM, Paint.Align.RIGHT, Art.bold, true)
    Art.text(c, "برای وقتی که جان کم آوردی", rect.right - cardH * 0.28f, rect.centerY() + cardH * 0.17f,
        cardH * 0.17f, Palette.MUTED, Paint.Align.RIGHT)
    val buy = RectF(rect.left + cardH * 1.11f, rect.top + cardH * 0.2f, rect.left + cardH * 2.1f, rect.bottom - cardH * 0.2f)
    Art.buttonSkin(c, buy, buy.height() * 0.34f, Palette.LIME, 0xFF6FAF30.toInt())
    Art.coin(c, buy.left + buy.height() * 0.34f, buy.centerY(), buy.height() * 0.22f)
    Art.text(c, "${eco.lifePrice}", buy.centerX() + buy.height() * 0.22f, buy.centerY() + buy.height() * 0.16f,
        buy.height() * 0.4f, 0xFF17330A.toInt(), Paint.Align.CENTER, Art.black)
    hotspots.add(Hotspot(RectF(buy), { buyLife() }))
}

/* ----------------------------------------------------------------- wheel */

internal fun GameView.drawWheel(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "چرخ شانس")

    val cx = w / 2f
    val cy = h * 0.415f
    val radius = min(w * 0.41f, h * 0.225f)
    val slices = eco.wheel
    if (slices.isEmpty()) return
    val seg = 360f / slices.size

    val sliceColors = intArrayOf(
        0xFFF2545B.toInt(), 0xFFFF9F1C.toInt(), 0xFFFFD93D.toInt(), 0xFF7ED957.toInt(),
        0xFF34C759.toInt(), 0xFF41B8E0.toInt(), 0xFF8B5CF6.toInt(), 0xFFD96BD0.toInt()
    )

    // outer wooden rim
    Art.circle(c, cx, cy, radius * 1.1f, 0xFF5E3818.toInt())
    Art.circle(c, cx, cy, radius * 1.06f, 0xFFC08A52.toInt())

    c.save()
    c.rotate(wheelAngle, cx, cy)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val arc = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
    for (i in slices.indices) {
        paint.color = sliceColors[i % sliceColors.size]
        c.drawArc(arc, i * seg, seg, true, paint)
        paint.color = 0x1A000000
        c.drawArc(arc, i * seg + seg * 0.92f, seg * 0.08f, true, paint)
        paint.color = 0x22FFFFFF
        c.drawArc(arc, i * seg, seg * 0.08f, true, paint)
    }
    for (i in slices.indices) {
        c.save()
        c.rotate(i * seg + seg / 2f, cx, cy)
        val ir = radius * 0.16f
        val iy = cy - radius * 0.62f
        iconFor(slices[i].kind, c, cx, iy - ir * 0.25f, ir * 0.8f, 0xFFFFFFFF.toInt())
        Art.text(c, slices[i].label, cx, iy + ir * 1.15f, radius * 0.105f, 0xFFFFFFFF.toInt(),
            Paint.Align.CENTER, Art.bold, true)
        c.restore()
    }
    c.restore()

    // studs
    for (i in 0 until 16) {
        val a = Math.toRadians(i * 22.5)
        val sx = cx + (cos(a) * radius * 1.08f).toFloat()
        val sy = cy + (sin(a) * radius * 1.08f).toFloat()
        Art.circle(c, sx, sy, radius * 0.028f, 0xFF6E4220.toInt())
        Art.circle(c, sx, sy - radius * 0.006f, radius * 0.02f, 0xFFFFE0A8.toInt())
    }

    // hub
    Art.circle(c, cx, cy, radius * 0.2f, 0xFF5E3818.toInt())
    Art.circle(c, cx, cy, radius * 0.17f, Palette.GOLD)
    Art.text(c, "شانس", cx, cy + radius * 0.05f, radius * 0.13f, 0xFF6B4300.toInt(), Paint.Align.CENTER, Art.black)

    // pointer
    val p = Path()
    p.moveTo(cx, cy - radius * 0.98f)
    p.lineTo(cx - w * 0.045f, cy - radius * 1.2f)
    p.lineTo(cx + w * 0.045f, cy - radius * 1.2f)
    p.close()
    paint.color = 0xFFE5455F.toInt()
    c.drawPath(p, paint)
    paint.color = 0x66FFFFFF
    c.drawCircle(cx, cy - radius * 1.13f, w * 0.012f, paint)

    val ready = wheelReadyIn()
    val spinning = wheelSpinDur > 0L
    val btn = RectF(w * 0.18f, h * 0.705f, w * 0.82f, h * 0.782f)
    val label = when {
        spinning -> "می‌چرخد…"
        ready > 0 -> "شارژ مجدد: " + fmtTime(ready)
        else -> "بچرخان (رایگان)"
    }
    if (ready == 0L && !spinning) {
        Art.glow(c, btn.centerX(), btn.centerY(), btn.width() * 0.5f, Palette.LIME, 0.28f)
        Art.buttonSkin(c, btn, btn.height() * 0.36f, Palette.LIME, 0xFF6FAF30.toInt())
    } else {
        Art.buttonSkin(c, btn, btn.height() * 0.36f, Palette.PANEL_SOFT, 0xFF0C2C21.toInt())
    }
    Art.text(c, label, btn.centerX(), btn.centerY() + btn.height() * 0.17f, btn.height() * 0.4f,
        if (ready == 0L && !spinning) 0xFF17330A.toInt() else Palette.CREAM, Paint.Align.CENTER, Art.black, true)
    hotspots.add(Hotspot(RectF(btn), { if (!spinning) spinWheel() }))

    val prize = wheelPrize
    if (prize != null && !spinning && wheelAwarded) {
        val card = RectF(w * 0.16f, h * 0.80f, w * 0.84f, h * 0.9f)
        Art.card(c, card, card.height() * 0.26f, Palette.GOLD)
        Art.text(c, "جایزه", card.centerX(), card.top + card.height() * 0.28f, card.height() * 0.2f, Palette.MUTED)
        Art.text(c, prize.label, card.centerX(), card.centerY() + card.height() * 0.14f, card.height() * 0.36f,
            Palette.GOLD, Paint.Align.CENTER, Art.black, true)
        val ok = RectF(card.right - w * 0.24f, card.top + card.height() * 0.22f, card.right - w * 0.05f, card.bottom - card.height() * 0.22f)
        Art.buttonSkin(c, ok, ok.height() * 0.36f, Palette.LIME, 0xFF6FAF30.toInt())
        Art.text(c, "باشه", ok.centerX(), ok.centerY() + ok.height() * 0.17f, ok.height() * 0.38f,
            0xFF17330A.toInt(), Paint.Align.CENTER, Art.black)
        hotspots.add(Hotspot(RectF(ok), { wheelPrize = null }))
    } else {
        Art.text(c, "هر ۴ ساعت یک چرخ رایگان", cx, h * 0.85f, w * 0.04f, Palette.CREAM_DIM, Paint.Align.CENTER, Art.regular, true)
    }
}

/* ------------------------------------------------------------------ pass */

internal var passScroll = 0

internal fun GameView.drawPass(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "مسیر ستاره")

    val bar = RectF(w * 0.16f, h * 0.122f, w * 0.84f, h * 0.166f)
    Art.progress(c, bar, save.totalStars / 60f, Palette.GOLD, 0x660A1F16)
    Art.star(c, bar.left + bar.height() * 0.5f, bar.centerY(), bar.height() * 0.34f, true)
    Art.text(c, "${save.totalStars}", bar.centerX(), bar.centerY() + bar.height() * 0.17f, bar.height() * 0.5f,
        Palette.CREAM, Paint.Align.CENTER, Art.black, true)
    Art.text(c, "با هر ۲ ستاره یک پله باز می‌شود", w * 0.5f, h * 0.196f, w * 0.038f, Palette.MUTED)

    val top = h * 0.225f
    val bottom = h * 0.94f
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
        Art.card(c, rect, cardH * 0.28f, when {
            claimed -> Palette.GREEN
            unlocked -> Palette.GOLD
            else -> Palette.MUTED
        })
        val badgeR = cardH * 0.28f
        Art.badge(c, rect.left + cardH * 0.32f + badgeR, rect.centerY(), badgeR,
            if (unlocked) 0xFF2F7A4E.toInt() else 0xFF243B33.toInt(),
            0xFF12271F.toInt(), if (unlocked) Palette.GOLD else 0x66FFFFFF.toInt()) { cc, cx, cy, r ->
            iconFor(tier.kind, cc, cx, cy, r, Palette.CREAM)
        }
        Art.text(c, tier.label, rect.right - cardH * 0.26f, rect.centerY() - cardH * 0.08f,
            cardH * 0.22f, Palette.CREAM, Paint.Align.RIGHT, Art.bold, true)
        Art.text(c, "پله ${tier.index}", rect.right - cardH * 0.26f, rect.centerY() + cardH * 0.18f,
            cardH * 0.16f, Palette.MUTED, Paint.Align.RIGHT)

        val btn = RectF(rect.left + cardH * 1.0f, rect.top + cardH * 0.24f, rect.left + cardH * 1.95f, rect.bottom - cardH * 0.24f)
        val label = when {
            claimed -> "گرفته شد"
            !unlocked -> "${tier.stars} ★".replace("★", "ستاره")
            else -> "دریافت"
        }
        if (unlocked && !claimed) {
            Art.buttonSkin(c, btn, btn.height() * 0.36f, Palette.LIME, 0xFF6FAF30.toInt())
            Art.text(c, label, btn.centerX(), btn.centerY() + btn.height() * 0.17f, btn.height() * 0.36f,
                0xFF17330A.toInt(), Paint.Align.CENTER, Art.black)
        } else {
            Art.panel(c, btn, btn.height() * 0.36f, 0x4D0A1F16, 1.2f, 0x33FFFFFF)
            Art.text(c, label, btn.centerX(), btn.centerY() + btn.height() * 0.17f, btn.height() * 0.32f,
                Palette.MUTED, Paint.Align.CENTER, Art.bold)
        }
        if (unlocked && !claimed) {
            hotspots.add(Hotspot(RectF(btn), { claimTier(tier) }))
        }
    }
    Art.text(c, "بکش تا پله‌های بیشتر ببینی", w * 0.5f, h * 0.968f, w * 0.032f, 0x88FFF6E5.toInt(), Paint.Align.CENTER, Art.regular, true)
}

/* -------------------------------------------------------------- settings */

internal fun GameView.drawSettings(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "تنظیمات")

    val rowY = h * 0.16f
    val rowH = h * 0.088f
    val soundRect = RectF(w * 0.045f, rowY, w * 0.955f, rowY + rowH)
    Art.card(c, soundRect, rowH * 0.28f)
    Art.text(c, "صدا", soundRect.right - w * 0.05f, soundRect.centerY() + w * 0.014f, w * 0.038f,
        Palette.CREAM, Paint.Align.RIGHT, Art.bold, true)
    val toggle = RectF(soundRect.left + w * 0.045f, soundRect.centerY() - rowH * 0.24f,
        soundRect.left + w * 0.045f + w * 0.16f, soundRect.centerY() + rowH * 0.24f)
    Art.panel(c, toggle, toggle.height() / 2f, if (save.sound) Palette.LIME else 0x550A1F16)
    Art.circle(c, if (save.sound) toggle.right - toggle.height() / 2f else toggle.left + toggle.height() / 2f,
        toggle.centerY(), toggle.height() * 0.38f, Palette.PANEL_DEEP)
    hotspots.add(Hotspot(RectF(toggle), {
        save.sound = !save.sound
        save.persist()
        sound(R.raw.sfx_click)
    }))

    val resetRect = RectF(w * 0.045f, rowY + rowH * 1.35f, w * 0.955f, rowY + rowH * 2.35f)
    Art.buttonSkin(c, resetRect, resetRect.height() * 0.32f, 0xFFFF7D8F.toInt(), 0xFFC22B44.toInt())
    Art.text(c, "شروع دوباره از سطح ۱", resetRect.centerX(), resetRect.centerY() + resetRect.height() * 0.17f,
        resetRect.height() * 0.36f, 0xFF3D0510.toInt(), Paint.Align.CENTER, Art.black)
    hotspots.add(Hotspot(RectF(resetRect), {
        save.reset(eco)
        levelIndex = 0
        toast("پیشرفت پاک شد")
    }))

    val about = RectF(w * 0.1f, h * 0.5f, w * 0.9f, h * 0.72f)
    Art.card(c, about, about.height() * 0.14f, Palette.GOLD)
    var x = about.left + about.width() * 0.12f
    for (t in intArrayOf(0, 1, 2, 3, 4)) {
        Art.produce(c, t, x, about.top + about.height() * 0.28f, about.width() * 0.16f)
        x += about.width() * 0.19f
    }
    Art.text(c, "میوه‌چین — نسخهٔ ۱٫۲", about.centerX(), about.top + about.height() * 0.62f,
        about.width() * 0.075f, Palette.CREAM, Paint.Align.CENTER, Art.black, true)
    Art.text(c, "هفت محصول، ۶۰ سطح، یک باغ.", about.centerX(), about.top + about.height() * 0.78f,
        about.width() * 0.055f, Palette.MUTED)
}

/* -------------------------------------------------------------- overlays */

internal fun GameView.drawOverlays(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val res = result
    when {
        res != null -> drawResult(c, res)
        paused -> drawPause(c)
    }
    if (screen == Screen.GAME && boosterMode == "hammer" && res == null && !paused) {
        Art.text(c, "روی یک میوه بزن تا حذف شود", w * 0.5f, h * 0.925f, w * 0.038f,
            Palette.LIME, Paint.Align.CENTER, Art.bold, true)
    }
}

private fun GameView.drawPause(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val dim = Paint()
    dim.color = 0xB3000000.toInt()
    c.drawRect(0f, 0f, w, h, dim)

    val panel = RectF(w * 0.08f, h * 0.27f, w * 0.92f, h * 0.69f)
    Art.card(c, panel, w * 0.06f, Palette.LIME)
    Art.text(c, "مکث", panel.centerX(), panel.top + h * 0.070f, w * 0.062f, Palette.CREAM,
        Paint.Align.CENTER, Art.black, true)

    val bh = h * 0.068f
    val left = panel.left + w * 0.06f
    val right = panel.right - w * 0.06f
    button(c, RectF(left, panel.top + h * 0.11f, right, panel.top + h * 0.11f + bh), "ادامه", Style.PRIMARY) {
        paused = false
    }
    button(c, RectF(left, panel.top + h * 0.11f + bh * 1.24f, right, panel.top + h * 0.11f + bh * 2.24f),
        "شروع دوباره", Style.SECONDARY) {
        paused = false
        restartLevel()
    }
    button(c, RectF(left, panel.top + h * 0.11f + bh * 2.48f, right, panel.top + h * 0.11f + bh * 3.48f),
        "خانه", Style.GHOST) {
        paused = false
        result = null
        screen = Screen.HOME
    }
}

private fun GameView.drawResult(c: Canvas, res: GameView.Result) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val age = (now - resultAt).coerceAtLeast(0L)
    val t = age / 1000f
    val win = res.win

    fun easeOutBack(k: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = k - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    fun easeOutCubic(k: Float): Float {
        val u = 1f - k
        return 1f - u * u * u
    }

    val gold = Palette.GOLD
    val accent = if (win) gold else 0xFFFF7D8F.toInt()

    /* ------------------------------------------------------- backdrop */
    val dimK = (age / 240f).coerceIn(0f, 1f)
    val dim = Paint()
    dim.color = Art.withAlpha(0xFF03100A.toInt(), 0.84f * dimK)
    c.drawRect(0f, 0f, w, h, dim)

    val rayCx = w / 2f
    val rayCy = h * 0.30f
    c.save()
    c.rotate(t * 7f, rayCx, rayCy)
    val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    rayPaint.color = Art.withAlpha(accent, 0.075f * dimK)
    for (i in 0 until 16) {
        val a0 = Math.toRadians(i * 22.5)
        val a1 = Math.toRadians(i * 22.5 + 9.0)
        val ray = Path()
        ray.moveTo(rayCx, rayCy)
        ray.lineTo(rayCx + (cos(a0) * w * 1.5f).toFloat(), rayCy + (sin(a0) * w * 1.5f).toFloat())
        ray.lineTo(rayCx + (cos(a1) * w * 1.5f).toFloat(), rayCy + (sin(a1) * w * 1.5f).toFloat())
        ray.close()
        c.drawPath(ray, rayPaint)
    }
    c.restore()

    if (win) {
        val conf = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 24) {
            val phase = (i * 0.377f) % 1f
            val speed = 0.14f + 0.09f * ((i * 7) % 5) / 4f
            val y = ((phase + t * speed) % 1.18f - 0.09f) * h
            val x = (0.05f + 0.90f * ((i * 0.618f) % 1f)) * w + sin(t * 1.6f + i) * w * 0.018f
            val col = when (i % 4) {
                0 -> Palette.LIME
                1 -> Palette.GOLD
                2 -> Palette.CORAL
                else -> 0xFF9FE6FF.toInt()
            }
            conf.color = Art.withAlpha(col, 0.8f * dimK)
            c.save()
            c.rotate(t * 130f + i * 37f, x, y)
            c.drawRect(x - w * 0.007f, y - w * 0.013f, x + w * 0.007f, y + w * 0.013f, conf)
            c.restore()
        }
    }

    /* ----------------------------------------------------------- frame */
    val open = easeOutBack(((age - 80f) / 430f).coerceIn(0f, 1f))
    val panel = RectF(w * 0.055f, h * 0.112f, w * 0.945f, h * 0.888f)
    val pulse = 0.5f + 0.5f * sin(t * 2.1f)
    Art.glow(c, panel.centerX(), panel.centerY(), panel.width() * 0.58f, accent, (0.14f + 0.07f * pulse) * open)

    c.save()
    val panelScale = 0.84f + 0.16f * open
    c.scale(panelScale, panelScale, panel.centerX(), panel.centerY())

    val frameTop = if (win) 0xFFF0C070.toInt() else 0xFFE4707F.toInt()
    val frameBot = if (win) 0xFF8F5E18.toInt() else 0xFF8C1F30.toInt()
    val feltTop = if (win) 0xFF1B4A34.toInt() else 0xFF3A1220.toInt()
    val feltBot = if (win) 0xFF071E16.toInt() else 0xFF170509.toInt()

    Art.gradientPanel(c, panel, w * 0.078f, frameTop, frameBot)
    val inner = RectF(panel.left + w * 0.024f, panel.top + w * 0.024f, panel.right - w * 0.024f, panel.bottom - w * 0.024f)
    Art.gradientPanel(c, inner, w * 0.058f, feltTop, feltBot)
    Art.panel(c, inner, w * 0.058f, 0x00000000, 2.5f, 0xB3FFE9C8.toInt())
    val hair = RectF(inner.left + w * 0.014f, inner.top + w * 0.014f, inner.right - w * 0.014f, inner.bottom - w * 0.014f)
    Art.panel(c, hair, w * 0.05f, 0x00000000, 1.2f, 0x40FFE9C8)
    for (i in 0 until 4) {
        val sx = if (i % 2 == 0) inner.left + w * 0.030f else inner.right - w * 0.030f
        val sy = if (i < 2) inner.top + w * 0.030f else inner.bottom - w * 0.030f
        Art.circle(c, sx, sy, w * 0.010f, Art.withAlpha(accent, 0.9f))
        Art.circle(c, sx - w * 0.002f, sy - w * 0.002f, w * 0.004f, 0x99FFFFFF.toInt())
    }

    /* ---------------------------------------------------------- ribbon */
    val ribH = h * 0.080f
    val ribbon = RectF(panel.centerX() - w * 0.305f, panel.top + h * 0.004f,
        panel.centerX() + w * 0.305f, panel.top + h * 0.004f + ribH)
    Art.panel(c, RectF(ribbon.left + w * 0.006f, ribbon.top + h * 0.008f, ribbon.right + w * 0.006f, ribbon.bottom + h * 0.008f),
        ribH * 0.34f, 0x66000000)
    Art.plaque(c, ribbon, ribH * 0.34f)
    val titleSize = if (win) ribH * 0.46f else ribH * 0.32f
    Art.title(c, if (win) "آفرین!" else "حرکات تمام شد", ribbon.centerX(), ribbon.centerY() + ribH * 0.18f,
        titleSize, Palette.CREAM, if (win) 0xFF6B4300.toInt() else 0xFF5A0A18.toInt())
    // moving highlight across the ribbon
    c.save()
    val ribClip = Path()
    ribClip.addRoundRect(ribbon, ribH * 0.34f, ribH * 0.34f, Path.Direction.CW)
    c.clipPath(ribClip)
    val sweepX = ribbon.left + ((t * 0.55f) % 1.4f) * ribbon.width()
    val sweep = Paint(Paint.ANTI_ALIAS_FLAG)
    sweep.color = 0x33FFFFFF
    val sweepPath = Path()
    sweepPath.moveTo(sweepX, ribbon.top)
    sweepPath.lineTo(sweepX + w * 0.05f, ribbon.top)
    sweepPath.lineTo(sweepX - w * 0.04f, ribbon.bottom)
    sweepPath.lineTo(sweepX - w * 0.09f, ribbon.bottom)
    sweepPath.close()
    c.drawPath(sweepPath, sweep)
    c.restore()

    /* ------------------------------------------------------- level chip */
    val chipTop = ribbon.bottom + h * 0.010f
    val chip = RectF(panel.centerX() - w * 0.30f, chipTop, panel.centerX() + w * 0.30f, chipTop + h * 0.040f)
    Art.panel(c, chip, chip.height() / 2f, 0x4D000000, 1.4f, 0x40FFE9C8)
    val lvlName = levels.getOrNull(res.levelId - 1)?.name ?: ""
    val chipLabel = if (lvlName.isBlank()) "سطح ${fa(res.levelId)}" else "سطح ${fa(res.levelId)}  •  $lvlName"
    Art.text(c, chipLabel, chip.centerX(), chip.centerY() + chip.height() * 0.20f, chip.height() * 0.44f,
        Palette.CREAM_DIM, Paint.Align.CENTER, Art.bold)

    /* ------------------------------------------------------------ stars */
    val starY = panel.top + h * 0.205f
    val gap = w * 0.205f
    val bigR = w * 0.075f
    var landed = 0
    for (i in 0 until 3) {
        val sx = panel.centerX() + (i - 1) * gap
        val socket = RectF(sx - bigR * 1.22f, starY - bigR * 1.22f, sx + bigR * 1.22f, starY + bigR * 1.22f)
        Art.panel(c, socket, bigR * 0.62f, 0x66000000, 2f, 0x33FFE9C8)
        Art.panel(c, RectF(socket.left + 4f, socket.top + 4f, socket.right - 4f, socket.bottom - 4f),
            bigR * 0.58f, 0x1AFFFFFF, 1.2f, 0x22FFFFFF)
        if (i >= res.stars) {
            Art.star(c, sx, starY, bigR * 0.72f, false, 0x40FFE9C8)
            continue
        }

        val land = 520f + i * 260f
        val fly = ((age - (land - 320f)) / 320f).coerceIn(0f, 1f)
        if (fly <= 0f) {
            Art.star(c, sx, starY, bigR * 0.72f, false, 0x40FFE9C8)
            if (age >= land) landed++
            continue
        }
        if (age >= land) landed++

        val fall = fly * fly
        val cy = starY - h * 0.16f + h * 0.16f * fall
        val sc = 1.85f - 0.85f * fly

        // motion trail
        for (j in 1..3) {
            val trailY = cy - h * 0.048f * j * (1f - fly * 0.55f)
            Art.star(c, sx, trailY, bigR * sc * (1f - 0.14f * j), false,
                Art.withAlpha(gold, 0.24f * (1f - fly * 0.6f) / j))
        }
        c.save()
        c.scale(sc, sc, sx, cy)
        Art.star(c, sx, cy, bigR, true)
        c.restore()

        val hit = (age - land).toInt()
        if (hit in 0..340) {
            val hk = hit / 340f
            val ringR = bigR * (1.05f + 1.5f * hk)
            Art.panel(c, RectF(sx - ringR, cy - ringR, sx + ringR, cy + ringR), ringR, 0x00000000,
                3f * (1f - hk) + 1f, Art.withAlpha(gold, 0.75f * (1f - hk)))
            Art.glow(c, sx, cy, bigR * (1.2f + 1.4f * hk), gold, 0.42f * (1f - hk))
            for (j in 0 until 8) {
                val a = j * (Math.PI / 4) + hk * 1.2f
                val d = bigR * (0.9f + 1.7f * hk)
                Art.sparkle(c, sx + (cos(a) * d).toFloat(), cy + (sin(a) * d).toFloat(),
                    bigR * 0.22f * (1f - hk), 0xFFFFFFFF.toInt(), 0.9f * (1f - hk), hk * 220f)
            }
        }
        if (hit > 340) {
            val idle = 1f + 0.045f * sin(t * 3.4f + i * 1.3f)
            Art.glow(c, sx, cy, bigR * 1.5f, gold, 0.16f + 0.06f * sin(t * 2.4f + i))
            c.save()
            c.scale(idle, idle, sx, cy)
            Art.star(c, sx, cy, bigR, true)
            c.restore()
            Art.sparkle(c, sx + bigR * 1.05f, cy - bigR * 0.9f, bigR * 0.20f, 0xFFFFFFFF.toInt(),
                0.45f + 0.4f * sin(t * 3f + i), t * 90f)
        }
    }

    if (win) {
        if (landed > resultStarSfx) {
            sound(R.raw.sfx_star, 1f + 0.07f * (landed - 1))
            flashAt = System.currentTimeMillis()
            flashColor = gold
            resultStarSfx = landed
        }
    }

    /* ------------------------------------------------------------ score */
    val plate = RectF(panel.left + w * 0.100f, panel.top + h * 0.274f,
        panel.right - w * 0.100f, panel.top + h * 0.468f)
    Art.panel(c, RectF(plate.left, plate.top + h * 0.004f, plate.right, plate.bottom + h * 0.004f), w * 0.045f, 0x33000000)
    Art.panel(c, plate, w * 0.045f, 0x2A000000, 1.4f, 0x3DFFE9C8)
    Art.panel(c, RectF(plate.left + 5f, plate.top + 5f, plate.right - 5f, plate.bottom - 5f), w * 0.040f,
        0x00000000, 1f, 0x1AFFFFFF)

    val scoreK = easeOutCubic(((age - 1420f) / 720f).coerceIn(0f, 1f))
    val scoreY = panel.top + h * 0.318f
    Art.text(c, "امتیاز", panel.centerX(), scoreY - h * 0.030f, w * 0.030f, Palette.MUTED,
        Paint.Align.CENTER, Art.bold)
    val bump = 1f + 0.06f * sin(((age - 1420f) / 720f).coerceIn(0f, 1f) * Math.PI.toFloat())
    c.save()
    c.scale(bump, bump, panel.centerX(), scoreY)
    Art.text(c, fa((res.score * scoreK).toInt()), panel.centerX(), scoreY + h * 0.018f, w * 0.072f,
        Palette.CREAM, Paint.Align.CENTER, Art.black, true)
    c.restore()

    val divY = panel.top + h * 0.372f
    Art.panel(c, RectF(panel.left + w * 0.13f, divY, panel.centerX() - w * 0.065f, divY + 2.5f), 2f, 0x40FFE9C8)
    Art.panel(c, RectF(panel.centerX() + w * 0.065f, divY, panel.right - w * 0.13f, divY + 2.5f), 2f, 0x40FFE9C8)
    Art.leaf(c, panel.centerX(), divY + 2f, w * 0.030f, Palette.LEAF_DARK, Palette.LEAF, 0f)

    /* ----------------------------------------------------------- reward */
    val pillH = h * 0.056f
    val pill = RectF(panel.centerX() - w * 0.215f, panel.top + h * 0.400f,
        panel.centerX() + w * 0.215f, panel.top + h * 0.400f + pillH)
    val pillK = easeOutBack(((age - 1850f) / 420f).coerceIn(0f, 1f))
    if (pillK > 0.02f) {
        c.save()
        c.scale(pillK, pillK, pill.centerX(), pill.centerY())
        Art.glow(c, pill.centerX(), pill.centerY(), pill.width() * 0.55f, gold, 0.14f * pillK)
        Art.panel(c, pill, pill.height() / 2f, 0x66000000, 1.8f, 0xB3FFE9C8.toInt())
        val coinR = pill.height() * 0.34f
        c.save()
        c.scale(1f, Math.abs(cos(t * 2.2f)) * 0.75f + 0.25f, pill.left + pill.height() * 0.62f, pill.centerY())
        Art.coin(c, pill.left + pill.height() * 0.62f, pill.centerY(), coinR)
        c.restore()
        Art.text(c, if (win) "+${fa(res.coins)} سکه" else "بدون جایزه", pill.centerX() + pill.height() * 0.20f,
            pill.centerY() + pill.height() * 0.17f, pill.height() * 0.42f,
            if (win) Palette.GOLD else Palette.MUTED, Paint.Align.CENTER, Art.black, true)
        c.restore()
        if (win && !resultCoinSfx && age >= 1850f) {
            sound(R.raw.sfx_coin)
            resultCoinSfx = true
        }
    }

    /* ---------------------------------------------------- garden vignette */
    val gardenTop = panel.top + h * (if (win) 0.638f else 0.700f)
    c.save()
    val clip = Path()
    clip.addRoundRect(inner, w * 0.064f, w * 0.064f, Path.Direction.CW)
    c.clipPath(clip)
    Art.gradientPanel(c, RectF(inner.left, gardenTop, inner.right, inner.bottom), 0f,
        0x00000000, if (win) 0xFF061B13.toInt() else 0xFF120408.toInt())
    Art.bush(c, inner.left + w * 0.16f, inner.bottom + h * 0.004f, w * 0.34f, h * 0.075f,
        Palette.LEAF_DARK, Palette.LEAF, 0.35f)
    Art.bush(c, inner.right - w * 0.15f, inner.bottom + h * 0.006f, w * 0.36f, h * 0.080f,
        Palette.LEAF_DARK, Palette.LEAF, 0.35f)
    Art.bush(c, inner.centerX(), inner.bottom + h * 0.008f, w * 0.30f, h * 0.065f,
        Palette.LEAF_DARK, Palette.LEAF, 0.5f)
    val flowerCols = intArrayOf(0xFFFF8FA3.toInt(), 0xFFFFD166.toInt(), 0xFFB9A6FF.toInt(), 0xFFFFB4E1.toInt())
    for (i in 0 until 7) {
        val fx = inner.left + w * (0.10f + 0.80f * i / 6f)
        val fy = inner.bottom - h * 0.010f - h * 0.012f * (i % 3)
        val sway = sin(t * 1.6f + i) * w * 0.004f
        Art.flower(c, fx + sway, fy, w * 0.015f, flowerCols[i % flowerCols.size], 0xFFFFF3C4.toInt())
    }
    if (win) {
        for (i in 0 until 3) {
            val px = inner.left + w * (0.22f + 0.28f * i)
            val py = inner.bottom - h * 0.052f + sin(t * 2.2f + i * 1.7f) * h * 0.003f
            Art.produceHalo(c, i * 2, px, py, w * 0.105f)
            Art.produce(c, i * 2, px, py, w * 0.108f)
        }
    }
    c.restore()

    /* ----------------------------------------------------------- buttons */
    val bh = h * 0.062f
    val left = panel.left + w * 0.075f
    val right = panel.right - w * 0.075f
    val btnK = easeOutCubic(((age - 2080f) / 420f).coerceIn(0f, 1f))
    val slide = (1f - btnK) * h * 0.02f

    if (win) {
        val primary = RectF(left, panel.top + h * 0.488f + slide, right, panel.top + h * 0.488f + bh + slide)
        val ghost = RectF(left, primary.bottom + h * 0.018f, right, primary.bottom + h * 0.018f + bh * 0.92f)
        if (btnK > 0.05f) {
            button(c, primary, if (res.levelId < levels.size) "سطح بعد" else "پایان مراحل", Style.PRIMARY) {
                result = null
                if (res.levelId < levels.size) {
                    nextLevel()
                } else {
                    screen = Screen.MAP
                }
            }
            button(c, ghost, "نقشه", Style.GHOST) {
                result = null
                screen = Screen.MAP
            }
        }
    } else {
        val b1 = RectF(left, panel.top + h * 0.470f + slide, right, panel.top + h * 0.470f + bh + slide)
        val b2 = RectF(left, b1.bottom + h * 0.018f, right, b1.bottom + h * 0.018f + bh)
        val b3 = RectF(left, b2.bottom + h * 0.018f, right, b2.bottom + h * 0.018f + bh * 0.92f)
        if (btnK > 0.05f) {
            button(c, b1, "۵ حرکت با ۸۰ سکه", Style.PRIMARY) { buyMovesForCoins() }
            button(c, b2, "تلاش دوباره (یک جان)", Style.SECONDARY) { restartLevel() }
            button(c, b3, "نقشه", Style.GHOST) {
                result = null
                screen = Screen.MAP
            }
        }
    }

    c.restore()
}

private fun GameView.header(c: Canvas, title: String) {
    val w = widthPx
    val h = heightPx
    val plaque = RectF(w * 0.22f, h * 0.03f, w * 0.78f, h * 0.098f)
    Art.plaque(c, plaque, plaque.height() * 0.36f)
    Art.text(c, title, plaque.centerX(), plaque.centerY() + plaque.height() * 0.18f, plaque.height() * 0.46f,
        Palette.CREAM, Paint.Align.CENTER, Art.black, true)

    val back = RectF(w * 0.045f, h * 0.032f, w * 0.12f, h * 0.096f)
    Art.card(c, back, back.height() * 0.34f)
    Art.chevronLeft(c, back.centerX(), back.centerY(), back.height() * 0.3f, Palette.CREAM)
    hotspots.add(Hotspot(RectF(back), { screen = Screen.HOME }))

    val coinPill = RectF(w * 0.845f, h * 0.036f, w * 0.955f, h * 0.09f)
    Art.panel(c, coinPill, coinPill.height() / 2f, 0x4D0A1F16, 1.2f, 0x33FFFFFF)
    Art.coin(c, coinPill.left + coinPill.height() * 0.42f, coinPill.centerY(), coinPill.height() * 0.24f)
    Art.text(c, "${save.coins}", coinPill.right - coinPill.height() * 0.12f, coinPill.centerY() + coinPill.height() * 0.16f,
        coinPill.height() * 0.3f, Palette.CREAM, Paint.Align.RIGHT, Art.bold)
}
