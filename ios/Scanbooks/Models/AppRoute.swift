import Foundation

/// UI shell routes (docs/UIUX.md §2). PageReview/Library = later milestones.
enum AppRoute: Equatable, Hashable, Sendable {
    case camera
    case process
    case session
    case settings
}
