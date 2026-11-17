import Foundation

/// Formats `PageMetrics` for Process progress UI (ARCHITECTURE §4.4).
enum PageMetricsPresentation {
    static func contractLine(_ metrics: PageMetrics) -> String? {
        var parts: [String] = []
        if let work = metrics.workLongEdge {
            parts.append("workLongEdge \(work)")
        }
        if let geo = metrics.geoNetSize {
            parts.append("geoNet \(geo)")
        }
        if let ep = metrics.ortEp, !ep.isEmpty {
            parts.append("EP \(ep)")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    static func latencyLine(_ metrics: PageMetrics) -> String? {
        var parts: [String] = []
        if let ms = metrics.cropMs {
            parts.append("자르기 \(ms)ms")
        }
        if let ms = metrics.geoMs {
            parts.append("곡면 펴기 \(ms)ms")
        }
        if let ms = metrics.fingerMs {
            parts.append("손가락 지우기 \(ms)ms")
        }
        if let ms = metrics.illMs {
            parts.append("조명 보정 \(ms)ms")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    static func accessibilitySummary(_ metrics: PageMetrics) -> String {
        [contractLine(metrics), latencyLine(metrics)]
            .compactMap { $0 }
            .joined(separator: ". ")
    }
}
