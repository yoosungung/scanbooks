import Foundation

/// Continuous/batch scan badge copy (docs/UIUX.md §3.2).
enum BatchScanPresentation {
    static func isProcessing(_ status: PipelineStage) -> Bool {
        switch status {
        case .crop, .geo, .finger, .ill: return true
        case .done, .failed: return false
        }
    }

    static func processingCount(statuses: [PipelineStage]) -> Int {
        statuses.reduce(0) { $0 + (isProcessing($1) ? 1 : 0) }
    }

    /// Corner badge; nil when nothing in flight.
    static func badgeLabel(processingCount: Int) -> String? {
        guard processingCount > 0 else { return nil }
        return "처리 중 \(processingCount)장"
    }
}

/// Pure reorder helper for Session grid (docs/UIUX.md §4.4).
enum SessionPageOrder {
    /// Returns new order after moving `from` → `to`, or nil if indices invalid.
    static func move(pageIds: [String], from: Int, to: Int) -> [String]? {
        guard pageIds.indices.contains(from), pageIds.indices.contains(to), from != to else {
            return nil
        }
        var next = pageIds
        let id = next.remove(at: from)
        next.insert(id, at: to)
        return next
    }
}
