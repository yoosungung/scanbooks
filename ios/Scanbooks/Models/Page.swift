import Foundation

/// ARCHITECTURE §4.2 `Page`.
struct Page: Equatable, Sendable, Codable {
    var id: String
    var sessionId: String
    var index: Int
    var originalUri: String
    var croppedUri: String?
    var dewarpedUri: String?
    var cleanedUri: String?
    var status: PipelineStage
    var quad: Quad?
    var metrics: PageMetrics?
    var errorCode: String?
    /// ARCHITECTURE §3 — stage that failed; retry resumes here.
    var failedStage: PipelineStage?
}

/// ARCHITECTURE §4.4 — stored when pipeline fills metrics (M3+).
struct PageMetrics: Equatable, Sendable, Codable {
    var cropMs: Int?
    var geoMs: Int?
    var fingerMs: Int?
    var illMs: Int?
    var workLongEdge: Int?
    var geoNetSize: Int?
    var illPatchSize: Int?
    var modelVersion: String?
    var ortEp: String?
}
