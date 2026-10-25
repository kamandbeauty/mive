package ir.mivehchin.game

/**
 * Design tokens for the whole game — one place that owns spacing, radii, type
 * sizes, motion timings and the material palette used by every screen.
 *
 * Everything is expressed as a fraction of the viewport so the same numbers work
 * on a 320 dp phone and on a tablet. Screens must never hard-code their own
 * corner radius, font size or gap; they call into here instead.
 */
internal object Design {

    /* ------------------------------------------------------------- spacing */

    /** Base rhythm unit (~8 dp at 1080 px width). */
    fun unit(w: Float): Float = w * 0.008f

    /** Gaps in rhythm units: 1 = tight, 2 = default, 3 = section, 4 = hero. */
    fun gap(w: Float, units: Float): Float = unit(w) * units

    /** Horizontal safe margin for full-width content. */
    fun margin(w: Float): Float = w * 0.045f

    /* -------------------------------------------------------------- radii */

    fun radiusPill(h: Float): Float = h * 0.5f
    fun radiusChip(h: Float): Float = h * 0.42f
    fun radiusCard(w: Float): Float = w * 0.030f
    fun radiusPanel(w: Float): Float = w * 0.055f
    fun radiusDialog(w: Float): Float = w * 0.062f

    /** Thickness of the golden rim around cream frames. */
    fun rim(w: Float): Float = w * 0.013f

    /* --------------------------------------------------------- typography */

    /** Big congratulation headings. */
    fun display(w: Float): Float = w * 0.086f

    /** Screen titles and section headers. */
    fun title(w: Float): Float = w * 0.058f

    /** Card headlines, button labels. */
    fun headline(w: Float): Float = w * 0.044f

    /** Default body copy. */
    fun body(w: Float): Float = w * 0.036f

    /** Secondary captions and hints. */
    fun label(w: Float): Float = w * 0.030f

    /** Smallest legible print. */
    fun micro(w: Float): Float = w * 0.024f

    /** Large numeric readouts (score, coin balance). */
    fun number(w: Float): Float = w * 0.070f

    /* ------------------------------------------------------------- motion */

    const val ENTER_MS = 320f
    const val STAGGER_MS = 70f
    const val POP_MS = 220f
    const val SHAKE_MS = 170f

    /** How far a button sinks when pressed, as a fraction of its height. */
    const val PRESS_DIP = 0.055f

    /* ---------------------------------------------------------- materials */

    // cream content surface
    val CREAM_TOP = 0xFFFFFCF3.toInt()
    val CREAM_BOTTOM = 0xFFFFE6BE.toInt()

    // golden rim
    val FRAME_TOP = 0xFFFFECB4.toInt()
    val FRAME_BOTTOM = 0xFFB8740C.toInt()
    val FRAME_DEEP = 0xFF7A4A05.toInt()

    // deep emerald panel (score, HUD)
    val EMERALD_TOP = 0xFF17744F.toInt()
    val EMERALD_BOTTOM = 0xFF052C1C.toInt()
    val EMERALD_LIP = 0xFF0A2A1E.toInt()

    // glass card on the garden
    val GLASS_TOP = 0xFF1C5440.toInt()
    val GLASS_BOTTOM = 0xFF0C2C21.toInt()

    // royal blue secondary action
    val ROYAL_TOP = 0xFF7FC0FF.toInt()
    val ROYAL_BOTTOM = 0xFF2A56CC.toInt()
    val ROYAL_DEEP = 0xFF14307F.toInt()

    // lime primary action
    val LIME_TOP = 0xFFD9FB90.toInt()
    val LIME_BOTTOM = 0xFF57992A.toInt()
    val LIME_DEEP = 0xFF3B6E1B.toInt()

    // danger
    val DANGER_TOP = 0xFFFF9AA6.toInt()
    val DANGER_BOTTOM = 0xFFC22B44.toInt()

    val INK_ON_LIME = 0xFF14330B.toInt()
    val INK_ON_GOLD = 0xFF43280A.toInt()
    val INK_ON_CREAM = 0xFF5A2E05.toInt()
    val INK_BODY = 0xFF7A5220.toInt()
}
