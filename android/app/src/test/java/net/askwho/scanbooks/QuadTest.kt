package net.askwho.scanbooks

import net.askwho.scanbooks.model.NormPoint
import net.askwho.scanbooks.model.Quad
import org.junit.Assert.assertEquals
import org.junit.Test

class QuadTest {
    @Test
    fun clampsNormalizedCornersToUnitSquare() {
        val raw = Quad(
            tl = NormPoint(-0.2f, -0.1f),
            tr = NormPoint(1.4f, 0f),
            br = NormPoint(1.1f, 1.2f),
            bl = NormPoint(0f, 2f),
        )
        val q = raw.clamped()
        assertEquals(0f, q.tl.x, 1e-6f)
        assertEquals(0f, q.tl.y, 1e-6f)
        assertEquals(1f, q.tr.x, 1e-6f)
        assertEquals(1f, q.br.y, 1e-6f)
        assertEquals(1f, q.bl.y, 1e-6f)
    }

    @Test
    fun axisAlignedFactoryMatchesArchitectureOrigin() {
        val q = Quad.axisAligned(inset = 0.1f)
        assertEquals(0.1f, q.tl.x, 1e-6f)
        assertEquals(0.1f, q.tl.y, 1e-6f)
        assertEquals(0.9f, q.br.x, 1e-6f)
        assertEquals(0.9f, q.br.y, 1e-6f)
    }
}
