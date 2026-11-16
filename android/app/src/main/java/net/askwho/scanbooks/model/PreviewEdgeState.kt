package net.askwho.scanbooks.model

enum class OverlayKind {
    GRAY_GUIDE,
    GREEN_QUAD,
}

sealed class PreviewEdgeState {
    abstract val overlayKind: OverlayKind
    abstract val quad: Quad?
    abstract val hintCopy: String

    /** ARCHITECTURE §1.4 — live preview never shows Dewarp. */
    val showsDewarpPreview: Boolean get() = false

    data object Undetected : PreviewEdgeState() {
        override val overlayKind = OverlayKind.GRAY_GUIDE
        override val quad: Quad? = null
        override val hintCopy = "문서를 화면에 맞춰 주세요"
    }

    data class Detected(override val quad: Quad) : PreviewEdgeState() {
        override val overlayKind = OverlayKind.GREEN_QUAD
        override val hintCopy = ""
    }
}
