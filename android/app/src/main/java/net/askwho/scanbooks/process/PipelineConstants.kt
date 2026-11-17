package net.askwho.scanbooks.process

/** Mirrors `engine/contracts.py` / ARCHITECTURE §5 — keep in sync. */
object PipelineConstants {
    const val GEO_NET_SIZE = 288
    const val ILL_PATCH_SIZE = 128
    const val WORK_LONG_EDGE = 1024
    const val CROP_MARGIN_RATIO = 0.05
    const val MODEL_VERSION = "doctr-geotr-fp16-v1+fingerseg-pending+illtr-pending"
    /** M1 latency.md — NNAPI deferred; CPU until on-device bench. */
    const val DEFAULT_ORT_EP_ANDROID = "CPUExecutionProvider"
    /** Apple host recommendation (parity with iOS PageMetrics). */
    const val DEFAULT_ORT_EP_IOS = "CoreML_MLProgram_NeuralEngine+CPU_fallback"
}
