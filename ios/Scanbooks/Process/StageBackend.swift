import Foundation

struct StageOutput: Equatable, Sendable {
    var png: Data
    var elapsedMs: Int
    var ortEp: String?
    var modelVersion: String?
}

/// One pipeline network/post stage (geo / finger / ill). Injectable for tests.
protocol StageBackend: Sendable {
    func run(inputPNG: Data) throws -> StageOutput
}

/// Identity stage used when ORT weights are absent or in unit tests.
/// Preserves `geo → finger → ill` order while writing real stage PNGs.
struct PassthroughStageBackend: StageBackend {
    var ortEp: String
    var modelVersion: String

    init(
        ortEp: String = PipelineConstants.defaultOrtEpIOS,
        modelVersion: String = PipelineConstants.modelVersion
    ) {
        self.ortEp = ortEp
        self.modelVersion = modelVersion
    }

    func run(inputPNG: Data) throws -> StageOutput {
        let start = Date()
        guard SessionStore.isPNG(inputPNG) else { throw SessionStoreError.notPNG }
        let ms = Int(Date().timeIntervalSince(start) * 1000)
        return StageOutput(png: inputPNG, elapsedMs: max(ms, 0), ortEp: ortEp, modelVersion: modelVersion)
    }
}

/// Throws so PipelineRunner can mark `failed` + `errorCode` (ARCHITECTURE §1.3).
struct FailingStageBackend: StageBackend {
    var code: String

    func run(inputPNG: Data) throws -> StageOutput {
        _ = inputPNG
        throw StageBackendError.failed(code)
    }
}

enum StageBackendError: Error, Equatable {
    case failed(String)
}
