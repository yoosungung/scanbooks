import Foundation

/// M3 ORT pipeline: `crop → geo → finger → ill → done` with failed-stage retry.
/// Stage nets injectable; selected EP recorded in `PageMetrics.ortEp`.
final class PipelineRunner: @unchecked Sendable {
    private let geo: StageBackend
    private let finger: StageBackend
    private let ill: StageBackend
    private let workLongEdge: Int
    private let geoNetSize: Int
    private let illPatchSize: Int
    private let stepDurationNanoseconds: UInt64

    init(
        geo: StageBackend = PassthroughStageBackend(),
        finger: StageBackend = PassthroughStageBackend(),
        ill: StageBackend = PassthroughStageBackend(),
        workLongEdge: Int = PipelineConstants.workLongEdge,
        geoNetSize: Int = PipelineConstants.geoNetSize,
        illPatchSize: Int = PipelineConstants.illPatchSize,
        stepDurationNanoseconds: UInt64 = 0
    ) {
        self.geo = geo
        self.finger = finger
        self.ill = ill
        self.workLongEdge = workLongEdge
        self.geoNetSize = geoNetSize
        self.illPatchSize = illPatchSize
        self.stepDurationNanoseconds = stepDurationNanoseconds
    }

    func run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        startFrom: PipelineStage = .crop,
        onUpdate: @escaping (PipelineStage) -> Void
    ) {
        Task { @MainActor in
            _ = try? await runAsync(
                store: store,
                sessionId: sessionId,
                pageId: pageId,
                startFrom: startFrom,
                onUpdate: onUpdate
            )
        }
    }

    @discardableResult
    func runAsync(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        startFrom: PipelineStage = .crop,
        onUpdate: @escaping (PipelineStage) -> Void
    ) async throws -> [PipelineStage] {
        var page = try store.loadPage(sessionId: sessionId, pageId: pageId)
        let resume: PipelineStage = {
            if page.status == .failed, let failed = page.failedStage { return failed }
            return startFrom
        }()
        var metrics = page.metrics ?? PageMetrics()
        metrics.workLongEdge = workLongEdge
        metrics.geoNetSize = geoNetSize
        metrics.illPatchSize = illPatchSize
        metrics.modelVersion = PipelineConstants.modelVersion

        var visited: [PipelineStage] = []
        guard let startIdx = PipelineStage.processingOrder.firstIndex(of: resume) else {
            throw StageBackendError.failed("invalid_start")
        }

        do {
            for stage in PipelineStage.processingOrder[startIdx...] {
                page.status = stage
                page.errorCode = nil
                page.failedStage = nil
                page.metrics = metrics
                try store.savePage(page)
                visited.append(stage)
                onUpdate(stage)

                switch stage {
                case .crop:
                    let t0 = Date()
                    let working = try store.readPageFile(sessionId: sessionId, pageId: pageId, fileName: "working.png")
                    let cropped = try BBoxCropper.cropPNG(
                        workingPNG: working,
                        quad: page.quad,
                        workLongEdge: workLongEdge
                    )
                    page.croppedUri = try store.writePagePNG(
                        sessionId: sessionId,
                        pageId: pageId,
                        fileName: "cropped.png",
                        data: cropped
                    )
                    metrics.cropMs = Int(Date().timeIntervalSince(t0) * 1000)
                case .geo:
                    let input = try loadStagePNG(store: store, sessionId: sessionId, pageId: pageId, uri: page.croppedUri, fallback: "cropped.png")
                    let out = try geo.run(inputPNG: input)
                    page.dewarpedUri = try store.writePagePNG(
                        sessionId: sessionId,
                        pageId: pageId,
                        fileName: "dewarped.png",
                        data: out.png
                    )
                    metrics.geoMs = out.elapsedMs
                    metrics.ortEp = out.ortEp ?? metrics.ortEp
                    metrics.modelVersion = out.modelVersion ?? metrics.modelVersion
                case .finger:
                    let input = try loadStagePNG(store: store, sessionId: sessionId, pageId: pageId, uri: page.dewarpedUri, fallback: "dewarped.png")
                    let out = try finger.run(inputPNG: input)
                    _ = try store.writePagePNG(
                        sessionId: sessionId,
                        pageId: pageId,
                        fileName: "finger.png",
                        data: out.png
                    )
                    metrics.fingerMs = out.elapsedMs
                    metrics.ortEp = out.ortEp ?? metrics.ortEp
                case .ill:
                    let fingerURL = store.pageDirPublic(sessionId: sessionId, pageId: pageId)
                        .appendingPathComponent("finger.png")
                    let input: Data
                    if FileManager.default.fileExists(atPath: fingerURL.path) {
                        input = try Data(contentsOf: fingerURL)
                    } else {
                        input = try loadStagePNG(store: store, sessionId: sessionId, pageId: pageId, uri: page.dewarpedUri, fallback: "dewarped.png")
                    }
                    let out = try ill.run(inputPNG: input)
                    page.cleanedUri = try store.writePagePNG(
                        sessionId: sessionId,
                        pageId: pageId,
                        fileName: "cleaned.png",
                        data: out.png
                    )
                    metrics.illMs = out.elapsedMs
                    metrics.ortEp = out.ortEp ?? metrics.ortEp
                default:
                    break
                }
                page.metrics = metrics
                try store.savePage(page)
                if stepDurationNanoseconds > 0 {
                    try? await Task.sleep(nanoseconds: stepDurationNanoseconds)
                }
            }
            page.status = .done
            page.errorCode = nil
            page.failedStage = nil
            page.metrics = metrics
            try store.savePage(page)
            visited.append(.done)
            onUpdate(.done)
            return visited
        } catch {
            let code: String
            if case StageBackendError.failed(let c) = error {
                code = c
            } else {
                code = String(describing: error)
            }
            let failedStage = page.status
            page.status = .failed
            page.failedStage = failedStage == .failed ? resume : failedStage
            page.errorCode = code
            page.metrics = metrics
            try? store.savePage(page)
            visited.append(.failed)
            onUpdate(.failed)
            throw error
        }
    }

    private func loadStagePNG(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        uri: String?,
        fallback: String
    ) throws -> Data {
        if let uri {
            return try Data(contentsOf: store.absoluteFile(sessionId: sessionId, relativeUri: uri))
        }
        return try store.readPageFile(sessionId: sessionId, pageId: pageId, fileName: fallback)
    }
}
