package ir.mivehchin.game

/**
 * Geometry of the home screen: resource chips, logo, helper girl, primary action
 * and the navigation dock. Pure Kotlin so the JVM tests can check that nothing
 * overlaps and that the layout survives a 320x640 phone.
 */
internal class HomeLayout(width: Float, height: Float) {

    val w = width
    val h = height
    val margin = Design.margin(width)

    val settings: Box
    val coin: Box
    val lives: Box
    val logo: Box
    val taglineY: Float
    val hero: Box
    val play: Box
    val captionY: Float
    val dock: Box
    val tiles: List<Box>

    /** Height the helper girl is drawn at. */
    val helperH: Float

    init {
        val chipH = height * 0.056f
        val chipTop = height * 0.028f
        // RTL: settings on the trailing (right) edge, balances on the leading (left) edge
        val gearH = chipH * 1.18f
        settings = Box(width - margin - gearH, chipTop - chipH * 0.09f, width - margin, chipTop - chipH * 0.09f + gearH)

        val coinW = width * 0.285f
        coin = Box(margin, chipTop, margin + coinW, chipTop + chipH)
        val livesW = width * 0.245f
        lives = Box(coin.right + width * 0.018f, chipTop, coin.right + width * 0.018f + livesW, chipTop + chipH)

        val logoH = height * 0.086f
        val logoTop = height * 0.100f
        logo = Box(width * 0.055f, logoTop, width * 0.945f, logoTop + logoH)
        taglineY = logo.bottom + height * 0.030f

        val dockH = height * 0.112f
        dock = Box(margin, height * 0.918f - dockH, width - margin, height * 0.918f)
        val tileGap = Design.gap(width, 1.6f)
        val tileW = (dock.width - tileGap * 3f) / 4f
        tiles = (0 until 4).map { i ->
            // index 0 is the first destination for the player, so it sits on the right
            val left = dock.right - (i + 1) * tileW - i * tileGap
            Box(left, dock.top, left + tileW, dock.bottom)
        }

        val playH = height * 0.090f
        val playBottom = dock.top - height * 0.058f
        play = Box(width * 0.11f, playBottom - playH, width * 0.89f, playBottom)
        captionY = play.bottom + height * 0.030f

        val heroTop = taglineY + height * 0.016f
        val heroBottom = play.top - height * 0.026f
        hero = Box(width * 0.10f, heroTop, width * 0.90f, heroBottom)
        helperH = minOf(hero.height * 0.96f, height * 0.315f)
    }

    fun violations(): List<String> {
        val out = ArrayList<String>()
        if (lives.left < coin.right) out.add("lives chip overlaps coin chip")
        if (lives.right > settings.left) out.add("lives chip overlaps settings button")
        if (logo.top < coin.bottom) out.add("logo overlaps the resource chips")
        if (play.top < hero.top) out.add("helper area has negative height")
        if (captionY > dock.top) out.add("continue caption collides with the dock")
        if (play.height < h * 0.07f) out.add("play button is smaller than 7% of the screen")
        if (helperH < h * 0.15f) out.add("helper girl would be cropped to nothing")
        if (dock.bottom > h) out.add("dock leaves the screen")
        for (t in tiles) if (t.width < w * 0.16f) out.add("dock tile narrower than 16% of the width")
        if (tiles.last().right > dock.right + 0.5f) out.add("dock tiles overflow the dock")
        if (!hero.isEmpty()) {
            if (hero.overlaps(play)) out.add("hero overlaps the play button")
            if (hero.overlaps(logo)) out.add("hero overlaps the logo")
        }
        return out
    }

    private fun Box.isEmpty(): Boolean = width <= 0f || height <= 0f
}
