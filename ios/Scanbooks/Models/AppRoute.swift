import Foundation

/// M2 UI shell routes (docs/UIUX.md §2). PageReview/Library/Settings = later milestones.
enum AppRoute: Equatable, Hashable, Sendable {
    case camera
    case process
    case session
}
