import Foundation

enum OverlayKind: Equatable, Sendable {
    case grayGuide
    case greenQuad
}

enum PreviewEdgeState: Equatable, Sendable {
    case undetected
    case detected(Quad)

    var overlayKind: OverlayKind {
        switch self {
        case .undetected: return .grayGuide
        case .detected: return .greenQuad
        }
    }

    var quad: Quad? {
        switch self {
        case .undetected: return nil
        case .detected(let q): return q
        }
    }

    var hintCopy: String {
        switch self {
        case .undetected: return "문서를 화면에 맞춰 주세요"
        case .detected: return ""
        }
    }

    /// ARCHITECTURE §1.4 — live preview never shows Dewarp.
    var showsDewarpPreview: Bool { false }
}

struct EdgeStabilityTracker: Sendable {
    let requiredHits: Int
    private var consecutiveHits: Int = 0
    private var lastQuad: Quad?

    init(requiredHits: Int = 3) {
        self.requiredHits = max(1, requiredHits)
    }

    mutating func ingest(_ candidate: Quad?) -> PreviewEdgeState {
        guard let candidate else {
            consecutiveHits = 0
            lastQuad = nil
            return .undetected
        }
        if let last = lastQuad, Self.similar(last, candidate) {
            consecutiveHits += 1
        } else {
            consecutiveHits = 1
            lastQuad = candidate
        }
        if consecutiveHits >= requiredHits {
            return .detected(candidate.clamped())
        }
        return .undetected
    }

    private static func similar(_ a: Quad, _ b: Quad, tolerance: Double = 0.08) -> Bool {
        abs(a.tl.x - b.tl.x) < tolerance
            && abs(a.tl.y - b.tl.y) < tolerance
            && abs(a.br.x - b.br.x) < tolerance
            && abs(a.br.y - b.br.y) < tolerance
    }
}
