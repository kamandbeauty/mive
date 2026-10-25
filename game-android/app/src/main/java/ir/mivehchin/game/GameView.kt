package ir.mivehchin.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.SoundPool
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Screen { HOME, MAP, GAME, SHOP, WHEEL, PASS, SETTINGS }

class GameView(context: Context) : View(context) {

    companion object {
        const val SPARK = 0
        const val SHARD = 1
        const val RING = 2
        const val FLASH = 3
        const val BEAM = 4
        const val SHOCK = 5
    }

    enum class Style { PRIMARY, SECONDARY, GHOST, DANGER, GOLD, GLASS, ROYAL }

    class Hotspot(val rect: RectF, val action: () -> Unit)

    class Result(
        val win: Boolean,
        val stars: Int,
        val score: Int,
        val coins: Int,
        val levelId: Int
    )

    class Sprite(val tile: Tile, var r: Int, var c: Int, px: Float, py: Float) {
        var x = px
        var y = py
        var fromX = px
        var fromY = py
        var toX = px
        var toY = py
        var t0 = 0L
        var dur = 0L
        var easeIn = false
        var popStart = 0L
        var landAt = 0L
        var spawnAt = 0L
        val phase = Random.nextFloat() * 6.28f

        fun move(tx: Float, ty: Float, now: Long, duration: Long, easeInFlag: Boolean) {
            fromX = x
            fromY = y
            toX = tx
            toY = ty
            t0 = now
            dur = duration
            easeIn = easeInFlag
        }

        fun update(now: Long) {
            if (dur <= 0L) {
                x = toX
                y = toY
                return
            }
            val k = ((now - t0).toFloat() / dur).coerceIn(0f, 1f)
            val e = if (easeIn) k * k else 1f - (1f - k) * (1f - k)
            x = fromX + (toX - fromX) * e
            y = fromY + (toY - fromY) * e
        }
    }

    class Particle(
        var x: Float,
        var y: Float,
        val vx: Float,
        val vy: Float,
        val color: Int,
        val start: Long,
        val life: Long,
        val radius: Float,
        val kind: Int = GameView.SPARK,
        val rot: Float = 0f,
        val spin: Float = 0f,
        val gravity: Float = 900f
    )

    class FloatText(
        val text: String,
        var x: Float,
        var y: Float,
        val start: Long,
        val color: Int,
        val size: Float
    )

    /* --------------------------------------------------------------- state */

    internal val save = Save(context)
    internal val eco = Config.load(context)
    internal val levels = Levels.load(context)
    internal val fruits = Levels.fruitNames(context)

    internal var screen = Screen.HOME
    internal var levelIndex = 0
    internal var game: Game? = null
    internal var selected: Cell? = null
    internal var boosterMode: String? = null
    internal var result: Result? = null
    internal var resultAt = 0L
    internal var screenAt = 0L
    private var lastScreen: Screen? = null
    internal var resultStarSfx = 0
    internal var resultCoinSfx = false

    /** Honour the system "remove animations" accessibility setting. */
    internal val reduceMotion: Boolean = run {
        val scale = android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }

    /** Pixels reserved for a camera cutout / gesture bar; content never enters them. */
    internal var insetTop = 0f
    internal var insetBottom = 0f

    internal var pressActive = false
    internal var pressX = 0f
    internal var pressY = 0f
    internal var paused = false
    internal var busy = false

    internal var widthPx = 0f
    internal var heightPx = 0f
    internal var boardRect = RectF()
    internal var cellSize = 1f
    internal var framePath: android.graphics.Path? = null
    internal val frameBounds = RectF()
    private var frameLevel = -1
    private var frameCell = 0f

    internal val hotspots = ArrayList<Hotspot>()
    internal var toastText: String? = null
    internal var toastUntil = 0L

    internal var wheelAngle = 0f
    private var wheelSpinStart = 0L
    private var wheelSpinFrom = 0f
    private var wheelSpinTo = 0f
    internal var wheelSpinDur = 0L
    private var lastWheelTick = 0
    internal var wheelPrize: WheelSlice? = null
    internal var wheelAwarded = true

    internal var mapScroll = 0f
    private var mapDragStart = 0f
    private var passDragStart = 0f
    private var draggingMap = false

    internal val sprites = HashMap<Int, Sprite>()
    internal val particles = ArrayList<Particle>()
    internal val floats = ArrayList<FloatText>()

    internal var introAt = 0L
    internal var comboAt = 0L
    internal var comboValue = 0
    internal var flashAt = 0L
    internal var flashColor = 0
    internal var shakeUntil = 0L
    internal var shakeMag = 0f
    internal var boardGlow = 0f

    private val pending = ArrayDeque<Step>()
    private var activeStep: Step? = null
    private var stepStart = 0L
    private var stepDur = 0L
    private var popCells: List<Cell> = emptyList()

    private var downX = 0f
    private var downY = 0f
    private var downCell: Cell? = null

    private val soundPool = SoundPool.Builder().setMaxStreams(6).build()
    private val soundIds = HashMap<Int, Int>()

    init {
        Art.init(context)
        save.initDefaults(eco)
        save.tickLives(System.currentTimeMillis(), eco)
        for (id in intArrayOf(
            R.raw.sfx_click, R.raw.sfx_swap, R.raw.sfx_pop, R.raw.sfx_match, R.raw.sfx_special,
            R.raw.sfx_explode, R.raw.sfx_ice, R.raw.sfx_coin, R.raw.sfx_star, R.raw.sfx_wheel,
            R.raw.sfx_booster, R.raw.sfx_win, R.raw.sfx_lose
        )) loadSound(id)
        isFocusable = true
    }

    private fun loadSound(id: Int) {
        soundIds[id] = soundPool.load(context, id, 1)
    }

    internal fun sound(id: Int) {
        sound(id, 1f)
    }

    internal fun sound(id: Int, rate: Float) {
        if (!save.sound) return
        val s = soundIds[id] ?: return
        soundPool.play(s, 0.85f, 0.85f, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cut = insets.displayCutout
            insetTop = (cut?.safeInsetTop ?: 0).toFloat()
            insetBottom = (cut?.safeInsetBottom ?: 0).toFloat()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w.toFloat()
        heightPx = h.toFloat()
        ensureBoardLayout()
    }

    /* ------------------------------------------------------------- drawing */

    override fun onDraw(canvas: Canvas) {
        widthPx = width.toFloat()
        heightPx = kotlin.math.max(1f, height - insetTop - insetBottom)
        val now = System.currentTimeMillis()

        // fill the cutout bands so nothing reads as a black stripe
        if (insetTop > 0f || insetBottom > 0f) {
            canvas.save()
            val bands = Paint()
            bands.color = Palette.BG_TOP
            canvas.drawRect(0f, 0f, widthPx, insetTop + 2f, bands)
            bands.color = Palette.BG_BOTTOM
            canvas.drawRect(0f, height - insetBottom, widthPx, height.toFloat(), bands)
            canvas.restore()
            canvas.translate(0f, insetTop)
        }

        save.tickLives(now, eco)
        if (screen == Screen.GAME) {
            ensureBoardLayout()
            advance(now)
            updateEffects(now)
            updateWheel(now)
        }

        if (screen != lastScreen) {
            lastScreen = screen
            screenAt = now
        }

        hotspots.clear()
        drawBackdrop(canvas)
        val age = (now - screenAt).toFloat()
        val enter = ((age / 320f).coerceIn(0f, 1f)).let { 1f - (1f - it) * (1f - it) * (1f - it) }
        canvas.save()
        if (enter < 0.999f) {
            canvas.translate(0f, (1f - enter) * heightPx * 0.022f)
            val sc = 0.975f + 0.025f * enter
            canvas.scale(sc, sc, widthPx / 2f, heightPx / 2f)
        }
        when (screen) {
            Screen.HOME -> drawHome(canvas)
            Screen.MAP -> drawMap(canvas)
            Screen.GAME -> drawGame(canvas)
            Screen.SHOP -> drawShop(canvas)
            Screen.WHEEL -> drawWheel(canvas)
            Screen.PASS -> drawPass(canvas)
            Screen.SETTINGS -> drawSettings(canvas)
        }
        canvas.restore()
        drawOverlays(canvas)
        drawToast(canvas, now)

        if (insetTop > 0f) canvas.translate(0f, -insetTop)
        postInvalidateOnAnimation()
    }

    private fun drawBackdrop(c: Canvas) {
        val t = System.currentTimeMillis() / 1000f
        Art.bgGarden(c, widthPx, heightPx, t)
    }

    /* -------------------------------------------------------------- layout */

    internal fun ensureBoardLayout() {
        if (widthPx <= 0f || heightPx <= 0f) return
        val g = game

        // The level mask rarely fills the whole grid (the diamond never uses
        // column 0), so centre on the *playable* box, not on rows x cols.
        var minR = 0
        var maxR = (g?.rows ?: 9) - 1
        var minC = 0
        var maxC = (g?.cols ?: 8) - 1
        if (g != null) {
            var r0 = g.rows
            var r1 = -1
            var c0 = g.cols
            var c1 = -1
            for (r in 0 until g.rows) {
                for (c in 0 until g.cols) {
                    if (!g.playable(r, c)) continue
                    if (r < r0) r0 = r
                    if (r > r1) r1 = r
                    if (c < c0) c0 = c
                    if (c > c1) c1 = c
                }
            }
            if (r1 >= 0) {
                minR = r0
                maxR = r1
                minC = c0
                maxC = c1
            }
        }
        val spanR = maxR - minR + 1
        val spanC = maxC - minC + 1

        val sidePad = widthPx * 0.05f
        val hasGoals = g != null && (g.level.collect.isNotEmpty() || g.iceLeft > 0)
        // the HUD owns the bands above and below the board, so neither can overlap
        val hud = HudGeometry(widthPx, heightPx, hasGoals)
        val top = hud.boardTop
        val bottom = heightPx - hud.boardBottom
        val availH = heightPx - top - bottom

        // 0.34 of a cell is the wooden rim that hangs around the shape
        val cellW = (widthPx - 2 * sidePad) / (spanC + 0.30f)
        val cellH = availH / (spanR + 0.30f)
        val newCell = min(cellW, cellH)

        val bw = newCell * spanC
        val bh = newCell * spanR
        val gridLeft = (widthPx - bw) / 2f - minC * newCell
        val gridTop = top + (availH - bh) / 2f - minR * newCell
        boardRect.set(gridLeft, gridTop, gridLeft + newCell * (g?.cols ?: 8), gridTop + newCell * (g?.rows ?: 9))

        if (abs(newCell - cellSize) > 0.5f) {
            cellSize = newCell
            snapSprites()
            framePath = null
        }
        if (g != null && (framePath == null || frameLevel != g.level.id || abs(frameCell - cellSize) > 0.5f)) {
            framePath = Art.boardShape(g, boardRect.left, boardRect.top, cellSize, cellSize * 0.15f)
            framePath?.let { frameBoundsOf(it) }
            frameLevel = g.level.id
            frameCell = cellSize
        }
    }

    private fun frameBoundsOf(path: android.graphics.Path) {
        val r = RectF()
        path.computeBounds(r, true)
        frameBounds.set(r)
    }

    private fun snapSprites() {
        for (sp in sprites.values) {
            sp.x = sp.c * cellSize
            sp.y = sp.r * cellSize
            sp.toX = sp.x
            sp.toY = sp.y
            sp.dur = 0L
        }
    }

    internal fun cellAt(x: Float, y: Float): Cell? {
        val g = game ?: return null
        if (!boardRect.contains(x, y)) return null
        val c = ((x - boardRect.left) / cellSize).toInt()
        val r = ((y - boardRect.top) / cellSize).toInt()
        if (r !in 0 until g.rows || c !in 0 until g.cols) return null
        if (!g.playable(r, c)) return null
        return Cell(r, c)
    }

    internal fun spriteAt(r: Int, c: Int): Sprite? {
        for (sp in sprites.values) if (sp.r == r && sp.c == c) return sp
        return null
    }

    /* --------------------------------------------------------- level flow */

    internal fun invalidateFrame() {
        framePath = null
    }

    internal fun startLevel(index: Int) {
        val lvl = levels.getOrNull(index) ?: return
        if (save.lives <= 0) {
            toast("جانی باقی نمانده — کمی صبر کن یا سکه بده")
            return
        }
        save.lives -= 1
        save.persist()

        levelIndex = index
        game = Game(lvl, System.nanoTime())
        selected = null
        result = null
        paused = false
        boosterMode = null
        pending.clear()
        activeStep = null
        stepDur = 0L
        sprites.clear()
        particles.clear()
        floats.clear()
        screen = Screen.GAME
        framePath = null
        ensureBoardLayout()
        rebuildSprites(game!!.snapshot())
        introAt = System.currentTimeMillis()
        busy = false
        sound(R.raw.sfx_click)
    }

    internal fun restartLevel() {
        result = null
        startLevel(levelIndex)
    }

    internal fun nextLevel() {
        result = null
        if (levelIndex + 1 < levels.size) startLevel(levelIndex + 1) else screen = Screen.MAP
    }

    private fun rebuildSprites(list: List<TilePos>) {
        val now = System.currentTimeMillis()
        sprites.clear()
        for (pos in list) {
            val sp = Sprite(pos.tile, pos.r, pos.c, pos.c * cellSize, pos.r * cellSize)
            sp.spawnAt = now + (pos.r * 34L + pos.c * 11L)
            sprites[pos.tile.id] = sp
        }
    }

    /* ------------------------------------------------------------ swapping */

    private fun performSwap(a: Cell, b: Cell) {
        val g = game ?: return
        if (busy || g.status != Status.PLAYING) return
        if (!g.isAdjacent(a, b)) {
            selected = b
            return
        }

        val sa = spriteAt(a.r, a.c)
        val sb = spriteAt(b.r, b.c)
        busy = true
        selected = null
        boosterMode = null

        val res = g.trySwap(a, b)
        if (!res.valid) {
            sound(R.raw.sfx_click)
            val now = System.currentTimeMillis()
            if (sa != null) sa.move(sa.x + (b.c - a.c) * cellSize * 0.16f, sa.y + (b.r - a.r) * cellSize * 0.16f, now, 70, false)
            if (sb != null) sb.move(sb.x + (a.c - b.c) * cellSize * 0.16f, sb.y + (a.r - b.r) * cellSize * 0.16f, now, 70, false)
            postDelayed({
                if (sa != null) sa.move(sa.c * cellSize, sa.r * cellSize, System.currentTimeMillis(), 90, false)
                if (sb != null) sb.move(sb.c * cellSize, sb.r * cellSize, System.currentTimeMillis(), 90, false)
                postDelayed({ busy = false }, 120)
            }, 90)
            return
        }

        if (sa != null && sb != null) {
            val ar = sa.r
            val ac = sa.c
            sa.r = sb.r
            sa.c = sb.c
            sb.r = ar
            sb.c = ac
            val now = System.currentTimeMillis()
            sa.move(sa.c * cellSize, sa.r * cellSize, now, 150, false)
            sb.move(sb.c * cellSize, sb.r * cellSize, now, 150, false)
        }
        sound(R.raw.sfx_swap)
        postDelayed({ enqueue(res.steps) }, 140)
    }

    private fun handleTap(cell: Cell) {
        if (busy) return
        val g = game ?: return
        if (g.status != Status.PLAYING) return

        if (boosterMode == "hammer") {
            useHammer(cell)
            return
        }
        val sel = selected
        when {
            sel == null -> selected = cell
            sel == cell -> selected = null
            g.isAdjacent(sel, cell) -> performSwap(sel, cell)
            else -> selected = cell
        }
    }

    internal fun armBooster(kind: String) {
        if (busy) return
        if (save.booster(kind) <= 0) {
            toast("این بوستر تمام شده")
            return
        }
        boosterMode = if (kind == "hammer") "hammer" else null
        sound(R.raw.sfx_booster)
        when (kind) {
            "hammer" -> toast("یک میوه را انتخاب کن")
            "shuffle" -> useShuffle()
            "moves" -> useMoves()
        }
    }

    private fun useHammer(cell: Cell) {
        val g = game ?: return
        if (save.booster("hammer") <= 0) {
            boosterMode = null
            return
        }
        val steps = g.useHammer(cell) ?: return
        save.addBooster("hammer", -1)
        save.persist()
        boosterMode = null
        busy = true
        sound(R.raw.sfx_special)
        enqueue(steps)
    }

    private fun useShuffle() {
        val g = game ?: return
        if (save.booster("shuffle") <= 0) return
        if (g.status != Status.PLAYING) return
        save.addBooster("shuffle", -1)
        save.persist()
        busy = true
        enqueue(listOf(g.shuffleStep(true)))
    }

    private fun useMoves() {
        val g = game ?: return
        if (save.booster("moves") <= 0) return
        if (g.status != Status.PLAYING) return
        save.addBooster("moves", -1)
        save.persist()
        g.addMoves(5)
        toast("۵ حرکت اضافه شد")
        sound(R.raw.sfx_coin)
    }

    internal fun buyMovesForCoins() {
        val g = game ?: return
        val price = 80
        if (save.coins < price) {
            toast("سکه کافی نداری")
            return
        }
        save.coins -= price
        save.persist()
        g.addMoves(5)
        result = null
        busy = false
        toast("۵ حرکت اضافه شد")
        sound(R.raw.sfx_coin)
    }

    /* --------------------------------------------------------- step player */

    private fun enqueue(steps: List<Step>) {
        if (steps.isEmpty()) {
            busy = false
            finishSequence()
            return
        }
        pending.addAll(steps)
        if (activeStep == null) startNext(System.currentTimeMillis())
    }

    private fun startNext(now: Long) {
        val step = pending.removeFirstOrNull()
        if (step == null) {
            activeStep = null
            finishSequence()
            return
        }
        activeStep = step
        stepStart = now
        stepDur = applyStep(step, now)
    }

    private fun advance(now: Long) {
        for (sp in sprites.values) sp.update(now)
        val step = activeStep ?: return
        if (now - stepStart >= stepDur) {
            finalizeStep(step)
            startNext(now)
        }
    }

    private fun applyStep(step: Step, now: Long): Long = when (step) {
        is Step.Clear -> {
            var bombs = 0
            var stripes = 0
            for (cell in step.cells) {
                val sp = spriteAt(cell.r, cell.c) ?: continue
                val x = boardRect.left + sp.x + cellSize / 2f
                val y = boardRect.top + sp.y + cellSize / 2f
                val color = Art.produceColor(sp.tile.type)
                when (sp.tile.special) {
                    "stripeH" -> {
                        stripes++
                        beam(x, y, cellSize * 3.2f, false, 0xFFFFF5D0.toInt())
                        sparkBurst(x, y, color, 9)
                    }
                    "stripeV" -> {
                        stripes++
                        beam(x, y, cellSize * 3.2f, true, 0xFFFFF5D0.toInt())
                        sparkBurst(x, y, color, 9)
                    }
                    "bomb" -> {
                        bombs++
                        shock(x, y, cellSize * 2.4f, 0xFFFFA15C.toInt())
                        shardBurst(x, y, color, 10)
                        flash(x, y, 0xFFFFD08A.toInt(), cellSize * 1.6f, 380L)
                    }
                    else -> {
                        sparkBurst(x, y, color, 8)
                        shardBurst(x, y, color, 4)
                    }
                }
                ring(x, y, 0xFFFFFFFF.toInt(), cellSize * 0.8f, 300L)
                flash(x, y, color, cellSize * 0.9f, 260L)
            }
            for (tr in step.transforms) {
                val sp = spriteAt(tr.r, tr.c) ?: continue
                sp.tile.special = tr.special
                if (tr.special == "rainbow") sp.tile.type = -1
                val x = boardRect.left + sp.x + cellSize / 2f
                val y = boardRect.top + sp.y + cellSize / 2f
                sparkBurst(x, y, Palette.GOLD, 8)
                ring(x, y, Palette.GOLD, cellSize * 1.15f, 430L)
                flash(x, y, 0xFFFFE9A8.toInt(), cellSize * 1.05f, 330L)
                if (tr.special == "bomb" || tr.special == "rainbow") shake(cellSize * 0.16f, 230L)
            }
            for (cell in step.ice) {
                val x = boardRect.left + cell.c * cellSize + cellSize / 2f
                val y = boardRect.top + cell.r * cellSize + cellSize / 2f
                shardBurst(x, y, 0xFFB9E7F7.toInt(), 8)
                ring(x, y, 0xFFE3F6FF.toInt(), cellSize * 0.95f, 380L)
            }
            if (step.gained > 0) {
                floats.add(
                    FloatText(
                        "+${step.gained}",
                        boardRect.centerX() + Random.nextFloat() * cellSize * 1.2f - cellSize * 0.6f,
                        boardRect.top + cellSize * (1.2f + Random.nextFloat()),
                        now,
                        if (step.combo >= 3) Palette.GOLD else Palette.LIME,
                        cellSize * 0.52f
                    )
                )
            }
            if (step.combo >= 2) {
                comboAt = now
                comboValue = step.combo
                shake(cellSize * 0.045f * step.combo.coerceAtMost(4), 170L)
            }
            // audio: one voice per clear, picked by what actually happened
            val pitch = 1f + 0.055f * (step.combo - 1).coerceIn(0, 7)
            when {
                bombs > 0 -> {
                    sound(R.raw.sfx_explode)
                    shake(cellSize * 0.3f, 300L)
                    screenFlash(0xFFFFC98A.toInt())
                }
                stripes > 0 -> sound(R.raw.sfx_special, 1f + 0.03f * stripes)
                step.ice.isNotEmpty() -> sound(R.raw.sfx_ice)
                step.combo >= 2 -> sound(R.raw.sfx_match, pitch)
                else -> sound(R.raw.sfx_pop, 1f + 0.02f * (step.combo - 1).coerceAtLeast(0))
            }
            popCells = step.cells
            200L
        }
        is Step.Settle -> {
            var longest = 0L
            for (f in step.falls) {
                val sp = sprites[f.id] ?: continue
                sp.r = f.toR
                sp.c = f.toC
                val dist = (f.toR - f.fromR).coerceAtLeast(1)
                val dur = 150L + 24L * dist
                sp.move(f.toC * cellSize, f.toR * cellSize, now, dur, true)
                sp.landAt = 0L
                longest = kotlin.math.max(longest, dur)
            }
            for (s in step.spawns) {
                val sp = Sprite(s.tile, s.r, s.c, s.c * cellSize, s.fromR * cellSize)
                val dist = (s.r - s.fromR).coerceAtLeast(1)
                val dur = 150L + 24L * dist
                sp.move(s.c * cellSize, s.r * cellSize, now, dur, true)
                sp.spawnAt = now
                sprites[s.tile.id] = sp
                longest = kotlin.math.max(longest, dur)
            }
            if (longest > 0L) busy = true
            longest + 40L
        }
        is Step.Rebuild -> {
            rebuildSprites(step.tiles)
            90L
        }
    }

    private fun finalizeStep(step: Step) {
        if (step is Step.Settle) {
            val now = System.currentTimeMillis()
            for (f in step.falls) {
                sprites[f.id]?.landAt = now
            }
            for (s in step.spawns) {
                sprites[s.tile.id]?.landAt = now
            }
        }
        if (step is Step.Clear) {
            for (cell in step.cells) {
                val sp = spriteAt(cell.r, cell.c) ?: continue
                sprites.remove(sp.tile.id)
            }
            popCells = emptyList()
        }
    }

    private fun finishSequence() {
        busy = false
        val g = game ?: return
        when (g.status) {
            Status.WON -> onWin(g)
            Status.LOST -> onLose(g)
            else -> Unit
        }
    }

    private fun onWin(g: Game) {
        if (result != null) return
        val stars = g.stars()
        val coins = eco.winBase + stars * eco.winPerStar + g.moves * eco.winPerMove
        save.coins += coins
        save.setStars(g.level.id, stars)
        if (g.level.id + 1 > save.level) save.level = g.level.id + 1
        refreshStars()
        save.persist()
        result = Result(true, stars, g.score, coins, g.level.id)
        resultAt = System.currentTimeMillis()
        resultStarSfx = 0
        resultCoinSfx = false
        confetti()
        sound(R.raw.sfx_win)
    }

    private fun onLose(g: Game) {
        if (result != null) return
        result = Result(false, 0, g.score, 0, g.level.id)
        resultAt = System.currentTimeMillis()
        resultStarSfx = 0
        resultCoinSfx = false
        sound(R.raw.sfx_lose)
    }

    internal fun refreshStars() {
        var total = 0
        for (v in save.stars.values) total += v
        save.totalStars = total
    }

    /* -------------------------------------------------------------- wheel */

    internal fun spinWheel() {
        val now = System.currentTimeMillis()
        val cooldown = eco.wheelCooldownSec * 1000L
        val free = now - save.wheelLast >= cooldown
        if (!free) {
            if (save.coins < eco.wheelCost) {
                toast("چرخ در انتظار شارژ است")
                return
            }
            save.coins -= eco.wheelCost
        }
        save.wheelLast = now
        save.persist()

        var total = 0
        for (s in eco.wheel) total += s.weight
        var r = Random.nextInt(Math.max(1, total))
        var idx = 0
        for (i in eco.wheel.indices) {
            r -= eco.wheel[i].weight
            if (r < 0) {
                idx = i
                break
            }
        }
        wheelPrize = eco.wheel.getOrNull(idx)
        wheelAwarded = false
        val seg = 360f / eco.wheel.size
        val target = wheelAngle + 360f * 5 + (360f - (idx * seg + seg / 2f)) - (wheelAngle % 360f)
        wheelSpinFrom = wheelAngle
        wheelSpinTo = target
        wheelSpinStart = now
        wheelSpinDur = 3400L
        lastWheelTick = Int.MIN_VALUE
        sound(R.raw.sfx_click)
    }

    private fun updateWheel(now: Long) {
        if (wheelSpinDur <= 0L) return
        val k = ((now - wheelSpinStart).toFloat() / wheelSpinDur).coerceIn(0f, 1f)
        val eased = 1f - (1f - k) * (1f - k) * (1f - k)
        wheelAngle = wheelSpinFrom + (wheelSpinTo - wheelSpinFrom) * eased
        val slices = eco.wheel.size.coerceAtLeast(1)
        val tick = ((wheelAngle / (360f / slices)).toInt())
        if (tick != lastWheelTick) {
            lastWheelTick = tick
            if (k < 0.96f) sound(R.raw.sfx_wheel, 0.9f + 0.3f * (1f - k))
        }
        if (k >= 1f) {
            wheelSpinDur = 0L
            if (!wheelAwarded) {
                wheelAwarded = true
                wheelPrize?.let { award(it) }
                sound(R.raw.sfx_star)
            }
        }
    }

    private fun award(slice: WheelSlice) {
        when (slice.kind) {
            "coins" -> save.coins += slice.amount
            "life" -> save.lives = min(eco.maxLives, save.lives + slice.amount)
            "hammer", "shuffle", "moves" -> save.addBooster(slice.kind, slice.amount)
        }
        save.persist()
        sound(R.raw.sfx_coin)
        screenFlash(0xFFFFE9A8.toInt())
    }

    internal fun wheelReadyIn(): Long {
        val cooldown = eco.wheelCooldownSec * 1000L
        val left = cooldown - (System.currentTimeMillis() - save.wheelLast)
        return if (left < 0) 0L else left
    }

    /* --------------------------------------------------------- star pass */

    internal fun claimTier(tier: PassTier) {
        if (save.totalStars < tier.stars) {
            toast("هنوز ${tier.stars} ستاره نداری")
            return
        }
        if (save.claimed.contains(tier.index)) {
            toast("قبلاً گرفتی")
            return
        }
        save.claimed.add(tier.index)
        when (tier.kind) {
            "coins" -> save.coins += tier.amount
            "life" -> save.lives = min(eco.maxLives, save.lives + tier.amount)
            else -> save.addBooster(tier.kind, tier.amount)
        }
        save.persist()
        sound(R.raw.sfx_coin)
    }

    /* --------------------------------------------------------------- shop */

    internal fun buyItem(item: ShopItem) {
        if (save.coins < item.price) {
            toast("سکه کافی نداری")
            return
        }
        save.coins -= item.price
        when (item.kind) {
            "lifefull" -> save.lives = eco.maxLives
            "coins" -> save.coins += item.amount
            else -> save.addBooster(item.kind, item.amount)
        }
        save.persist()
        sound(R.raw.sfx_coin)
        toast("خرید انجام شد")
    }

    internal fun buyLife() {
        if (save.coins < eco.lifePrice) {
            toast("سکه کافی نداری")
            return
        }
        if (save.lives >= eco.maxLives) {
            toast("جان‌ها پر است")
            return
        }
        save.coins -= eco.lifePrice
        save.lives += 1
        save.persist()
        sound(R.raw.sfx_coin)
    }

    /* --------------------------------------------------------------- misc */

    internal fun onHostPause() {
        save.persist()
    }

    internal fun toast(msg: String) {
        toastText = msg
        toastUntil = System.currentTimeMillis() + 1800
    }

    internal fun onBackPressed(): Boolean {
        return when {
            result != null -> {
                result = null
                screen = Screen.MAP
                true
            }
            screen == Screen.GAME && paused -> {
                paused = false
                true
            }
            screen == Screen.GAME -> {
                paused = true
                true
            }
            screen != Screen.HOME -> {
                screen = Screen.HOME
                true
            }
            else -> false
        }
    }

    /* ------------------------------------------------------------ effects */

    internal fun burst(x: Float, y: Float, color: Int, count: Int) {
        sparkBurst(x, y, color, count)
    }

    internal fun sparkBurst(x: Float, y: Float, color: Int, count: Int) {
        val now = System.currentTimeMillis()
        for (i in 0 until count) {
            val ang = Random.nextFloat() * 6.2832f
            val speed = cellSize * (0.8f + Random.nextFloat() * 1.9f)
            particles.add(
                Particle(
                    x, y,
                    cos(ang) * speed,
                    sin(ang) * speed,
                    color,
                    now,
                    420L + (Random.nextFloat() * 300f).toLong(),
                    cellSize * (0.07f + Random.nextFloat() * 0.09f),
                    SPARK,
                    Random.nextFloat() * 90f,
                    (Random.nextFloat() - 0.5f) * 540f,
                    420f
                )
            )
        }
    }

    internal fun shardBurst(x: Float, y: Float, color: Int, count: Int) {
        val now = System.currentTimeMillis()
        for (i in 0 until count) {
            val ang = Random.nextFloat() * 6.2832f
            val speed = cellSize * (0.5f + Random.nextFloat() * 1.5f)
            particles.add(
                Particle(
                    x, y,
                    cos(ang) * speed,
                    sin(ang) * speed - cellSize * 0.6f,
                    color,
                    now,
                    380L + (Random.nextFloat() * 260f).toLong(),
                    cellSize * (0.06f + Random.nextFloat() * 0.07f),
                    SHARD,
                    Random.nextFloat() * 360f,
                    (Random.nextFloat() - 0.5f) * 720f,
                    1100f
                )
            )
        }
    }

    internal fun beam(x: Float, y: Float, length: Float, vertical: Boolean, color: Int) {
        particles.add(
            Particle(
                x, y, 0f, 0f, color, System.currentTimeMillis(), 320L, length,
                BEAM, if (vertical) 1f else 0f, 0f, 0f
            )
        )
    }

    internal fun shock(x: Float, y: Float, radius: Float, color: Int) {
        particles.add(
            Particle(x, y, 0f, 0f, color, System.currentTimeMillis(), 420L, radius, SHOCK, 0f, 0f, 0f)
        )
    }

    internal fun ring(x: Float, y: Float, color: Int, radius: Float, life: Long) {
        particles.add(Particle(x, y, 0f, 0f, color, System.currentTimeMillis(), life, radius, RING, 0f, 0f, 0f))
    }

    internal fun flash(x: Float, y: Float, color: Int, radius: Float, life: Long) {
        particles.add(Particle(x, y, 0f, 0f, color, System.currentTimeMillis(), life, radius, FLASH, 0f, 0f, 0f))
    }

    internal fun confetti() {
        val now = System.currentTimeMillis()
        val tints = intArrayOf(
            0xFFFF5C7A.toInt(), 0xFFFFC94D.toInt(), 0xFFC6F36B.toInt(),
            0xFF34C759.toInt(), 0xFF7DD3FC.toInt(), 0xFFA78BFA.toInt()
        )
        for (i in 0 until 38) {
            particles.add(
                Particle(
                    widthPx * Random.nextFloat(),
                    -heightPx * 0.05f - Random.nextFloat() * heightPx * 0.25f,
                    (Random.nextFloat() - 0.5f) * widthPx * 0.25f,
                    heightPx * (0.18f + Random.nextFloat() * 0.28f),
                    tints[i % tints.size],
                    now + (Random.nextFloat() * 700f).toLong(),
                    1800L + (Random.nextFloat() * 1100f).toLong(),
                    widthPx * (0.008f + Random.nextFloat() * 0.008f),
                    SHARD,
                    Random.nextFloat() * 360f,
                    (Random.nextFloat() - 0.5f) * 620f,
                    120f
                )
            )
        }
    }

    internal fun screenFlash(color: Int) {
        flashAt = System.currentTimeMillis()
        flashColor = color
    }

    internal fun shake(mag: Float, duration: Long) {
        shakeMag = kotlin.math.max(shakeMag, mag)
        shakeUntil = System.currentTimeMillis() + duration
    }

    private fun updateEffects(now: Long) {
        particles.removeAll { now - it.start > it.life }
        floats.removeAll { now - it.start > 950L }
        if (now > shakeUntil) shakeMag = 0f
    }

    internal fun fruitColor(type: Int): Int = Art.produceColor(type)

    internal fun shakeOffset(now: Long): Float {
        if (now > shakeUntil || shakeMag <= 0f) return 0f
        val k = (shakeUntil - now).toFloat() / 240f
        return sin(now / 22.0).toFloat() * shakeMag * k.coerceIn(0f, 1f)
    }

    /* -------------------------------------------------------------- touch */

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y - insetTop
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                downCell = null
                draggingMap = false
                pressActive = true
                pressX = x
                pressY = y
                if (screen == Screen.GAME && !paused && result == null && !busy) {
                    downCell = cellAt(x, y)
                    if (downCell != null) selected = downCell
                } else if (screen == Screen.MAP) {
                    mapDragStart = mapScroll
                } else if (screen == Screen.PASS) {
                    passDragStart = passScroll.toFloat()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressActive && (abs(x - pressX) > 40f || abs(y - pressY) > 40f)) pressActive = false
                if (screen == Screen.GAME && downCell != null && !busy) {
                    val dx = x - downX
                    val dy = y - downY
                    val threshold = cellSize * 0.3f
                    if (abs(dx) > threshold || abs(dy) > threshold) {
                        val from = downCell
                        downCell = null
                        if (from != null) {
                            val to = if (abs(dx) > abs(dy)) {
                                Cell(from.r, from.c + if (dx > 0) 1 else -1)
                            } else {
                                Cell(from.r + if (dy > 0) 1 else -1, from.c)
                            }
                            val gg = game
                            if (gg != null && gg.playable(to.r, to.c)) performSwap(from, to)
                        }
                    }
                } else if (screen == Screen.MAP) {
                    val dy = y - downY
                    if (abs(dy) > 12f) draggingMap = true
                    mapScroll = mapDragStart - dy
                } else if (screen == Screen.PASS) {
                    val dy = y - downY
                    if (abs(dy) > 12f) draggingMap = true
                    passScroll = (passDragStart - dy).toInt().coerceAtLeast(0)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressActive = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressActive = false
                if (draggingMap) {
                    draggingMap = false
                    return true
                }
                val spot = hotspots.lastOrNull { it.rect.contains(x, y) }
                if (spot != null) {
                    spot.action()
                    return true
                }
                if (screen == Screen.GAME) {
                    val cell = downCell
                    if (cell != null && !busy && result == null && !paused) handleTap(cell)
                }
                return true
            }
        }
        return true
    }

    /* ------------------------------------------------------------- widgets */

    internal fun button(
        c: Canvas,
        rect: RectF,
        label: String,
        style: Style,
        icon: ((Canvas, Float, Float, Float) -> Unit)? = null,
        action: () -> Unit
    ) {
        val t = System.currentTimeMillis() / 1000f
        val pressed = pressActive && rect.contains(pressX, pressY)
        // pressed: sink into the frame, drop the lift and the gloss
        val body = if (pressed) {
            RectF(rect.left + rect.width() * 0.015f, rect.top + rect.height() * 0.055f,
                rect.right - rect.width() * 0.015f, rect.bottom)
        } else {
            RectF(rect)
        }
        val radius = body.height() * 0.40f
        val top: Int
        val bottom: Int
        val textColor: Int
        val rim: Int
        when (style) {
            Style.PRIMARY -> {
                top = 0xFFD9FB90.toInt(); bottom = 0xFF57992A.toInt(); textColor = 0xFF14330B.toInt(); rim = Palette.LIME
            }
            Style.GOLD -> {
                top = 0xFFFFE08A.toInt(); bottom = 0xFFC98814.toInt(); textColor = 0xFF43280A.toInt(); rim = Palette.GOLD
            }
            Style.DANGER -> {
                top = 0xFFFF9AA6.toInt(); bottom = 0xFFC22B44.toInt(); textColor = 0xFF3D0510.toInt(); rim = Palette.DANGER
            }
            Style.ROYAL -> {
                top = 0xFF7FC0FF.toInt(); bottom = 0xFF2A56CC.toInt(); textColor = 0xFFFFFDF5.toInt(); rim = 0xFFB7DBFF.toInt()
            }
            Style.SECONDARY -> {
                top = 0xFF2E5C48.toInt(); bottom = 0xFF103528.toInt(); textColor = Palette.CREAM; rim = 0xFF7BD6A6.toInt()
            }
            Style.GLASS -> {
                top = 0x33FFFFFF; bottom = 0x14000000; textColor = Palette.CREAM; rim = 0x55FFFFFF
            }
            else -> {
                top = 0x1AFFFFFF; bottom = 0x0D000000; textColor = Palette.CREAM; rim = 0x44FFFFFF
            }
        }

        if (!pressed) {
            val drop = body.height() * 0.12f
            Art.panel(c, RectF(body.left + body.height() * 0.02f, body.top + drop, body.right + body.height() * 0.02f, body.bottom + drop),
                radius, 0x55000000)
            if (style == Style.PRIMARY || style == Style.GOLD || style == Style.DANGER || style == Style.ROYAL) {
                val pulse = 0.5f + 0.5f * sin(t * 2.4f)
                Art.glow(c, body.centerX(), body.centerY(), body.width() * 0.52f, rim, 0.10f + 0.08f * pulse)
            }
        }
        val skinName = when (style) {
            Style.PRIMARY -> "btn_lime"
            Style.ROYAL -> "btn_royal"
            Style.GOLD -> "btn_gold"
            Style.DANGER -> "btn_danger"
            else -> null
        }
        Art.buttonSkin(c, body, radius, top, bottom, skinName)

        c.save()
        val clip = Path()
        clip.addRoundRect(body, radius, radius, Path.Direction.CW)
        c.clipPath(clip)
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
        gloss.color = if (pressed) 0x18FFFFFF else 0x33FFFFFF
        c.drawRoundRect(
            RectF(body.left + radius * 0.28f, body.top + radius * 0.14f, body.right - radius * 0.28f, body.top + body.height() * 0.44f),
            radius * 0.7f, radius * 0.7f, gloss
        )
        gloss.color = 0x14000000
        c.drawRect(body.left, body.bottom - body.height() * 0.18f, body.right, body.bottom, gloss)
        if (pressed) {
            gloss.color = 0x22000000
            c.drawRoundRect(body, radius, radius, gloss)
        }
        c.restore()

        if (!pressed) c.shine(body, radius, t, 0.5f, (body.height() * 0.013f) % 1f,
            if (style == Style.GHOST || style == Style.GLASS) 0.12f else 0.32f)

        Art.panel(c, body, radius, 0x00000000, 1.6f, Art.withAlpha(rim, 0.55f))

        var labelSize = body.height() * 0.36f
        var textX = body.centerX()
        if (icon != null) {
            // icon sits on the leading (right) edge in RTL, label shifts away from it
            icon(c, body.right - body.height() * 0.60f, body.centerY(), body.height() * 0.24f)
            textX = body.centerX() - body.height() * 0.22f
            val room = body.width() - body.height() * 1.35f
            val need = Art.measure(label, labelSize, Art.bold)
            if (need > room) labelSize *= (room / need).coerceIn(0.6f, 1f)
        }
        Art.text(
            c, label, textX, body.centerY() + labelSize * 0.36f,
            labelSize, textColor, Paint.Align.CENTER, Art.bold,
            style != Style.GHOST && !pressed
        )
        hotspots.add(Hotspot(RectF(rect), action))
    }

    internal fun pill(c: Canvas, rect: RectF, icon: (Canvas, Float, Float, Float) -> Unit, label: String) {
        Art.panel(c, rect, rect.height() / 2f, 0x66000000, 1f, 0x22FFFFFF)
        val r = rect.height() * 0.3f
        icon(c, rect.left + rect.height() * 0.55f, rect.centerY(), r)
        Art.text(
            c, label, rect.left + rect.height() * 1.05f, rect.centerY() + rect.height() * 0.16f,
            rect.height() * 0.4f, Palette.CREAM, Paint.Align.LEFT, Art.bold
        )
    }

    internal fun drawToast(c: Canvas, now: Long) {
        val text = toastText ?: return
        if (now > toastUntil) {
            toastText = null
            return
        }
        val room = widthPx * 0.86f
        val size = Art.fit(text, widthPx * 0.040f, room - widthPx * 0.10f, Art.bold)
        val w = min(room, Art.measure(text, size, Art.bold) + widthPx * 0.12f)
        val rect = RectF(
            (widthPx - w) / 2f,
            heightPx * 0.700f,
            (widthPx + w) / 2f,
            heightPx * 0.700f + heightPx * 0.062f
        )
        Art.panel(c, rect, rect.height() / 2f, 0xEE10331F.toInt(), 1.4f, 0x33C6F36B)
        Art.text(
            c, text, rect.centerX(), rect.centerY() + size * 0.36f,
            size, Palette.CREAM, Paint.Align.CENTER, Art.bold, true
        )
    }
}
