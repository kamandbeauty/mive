package ir.mivehchin.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.SoundPool
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

enum class Screen { HOME, MAP, GAME, SHOP, WHEEL, PASS, SETTINGS }

class GameView(context: Context) : View(context) {

    enum class Style { PRIMARY, SECONDARY, GHOST, DANGER }

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
        val radius: Float
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
    internal var paused = false
    internal var busy = false

    internal var widthPx = 0f
    internal var heightPx = 0f
    internal var boardRect = RectF()
    internal var cellSize = 1f

    internal val hotspots = ArrayList<Hotspot>()
    internal var toastText: String? = null
    internal var toastUntil = 0L

    internal var wheelAngle = 0f
    private var wheelSpinStart = 0L
    private var wheelSpinFrom = 0f
    private var wheelSpinTo = 0f
    internal var wheelSpinDur = 0L
    internal var wheelPrize: WheelSlice? = null
    internal var wheelAwarded = true

    internal var mapScroll = 0f
    private var mapDragStart = 0f
    private var passDragStart = 0f
    private var draggingMap = false

    internal val sprites = HashMap<Int, Sprite>()
    internal val particles = ArrayList<Particle>()
    internal val floats = ArrayList<FloatText>()

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
        loadSound(R.raw.sfx_swap)
        loadSound(R.raw.sfx_pop)
        loadSound(R.raw.sfx_special)
        loadSound(R.raw.sfx_coin)
        loadSound(R.raw.sfx_win)
        loadSound(R.raw.sfx_lose)
        loadSound(R.raw.sfx_click)
        isFocusable = true
    }

    private fun loadSound(id: Int) {
        soundIds[id] = soundPool.load(context, id, 1)
    }

    internal fun sound(id: Int) {
        if (!save.sound) return
        val s = soundIds[id] ?: return
        soundPool.play(s, 0.9f, 0.9f, 1, 0, 1f)
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
        heightPx = height.toFloat()
        val now = System.currentTimeMillis()

        save.tickLives(now, eco)
        if (screen == Screen.GAME) {
            ensureBoardLayout()
            advance(now)
            updateEffects(now)
            updateWheel(now)
        }

        hotspots.clear()
        drawBackdrop(canvas)
        when (screen) {
            Screen.HOME -> drawHome(canvas)
            Screen.MAP -> drawMap(canvas)
            Screen.GAME -> drawGame(canvas)
            Screen.SHOP -> drawShop(canvas)
            Screen.WHEEL -> drawWheel(canvas)
            Screen.PASS -> drawPass(canvas)
            Screen.SETTINGS -> drawSettings(canvas)
        }
        drawOverlays(canvas)
        drawToast(canvas, now)

        if (needsFrame(now)) postInvalidateOnAnimation() else postInvalidateDelayed(500)
    }

    private fun needsFrame(now: Long): Boolean =
        busy || particles.isNotEmpty() || floats.isNotEmpty() ||
                (wheelSpinDur > 0L && now - wheelSpinStart < wheelSpinDur) ||
                toastText != null

    private fun drawBackdrop(c: Canvas) {
        val paint = Paint()
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, heightPx,
            Palette.BG_TOP, Palette.BG_BOTTOM,
            android.graphics.Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, widthPx, heightPx, paint)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = 0x14C6F36B
        c.drawCircle(widthPx * 0.12f, heightPx * 0.1f, widthPx * 0.4f, paint)
        paint.color = 0x10A78BFA
        c.drawCircle(widthPx * 0.92f, heightPx * 0.85f, widthPx * 0.45f, paint)
    }

    /* -------------------------------------------------------------- layout */

    internal fun ensureBoardLayout() {
        if (widthPx <= 0f || heightPx <= 0f) return
        val rowsN = game?.rows ?: 9
        val colsN = game?.cols ?: 8
        val pad = widthPx * 0.035f
        val top = heightPx * 0.185f
        val bottom = heightPx * 0.205f
        val availH = heightPx - top - bottom
        val cellW = (widthPx - 2 * pad) / colsN
        val cellH = availH / rowsN
        val newCell = min(cellW, cellH)
        val bw = newCell * colsN
        val bh = newCell * rowsN
        val left = (widthPx - bw) / 2f
        val boardTop = top + (availH - bh) / 2f
        boardRect.set(left, boardTop, left + bw, boardTop + bh)
        if (abs(newCell - cellSize) > 0.5f) {
            cellSize = newCell
            snapSprites()
        }
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
        ensureBoardLayout()
        rebuildSprites(game!!.snapshot())
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
        sprites.clear()
        for (pos in list) {
            sprites[pos.tile.id] = Sprite(
                pos.tile, pos.r, pos.c,
                pos.c * cellSize, pos.r * cellSize
            )
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
            sound(R.raw.sfx_swap)
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
            for (cell in step.cells) {
                val sp = spriteAt(cell.r, cell.c) ?: continue
                sp.popStart = now
                burst(
                    boardRect.left + sp.x + cellSize / 2f,
                    boardRect.top + sp.y + cellSize / 2f,
                    fruitColor(sp.tile.type),
                    7
                )
            }
            for (tr in step.transforms) {
                val sp = spriteAt(tr.r, tr.c) ?: continue
                sp.tile.special = tr.special
                if (tr.special == "rainbow") sp.tile.type = -1
                burst(
                    boardRect.left + sp.x + cellSize / 2f,
                    boardRect.top + sp.y + cellSize / 2f,
                    Palette.GOLD,
                    4
                )
            }
            if (step.gained > 0) {
                floats.add(
                    FloatText(
                        "+${step.gained}",
                        boardRect.centerX() + Random.nextFloat() * cellSize,
                        boardRect.top + cellSize * (1.2f + Random.nextFloat()),
                        now,
                        Palette.LIME,
                        cellSize * 0.46f
                    )
                )
            }
            if (step.combo >= 2) {
                floats.add(
                    FloatText(
                        "کمبو ×${step.combo}",
                        boardRect.centerX(),
                        boardRect.centerY(),
                        now,
                        Palette.CORAL,
                        cellSize * 0.62f
                    )
                )
            }
            popCells = step.cells
            val specialSound = step.combo >= 2 || step.transforms.isNotEmpty() || step.ice.isNotEmpty()
            sound(if (specialSound) R.raw.sfx_special else R.raw.sfx_pop)
            190L
        }
        is Step.Settle -> {
            for (f in step.falls) {
                val sp = sprites[f.id] ?: continue
                sp.r = f.toR
                sp.c = f.toC
                val dist = (f.toR - f.fromR).coerceAtLeast(1)
                sp.move(f.toC * cellSize, f.toR * cellSize, now, 170L + 26L * dist, true)
            }
            for (s in step.spawns) {
                val sp = Sprite(s.tile, s.r, s.c, s.c * cellSize, s.fromR * cellSize)
                val dist = (s.r - s.fromR).coerceAtLeast(1)
                sp.move(s.c * cellSize, s.r * cellSize, now, 170L + 26L * dist, true)
                sprites[s.tile.id] = sp
            }
            240L
        }
        is Step.Rebuild -> {
            rebuildSprites(step.tiles)
            90L
        }
    }

    private fun finalizeStep(step: Step) {
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
        sound(R.raw.sfx_win)
    }

    private fun onLose(g: Game) {
        if (result != null) return
        result = Result(false, 0, g.score, 0, g.level.id)
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
        wheelSpinDur = 2600L
        sound(R.raw.sfx_click)
    }

    private fun updateWheel(now: Long) {
        if (wheelSpinDur <= 0L) return
        val k = ((now - wheelSpinStart).toFloat() / wheelSpinDur).coerceIn(0f, 1f)
        val eased = 1f - (1f - k) * (1f - k) * (1f - k)
        wheelAngle = wheelSpinFrom + (wheelSpinTo - wheelSpinFrom) * eased
        if (k >= 1f) {
            wheelSpinDur = 0L
            if (!wheelAwarded) {
                wheelAwarded = true
                wheelPrize?.let { award(it) }
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
        val now = System.currentTimeMillis()
        for (i in 0 until count) {
            val ang = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = cellSize * (0.6f + Random.nextFloat() * 1.6f)
            particles.add(
                Particle(
                    x, y,
                    kotlin.math.cos(ang) * speed,
                    kotlin.math.sin(ang) * speed,
                    color,
                    now,
                    520L + (Random.nextFloat() * 220f).toLong(),
                    cellSize * (0.05f + Random.nextFloat() * 0.06f)
                )
            )
        }
    }

    private fun updateEffects(now: Long) {
        particles.removeAll { now - it.start > it.life }
        floats.removeAll { now - it.start > 900L }
    }

    internal fun fruitColor(type: Int): Int = when (type) {
        0 -> 0xFFF43F5E.toInt()
        1 -> 0xFFFF9F1C.toInt()
        2 -> 0xFFFFE04D.toInt()
        3 -> 0xFF7ED957.toInt()
        4 -> 0xFF8B5CF6.toInt()
        5 -> 0xFF6D5BFF.toInt()
        else -> 0xFFF6EBD2.toInt()
    }

    /* -------------------------------------------------------------- touch */

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                downCell = null
                draggingMap = false
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
            MotionEvent.ACTION_UP -> {
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

    internal fun button(c: Canvas, rect: RectF, label: String, style: Style, action: () -> Unit) {
        when (style) {
            Style.PRIMARY -> Art.gradientPanel(c, rect, rect.height() * 0.34f, Palette.LIME, 0xFF8ACB4A.toInt())
            Style.SECONDARY -> Art.gradientPanel(c, rect, rect.height() * 0.34f, Palette.PANEL_SOFT, Palette.PANEL)
            Style.DANGER -> Art.gradientPanel(c, rect, rect.height() * 0.34f, 0xFFFF8A9A.toInt(), Palette.DANGER)
            Style.GHOST -> Art.panel(c, rect, rect.height() * 0.34f, 0x1AFFFFFF, 1.6f, 0x33FFFFFF)
        }
        val textColor = when (style) {
            Style.PRIMARY -> 0xFF10331F.toInt()
            Style.DANGER -> 0xFF3A0710.toInt()
            else -> Palette.CREAM
        }
        Art.text(
            c, label, rect.centerX(), rect.centerY() + rect.height() * 0.16f,
            rect.height() * 0.36f, textColor, Paint.Align.CENTER, Art.bold
        )
        hotspots.add(Hotspot(RectF(rect), action))
    }

    internal fun pill(c: Canvas, rect: RectF, icon: (Canvas, Float, Float, Float) -> Unit, label: String) {
        Art.panel(c, rect, rect.height() / 2f, 0x33000000)
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
        val w = min(widthPx * 0.86f, Art.measure(text, widthPx * 0.042f, Art.bold) + widthPx * 0.12f)
        val rect = RectF(
            (widthPx - w) / 2f,
            heightPx * 0.78f,
            (widthPx + w) / 2f,
            heightPx * 0.78f + heightPx * 0.07f
        )
        Art.panel(c, rect, rect.height() / 2f, 0xEE10331F.toInt())
        Art.text(
            c, text, rect.centerX(), rect.centerY() + rect.height() * 0.16f,
            widthPx * 0.042f, Palette.CREAM, Paint.Align.CENTER, Art.bold
        )
    }
}
