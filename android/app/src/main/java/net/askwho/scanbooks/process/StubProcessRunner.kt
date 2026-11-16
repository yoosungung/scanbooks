package net.askwho.scanbooks.process

import kotlinx.coroutines.delay
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.storage.SessionStore

/**
 * M2 stub: walks `crop → geo → finger → ill → done` and persists each status.
 * No ORT / OpenCV (M3). Mid-PNG URIs remain for later tickets.
 */
class StubProcessRunner(
    private val stepDelayMs: Long = 350L,
) {
    /** UI-only walk (no store). Prefer [run] with [SessionStore] after capture. */
    suspend fun run(onUpdate: (PipelineStage) -> Unit): List<PipelineStage> {
        val visited = mutableListOf<PipelineStage>()
        for (stage in PipelineStage.processingOrder + PipelineStage.DONE) {
            visited += stage
            onUpdate(stage)
            if (stage != PipelineStage.DONE && stepDelayMs > 0) delay(stepDelayMs)
        }
        return visited
    }

    /** Persists each stage on `Page.status` then notifies UI. */
    suspend fun run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        onUpdate: (PipelineStage) -> Unit,
    ): List<PipelineStage> {
        val visited = mutableListOf<PipelineStage>()
        for (stage in PipelineStage.processingOrder + PipelineStage.DONE) {
            runCatching { store.updatePageStatus(sessionId, pageId, stage) }
            visited += stage
            onUpdate(stage)
            if (stage != PipelineStage.DONE && stepDelayMs > 0) delay(stepDelayMs)
        }
        return visited
    }
}
