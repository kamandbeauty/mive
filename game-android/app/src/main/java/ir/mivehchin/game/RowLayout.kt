package ir.mivehchin.game

/**
 * Shared geometry for the list rows used by the shop, the star pass and the
 * settings screen. Right-to-left: the item icon sits on the trailing (right)
 * edge, the action (price / claim / toggle) on the leading (left) edge, and the
 * text occupies the space in between. Pure Kotlin so the JVM tests can prove the
 * three never overlap — even on a 320 dp phone.
 */
internal class RowGeometry(
    val rect: Box,
    val cardH: Float,
    actionWidth: Float = cardH * 1.22f,
    actionHeight: Float = cardH * 0.52f,
    iconSize: Float = cardH * 0.52f
) {
    /** Circular icon plate on the right. */
    val icon: Box
    /** Button, price badge or toggle on the left. */
    val action: Box
    /** Title + subtitle column between them. */
    val text: Box

    init {
        val inset = cardH * 0.30f
        val right = rect.right - inset
        icon = Box(right - iconSize, rect.centerY - iconSize / 2f, right, rect.centerY + iconSize / 2f)
        val left = rect.left + cardH * 0.22f
        action = Box(
            left, rect.centerY - actionHeight / 2f,
            left + actionWidth, rect.centerY + actionHeight / 2f
        )
        text = Box(action.right + cardH * 0.16f, rect.top, icon.left - cardH * 0.14f, rect.bottom)
    }

    fun violations(height: Float): List<String> {
        val out = ArrayList<String>()
        if (text.right <= text.left) out.add("no room for the row text")
        if (text.left < action.right) out.add("text overlaps the action")
        if (text.right > icon.left + 0.5f) out.add("text overlaps the icon")
        if (icon.right > rect.right + 0.5f) out.add("icon leaves the row")
        if (action.left < rect.left - 0.5f) out.add("action leaves the row")
        if (action.height < height * 0.030f) out.add("action is too short to tap")
        return out
    }
}
