import Foundation

/// `models/` manifest + on-device cache paths (ARCHITECTURE §1.10 — binaries not in git).
struct OrtModelCatalog: Equatable, Sendable {
    var version: String
    var geotrFile: String
    var illtrFile: String
    var fingersegFile: String
    var defaultOrtEp: String
    /// Directory that may contain downloaded/bundled `.onnx` files.
    var modelsDirectory: URL?

    static let bundled = OrtModelCatalog(
        version: PipelineConstants.modelVersion,
        geotrFile: "geotr.onnx",
        illtrFile: "illtr.onnx",
        fingersegFile: "fingerseg.onnx",
        defaultOrtEp: PipelineConstants.defaultOrtEpIOS,
        modelsDirectory: nil
    )

    func fileURL(_ name: String) -> URL? {
        guard let dir = modelsDirectory else { return nil }
        let url = dir.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    var hasGeo: Bool { fileURL(geotrFile) != nil }
    var hasIll: Bool { fileURL(illtrFile) != nil }
    var hasFinger: Bool { fileURL(fingersegFile) != nil }
}

/// Builds stage backends: real ORT when weights exist, else passthrough (order still enforced).
enum OrtBackendFactory {
    static func makeRunner(catalog: OrtModelCatalog = .bundled) -> PipelineRunner {
        let ep = catalog.defaultOrtEp
        let ver = catalog.version
        // Real ORT session binding lands when `.onnx` is on device; until then passthrough
        // keeps Process→PDF working and records selected EP in PageMetrics.
        let geo: StageBackend = catalog.hasGeo
            ? OrtGeoStageBackend(modelURL: catalog.fileURL(catalog.geotrFile)!, ortEp: ep, modelVersion: ver)
            : PassthroughStageBackend(ortEp: ep, modelVersion: ver)
        let finger: StageBackend = catalog.hasFinger
            ? OrtFingerStageBackend(modelURL: catalog.fileURL(catalog.fingersegFile)!, ortEp: ep, modelVersion: ver)
            : PassthroughStageBackend(ortEp: ep, modelVersion: ver)
        let ill: StageBackend = catalog.hasIll
            ? OrtIllStageBackend(modelURL: catalog.fileURL(catalog.illtrFile)!, ortEp: ep, modelVersion: ver)
            : PassthroughStageBackend(ortEp: ep, modelVersion: ver)
        return PipelineRunner(geo: geo, finger: finger, ill: ill)
    }
}

/// Placeholder ORT geo backend — loads when weights present; remap via CvBridge.
struct OrtGeoStageBackend: StageBackend {
    var modelURL: URL
    var ortEp: String
    var modelVersion: String

    func run(inputPNG: Data) throws -> StageOutput {
        _ = modelURL
        _ = inputPNG
        throw StageBackendError.failed("ort_geo_not_linked")
    }
}

struct OrtFingerStageBackend: StageBackend {
    var modelURL: URL
    var ortEp: String
    var modelVersion: String

    func run(inputPNG: Data) throws -> StageOutput {
        _ = modelURL
        _ = inputPNG
        throw StageBackendError.failed("ort_finger_not_linked")
    }
}

struct OrtIllStageBackend: StageBackend {
    var modelURL: URL
    var ortEp: String
    var modelVersion: String

    func run(inputPNG: Data) throws -> StageOutput {
        _ = modelURL
        _ = inputPNG
        throw StageBackendError.failed("ort_ill_not_linked")
    }
}
