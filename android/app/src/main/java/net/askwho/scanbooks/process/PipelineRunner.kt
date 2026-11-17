package net.askwho.scanbooks.process

import net.askwho.scanbooks.model.PageMetrics
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.storage.SessionStore
import java.io.File

/**
 * M3 pipeline: `crop → geo → finger → ill → done` with disk URIs + [PageMetrics].
 * ORT backends injectable; default passthrough until on-device `.onnx` exists.
 */
class PipelineRunner(
    private val geo: StageBackend = PassthroughStageBackend(),
    private val finger: StageBackend = PassthroughStageBackend(),
    private val ill: StageBackend = PassthroughStageBackend(),
    private val workLongEdge: Int = PipelineConstants.WORK_LONG_EDGE,
    private val geoNetSize: Int = PipelineConstants.GEO_NET_SIZE,
    private val illPatchSize: Int = PipelineConstants.ILL_PATCH_SIZE,
) {
    suspend fun run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        startFrom: PipelineStage = PipelineStage.CROP,
        onUpdate: (PipelineStage) -> Unit,
    ): List<PipelineStage> {
        // Suspend surface for Compose; work is CPU-bound sync today.
        return runBlockingCompatible(store, sessionId, pageId, startFrom, onUpdate)
    }

    fun runBlockingCompatible(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        startFrom: PipelineStage = PipelineStage.CROP,
        onUpdate: (PipelineStage) -> Unit,
    ): List<PipelineStage> {
        var page = store.loadPage(sessionId, pageId)
        var metrics = page.metrics ?: PageMetrics()
        metrics = metrics.copy(
            workLongEdge = workLongEdge,
            geoNetSize = geoNetSize,
            illPatchSize = illPatchSize,
            modelVersion = PipelineConstants.MODEL_VERSION,
        )
        val visited = mutableListOf<PipelineStage>()
        val order = PipelineStage.processingOrder
        val startIdx = order.indexOf(startFrom).takeIf { it >= 0 }
            ?: throw StageBackendException("invalid_start")

        try {
            for (stage in order.subList(startIdx, order.size)) {
                page = page.copy(status = stage, errorCode = null, failedStage = null, metrics = metrics)
                store.savePage(page)
                visited += stage
                onUpdate(stage)

                when (stage) {
                    PipelineStage.CROP -> {
                        val t0 = System.currentTimeMillis()
                        val working = store.readPageFile(sessionId, pageId, "working.png")
                        val cropped = BBoxCropper.cropPng(
                            workingPng = working,
                            quad = page.quad,
                            workLongEdge = workLongEdge,
                        )
                        page = page.copy(
                            croppedUri = store.writePagePng(sessionId, pageId, "cropped.png", cropped),
                        )
                        metrics = metrics.copy(cropMs = (System.currentTimeMillis() - t0).toInt())
                    }
                    PipelineStage.GEO -> {
                        val input = loadStagePng(store, sessionId, pageId, page.croppedUri, "cropped.png")
                        val out = geo.run(input)
                        page = page.copy(
                            dewarpedUri = store.writePagePng(sessionId, pageId, "dewarped.png", out.png),
                        )
                        metrics = metrics.copy(
                            geoMs = out.elapsedMs,
                            ortEp = out.ortEp ?: metrics.ortEp,
                            modelVersion = out.modelVersion ?: metrics.modelVersion,
                        )
                    }
                    PipelineStage.FINGER -> {
                        val input = loadStagePng(store, sessionId, pageId, page.dewarpedUri, "dewarped.png")
                        val out = finger.run(input)
                        store.writePagePng(sessionId, pageId, "finger.png", out.png)
                        metrics = metrics.copy(
                            fingerMs = out.elapsedMs,
                            ortEp = out.ortEp ?: metrics.ortEp,
                        )
                    }
                    PipelineStage.ILL -> {
                        val fingerFile = store.absoluteFile(sessionId, "pages/$pageId/finger.png")
                        val input = if (fingerFile.exists()) {
                            fingerFile.readBytes()
                        } else {
                            loadStagePng(store, sessionId, pageId, page.dewarpedUri, "dewarped.png")
                        }
                        val out = ill.run(input)
                        page = page.copy(
                            cleanedUri = store.writePagePng(sessionId, pageId, "cleaned.png", out.png),
                        )
                        metrics = metrics.copy(
                            illMs = out.elapsedMs,
                            ortEp = out.ortEp ?: metrics.ortEp,
                        )
                    }
                    else -> Unit
                }
                page = page.copy(metrics = metrics)
                store.savePage(page)
            }
            page = page.copy(status = PipelineStage.DONE, errorCode = null, failedStage = null, metrics = metrics)
            store.savePage(page)
            visited += PipelineStage.DONE
            onUpdate(PipelineStage.DONE)
            return visited
        } catch (e: Exception) {
            val code = (e as? StageBackendException)?.code ?: e.message ?: "unknown"
            val failedAt = page.status.takeIf { it in PipelineStage.processingOrder } ?: startFrom
            page = page.copy(
                status = PipelineStage.FAILED,
                failedStage = failedAt,
                errorCode = code,
                metrics = metrics,
            )
            store.savePage(page)
            visited += PipelineStage.FAILED
            onUpdate(PipelineStage.FAILED)
            throw e
        }
    }

    private fun loadStagePng(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        uri: String?,
        fallback: String,
    ): ByteArray {
        if (uri != null) {
            return File(store.absoluteFile(sessionId, uri).absolutePath).readBytes()
        }
        return store.readPageFile(sessionId, pageId, fallback)
    }
}
