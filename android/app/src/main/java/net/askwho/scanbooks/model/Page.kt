package net.askwho.scanbooks.model

/** ARCHITECTURE §4.2 `Page`. */
data class Page(
    val id: String,
    val sessionId: String,
    val index: Int,
    val originalUri: String,
    val croppedUri: String? = null,
    val dewarpedUri: String? = null,
    val cleanedUri: String? = null,
    val status: PipelineStage = PipelineStage.CROP,
    val quad: Quad? = null,
    val metrics: PageMetrics? = null,
    val errorCode: String? = null,
    /** ARCHITECTURE §3 — stage that failed; retry resumes here. */
    val failedStage: PipelineStage? = null,
)

/** ARCHITECTURE §4.4 — filled in M3+. */
data class PageMetrics(
    val cropMs: Int? = null,
    val geoMs: Int? = null,
    val fingerMs: Int? = null,
    val illMs: Int? = null,
    val workLongEdge: Int? = null,
    val geoNetSize: Int? = null,
    val illPatchSize: Int? = null,
    val modelVersion: String? = null,
    val ortEp: String? = null,
)
