import Foundation

/// M2 pipeline stub: `crop→geo→finger→ill→done` with `Page.status` persistence.
/// No ORT / OpenCV (ARCHITECTURE M3). UI labels: docs/UIUX.md §4.2.
final class PipelineStub: @unchecked Sendable {
    private let stepDurationNanoseconds: UInt64

    init(stepDurationNanoseconds: UInt64 = 350_000_000) {
        self.stepDurationNanoseconds = stepDurationNanoseconds
    }

    func run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        onUpdate: @escaping (PipelineStage) -> Void
    ) {
        Task { @MainActor in
            let stages = PipelineStage.processingOrder + [.done]
            for stage in stages {
                try? store.updatePageStatus(sessionId: sessionId, pageId: pageId, status: stage)
                onUpdate(stage)
                if stage != .done, stepDurationNanoseconds > 0 {
                    try? await Task.sleep(nanoseconds: stepDurationNanoseconds)
                }
            }
        }
    }
}
