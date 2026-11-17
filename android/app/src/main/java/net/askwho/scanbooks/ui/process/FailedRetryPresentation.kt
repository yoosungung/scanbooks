package net.askwho.scanbooks.ui.process

import net.askwho.scanbooks.model.PipelineStage

/** Process / PageReview failure copy (docs/UIUX.md §4.3). */
object FailedRetryPresentation {
    const val retryButtonLabel: String = "이 단계부터 재시도"

    fun errorLine(failedStage: PipelineStage?, errorCode: String?): String? {
        val stage = failedStage ?: return null
        val base = "${stage.koreanLabel} 실패"
        val code = errorCode?.takeIf { it.isNotBlank() } ?: return base
        return "$base · $code"
    }
}
