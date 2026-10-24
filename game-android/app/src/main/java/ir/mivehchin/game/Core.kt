package ir.mivehchin.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/* ------------------------------------------------------------------ model */

data class Cell(val r: Int, val c: Int)
data class CollectGoal(val type: Int, val count: Int)

data class Level(
    val id: Int,
    val name: String,
    val moves: Int,
    val target: Int,
    val collect: List<CollectGoal>,
    val ice: List<Cell>
)

private var tileSeq = 1

class Tile(var type: Int, var special: String? = null) {
    val id: Int = tileSeq++
}

data class Transform(val r: Int, val c: Int, val special: String)
data class Fall(val id: Int, val fromR: Int, val fromC: Int, val toR: Int, val toC: Int)
data class Spawn(val tile: Tile, val r: Int, val c: Int, val fromR: Int)
data class TilePos(val tile: Tile, val r: Int, val c: Int)

sealed class Step {
    class Clear(
        val cells: List<Cell>,
        val transforms: List<Transform>,
        val gained: Int,
        val combo: Int,
        val ice: List<Cell>
    ) : Step()

    class Settle(val falls: List<Fall>, val spawns: List<Spawn>) : Step()
    class Rebuild(val tiles: List<TilePos>) : Step()
}

class SwapResult(val valid: Boolean, val steps: List<Step>)

enum class Status { PLAYING, WON, LOST }

/* ------------------------------------------------------------------ game */

class Game(val level: Level, seed: Long = Random.nextLong()) {

    companion object {
        const val ROWS = 8
        const val COLS = 8
        const val TYPES = 7
    }

    class Group {
        val cells = ArrayList<Cell>()
        var hasH = false
        var hasV = false
        var type = 0
    }

    private class Plan(val cells: MutableSet<Cell>, val transforms: MutableList<Transform>)
    private class Run(val dir: Char, val type: Int, val cells: List<Cell>)

    private val rnd = Random(seed)
    val grid: Array<Array<Tile?>> = Array(ROWS) { arrayOfNulls<Tile?>(COLS) }
    val ice: Array<IntArray> = Array(ROWS) { IntArray(COLS) }
    val collected = IntArray(TYPES)

    var iceLeft = 0
        private set
    var score = 0
        private set
    var moves = level.moves
        private set
    var combo = 0
        private set
    var status = Status.PLAYING
        private set

    init {
        for (cell in level.ice) {
            if (inBounds(cell.r, cell.c)) {
                ice[cell.r][cell.c] = 1
                iceLeft++
            }
        }
        fillBoard()
    }

    fun inBounds(r: Int, c: Int): Boolean = r in 0 until ROWS && c in 0 until COLS

    fun tileAt(r: Int, c: Int): Tile? = if (inBounds(r, c)) grid[r][c] else null

    private fun randomType(): Int = rnd.nextInt(TYPES)

    private fun placeInitial() {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val banned = HashSet<Int>()
                if (c >= 2) {
                    val a = grid[r][c - 1]
                    val b = grid[r][c - 2]
                    if (a != null && b != null && a.type == b.type) banned.add(a.type)
                }
                if (r >= 2) {
                    val a = grid[r - 1][c]
                    val b = grid[r - 2][c]
                    if (a != null && b != null && a.type == b.type) banned.add(a.type)
                }
                var t = randomType()
                var guard = 0
                while (banned.contains(t) && guard++ < 30) t = randomType()
                grid[r][c] = Tile(t)
            }
        }
    }

    private fun fillBoard() {
        placeInitial()
        var guard = 0
        while (findMatches().isNotEmpty() && guard++ < 20) placeInitial()
    }

    fun snapshot(): List<TilePos> {
        val out = ArrayList<TilePos>()
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val t = grid[r][c]
                if (t != null) out.add(TilePos(t, r, c))
            }
        }
        return out
    }

    fun isAdjacent(a: Cell, b: Cell): Boolean =
        Math.abs(a.r - b.r) + Math.abs(a.c - b.c) == 1

    fun swapCells(a: Cell, b: Cell) {
        val t = grid[a.r][a.c]
        grid[a.r][a.c] = grid[b.r][b.c]
        grid[b.r][b.c] = t
    }

    /* ---------------------------------------------------------- matching */

    fun findMatches(): List<Group> {
        val runs = ArrayList<Run>()

        for (r in 0 until ROWS) {
            var c = 0
            while (c < COLS) {
                val t = grid[r][c]
                if (t == null || t.type < 0) {
                    c++
                    continue
                }
                var e = c + 1
                while (e < COLS && grid[r][e]?.type == t.type) e++
                if (e - c >= 3) {
                    val cells = ArrayList<Cell>()
                    for (x in c until e) cells.add(Cell(r, x))
                    runs.add(Run('h', t.type, cells))
                }
                c = e
            }
        }

        for (c in 0 until COLS) {
            var r = 0
            while (r < ROWS) {
                val t = grid[r][c]
                if (t == null || t.type < 0) {
                    r++
                    continue
                }
                var e = r + 1
                while (e < ROWS && grid[e][c]?.type == t.type) e++
                if (e - r >= 3) {
                    val cells = ArrayList<Cell>()
                    for (y in r until e) cells.add(Cell(y, c))
                    runs.add(Run('v', t.type, cells))
                }
                r = e
            }
        }

        val groups = ArrayList<Group>()
        val owner = HashMap<Cell, Group>()

        for (run in runs) {
            var target: Group? = null
            for (cell in run.cells) {
                val found = owner[cell] ?: continue
                if (target == null) {
                    target = found
                    continue
                }
                if (found === target) continue
                val t = target ?: continue
                for (cc in found.cells) {
                    if (!t.cells.contains(cc)) {
                        t.cells.add(cc)
                        owner[cc] = t
                    }
                }
                t.hasH = t.hasH || found.hasH
                t.hasV = t.hasV || found.hasV
                groups.remove(found)
            }

            val g = target ?: Group()
            if (target == null) groups.add(g)
            for (cell in run.cells) {
                if (!g.cells.contains(cell)) {
                    g.cells.add(cell)
                    owner[cell] = g
                }
            }
            if (run.dir == 'h') g.hasH = true else g.hasV = true
            g.type = run.type
        }

        return groups.filter { it.cells.size >= 3 }
    }

    fun specialArea(tile: Tile, r: Int, c: Int): List<Cell> {
        val out = ArrayList<Cell>()
        when (tile.special) {
            "stripeH" -> for (x in 0 until COLS) out.add(Cell(r, x))
            "stripeV" -> for (y in 0 until ROWS) out.add(Cell(y, c))
            "bomb" -> for (dr in -1..1) for (dc in -1..1) {
                if (inBounds(r + dr, c + dc)) out.add(Cell(r + dr, c + dc))
            }
            "rainbow" -> {
                val counts = IntArray(TYPES)
                for (y in 0 until ROWS) for (x in 0 until COLS) {
                    val t = grid[y][x]
                    if (t != null && t.type >= 0) counts[t.type]++
                }
                var best = 0
                for (i in 1 until TYPES) if (counts[i] > counts[best]) best = i
                for (y in 0 until ROWS) for (x in 0 until COLS) {
                    val t = grid[y][x]
                    if (t != null && t.type == best) out.add(Cell(y, x))
                }
            }
        }
        return out
    }

    /* ------------------------------------------------------------- moves */

    fun trySwap(a: Cell, b: Cell): SwapResult {
        if (status != Status.PLAYING) return SwapResult(false, emptyList())
        if (!inBounds(a.r, a.c) || !inBounds(b.r, b.c)) return SwapResult(false, emptyList())
        if (!isAdjacent(a, b)) return SwapResult(false, emptyList())
        val ta = grid[a.r][a.c] ?: return SwapResult(false, emptyList())
        val tb = grid[b.r][b.c] ?: return SwapResult(false, emptyList())

        swapCells(a, b)
        val rainbow = ta.special == "rainbow" || tb.special == "rainbow"
        if (!rainbow && findMatches().isEmpty()) {
            swapCells(a, b)
            return SwapResult(false, emptyList())
        }

        moves--
        combo = 0
        val steps = ArrayList<Step>()
        if (rainbow) steps.addAll(resolveRainbow(a, b, ta, tb))
        else steps.addAll(resolveCascades(listOf(a, b)))
        steps.addAll(shuffleIfStuck())
        finishMove()
        return SwapResult(true, steps)
    }

    private fun resolveRainbow(a: Cell, b: Cell, ta: Tile, tb: Tile): List<Step> {
        val both = ta.special == "rainbow" && tb.special == "rainbow"
        val type = if (both) -2 else if (ta.special == "rainbow") tb.type else ta.type
        val cells = HashSet<Cell>()

        if (type == -2) {
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                if (grid[r][c] != null) cells.add(Cell(r, c))
            }
        } else {
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                val t = grid[r][c] ?: continue
                if (t.type == type || t.special == "rainbow") cells.add(Cell(r, c))
            }
            cells.add(a)
            cells.add(b)
        }

        combo = 1
        val steps = ArrayList<Step>()
        steps.add(executeClear(Plan(cells, ArrayList()), 1.6f))
        steps.add(executeGravity())
        steps.addAll(resolveCascades(emptyList()))
        return steps
    }

    private fun resolveCascades(hints: List<Cell>): List<Step> {
        val steps = ArrayList<Step>()
        var current = hints
        var guard = 0
        while (guard++ < 40) {
            val groups = findMatches()
            if (groups.isEmpty()) break
            combo++
            steps.add(executeClear(planMatches(groups, current)))
            steps.add(executeGravity())
            current = emptyList()
        }
        combo = 0
        return steps
    }

    private fun planMatches(groups: List<Group>, hints: List<Cell>): Plan {
        val cells = HashSet<Cell>()
        val transforms = ArrayList<Transform>()

        for (g in groups) {
            cells.addAll(g.cells)
            val special = when {
                g.hasH && g.hasV -> "bomb"
                g.cells.size >= 5 -> "rainbow"
                g.cells.size == 4 -> if (g.hasH) "stripeH" else "stripeV"
                else -> null
            }
            if (special != null) {
                var anchor: Cell? = null
                for (h in hints) {
                    if (g.cells.contains(h)) {
                        anchor = h
                        break
                    }
                }
                val spot = anchor ?: g.cells[g.cells.size / 2]
                transforms.add(Transform(spot.r, spot.c, special))
            }
        }

        for (t in transforms) cells.remove(Cell(t.r, t.c))
        return Plan(cells, transforms)
    }

    private fun executeClear(plan: Plan, bonus: Float = 1f): Step.Clear {
        val keep = HashSet<Cell>()
        for (t in plan.transforms) keep.add(Cell(t.r, t.c))

        val queue = ArrayDeque<Cell>()
        queue.addAll(plan.cells)
        val activated = HashSet<Cell>()

        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (keep.contains(cell)) continue
            val tile = grid[cell.r][cell.c] ?: continue
            if (tile.special == null) continue
            if (activated.contains(cell)) continue
            activated.add(cell)
            for (area in specialArea(tile, cell.r, cell.c)) {
                if (keep.contains(area)) continue
                if (grid[area.r][area.c] != null && plan.cells.add(area)) queue.add(area)
            }
        }

        val cells = ArrayList<Cell>()
        val iceCleared = ArrayList<Cell>()
        val counts = IntArray(TYPES)

        for (cell in plan.cells) {
            val tile = grid[cell.r][cell.c] ?: continue
            if (tile.type >= 0) counts[tile.type]++
            cells.add(cell)
            grid[cell.r][cell.c] = null
            if (ice[cell.r][cell.c] > 0) {
                ice[cell.r][cell.c] = 0
                iceLeft--
                iceCleared.add(cell)
            }
        }

        for (tr in plan.transforms) {
            val tile = grid[tr.r][tr.c] ?: continue
            tile.special = tr.special
            if (tr.special == "rainbow") tile.type = -1
        }

        val mult = 1f + 0.5f * Math.max(0, combo - 1)
        val gained = (55f * cells.size * mult * bonus).toInt() +
                110 * plan.transforms.size +
                160 * activated.size
        score += gained
        for (i in 0 until TYPES) collected[i] += counts[i]

        return Step.Clear(cells, plan.transforms, gained, combo, iceCleared)
    }

    private fun executeGravity(): Step.Settle {
        val falls = ArrayList<Fall>()
        val spawns = ArrayList<Spawn>()

        for (c in 0 until COLS) {
            var write = ROWS - 1
            for (r in ROWS - 1 downTo 0) {
                val tile = grid[r][c] ?: continue
                if (r != write) {
                    grid[write][c] = tile
                    grid[r][c] = null
                    falls.add(Fall(tile.id, r, c, write, c))
                }
                write--
            }
            var above = 1
            for (r in write downTo 0) {
                val tile = Tile(randomType())
                grid[r][c] = tile
                spawns.add(Spawn(tile, r, c, -above))
                above++
            }
        }
        return Step.Settle(falls, spawns)
    }

    /* ---------------------------------------------------------- boosters */

    fun useHammer(cell: Cell): List<Step>? {
        if (status != Status.PLAYING) return null
        if (!inBounds(cell.r, cell.c) || grid[cell.r][cell.c] == null) return null
        combo = 1
        val steps = ArrayList<Step>()
        steps.add(executeClear(Plan(HashSet<Cell>().apply { add(cell) }, ArrayList()), 1f))
        steps.add(executeGravity())
        steps.addAll(resolveCascades(emptyList()))
        steps.addAll(shuffleIfStuck())
        combo = 0
        return steps
    }

    fun addMoves(amount: Int) {
        moves += amount
        if (status == Status.LOST && moves > 0) status = Status.PLAYING
    }

    /* ------------------------------------------------------- end of turn */

    fun objectivesMet(): Boolean {
        if (score < level.target) return false
        if (iceLeft > 0) return false
        for (goal in level.collect) {
            if (collected[goal.type] < goal.count) return false
        }
        return true
    }

    fun stars(): Int {
        if (status != Status.WON) return 0
        return when {
            score >= (level.target * 1.75f).toInt() -> 3
            score >= (level.target * 1.35f).toInt() -> 2
            else -> 1
        }
    }

    private fun finishMove() {
        when {
            objectivesMet() -> status = Status.WON
            moves <= 0 -> status = Status.LOST
        }
    }

    fun hasMove(): Boolean {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val t = grid[r][c]
                if (t != null && t.special == "rainbow") return true
                if (c + 1 < COLS) {
                    swapCells(Cell(r, c), Cell(r, c + 1))
                    val ok = findMatches().isNotEmpty()
                    swapCells(Cell(r, c), Cell(r, c + 1))
                    if (ok) return true
                }
                if (r + 1 < ROWS) {
                    swapCells(Cell(r, c), Cell(r + 1, c))
                    val ok = findMatches().isNotEmpty()
                    swapCells(Cell(r, c), Cell(r + 1, c))
                    if (ok) return true
                }
            }
        }
        return false
    }

    fun shuffleStep(force: Boolean = false): Step.Rebuild {
        val tiles = ArrayList<Tile>()
        for (r in 0 until ROWS) for (c in 0 until COLS) grid[r][c]?.let { tiles.add(it) }

        var attempt = 0
        while (attempt++ < 200) {
            tiles.shuffle(rnd)
            var i = 0
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                grid[r][c] = tiles[i]
                i++
            }
            if (findMatches().isEmpty() && hasMove()) break
        }
        if (findMatches().isNotEmpty() || !hasMove()) placeInitial()
        return Step.Rebuild(snapshot())
    }

    fun shuffleIfStuck(): List<Step> {
        if (!hasMove()) return listOf(shuffleStep())
        return emptyList()
    }
}

/* --------------------------------------------------------------- content */

object Levels {
    fun load(ctx: Context): List<Level> {
        val out = ArrayList<Level>()
        ctx.assets.open("levels/levelinfo.tsv").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split("\t")
                if (parts.size < 3) continue
                val id = parts[0].trim().toIntOrNull() ?: continue
                val name = parts[1].trim()
                val obj = try {
                    JSONObject(parts[2])
                } catch (e: Exception) {
                    continue
                }
                val collect = ArrayList<CollectGoal>()
                val collectArr = obj.optJSONArray("collect")
                if (collectArr != null) {
                    for (i in 0 until collectArr.length()) {
                        val o = collectArr.getJSONObject(i)
                        collect.add(CollectGoal(o.optInt("type", 0), o.optInt("count", 10)))
                    }
                }
                val ice = ArrayList<Cell>()
                val iceArr = obj.optJSONArray("ice")
                if (iceArr != null) {
                    for (i in 0 until iceArr.length()) {
                        val a = iceArr.getJSONArray(i)
                        ice.add(Cell(a.getInt(0), a.getInt(1)))
                    }
                }
                out.add(
                    Level(
                        id = id,
                        name = name,
                        moves = obj.optInt("moves", 20),
                        target = obj.optInt("target", 1500),
                        collect = collect,
                        ice = ice
                    )
                )
            }
        }
        return out
    }

    fun fruitNames(ctx: Context): List<String> {
        val fallback = listOf("توت‌فرنگی", "پرتقال", "لیمو", "سیب سبز", "انگور", "بادمجان", "گل‌کلم")
        return try {
            val text = ctx.assets.open("i18n/fa.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).optJSONArray("fruits") ?: return fallback
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            if (list.size >= 7) list else fallback
        } catch (e: Exception) {
            fallback
        }
    }
}

/* --------------------------------------------------------------- economy */

data class WheelSlice(val label: String, val kind: String, val amount: Int, val weight: Int)
data class PassTier(val index: Int, val stars: Int, val label: String, val kind: String, val amount: Int)
data class ShopItem(
    val id: String,
    val title: String,
    val desc: String,
    val kind: String,
    val amount: Int,
    val price: Int
)

data class Economy(
    val maxLives: Int,
    val lifeRegenSec: Int,
    val startCoins: Int,
    val lifePrice: Int,
    val hammerPrice: Int,
    val shufflePrice: Int,
    val movesPrice: Int,
    val winBase: Int,
    val winPerStar: Int,
    val winPerMove: Int,
    val wheelCooldownSec: Int,
    val wheelCost: Int,
    val wheel: List<WheelSlice>,
    val pass: List<PassTier>,
    val shop: List<ShopItem>
)

object Config {
    private fun read(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader().use { it.readText() }

    fun load(ctx: Context): Economy {
        val eco = JSONObject(read(ctx, "data/economy.json"))
        val prices = eco.optJSONObject("boosterPrices") ?: JSONObject()

        val wheelArr = JSONObject(read(ctx, "data/wheel.json")).optJSONArray("slices") ?: JSONArray()
        val wheel = ArrayList<WheelSlice>()
        for (i in 0 until wheelArr.length()) {
            val o = wheelArr.getJSONObject(i)
            wheel.add(
                WheelSlice(
                    label = o.optString("label", ""),
                    kind = o.optString("kind", "coins"),
                    amount = o.optInt("amount", 0),
                    weight = o.optInt("weight", 1)
                )
            )
        }

        val passArr = JSONObject(read(ctx, "data/starpass.json")).optJSONArray("tiers") ?: JSONArray()
        val pass = ArrayList<PassTier>()
        for (i in 0 until passArr.length()) {
            val o = passArr.getJSONObject(i)
            pass.add(
                PassTier(
                    index = o.optInt("index", i + 1),
                    stars = o.optInt("stars", (i + 1) * 2),
                    label = o.optString("label", ""),
                    kind = o.optString("kind", "coins"),
                    amount = o.optInt("amount", 0)
                )
            )
        }

        val shopArr = JSONObject(read(ctx, "data/shop.json")).optJSONArray("items") ?: JSONArray()
        val shop = ArrayList<ShopItem>()
        for (i in 0 until shopArr.length()) {
            val o = shopArr.getJSONObject(i)
            shop.add(
                ShopItem(
                    id = o.optString("id", "item$i"),
                    title = o.optString("title", ""),
                    desc = o.optString("desc", ""),
                    kind = o.optString("kind", "coins"),
                    amount = o.optInt("amount", 1),
                    price = o.optInt("price", 100)
                )
            )
        }

        return Economy(
            maxLives = eco.optInt("maxLives", 5),
            lifeRegenSec = eco.optInt("lifeRegenSec", 120),
            startCoins = eco.optInt("startCoins", 150),
            lifePrice = eco.optInt("lifePrice", 70),
            hammerPrice = prices.optInt("hammer", 80),
            shufflePrice = prices.optInt("shuffle", 60),
            movesPrice = prices.optInt("moves", 100),
            winBase = eco.optInt("winBase", 20),
            winPerStar = eco.optInt("winPerStar", 15),
            winPerMove = eco.optInt("winPerMove", 3),
            wheelCooldownSec = eco.optInt("wheelCooldownSec", 14400),
            wheelCost = eco.optInt("wheelCost", 120),
            wheel = wheel,
            pass = pass,
            shop = shop
        )
    }
}

/* ------------------------------------------------------------------ save */

class Save(context: Context) {
    private val prefs = context.getSharedPreferences("mivehchin", Context.MODE_PRIVATE)

    var level: Int = prefs.getInt("level", 1)
    var coins: Int = prefs.getInt("coins", -1)
    var lives: Int = prefs.getInt("lives", -1)
    var anchor: Long = prefs.getLong("anchor", 0L)
    var wheelLast: Long = prefs.getLong("wheelLast", 0L)
    var sound: Boolean = prefs.getBoolean("sound", true)
    var totalStars: Int = prefs.getInt("totalStars", 0)

    val stars = HashMap<Int, Int>()
    val boosters = HashMap<String, Int>()
    val claimed = HashSet<Int>()

    init {
        try {
            val o = JSONObject(prefs.getString("stars", "{}") ?: "{}")
            for (key in o.keys()) {
                val id = key.toIntOrNull() ?: continue
                stars[id] = o.optInt(key, 0)
            }
        } catch (e: Exception) {
        }
        try {
            val o = JSONObject(prefs.getString("boosters", "{}") ?: "{}")
            for (key in o.keys()) boosters[key] = o.optInt(key, 0)
        } catch (e: Exception) {
        }
        try {
            val arr = JSONArray(prefs.getString("claimed", "[]") ?: "[]")
            for (i in 0 until arr.length()) claimed.add(arr.getInt(i))
        } catch (e: Exception) {
        }
    }

    fun initDefaults(eco: Economy) {
        if (coins < 0) coins = eco.startCoins
        if (lives < 0) lives = eco.maxLives
        if (anchor == 0L) anchor = System.currentTimeMillis()
        if (boosters.isEmpty()) {
            boosters["hammer"] = 1
            boosters["shuffle"] = 1
            boosters["moves"] = 1
        }
    }

    fun booster(name: String): Int = boosters[name] ?: 0

    fun addBooster(name: String, amount: Int) {
        boosters[name] = booster(name) + amount
    }

    fun tickLives(now: Long, eco: Economy) {
        if (anchor == 0L) anchor = now
        if (lives >= eco.maxLives) {
            lives = eco.maxLives
            anchor = now
            return
        }
        val step = eco.lifeRegenSec * 1000L
        val elapsed = now - anchor
        if (elapsed >= step) {
            val gained = (elapsed / step).toInt()
            lives = Math.min(eco.maxLives, lives + gained)
            anchor += gained * step
            if (lives >= eco.maxLives) anchor = now
            persist()
        }
    }

    fun secondsToNextLife(now: Long, eco: Economy): Int {
        if (lives >= eco.maxLives) return 0
        val step = eco.lifeRegenSec * 1000L
        val left = step - ((now - anchor) % step)
        return (left / 1000L).toInt()
    }

    fun starsFor(id: Int): Int = stars[id] ?: 0

    fun setStars(id: Int, value: Int) {
        if (value > starsFor(id)) stars[id] = value
    }

    fun persist() {
        val e = prefs.edit()
        e.putInt("level", level)
        e.putInt("coins", coins)
        e.putInt("lives", lives)
        e.putLong("anchor", anchor)
        e.putLong("wheelLast", wheelLast)
        e.putBoolean("sound", sound)
        e.putInt("totalStars", totalStars)

        val so = JSONObject()
        for ((k, v) in stars) so.put(k.toString(), v)
        e.putString("stars", so.toString())

        val bo = JSONObject()
        for ((k, v) in boosters) bo.put(k, v)
        e.putString("boosters", bo.toString())

        val co = JSONArray()
        for (k in claimed) co.put(k)
        e.putString("claimed", co.toString())

        e.apply()
    }

    fun reset(eco: Economy) {
        level = 1
        coins = eco.startCoins
        lives = eco.maxLives
        anchor = System.currentTimeMillis()
        wheelLast = 0L
        totalStars = 0
        stars.clear()
        claimed.clear()
        boosters.clear()
        boosters["hammer"] = 1
        boosters["shuffle"] = 1
        boosters["moves"] = 1
        persist()
    }
}
