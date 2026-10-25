package ir.mivehchin.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the level-result panel geometry. These are the real numbers the canvas
 * draws from, so a bad ratio shows up here before it reaches a device.
 */
class VictoryLayoutTest {

    private val sizes = listOf(
        320f to 640f,
        360f to 800f,
        412f to 915f,
        393f to 851f,   // Pixel-ish
        768f to 1024f,  // small tablet
        1080f to 2400f
    )

    @Test
    fun everySupportedSizeKeepsTheContentInOrder() {
        for ((w, h) in sizes) {
            for (win in listOf(true, false)) {
                val geo = VictoryGeometry(w, h, win)
                val label = "${w.toInt()}x${h.toInt()} win=$win"
                assertEquals(label, emptyList<String>(), geo.violations())
                assertTrue("$label panel inside viewport", geo.panel.top >= 0f && geo.panel.bottom <= h)
                assertTrue("$label buttons keep a real touch target", geo.primary.height >= 36f)
                assertTrue("$label secondary keeps a real touch target", geo.secondary.height >= 30f)
                assertTrue("$label score readable", geo.score.height >= 40f)
            }
        }
    }

    @Test
    fun buttonsNeverReachTheGardenStrip() {
        for ((w, h) in sizes) {
            val geo = VictoryGeometry(w, h, true)
            val label = "${w.toInt()}x${h.toInt()}"
            assertTrue("$label secondary ends above the hedge", geo.secondary.bottom <= geo.gardenTop)
            assertTrue("$label garden strip has height", geo.gardenH > 8f)
            assertTrue("$label garden strip inside inner", geo.gardenTop >= geo.score.bottom)
        }
    }

    @Test
    fun shortScreenCompressesInsteadOfOverflowing() {
        val tiny = VictoryGeometry(320f, 480f, true)
        assertTrue("320x480 must compress", tiny.compressed)
        assertEquals(emptyList<String>(), tiny.violations())
        assertTrue(tiny.secondary.bottom <= tiny.gardenTop)
    }

    @Test
    fun tallScreenGetsMoreBreathingRoom() {
        val short = VictoryGeometry(360f, 800f, true)
        val tall = VictoryGeometry(360f, 1000f, true)
        assertFalse(short.compressed)
        assertFalse(tall.compressed)
        assertTrue("extra height goes into spacing", tall.score.height > short.score.height)
    }

    @Test
    fun blocksNeverOverlapEachOther() {
        val geo = VictoryGeometry(412f, 915f, true)
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
                    "${blocks[i].first} overlaps ${blocks[j].first}",
                    blocks[i].second.overlaps(blocks[j].second)
                )
            }
        }
        // the star row sits between the chip and the score panel
        assertTrue(geo.starCy - geo.socketR >= geo.chip.bottom)
        assertTrue(geo.starCy + geo.socketR <= geo.score.top + 1f)
    }

    @Test
    fun starsStayInsideThePanelWidth() {
        for ((w, h) in sizes) {
            val geo = VictoryGeometry(w, h, true)
            val half = geo.inner.width * 0.205f + geo.socketR
            assertTrue("${w.toInt()}x${h.toInt()} star row fits", half <= geo.inner.width / 2f)
        }
    }
}
