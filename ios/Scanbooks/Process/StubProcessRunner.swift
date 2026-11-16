import Foundation

/// In-memory stage walk for M2 stub Process UI. No ORT / disk I/O.
final class StubProcessRunner: @unchecked Sendable {
    private let stepDurationNanoseconds: UInt64
    private(set) var visited: [PipelineStage] = []

    init(stepDurationNanoseconds: UInt64 = 350_000_000) {
        self.stepDurationNanoseconds = stepDurationNanoseconds
    }

    func run(onUpdate: @escaping (PipelineStage) -> Void) {
        visited = []
        Task { @MainActor in
            for stage in PipelineStage.processingOrder {
                visited.append(stage)
                onUpdate(stage)
                if stepDurationNanoseconds > 0 {
                    try? await Task.sleep(nanoseconds: stepDurationNanoseconds)
                }
            }
            visited.append(.done)
            onUpdate(.done)
        }
    }
}
