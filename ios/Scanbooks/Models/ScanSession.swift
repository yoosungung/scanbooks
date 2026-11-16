import Foundation

/// ARCHITECTURE §4.1 — local-only session.
struct ScanSession: Equatable, Codable, Sendable {
    var id: String
    var createdAt: String
    var title: String
    var pageIds: [String]
    var exportPdfUri: String?
}
