package net.askwho.scanbooks.process

import kotlinx.coroutines.delay
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.storage.SessionStore

/**
 * M2 pipeline stub: `crop→geo→finger→ill→done` with [Page.status] persistence.
 * No ORT / OpenCV (ARCHITECTURE M3). UI labels: docs/UIUX.md §4.2.
 */
class PipelineStub(
    private val stepDelayMs: Long = 350L,
) {
    suspend fun run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        onUpdate: (PipelineStage) -> Unit,
    ): List<PipelineStage> {
        val visited = mutableListOf<PipelineStage>()
        val stages = PipelineStage.processingOrder + PipelineStage.DONE
        for (stage in stages) {
            if (stage == PipelineStage.DONE) {
                store.finalizeStubCleaned(sessionId, pageId)
            } else {
                store.updatePageStatus(sessionId, pageId, stage)
            }
            visited += stage
            onUpdate(stage)
            if (stage != PipelineStage.DONE && stepDelayMs > 0) delay(stepDelayMs)
        }
        return visited
    }
}
