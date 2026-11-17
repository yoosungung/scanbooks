import Foundation

/// Mirrors `engine/contracts.py` / ARCHITECTURE §5 — keep in sync.
enum PipelineConstants {
    static let geoNetSize = 288
    static let illPatchSize = 128
    static let illOverlapRatio = 0.125
    static let workLongEdge = 1024
    static let cropMarginRatio = 0.05
    static let modelVersion = "doctr-geotr-fp16-v1+fingerseg-pending+illtr-pending"
    /// M1 latency.md recommendation (Apple host).
    static let defaultOrtEpIOS = "CoreML_MLProgram_NeuralEngine+CPU_fallback"
    /// Android default until on-device NNAPI bench (latency.md: deferred_android_only).
    static let defaultOrtEpAndroid = "CPUExecutionProvider"
}
