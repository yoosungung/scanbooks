package net.askwho.scanbooks

import net.askwho.scanbooks.model.OverlayKind
import net.askwho.scanbooks.model.PreviewEdgeState
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.camera.EdgeStabilityTracker
import net.askwho.scanbooks.camera.RoughQuadDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PreviewEdgeStateTest {
    @Test
    fun undetectedUsesGrayGuideAndHintCopy() {
        val state = PreviewEdgeState.Undetected
        assertEquals(OverlayKind.GRAY_GUIDE, state.overlayKind)
        assertEquals("문서를 화면에 맞춰 주세요", state.hintCopy)
        assertNull(state.quad)
    }

    @Test
    fun detectedUsesGreenQuadWithoutDewarpPreview() {
        val quad = Quad.axisAligned(0.15f)
        val state = PreviewEdgeState.Detected(quad)
        assertEquals(OverlayKind.GREEN_QUAD, state.overlayKind)
        assertEquals(quad, state.quad)
        assertFalse(state.showsDewarpPreview)
    }

    @Test
    fun stabilityRequiresConsecutiveHits() {
        val tracker = EdgeStabilityTracker(requiredHits = 3)
        assertEquals(OverlayKind.GRAY_GUIDE, tracker.ingest(null).overlayKind)
        assertEquals(OverlayKind.GRAY_GUIDE, tracker.ingest(Quad.axisAligned(0.2f)).overlayKind)
        assertEquals(OverlayKind.GRAY_GUIDE, tracker.ingest(Quad.axisAligned(0.2f)).overlayKind)
        assertEquals(OverlayKind.GREEN_QUAD, tracker.ingest(Quad.axisAligned(0.2f)).overlayKind)
    }

    @Test
    fun roughDetectorFindsBrightPaperRectangle() {
        val w = 64
        val h = 80
        val pixels = IntArray(w * h) { 20 }
        for (y in 16 until 64) {
            for (x in 12 until 52) {
                pixels[y * w + x] = 220
            }
        }
        val quad = RoughQuadDetector().detect(pixels, w, h)
        assertNotNull(quad)
        val q = quad!!
        assertEquals(12f / w, q.tl.x, 0.08f)
        assertEquals(16f / h, q.tl.y, 0.08f)
        assertEquals(52f / w, q.br.x, 0.08f)
        assertEquals(64f / h, q.br.y, 0.08f)
    }

    @Test
    fun roughDetectorReturnsNullOnUniformFrame() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { 40 }
        assertNull(RoughQuadDetector().detect(pixels, w, h))
    }
}
