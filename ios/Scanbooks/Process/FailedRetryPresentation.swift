import Foundation

/// Process / PageReview failure copy (docs/UIUX.md §4.3).
enum FailedRetryPresentation {
    static let retryButtonLabel = "이 단계부터 재시도"

    static func errorLine(failedStage: PipelineStage?, errorCode: String?) -> String? {
        guard let stage = failedStage else { return nil }
        let base = "\(stage.koreanLabel) 실패"
        guard let code = errorCode, !code.isEmpty else { return base }
        return "\(base) · \(code)"
    }
}
