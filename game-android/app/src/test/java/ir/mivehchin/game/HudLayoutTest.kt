package ir.mivehchin.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-game HUD must never steal space from the board and never overlap its own
 * controls. These checks run on the three acceptance sizes plus a few edge cases.
 */
class HudLayoutTest {

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
    fun hudHasNoOverlapsOnEverySize() {
        for ((w, h) in sizes) {
            for (goals in listOf(false, true)) {
                val g = HudGeometry(w, h, goals)
                val problems = g.violations(h)
                assertEquals("hud problems at ${w}x$h goals=$goals: $problems", 0, problems.size)
            }
        }
    }

    @Test
    fun boardGetsTheLeftoverSpace() {
        for ((w, h) in sizes) {
            val g = HudGeometry(w, h, true)
            assertTrue("board space at ${w}x$h", g.boardSpace(h) > h * 0.30f)
            assertTrue("board starts under the goals", g.boardTop > g.goals.bottom)
            assertTrue("board ends above the toolbar", g.boardBottom < g.booster.top)
            assertTrue("board is inside the screen", g.boardTop < g.boardBottom)
        }
    }

    @Test
    fun guidePortraitIsReservedAndSeparate() {
        for ((w, h) in sizes) {
            val g = HudGeometry(w, h, true)
            assertTrue("guide has area at ${w}x$h", g.guide.width > h * 0.06f && g.guide.height > h * 0.06f)
            assertTrue("guide left of the toolbar", g.guide.right <= g.booster.left)
            for (card in g.cards) {
                assertTrue("card ${card} overlaps the guide at ${w}x$h", !card.overlaps(g.guide))
            }
        }
    }

    @Test
    fun boosterCardsAreEvenAndTappable() {
        for ((w, h) in sizes) {
            val g = HudGeometry(w, h, false)
            assertEquals(3, g.cards.size)
            val first = g.cards.first().width
            for (card in g.cards) {
                assertEquals("cards differ on ${w}x$h", first, card.width, 0.5f)
                assertTrue("card too narrow on ${w}x$h", card.width > h * 0.07f)
            }
            assertTrue("cards inside the band", g.cards.last().right <= g.booster.right + 0.5f)
            assertTrue("cards start inside the band", g.cards.first().left >= g.booster.left - 0.5f)
        }
    }

    @Test
    fun rtlPutsBackOnTheRightAndTheBalanceOnTheLeft() {
        for ((w, h) in sizes) {
            val g = HudGeometry(w, h, true)
            assertTrue("back hugs the right edge at ${w}x$h", g.back.right >= w - w * 0.05f)
            assertTrue("coin chip hugs the left edge at ${w}x$h", g.coin.left <= w * 0.05f)
            assertTrue("back is right of the title", g.back.left > g.title.right)
            assertTrue("coin is left of the title", g.coin.right < g.title.left)
        }
    }

    @Test
    fun goalChipsOnlyReserveSpaceWhenTheLevelHasGoals() {
        val withGoals = HudGeometry(360f, 800f, true)
        val without = HudGeometry(360f, 800f, false)
        assertTrue("goal levels need more room", without.boardSpace(800f) < withGoals.boardSpace(800f))
    }
}
