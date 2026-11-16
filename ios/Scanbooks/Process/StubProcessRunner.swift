import Foundation

/// M2 stub: walks `crop → geo → finger → ill → done` and persists each status.
/// No ORT / OpenCV (M3). Disk mid-PNG URIs remain for later tickets.
final class StubProcessRunner: @unchecked Sendable {
    private let stepDurationNanoseconds: UInt64
    private(set) var visited: [PipelineStage] = []

    init(stepDurationNanoseconds: UInt64 = 350_000_000) {
        self.stepDurationNanoseconds = stepDurationNanoseconds
    }

    /// UI-only walk (no store). Prefer `run(store:sessionId:pageId:)` after capture.
    func run(onUpdate: @escaping (PipelineStage) -> Void) {
        visited = []
        Task { @MainActor in
            for stage in PipelineStage.processingOrder + [.done] {
                visited.append(stage)
                onUpdate(stage)
                if stage != .done, stepDurationNanoseconds > 0 {
                    try? await Task.sleep(nanoseconds: stepDurationNanoseconds)
                }
            }
        }
    }

    /// Persists each stage on `Page.status` then notifies UI.
    func run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        onUpdate: @escaping (PipelineStage) -> Void
    ) {
        visited = []
        Task { @MainActor in
            for stage in PipelineStage.processingOrder + [.done] {
                do {
                    try store.updatePageStatus(sessionId: sessionId, pageId: pageId, status: stage)
                } catch {
                    // Leave last good status on disk; UI still advances for cancel path.
                }
                visited.append(stage)
                onUpdate(stage)
                if stage != .done, stepDurationNanoseconds > 0 {
                    try? await Task.sleep(nanoseconds: stepDurationNanoseconds)
                }
            }
        }
    }
}
