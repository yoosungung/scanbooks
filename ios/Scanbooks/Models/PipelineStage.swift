import Foundation

/// Mirrors ARCHITECTURE §4.2 `PipelineStage`. UI labels: docs/UIUX.md §4.2.
enum PipelineStage: String, CaseIterable, Equatable, Sendable, Codable {
    case crop
    case geo
    case finger
    case ill
    case done
    case failed

    /// Ordered processing steps shown on stub Process (excludes terminal states).
    static let processingOrder: [PipelineStage] = [.crop, .geo, .finger, .ill]

    var koreanLabel: String {
        switch self {
        case .crop: return "자르기"
        case .geo: return "곡면 펴기"
        case .finger: return "손가락 지우기"
        case .ill: return "조명 보정"
        case .done: return "완료"
        case .failed: return "실패"
        }
    }
}
