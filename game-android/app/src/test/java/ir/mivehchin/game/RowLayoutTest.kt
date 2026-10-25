package ir.mivehchin.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shop / star-pass / settings rows share one geometry. These checks keep the icon,
 * the text column and the action button clear of each other on every phone size.
 */
class RowLayoutTest {

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
    fun shopAndPassRowsNeverOverlap() {
        for ((w, h) in sizes) {
            val cardH = h * 0.098f
            val rect = Box(w * 0.045f, h * 0.30f, w * 0.955f, h * 0.30f + cardH)
            for (row in listOf(
                RowGeometry(rect, cardH),
                RowGeometry(rect, cardH, cardH * 1.20f, cardH * 0.50f)
            )) {
                val problems = row.violations(h)
                assertEquals("row problems at ${w}x$h: $problems", 0, problems.size)
                assertTrue("text column stays usable at ${w}x$h", row.text.width > w * 0.20f)
                assertTrue("icon plate is round-sized at ${w}x$h", kotlin.math.abs(row.icon.width - row.icon.height) < 0.5f)
            }
        }
    }

    @Test
    fun settingsRowToggleIsTappable() {
        for ((w, h) in sizes) {
            val rowH = h * 0.092f
            val rect = Box(w * 0.045f, h * 0.150f, w * 0.955f, h * 0.150f + rowH)
            val row = RowGeometry(rect, rowH, rowH * 0.80f, rowH * 0.42f, rowH * 0.52f)
            assertEquals("settings row at ${w}x$h: ${row.violations(h)}", 0, row.violations(h).size)
            assertTrue("toggle is at least 3% of the height at ${w}x$h", row.action.height >= h * 0.030f)
        }
    }

    @Test
    fun rtlOrderIsIconRightActionLeft() {
        val row = RowGeometry(Box(20f, 100f, 340f, 160f), 60f)
        assertTrue("action is on the leading (left) edge", row.action.left < row.text.left)
        assertTrue("icon is on the trailing (right) edge", row.icon.left > row.text.right - 0.5f)
        assertTrue("text sits between them", row.text.left > row.action.right && row.text.right <= row.icon.left)
    }
}
