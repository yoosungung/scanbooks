import XCTest
@testable import Scanbooks

final class PageMetricsPresentationTests: XCTestCase {
    func testContractLineIncludesWorkGeoEp() {
        let metrics = PageMetrics(
            workLongEdge: 1024,
            geoNetSize: 288,
            ortEp: "CoreML_MLProgram_NeuralEngine+CPU_fallback"
        )
        let line = PageMetricsPresentation.contractLine(metrics)
        XCTAssertEqual(
            line,
            "workLongEdge 1024 · geoNet 288 · EP CoreML_MLProgram_NeuralEngine+CPU_fallback"
        )
    }

    func testContractLineOmitsMissingFields() {
        let metrics = PageMetrics(workLongEdge: 768, geoNetSize: nil, ortEp: "CPU")
        XCTAssertEqual(
            PageMetricsPresentation.contractLine(metrics),
            "workLongEdge 768 · EP CPU"
        )
        XCTAssertNil(PageMetricsPresentation.contractLine(PageMetrics()))
    }

    func testLatencyLineListsCompletedStages() {
        let metrics = PageMetrics(cropMs: 10, geoMs: 1200, fingerMs: nil, illMs: 800)
        XCTAssertEqual(
            PageMetricsPresentation.latencyLine(metrics),
            "자르기 10ms · 곡면 펴기 1200ms · 조명 보정 800ms"
        )
        XCTAssertNil(PageMetricsPresentation.latencyLine(PageMetrics(workLongEdge: 1024)))
    }

    func testAccessibilitySummaryJoinsContractAndLatency() {
        let metrics = PageMetrics(
            cropMs: 5,
            workLongEdge: 64,
            geoNetSize: 288,
            ortEp: "CPUExecutionProvider"
        )
        let summary = PageMetricsPresentation.accessibilitySummary(metrics)
        XCTAssertTrue(summary.contains("workLongEdge 64"))
        XCTAssertTrue(summary.contains("geoNet 288"))
        XCTAssertTrue(summary.contains("EP CPUExecutionProvider"))
        XCTAssertTrue(summary.contains("자르기 5ms"))
    }
}
