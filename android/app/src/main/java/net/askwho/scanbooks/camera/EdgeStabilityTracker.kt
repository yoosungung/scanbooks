package net.askwho.scanbooks.camera

import net.askwho.scanbooks.model.PreviewEdgeState
import net.askwho.scanbooks.model.Quad
import kotlin.math.abs

class EdgeStabilityTracker(
    private val requiredHits: Int = 3,
) {
    private var consecutiveHits: Int = 0
    private var lastQuad: Quad? = null

    fun ingest(candidate: Quad?): PreviewEdgeState {
        if (candidate == null) {
            consecutiveHits = 0
            lastQuad = null
            return PreviewEdgeState.Undetected
        }
        val last = lastQuad
        if (last != null && similar(last, candidate)) {
            consecutiveHits += 1
        } else {
            consecutiveHits = 1
            lastQuad = candidate
        }
        return if (consecutiveHits >= requiredHits.coerceAtLeast(1)) {
            PreviewEdgeState.Detected(candidate.clamped())
        } else {
            PreviewEdgeState.Undetected
        }
    }

    private fun similar(a: Quad, b: Quad, tolerance: Float = 0.08f): Boolean =
        abs(a.tl.x - b.tl.x) < tolerance &&
            abs(a.tl.y - b.tl.y) < tolerance &&
            abs(a.br.x - b.br.x) < tolerance &&
            abs(a.br.y - b.br.y) < tolerance
}
