package ir.mivehchin.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home screen must keep its hierarchy and fit a 320x640 phone without clipping. */
class HomeLayoutTest {

    private val sizes = listOf(
        320f to 640f,
        360f to 800f,
        412f to 915f,
        393f to 851f,
        360f to 740f,
        320f to 480f,
        768f to 1024f,
        1080f to 2400f
    )

    @Test
    fun layoutHasNoProblemsOnEverySize() {
        for ((w, h) in sizes) {
            val problems = HomeLayout(w, h).violations()
            assertEquals("home problems at ${w}x$h: $problems", 0, problems.size)
        }
    }

    @Test
    fun primaryActionIsTheBiggestControl() {
        for ((w, h) in sizes) {
            val l = HomeLayout(w, h)
            assertTrue("play height at ${w}x$h", l.play.height >= h * 0.07f)
            assertTrue("play is wide", l.play.width >= w * 0.60f)
            assertTrue("play is below the hero", l.play.top > l.hero.top)
            assertTrue("play is above the dock", l.play.bottom < l.dock.top)
            for (tile in l.tiles) {
                assertTrue("play bigger than dock tile at ${w}x$h", l.play.width > tile.width * 2f)
            }
        }
    }

    @Test
    fun helperIsProminent() {
        for ((w, h) in sizes) {
            val l = HomeLayout(w, h)
            assertTrue("helper height at ${w}x$h", l.helperH >= h * 0.15f)
            assertTrue("helper fits its area", l.helperH <= l.hero.height + 0.5f)
            assertTrue("helper is centred horizontally", l.hero.width > w * 0.5f)
        }
    }

    @Test
    fun dockCarriesFourEqualDestinations() {
        for ((w, h) in sizes) {
            val l = HomeLayout(w, h)
            assertEquals(4, l.tiles.size)
            val first = l.tiles.first().width
            for (tile in l.tiles) {
                assertEquals("dock tiles differ at ${w}x$h", first, tile.width, 0.5f)
                assertTrue("dock tile too small at ${w}x$h", tile.width >= w * 0.16f)
                assertTrue("dock tile inside screen", tile.right <= w + 0.5f)
            }
            assertTrue("dock leaves the screen", l.dock.bottom <= h)
        }
    }

    @Test
    fun resourceChipsSitAboveTheLogo() {
        for ((w, h) in sizes) {
            val l = HomeLayout(w, h)
            assertTrue("chips above logo at ${w}x$h", l.coin.bottom <= l.logo.top)
            assertTrue("coin chip is wide enough", l.coin.width >= w * 0.22f)
            assertTrue("settings button is square", kotlin.math.abs(l.settings.width - l.settings.height) < 0.5f)
            assertEquals("the map is the first dock entry, on the right", l.tiles.first().right, l.dock.right, 0.5f)
            assertTrue("settings sits on the trailing edge in RTL", l.settings.left > l.lives.right)
        }
    }
}
