package ir.mivehchin.game

/**
 * Geometry of the in-game HUD: top chrome, the moves/score row, the goal chips,
 * the guide (Laleh) and the booster toolbar. The match-3 board is laid out in the
 * gap that is left over, so the HUD and the board can never overlap. Pure Kotlin
 * so it can be unit tested on the JVM.
 */
internal class HudGeometry(width: Float, height: Float, val hasGoals: Boolean) {

    val back: Box
    val title: Box
    val coin: Box
    val moves: Box
    val score: Box
    val goals: Box
    val guide: Box
    val booster: Box
    val cards: List<Box>

    /** Vertical span the board may use. */
    val boardTop: Float
    val boardBottom: Float

    init {
        val w = width
        val h = height
        val margin = Design.margin(w)

        val chromeH = h * 0.062f
        val chromeTop = h * 0.026f
        // RTL: back sits on the trailing (right) edge, the balance on the leading (left)
        back = Box(w - margin - chromeH, chromeTop, w - margin, chromeTop + chromeH)
        title = Box(w * 0.235f, chromeTop, w * 0.765f, chromeTop + chromeH)
        val coinW = w * 0.145f
        coin = Box(margin, chromeTop + chromeH * 0.06f, margin + coinW, chromeTop + chromeH * 0.94f)

        val rowTop = chromeTop + chromeH + h * 0.016f
        val rowH = h * 0.070f
        moves = Box(margin, rowTop, margin + w * 0.205f, rowTop + rowH)
        score = Box(moves.right + w * 0.018f, rowTop, w - margin, rowTop + rowH)

        val goalTop = rowTop + rowH + h * 0.012f
        val goalH = h * 0.044f
        goals = Box(margin, goalTop, w - margin, goalTop + goalH)

        // Laleh keeps a reserved column on the leading (left) edge of the toolbar
        val bandTop = h * 0.846f
        val bandH = h * 0.098f
        val guideW = bandH * 0.96f
        guide = Box(margin, bandTop, margin + guideW, bandTop + bandH)
        booster = Box(guide.right + w * 0.022f, bandTop, w - margin, bandTop + bandH)
        val gap = Design.gap(w, 2.4f)
        val cardW = (booster.width - gap * 2f) / 3f
        cards = (0 until 3).map { i ->
            val left = booster.left + i * (cardW + gap)
            Box(left, booster.top, left + cardW, booster.bottom)
        }

        boardTop = if (hasGoals) goals.bottom + h * 0.020f else rowTop + rowH + h * 0.022f
        boardBottom = bandTop - h * 0.020f
    }

    /** Height available to the board. */
    fun boardSpace(height: Float): Float = boardBottom - boardTop

    fun violations(height: Float): List<String> {
        val out = ArrayList<String>()
        if (moves.right > score.left) out.add("moves card overlaps the score card")
        if (title.right > back.left) out.add("title plaque overlaps the back button")
        if (title.left < coin.right) out.add("title plaque overlaps the coin chip")
        if (hasGoals && goals.top < score.bottom) out.add("goal chips overlap the score row")
        if (guide.right > booster.left) out.add("guide portrait overlaps the booster toolbar")
        if (cards.last().right > booster.right + 0.5f) out.add("booster cards overflow their band")
        for (box in cards) {
            if (box.width < height * 0.075f) out.add("booster card is narrower than 7.5% of the height")
            if (box.overlaps(guide)) out.add("booster card overlaps the guide portrait")
        }
        if (booster.top < if (hasGoals) goals.bottom else score.bottom) out.add("toolbar overlaps the HUD")
        if (boardSpace(height) < height * 0.30f) out.add("board area is too short")
        if (booster.bottom > height) out.add("toolbar leaves the screen")
        return out
    }
}
