package ir.mivehchin.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import ir.mivehchin.game.GameView.Hotspot
import ir.mivehchin.game.GameView.Style
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun fmtCoins(v: Int): String = "$v"

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return "$m:" + (if (s < 10) "0$s" else "$s")
}

private fun rrect(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

/* ------------------------------------------------------------------ home */

internal fun GameView.drawHome(c: Canvas) {
    val w = widthPx
    val h = heightPx

    // top status pills
    val pillH = h * 0.055f
    pill(c, rrect(w * 0.05f, h * 0.035f, w * 0.36f, pillH), { cc, cx, cy, r -> Art.coin(cc, cx, cy, r) }, fmtCoins(save.coins))
    pill(
        c, rrect(w * 0.59f, h * 0.035f, w * 0.36f, pillH),
        { cc, cx, cy, r -> Art.heart(cc, cx, cy, r * 0.9f, true) }, "${save.lives}/${eco.maxLives}"
    )

    Art.text(c, "میوه‌چین", w * 0.5f, h * 0.225f, w * 0.155f, Palette.CREAM, Paint.Align.CENTER, Art.black, true)
    Art.text(c, "سه‌تا کنار هم بچین، باغ را پر کن!", w * 0.5f, h * 0.268f, w * 0.043f, Palette.MUTED)

    // produce parade
    val types = intArrayOf(0, 1, 2, 4, 6)
    for (i in types.indices) {
        val x = w * (0.16f + 0.17f * i)
        val y = h * (0.355f + if (i % 2 == 0) 0f else 0.018f)
        Art.produce(c, types[i], x, y, w * 0.14f)
    }

    button(
        c, rrect(w * 0.14f, h * 0.47f, w * 0.72f, h * 0.078f),
        "بازی", Style.PRIMARY
    ) {
        if (save.level - 1 < levels.size) startLevel(save.level - 1) else screen = Screen.MAP
    }

    Art.text(c, "ادامه از سطح ${save.level}", w * 0.5f, h * 0.575f, w * 0.04f, Palette.MUTED)

    // 2x2 menu
    val bw = w * 0.42f
    val bh = h * 0.072f
    button(c, rrect(w * 0.05f, h * 0.615f, bw, bh), "نقشهٔ سطح‌ها", Style.SECONDARY) { screen = Screen.MAP }
    button(c, rrect(w * 0.53f, h * 0.615f, bw, bh), "فروشگاه", Style.SECONDARY) { screen = Screen.SHOP }
    button(c, rrect(w * 0.05f, h * 0.705f, bw, bh), "چرخ شانس", Style.SECONDARY) { screen = Screen.WHEEL }
    button(c, rrect(w * 0.53f, h * 0.705f, bw, bh), "مسیر ستاره", Style.SECONDARY) { screen = Screen.PASS }

    button(c, rrect(w * 0.30f, h * 0.815f, w * 0.40f, h * 0.058f), "تنظیمات", Style.GHOST) { screen = Screen.SETTINGS }

    Art.text(
        c, "آخرین سطح باز: ${save.level}  •  ستاره‌ها: ${save.totalStars}",
        w * 0.5f, h * 0.945f, w * 0.036f, 0x889FC4B6.toInt()
    )
}


/* ------------------------------------------------------------------- map */

internal fun GameView.drawMap(c: Canvas) {
    val w = widthPx
    val h = heightPx

    header(c, "نقشهٔ سطح‌ها")

    val top = h * 0.135f
    val bottom = h * 0.94f
    val cols = 4
    val gap = w * 0.032f
    val size = (w - gap * (cols + 1)) / cols
    val rows = ceil(levels.size.toFloat() / cols).toInt()
    val contentH = rows * size + gap * (rows + 1)
    val viewH = bottom - top
    val maxScroll = max(0f, contentH - viewH)
    mapScroll = mapScroll.coerceIn(0f, maxScroll)

    c.save()
    c.clipRect(0f, top, w, bottom)

    for (i in levels.indices) {
        val row = i / cols
        val col = i % cols
        val x = w - gap - (col + 1) * size - col * gap
        val y = top + gap + row * (size + gap) - mapScroll
        if (y + size < top - 10f || y > bottom + 10f) continue

        val lvl = levels[i]
        val unlocked = lvl.id <= save.level
        val stars = save.starsFor(lvl.id)
        val rect = RectF(x, y, x + size, y + size)

        if (unlocked) {
            Art.gradientPanel(c, rect, size * 0.24f, Palette.PANEL_SOFT, Palette.PANEL)
            if (lvl.id == save.level) {
                Art.panel(c, rect, size * 0.24f, 0x00000000, 3f, Palette.LIME)
            }
            Art.text(c, "${lvl.id}", rect.centerX(), rect.centerY() - size * 0.02f, size * 0.36f, Palette.CREAM, Paint.Align.CENTER, Art.black)
            for (s in 0 until 3) {
                Art.star(
                    c,
                    rect.centerX() + (s - 1) * size * 0.22f,
                    rect.bottom - size * 0.18f,
                    size * 0.085f,
                    s < stars,
                    Palette.GOLD
                )
            }
            hotspots.add(Hotspot(RectF(rect), { startLevel(i) }))
        } else {
            Art.panel(c, rect, size * 0.24f, 0x1AFFFFFF)
            Art.lock(c, rect.centerX(), rect.centerY(), size * 0.2f, 0x66FFFFFF)
        }
    }
    c.restore()

    Art.text(c, "بکش تا پایین بروی", w * 0.5f, h * 0.965f, w * 0.034f, 0x669FC4B6.toInt())
}

/* ------------------------------------------------------------------ game */

internal fun GameView.drawGame(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val g = game ?: return
    val lvl = g.level
    val now = System.currentTimeMillis()

    // top row: pause + level
    Art.panel(c, rrect(w * 0.045f, h * 0.035f, w * 0.1f, h * 0.045f), h * 0.023f, 0x33000000)
    Art.chevronLeft(c, w * 0.095f, h * 0.0575f, w * 0.022f, Palette.CREAM)
    hotspots.add(Hotspot(rrect(w * 0.04f, h * 0.03f, w * 0.11f, h * 0.055f), { paused = true }))

    Art.text(c, "سطح ${lvl.id}", w * 0.5f, h * 0.062f, w * 0.062f, Palette.CREAM, Paint.Align.CENTER, Art.black)
    Art.text(c, lvl.name, w * 0.5f, h * 0.092f, w * 0.038f, Palette.MUTED)

    // moves + score
    val movesRect = rrect(w * 0.045f, h * 0.108f, w * 0.26f, h * 0.052f)
    Art.panel(c, movesRect, movesRect.height() / 2f, 0x33000000)
    Art.text(c, "حرکت", movesRect.left + w * 0.045f, movesRect.centerY() + w * 0.014f, w * 0.036f, Palette.MUTED)
    Art.text(c, "${g.moves}", movesRect.right - w * 0.05f, movesRect.centerY() + w * 0.02f, w * 0.052f, if (g.moves <= 3) Palette.CORAL else Palette.CREAM, Paint.Align.RIGHT, Art.black)

    val scoreRect = rrect(w * 0.325f, h * 0.114f, w * 0.63f, h * 0.04f)
    val frac = if (lvl.target > 0) g.score.toFloat() / lvl.target else 0f
    Art.progress(c, scoreRect, frac, Palette.GREEN, 0x33000000)
    Art.text(
        c, "${g.score} / ${lvl.target}", scoreRect.centerX(), scoreRect.bottom + h * 0.028f,
        w * 0.036f, Palette.MUTED
    )

    drawObjectives(c, g, h * 0.155f)

    // board plate
    val plate = RectF(boardRect.left - cellSize * 0.12f, boardRect.top - cellSize * 0.12f, boardRect.right + cellSize * 0.12f, boardRect.bottom + cellSize * 0.12f)
    Art.panel(c, plate, cellSize * 0.3f, 0x33000000)

    for (r in 0 until 8) {
        for (col in 0 until 8) {
            val x = boardRect.left + col * cellSize
            val y = boardRect.top + r * cellSize
            Art.panel(c, RectF(x + 2f, y + 2f, x + cellSize - 2f, y + cellSize - 2f), cellSize * 0.22f, 0x1FFFFFFF)
            if (g.ice[r][col] > 0) drawIce(c, RectF(x + 2f, y + 2f, x + cellSize - 2f, y + cellSize - 2f))
        }
    }

    for (sp in sprites.values) {
        val cx = boardRect.left + sp.x + cellSize / 2f
        val cy = boardRect.top + sp.y + cellSize / 2f
        var scale = 1f
        var alpha = 255
        if (sp.popStart > 0L) {
            val k = ((now - sp.popStart).toFloat() / 190f).coerceIn(0f, 1f)
            scale = 1f - 0.45f * k
            alpha = (255 * (1f - k)).toInt().coerceIn(0, 255)
        }
        Art.produce(c, sp.tile.type, cx, cy, cellSize * 0.84f, alpha, scale)
        val special = sp.tile.special
        if (special != null && alpha > 40) {
            Art.special(c, special, cx, cy, cellSize * 0.84f, alpha)
        }
    }

    val sel = selected
    if (sel != null && !busy) {
        val x = boardRect.left + sel.c * cellSize
        val y = boardRect.top + sel.r * cellSize
        Art.panel(c, RectF(x + 2f, y + 2f, x + cellSize - 2f, y + cellSize - 2f), cellSize * 0.22f, 0x00000000, 3f, Palette.LIME)
    }

    drawEffects(c, now)
    drawBoosters(c, g)
}

private fun GameView.drawIce(c: Canvas, rect: RectF) {
    Art.panel(c, rect, rect.width() * 0.22f, 0x558FD3F0)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xAAFFFFFF.toInt()
    paint.strokeWidth = 2f
    val cx = rect.centerX()
    val cy = rect.centerY()
    c.drawLine(cx - rect.width() * 0.25f, cy - rect.height() * 0.2f, cx + rect.width() * 0.1f, cy + rect.height() * 0.05f, paint)
    c.drawLine(cx + rect.width() * 0.2f, cy - rect.height() * 0.28f, cx - rect.width() * 0.02f, cy + rect.height() * 0.22f, paint)
}

private fun GameView.drawObjectives(c: Canvas, g: Game, y: Float) {
    val w = widthPx
    var x = w * 0.06f
    val chipH = heightPx * 0.045f
    val chipY = y
    for (goal in g.level.collect) {
        val chipW = w * 0.24f
        val rect = rrect(x, chipY, chipW, chipH)
        val done = g.collected[goal.type] >= goal.count
        Art.panel(c, rect, chipH / 2f, if (done) 0x5522C55E else 0x33000000)
        Art.produce(c, goal.type, rect.left + chipH * 0.6f, rect.centerY(), chipH * 0.95f)
        val text = "${min(g.collected[goal.type], goal.count)}/${goal.count}"
        Art.text(c, text, rect.right - chipH * 0.35f, rect.centerY() + chipH * 0.17f, chipH * 0.42f, Palette.CREAM, Paint.Align.RIGHT, Art.bold)
        x += chipW + w * 0.02f
    }
    if (g.iceLeft > 0) {
        val rect = rrect(x, chipY, w * 0.2f, chipH)
        Art.panel(c, rect, chipH / 2f, 0x33000000)
        Art.panel(c, rrect(rect.left + chipH * 0.15f, rect.centerY() - chipH * 0.28f, chipH * 0.56f, chipH * 0.56f), chipH * 0.14f, 0x558FD3F0)
        Art.text(c, "${g.iceLeft}", rect.right - chipH * 0.3f, rect.centerY() + chipH * 0.17f, chipH * 0.44f, Palette.CREAM, Paint.Align.RIGHT, Art.bold)
    }
}

private fun GameView.drawBoosters(c: Canvas, g: Game) {
    val w = widthPx
    val h = heightPx
    val y = h * 0.845f
    val size = w * 0.19f
    val gap = w * 0.045f
    val total = size * 3 + gap * 2
    var x = (w - total) / 2f

    data class B(val kind: String, val label: String)
    val items = listOf(B("hammer", "چکش"), B("shuffle", "برهم‌زن"), B("moves", "حرکت"))
    for (b in items) {
        val rect = RectF(x, y, x + size, y + size * 0.78f)
        Art.panel(c, rect, size * 0.24f, if (boosterMode == b.kind) 0x55C6F36B else 0x33000000, 2f, if (boosterMode == b.kind) Palette.LIME else 0x22FFFFFF)
        when (b.kind) {
            "hammer" -> {
                Art.panel(c, rrect(rect.centerX() - size * 0.06f, rect.top + size * 0.16f, size * 0.12f, size * 0.3f), size * 0.05f, Palette.CREAM)
                Art.panel(c, rrect(rect.centerX() - size * 0.22f, rect.top + size * 0.14f, size * 0.44f, size * 0.14f), size * 0.06f, Palette.CREAM)
            }
            "shuffle" -> {
                Art.shuffleIcon(c, rect.centerX(), rect.top + size * 0.34f, size * 0.2f, Palette.CREAM)
            }
            else -> {
                Art.text(c, "+5", rect.centerX(), rect.top + size * 0.42f, size * 0.34f, Palette.CREAM, Paint.Align.CENTER, Art.black)
            }
        }
        Art.text(c, b.label, rect.centerX(), rect.bottom - size * 0.08f, size * 0.2f, Palette.MUTED)
        val count = save.booster(b.kind)
        Art.circle(c, rect.right - size * 0.13f, rect.top + size * 0.12f, size * 0.13f, if (count > 0) Palette.LIME else 0x66FFFFFF)
        Art.text(c, "$count", rect.right - size * 0.13f, rect.top + size * 0.12f + size * 0.055f, size * 0.16f, 0xFF10331F.toInt(), Paint.Align.CENTER, Art.black)
        hotspots.add(Hotspot(RectF(rect), { armBooster(b.kind) }))
        x += size + gap
    }
}

private fun GameView.drawEffects(c: Canvas, now: Long) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (p in particles) {
        val t = (now - p.start) / 1000f
        val x = p.x + p.vx * t
        val y = p.y + p.vy * t + 0.5f * 900f * t * t
        val k = ((now - p.start).toFloat() / p.life).coerceIn(0f, 1f)
        paint.color = Art.withAlpha(p.color, 1f - k)
        c.drawCircle(x, y, p.radius * (1f - 0.3f * k), paint)
    }
    for (f in floats) {
        val k = ((now - f.start).toFloat() / 900f).coerceIn(0f, 1f)
        val alpha = (1f - k * k).coerceIn(0f, 1f)
        val y = f.y - k * heightPx * 0.06f
        Art.text(c, f.text, f.x, y, f.size, Art.withAlpha(f.color, alpha), Paint.Align.CENTER, Art.black)
    }
}

/* ------------------------------------------------------------------ shop */

internal fun GameView.drawShop(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "فروشگاه")
    Art.text(c, "سکه‌ها: ${save.coins}", w * 0.5f, h * 0.13f, w * 0.045f, Palette.GOLD, Paint.Align.CENTER, Art.bold)

    val top = h * 0.16f
    val cardH = h * 0.115f
    val gap = h * 0.018f
    for (i in eco.shop.indices) {
        val item = eco.shop[i]
        val y = top + i * (cardH + gap)
        val rect = RectF(w * 0.05f, y, w * 0.95f, y + cardH)
        Art.panel(c, rect, cardH * 0.24f, Palette.PANEL)
        Art.text(c, item.title, rect.right - w * 0.04f, rect.top + cardH * 0.4f, w * 0.046f, Palette.CREAM, Paint.Align.RIGHT, Art.bold)
        Art.text(c, item.desc, rect.right - w * 0.04f, rect.top + cardH * 0.72f, w * 0.034f, Palette.MUTED, Paint.Align.RIGHT)
        val buy = RectF(rect.left + w * 0.03f, rect.top + cardH * 0.24f, rect.left + w * 0.3f, rect.bottom - cardH * 0.24f)
        Art.gradientPanel(c, buy, buy.height() * 0.34f, Palette.LIME, 0xFF8ACB4A.toInt())
        Art.text(c, "${item.price}", buy.centerX() + w * 0.02f, buy.centerY() + buy.height() * 0.16f, buy.height() * 0.4f, 0xFF10331F.toInt(), Paint.Align.CENTER, Art.black)
        Art.coin(c, buy.left + w * 0.035f, buy.centerY(), buy.height() * 0.22f)
        hotspots.add(Hotspot(RectF(buy), { buyItem(item) }))
    }

    val y = top + eco.shop.size * (cardH + gap)
    val rect = RectF(w * 0.05f, y, w * 0.95f, y + cardH)
    Art.panel(c, rect, cardH * 0.24f, Palette.PANEL)
    Art.text(c, "یک جان", rect.right - w * 0.04f, rect.top + cardH * 0.4f, w * 0.046f, Palette.CREAM, Paint.Align.RIGHT, Art.bold)
    Art.text(c, "برای وقتی که جان کم آوردی", rect.right - w * 0.04f, rect.top + cardH * 0.72f, w * 0.034f, Palette.MUTED, Paint.Align.RIGHT)
    val buy = RectF(rect.left + w * 0.03f, rect.top + cardH * 0.24f, rect.left + w * 0.3f, rect.bottom - cardH * 0.24f)
    Art.gradientPanel(c, buy, buy.height() * 0.34f, Palette.LIME, 0xFF8ACB4A.toInt())
    Art.text(c, "${eco.lifePrice}", buy.centerX() + w * 0.02f, buy.centerY() + buy.height() * 0.16f, buy.height() * 0.4f, 0xFF10331F.toInt(), Paint.Align.CENTER, Art.black)
    Art.coin(c, buy.left + w * 0.035f, buy.centerY(), buy.height() * 0.22f)
    hotspots.add(Hotspot(RectF(buy), { buyLife() }))
}

/* ----------------------------------------------------------------- wheel */

internal fun GameView.drawWheel(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "چرخ شانس")

    val cx = w / 2f
    val cy = h * 0.42f
    val radius = min(w * 0.4f, h * 0.24f)

    val colors = intArrayOf(
        0xFFF43F5E.toInt(), 0xFFFF9F1C.toInt(), 0xFFFFE04D.toInt(), 0xFF7ED957.toInt(),
        0xFF34C759.toInt(), 0xFF7DD3FC.toInt(), 0xFF8B5CF6.toInt(), 0xFFA78BFA.toInt()
    )
    val slices = eco.wheel
    if (slices.isEmpty()) return
    val seg = 360f / slices.size

    c.save()
    c.rotate(wheelAngle, cx, cy)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in slices.indices) {
        paint.color = colors[i % colors.size]
        c.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), i * seg, seg, true, paint)
    }
    // labels
    for (i in slices.indices) {
        c.save()
        c.rotate(i * seg + seg / 2f, cx, cy)
        Art.text(c, slices[i].label, cx, cy - radius * 0.62f, radius * 0.12f, 0xFFFFFFFF.toInt(), Paint.Align.CENTER, Art.bold)
        c.restore()
    }
    c.restore()

    Art.circle(c, cx, cy, radius * 0.16f, Palette.PANEL)
    Art.circle(c, cx, cy, radius * 0.1f, Palette.CREAM)

    // pointer
    val p = android.graphics.Path()
    p.moveTo(cx, cy - radius - h * 0.012f)
    p.lineTo(cx - w * 0.035f, cy - radius + h * 0.03f)
    p.lineTo(cx + w * 0.035f, cy - radius + h * 0.03f)
    p.close()
    val pp = Paint(Paint.ANTI_ALIAS_FLAG)
    pp.color = Palette.CREAM
    c.drawPath(p, pp)

    val ready = wheelReadyIn()
    val spinning = wheelSpinDur > 0L
    val btn = RectF(w * 0.2f, h * 0.72f, w * 0.8f, h * 0.72f + h * 0.075f)
    val label = when {
        spinning -> "می‌چرخد…"
        ready > 0 -> "شارژ مجدد: " + fmtTime(ready)
        else -> "بچرخان (رایگان)"
    }
    button(c, btn, label, if (ready == 0L && !spinning) Style.PRIMARY else Style.SECONDARY) {
        if (!spinning) spinWheel()
    }

    val prize = wheelPrize
    if (prize != null && !spinning && wheelAwarded) {
        Art.text(c, "جایزه: ${prize.label}", cx, h * 0.66f, w * 0.05f, Palette.LIME, Paint.Align.CENTER, Art.bold)
        button(c, RectF(w * 0.3f, h * 0.82f, w * 0.7f, h * 0.82f + h * 0.055f), "باشه", Style.GHOST) {
            wheelPrize = null
        }
    } else {
        Art.text(c, "هر ۴ ساعت یک چرخ رایگان", cx, h * 0.82f, w * 0.038f, Palette.MUTED)
    }
}

/* ------------------------------------------------------------------ pass */

internal fun GameView.drawPass(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "مسیر ستاره")
    Art.text(c, "ستاره‌ها: ${save.totalStars}", w * 0.5f, h * 0.135f, w * 0.048f, Palette.GOLD, Paint.Align.CENTER, Art.bold)
    Art.text(c, "با هر ۲ ستاره یک پله باز می‌شود", w * 0.5f, h * 0.165f, w * 0.036f, Palette.MUTED)

    val top = h * 0.2f
    val bottom = h * 0.94f
    val cardH = h * 0.1f
    val gap = h * 0.016f
    val visible = ((bottom - top) / (cardH + gap)).toInt()
    val start = max(0, min(passScroll, eco.pass.size - visible))
    for (k in 0 until visible) {
        val i = start + k
        if (i >= eco.pass.size) break
        val tier = eco.pass[i]
        val y = top + k * (cardH + gap)
        val rect = RectF(w * 0.05f, y, w * 0.95f, y + cardH)
        val unlocked = save.totalStars >= tier.stars
        Art.panel(c, rect, cardH * 0.24f, if (unlocked) Palette.PANEL else 0x22000000)
        Art.text(c, "${tier.index}", rect.right - w * 0.045f, rect.centerY() + w * 0.016f, w * 0.05f, Palette.CREAM, Paint.Align.RIGHT, Art.black)
        Art.text(c, tier.label, rect.right - w * 0.12f, rect.centerY() + w * 0.012f, w * 0.038f, Palette.MUTED, Paint.Align.RIGHT)
        val claimed = save.claimed.contains(tier.index)
        val btn = RectF(rect.left + w * 0.03f, rect.top + cardH * 0.22f, rect.left + w * 0.28f, rect.bottom - cardH * 0.22f)
        val label = when {
            claimed -> "گرفته شد"
            !unlocked -> "${tier.stars} ستاره"
            else -> "دریافت"
        }
        button(c, btn, label, if (unlocked && !claimed) Style.PRIMARY else Style.SECONDARY) {
            if (unlocked && !claimed) claimTier(tier)
        }
    }
    Art.text(c, "بکش تا پله‌های بیشتر ببینی", w * 0.5f, h * 0.965f, w * 0.034f, 0x669FC4B6.toInt())
}

internal var passScroll = 0

/* -------------------------------------------------------------- settings */

internal fun GameView.drawSettings(c: Canvas) {
    val w = widthPx
    val h = heightPx
    header(c, "تنظیمات")

    val rowY = h * 0.18f
    val rowH = h * 0.08f
    val soundRect = RectF(w * 0.05f, rowY, w * 0.95f, rowY + rowH)
    Art.panel(c, soundRect, rowH * 0.24f, Palette.PANEL)
    Art.text(c, "صدا", soundRect.right - w * 0.05f, soundRect.centerY() + w * 0.016f, w * 0.048f, Palette.CREAM, Paint.Align.RIGHT, Art.bold)
    val toggle = RectF(soundRect.left + w * 0.04f, soundRect.centerY() - rowH * 0.22f, soundRect.left + w * 0.16f, soundRect.centerY() + rowH * 0.22f)
    Art.panel(c, toggle, toggle.height() / 2f, if (save.sound) Palette.LIME else 0x44FFFFFF)
    Art.circle(c, if (save.sound) toggle.right - toggle.height() / 2f else toggle.left + toggle.height() / 2f, toggle.centerY(), toggle.height() * 0.38f, Palette.PANEL)
    hotspots.add(Hotspot(RectF(toggle), {
        save.sound = !save.sound
        save.persist()
    }))

    val resetRect = RectF(w * 0.05f, rowY + rowH * 1.4f, w * 0.95f, rowY + rowH * 2.4f)
    button(c, resetRect, "شروع دوباره از سطح ۱", Style.DANGER) {
        save.reset(eco)
        levelIndex = 0
        toast("پیشرفت پاک شد")
    }

    Art.text(c, "میوه‌چین — نسخهٔ ۱٫۰", w * 0.5f, h * 0.55f, w * 0.042f, Palette.MUTED)
    Art.text(c, "هفت محصول، ۶۰ سطح، یک باغ.", w * 0.5f, h * 0.585f, w * 0.036f, 0x889FC4B6.toInt())
    Art.text(c, "ساخته‌شده با الهام از اقتصاد بازی‌های Match-3", w * 0.5f, h * 0.62f, w * 0.034f, 0x669FC4B6.toInt())
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
        Art.text(c, "روی یک میوه بزن تا حذف شود", w * 0.5f, h * 0.925f, w * 0.038f, Palette.LIME, Paint.Align.CENTER, Art.bold)
    }
}

private fun GameView.drawPause(c: Canvas) {
    val w = widthPx
    val h = heightPx
    val dim = Paint()
    dim.color = 0xB3000000.toInt()
    c.drawRect(0f, 0f, w, h, dim)

    val panel = RectF(w * 0.1f, h * 0.29f, w * 0.9f, h * 0.67f)
    Art.panel(c, panel, w * 0.06f, Palette.PANEL)
    Art.text(c, "مکث", panel.centerX(), panel.top + h * 0.07f, w * 0.085f, Palette.CREAM, Paint.Align.CENTER, Art.black)

    val bh = h * 0.065f
    button(c, RectF(panel.left + w * 0.06f, panel.top + h * 0.1f, panel.right - w * 0.06f, panel.top + h * 0.1f + bh), "ادامه", Style.PRIMARY) {
        paused = false
    }
    button(c, RectF(panel.left + w * 0.06f, panel.top + h * 0.1f + bh * 1.25f, panel.right - w * 0.06f, panel.top + h * 0.1f + bh * 2.25f), "شروع دوباره", Style.SECONDARY) {
        paused = false
        restartLevel()
    }
    button(c, RectF(panel.left + w * 0.06f, panel.top + h * 0.1f + bh * 2.5f, panel.right - w * 0.06f, panel.top + h * 0.1f + bh * 3.5f), "خانه", Style.GHOST) {
        paused = false
        result = null
        screen = Screen.HOME
    }
}

private fun GameView.drawResult(c: Canvas, res: GameView.Result) {
    val w = widthPx
    val h = heightPx
    val now = System.currentTimeMillis()
    val dim = Paint()
    dim.color = 0xC0000000.toInt()
    c.drawRect(0f, 0f, w, h, dim)

    val panel = RectF(w * 0.08f, h * 0.24f, w * 0.92f, h * 0.76f)
    Art.gradientPanel(c, panel, w * 0.06f, Palette.PANEL_SOFT, Palette.PANEL)

    if (res.win) {
        Art.text(c, "آفرین!", panel.centerX(), panel.top + h * 0.075f, w * 0.095f, Palette.LIME, Paint.Align.CENTER, Art.black, true)
        for (i in 0 until 3) {
            val t = ((now % 700) / 700f)
            val scale = if (i < res.stars) 1f + 0.08f * sin(t * 6.28f + i).toFloat() else 1f
            Art.star(
                c,
                panel.centerX() + (i - 1) * w * 0.16f,
                panel.top + h * 0.145f,
                w * 0.06f * (if (i < res.stars) scale else 1f),
                i < res.stars
            )
        }
        Art.text(c, "${res.score}", panel.centerX(), panel.top + h * 0.215f, w * 0.07f, Palette.CREAM, Paint.Align.CENTER, Art.black)
        Art.text(c, "امتیاز", panel.centerX(), panel.top + h * 0.245f, w * 0.036f, Palette.MUTED)
        Art.coin(c, panel.centerX() - w * 0.09f, panel.top + h * 0.295f, w * 0.028f)
        Art.text(c, "+${res.coins}", panel.centerX() + w * 0.03f, panel.top + h * 0.305f, w * 0.048f, Palette.GOLD, Paint.Align.CENTER, Art.bold)

        val bh = h * 0.062f
        val hasNext = res.levelId < levels.size
        if (hasNext) {
            button(c, RectF(panel.left + w * 0.07f, panel.bottom - h * 0.175f, panel.right - w * 0.07f, panel.bottom - h * 0.175f + bh), "سطح بعد", Style.PRIMARY) {
                result = null
                nextLevel()
            }
            button(
                c,
                RectF(panel.left + w * 0.07f, panel.bottom - h * 0.095f, panel.right - w * 0.07f, panel.bottom - h * 0.095f + bh),
                "نقشه", Style.GHOST
            ) {
                result = null
                screen = Screen.MAP
            }
        } else {
            button(
                c,
                RectF(panel.left + w * 0.07f, panel.bottom - h * 0.115f, panel.right - w * 0.07f, panel.bottom - h * 0.115f + bh),
                "نقشه", Style.PRIMARY
            ) {
                result = null
                screen = Screen.MAP
            }
        }
    } else {
        Art.text(c, "حرکات تمام شد", panel.centerX(), panel.top + h * 0.08f, w * 0.07f, Palette.CORAL, Paint.Align.CENTER, Art.black)
        Art.text(c, "${res.score} امتیاز گرفتی", panel.centerX(), panel.top + h * 0.13f, w * 0.042f, Palette.MUTED)
        Art.text(c, "هدف: ${levels[levelIndex].target}", panel.centerX(), panel.top + h * 0.165f, w * 0.038f, Palette.MUTED)

        val bh = h * 0.062f
        button(c, RectF(panel.left + w * 0.07f, panel.top + h * 0.21f, panel.right - w * 0.07f, panel.top + h * 0.21f + bh), "۵ حرکت با ۸۰ سکه", Style.PRIMARY) {
            buyMovesForCoins()
        }
        button(c, RectF(panel.left + w * 0.07f, panel.top + h * 0.21f + bh * 1.2f, panel.right - w * 0.07f, panel.top + h * 0.21f + bh * 2.2f), "تلاش دوباره (یک جان)", Style.SECONDARY) {
            restartLevel()
        }
        button(c, RectF(panel.left + w * 0.07f, panel.bottom - h * 0.105f, panel.right - w * 0.07f, panel.bottom - h * 0.04f), "نقشه", Style.GHOST) {
            result = null
            screen = Screen.MAP
        }
    }
}

/* ---------------------------------------------------------------- header */

private fun GameView.header(c: Canvas, title: String) {
    val w = widthPx
    val h = heightPx
    Art.text(c, title, w * 0.5f, h * 0.085f, w * 0.062f, Palette.CREAM, Paint.Align.CENTER, Art.black)
    val back = RectF(w * 0.05f, h * 0.045f, w * 0.145f, h * 0.095f)
    Art.panel(c, back, back.height() / 2f, 0x33000000)
    Art.chevronLeft(c, back.centerX(), back.centerY(), w * 0.022f, Palette.CREAM)
    hotspots.add(Hotspot(RectF(back), { screen = Screen.HOME }))
}
