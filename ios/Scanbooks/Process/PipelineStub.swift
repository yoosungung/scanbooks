import Foundation

/// Compatibility façade — M3 delegates to `PipelineRunner` (selected EP + stage backends).
final class PipelineStub: @unchecked Sendable {
    private let runner: PipelineRunner

    init(stepDurationNanoseconds: UInt64 = 350_000_000) {
        self.runner = PipelineRunner(stepDurationNanoseconds: stepDurationNanoseconds)
    }

    init(runner: PipelineRunner) {
        self.runner = runner
    }

    func run(
        store: SessionStore,
        sessionId: String,
        pageId: String,
        onUpdate: @escaping (PipelineStage) -> Void
    ) {
        runner.run(store: store, sessionId: sessionId, pageId: pageId, onUpdate: onUpdate)
    }
}
