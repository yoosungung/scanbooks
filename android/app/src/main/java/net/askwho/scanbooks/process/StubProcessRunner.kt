package net.askwho.scanbooks.process

import kotlinx.coroutines.delay
import net.askwho.scanbooks.model.PipelineStage

/** In-memory stage walk for M2 stub Process UI. No ORT / disk I/O. */
class StubProcessRunner(
    private val stepDelayMs: Long = 350L,
) {
    suspend fun run(onUpdate: (PipelineStage) -> Unit): List<PipelineStage> {
        val visited = mutableListOf<PipelineStage>()
        for (stage in PipelineStage.processingOrder) {
            visited += stage
            onUpdate(stage)
            if (stepDelayMs > 0) delay(stepDelayMs)
        }
        visited += PipelineStage.DONE
        onUpdate(PipelineStage.DONE)
        return visited
    }
}
