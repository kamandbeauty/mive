package ir.mivehchin.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the level-result panel geometry. These are the numbers the canvas really
 * draws from, so a bad ratio fails here before it can reach a device.
 */
class VictoryLayoutTest {

    private val acceptanceSizes = listOf(
        320f to 640f,
        360f to 800f,
        412f to 915f
    )

    private val extraSizes = listOf(
        393f to 851f,   // Pixel-ish
        360f to 740f,   // short phone
        320f to 480f,   // very short phone
        768f to 1024f,  // small tablet
        1080f to 2400f
    )

    @Test
    fun acceptanceSizesHaveNoLayoutViolations() {
        for ((w, h) in acceptanceSizes + extraSizes) {
            for (win in listOf(true, false)) {
                val geo = VictoryGeometry(w, h, win)
                assertEquals("${w.toInt()}x${h.toInt()} win=$win", emptyList<String>(), geo.violations())
            }
        }
    }

    @Test
    fun tapTargetsStayBigEnoughOnSmallPhones() {
        for ((w, h) in acceptanceSizes + extraSizes) {
            val geo = VictoryGeometry(w, h, true)
            val label = "${w.toInt()}x${h.toInt()}"
            // dp-independent: compare against the panel height so it scales with density
            assertTrue("$label primary button", geo.primary.height >= geo.inner.height * 0.075f)
            assertTrue("$label secondary button", geo.secondary.height >= geo.inner.height * 0.065f)
            assertTrue("$label score panel", geo.score.height >= geo.inner.height * 0.13f)
            assertTrue("$label star size", geo.starR >= 8f)
        }
    }

    @Test
    fun buttonsNeverReachTheGardenStrip() {
        for ((w, h) in acceptanceSizes + extraSizes) {
            val geo = VictoryGeometry(w, h, true)
            val label = "${w.toInt()}x${h.toInt()}"
            assertTrue("$label secondary ends above the hedge", geo.secondary.bottom <= geo.gardenTop)
            assertTrue("$label garden strip has height", geo.gardenH > 8f)
            assertTrue("$label garden strip is below the score", geo.gardenTop >= geo.score.bottom)
        }
    }

    @Test
    fun contentIsOrderedTopToBottom() {
        val geo = VictoryGeometry(412f, 915f, true)
        assertTrue(geo.plaque.bottom <= geo.chip.top + 1f)
        assertTrue(geo.chip.bottom <= geo.starCy - geo.socketR + 1f)
        assertTrue(geo.starCy + geo.socketR <= geo.score.top + 1f)
        assertTrue(geo.score.bottom <= geo.capsule.top + 1f)
        assertTrue(geo.capsule.bottom <= geo.primary.top + 1f)
        assertTrue(geo.primary.bottom <= geo.secondary.top + 1f)
    }

    @Test
    fun blocksNeverOverlapEachOther() {
        for ((w, h) in acceptanceSizes) {
            val geo = VictoryGeometry(w, h, true)
            val blocks = listOf(
                "plaque" to geo.plaque,
                "chip" to geo.chip,
                "score" to geo.score,
                "capsule" to geo.capsule,
                "primary" to geo.primary,
                "secondary" to geo.secondary
            )
            for (i in blocks.indices) {
                for (j in i + 1 until blocks.size) {
                    assertFalse(
                        "${w.toInt()}x${h.toInt()}: ${blocks[i].first} overlaps ${blocks[j].first}",
                        blocks[i].second.overlaps(blocks[j].second)
                    )
                }
            }
        }
    }

    @Test
    fun starRowFitsInsideThePanelWidth() {
        for ((w, h) in acceptanceSizes + extraSizes) {
            val geo = VictoryGeometry(w, h, true)
            val reach = geo.inner.width * 0.205f + geo.socketR
            assertTrue("${w.toInt()}x${h.toInt()} star row fits", reach <= geo.inner.width / 2f)
        }
    }

    @Test
    fun anyAspectRatioStaysSound() {
        val widths = listOf(320f, 360f, 393f, 412f, 480f, 600f, 720f, 1080f, 1440f)
        val ratios = listOf(0.46f, 0.50f, 0.5625f, 0.60f, 0.75f, 1.0f, 1.4f)
        for (w in widths) {
            for (r in ratios) {
                val h = w / r
                if (h < 430f) continue
                val geo = VictoryGeometry(w, h, true)
                assertEquals("w=${w.toInt()} h=${h.toInt()}", emptyList<String>(), geo.violations())
            }
        }
    }

    @Test
    fun extraHeightIsSpentOnSpacingNotOnOverflow() {
        val short = VictoryGeometry(360f, 800f, true)
        val tall = VictoryGeometry(360f, 1000f, true)
        assertTrue("taller screens get a bigger score panel", tall.score.height > short.score.height)
        assertTrue("and still end above the hedge", tall.secondary.bottom <= tall.gardenTop)
    }
}
