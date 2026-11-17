package net.askwho.scanbooks.process

import kotlinx.coroutines.delay
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.storage.SessionStore

/**
 * Compatibility façade — M3 delegates to [PipelineRunner] (selected EP + stage backends).
 */
class PipelineStub(
    private val stepDelayMs: Long = 350L,
    private val runner: PipelineRunner = OrtBackendFactory.makeRunner(),
) {
    suspend fun run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        onUpdate: (PipelineStage) -> Unit,
    ): List<PipelineStage> {
        val visited = runner.run(store, sessionId, pageId, onUpdate = onUpdate)
        if (stepDelayMs > 0) delay(stepDelayMs)
        return visited
    }
}
