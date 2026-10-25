package ir.mivehchin.game

/**
 * Geometry of the level-result panel, kept free of any Android types so it can be
 * unit tested on the JVM (see VictoryLayoutTest).
 *
 * Everything is derived from the viewport size: the content column is stacked top
 * to bottom, the last [gardenH] pixels of the panel are reserved for the hedge and
 * fruit, and the stack is scaled down when a short screen cannot hold it.
 */
internal class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun overlaps(other: Box, tolerance: Float = 0.5f): Boolean =
        left < other.right - tolerance && other.left < right - tolerance &&
            top < other.bottom - tolerance && other.top < bottom - tolerance

    override fun toString(): String = "Box(%.1f, %.1f, %.1f, %.1f)".format(left, top, right, bottom)
}

internal class VictoryGeometry(
    val width: Float,
    val height: Float,
    val win: Boolean
) {
    val panel: Box
    val inner: Box
    val gardenTop: Float
    val gardenH: Float
    val plaque: Box
    val chip: Box
    val starCy: Float
    val starR: Float
    val socketR: Float
    val score: Box
    val capsule: Box
    val primary: Box
    val secondary: Box
    /** True when the content had to be scaled down to fit a short screen. */
    var compressed: Boolean = false
        private set

    init {
        val w = width
        val h = height
        panel = Box(w * 0.043f, h * 0.072f, w * 0.957f, h * 0.920f)
        val pw = panel.width
        val rim = w * 0.013f
        inner = Box(panel.left + rim, panel.top + rim, panel.right - rim, panel.bottom - rim)
        gardenH = inner.height * 0.075f
        val avail = inner.height - gardenH

        var plaqueH = avail * 0.142f
        var chipH = avail * 0.048f
        var starRv = minOf(w * 0.062f, avail * 0.052f)
        var scoreH = avail * 0.170f
        var capH = avail * 0.062f
        var priH = avail * 0.095f
        var secH = avail * 0.082f
        var g1 = avail * 0.030f
        var g2 = avail * 0.040f
        var g3 = avail * 0.040f
        var g4 = avail * 0.030f
        var g5 = avail * 0.045f
        var g6 = avail * 0.024f

        fun stackOf(): Float = plaqueH + chipH + starRv * 2.72f + scoreH + capH + priH + secH +
            g1 + g2 + g3 + g4 + g5 + g6

        var stack = stackOf()
        compressed = stack > avail
        if (compressed) {
            val f = avail / stack
            plaqueH *= f
            chipH *= f
            starRv *= f
            scoreH *= f
            capH *= f
            priH *= f
            secH *= f
            g1 *= f
            g2 *= f
            g3 *= f
            g4 *= f
            g5 *= f
            g6 *= f
        } else {
            // spread the leftover space evenly so no single void appears
            val slack = avail - stack
            scoreH += slack * 0.26f
            g2 += slack * 0.18f
            g3 += slack * 0.18f
            g5 += slack * 0.14f
            g4 += slack * 0.10f
            g6 += slack * 0.06f
            plaqueH += slack * 0.08f
        }
        stack = stackOf()

        val stackTop = inner.top + avail * 0.012f
        val plaqueTop = stackTop - plaqueH * 0.26f
        plaque = Box(inner.centerX - pw * 0.315f, plaqueTop, inner.centerX + pw * 0.315f, plaqueTop + plaqueH)
        chip = Box(plaque.left + pw * 0.055f, plaque.bottom + g1,
            plaque.right - pw * 0.055f, plaque.bottom + g1 + chipH)
        starR = starRv
        socketR = starR * 1.36f
        starCy = chip.bottom + g2 + socketR
        score = Box(inner.left + pw * 0.072f, starCy + socketR + g3,
            inner.right - pw * 0.072f, starCy + socketR + g3 + scoreH)
        capsule = Box(inner.centerX - pw * 0.255f, score.bottom + g4,
            inner.centerX + pw * 0.255f, score.bottom + g4 + capH)
        primary = Box(inner.left + pw * 0.082f, capsule.bottom + g5,
            inner.right - pw * 0.082f, capsule.bottom + g5 + priH)
        secondary = Box(primary.left, primary.bottom + g6, primary.right, primary.bottom + g6 + secH)
        gardenTop = inner.bottom - gardenH
    }

    /** Problems that would ship a broken screen; empty when the layout is sound. */
    fun violations(): List<String> {
        val out = ArrayList<String>()
        if (secondary.bottom > gardenTop + 0.5f) out.add("action buttons run into the garden strip")
        if (primary.bottom > secondary.top + 0.5f) out.add("primary overlaps secondary")
        if (capsule.bottom > primary.top + 0.5f) out.add("capsule overlaps primary")
        if (score.bottom > capsule.top + 0.5f) out.add("score overlaps capsule")
        if (starCy + socketR > score.top + 0.5f) out.add("stars overlap score")
        if (chip.bottom > starCy - socketR + 0.5f) out.add("chip overlaps stars")
        if (plaque.bottom > chip.top + 0.5f) out.add("chip overlaps plaque")
        if (panel.top < 0f || panel.bottom > height) out.add("panel leaves the viewport")
        if (panel.left < 0f || panel.right > width) out.add("panel leaves the viewport")
        if (plaque.top < panel.top - height * 0.06f) out.add("plaque floats too far above the panel")
        if (secondary.bottom > height) out.add("secondary below the viewport")
        return out
    }
}
