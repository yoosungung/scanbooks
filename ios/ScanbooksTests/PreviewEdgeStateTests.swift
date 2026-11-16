import XCTest
@testable import Scanbooks

final class PreviewEdgeStateTests: XCTestCase {
    func testUndetectedUsesGrayGuideAndHintCopy() {
        let state = PreviewEdgeState.undetected
        XCTAssertEqual(state.overlayKind, .grayGuide)
        XCTAssertEqual(state.hintCopy, "문서를 화면에 맞춰 주세요")
        XCTAssertNil(state.quad)
    }

    func testDetectedUsesGreenQuadWithoutDewarpPreview() {
        let quad = Quad.axisAligned(inset: 0.15)
        let state = PreviewEdgeState.detected(quad)
        XCTAssertEqual(state.overlayKind, .greenQuad)
        XCTAssertEqual(state.quad, quad)
        XCTAssertFalse(state.showsDewarpPreview)
    }

    func testStabilityRequiresConsecutiveHits() {
        var tracker = EdgeStabilityTracker(requiredHits: 3)
        XCTAssertEqual(tracker.ingest(nil).overlayKind, .grayGuide)
        XCTAssertEqual(tracker.ingest(Quad.axisAligned(inset: 0.2)).overlayKind, .grayGuide)
        XCTAssertEqual(tracker.ingest(Quad.axisAligned(inset: 0.2)).overlayKind, .grayGuide)
        let stable = tracker.ingest(Quad.axisAligned(inset: 0.2))
        XCTAssertEqual(stable.overlayKind, .greenQuad)
    }
}
