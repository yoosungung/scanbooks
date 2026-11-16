package net.askwho.scanbooks.model

data class NormPoint(val x: Float, val y: Float)

/** ARCHITECTURE §4.3 — normalized [0,1], top-left origin. */
data class Quad(
    val tl: NormPoint,
    val tr: NormPoint,
    val br: NormPoint,
    val bl: NormPoint,
) {
    fun clamped(): Quad = Quad(
        tl = NormPoint(tl.x.clamped01(), tl.y.clamped01()),
        tr = NormPoint(tr.x.clamped01(), tr.y.clamped01()),
        br = NormPoint(br.x.clamped01(), br.y.clamped01()),
        bl = NormPoint(bl.x.clamped01(), bl.y.clamped01()),
    )

    companion object {
        fun axisAligned(inset: Float): Quad {
            val i = inset.clamped01()
            val o = (1f - inset).clamped01()
            return Quad(
                tl = NormPoint(i, i),
                tr = NormPoint(o, i),
                br = NormPoint(o, o),
                bl = NormPoint(i, o),
            )
        }
    }
}

private fun Float.clamped01(): Float = coerceIn(0f, 1f)
