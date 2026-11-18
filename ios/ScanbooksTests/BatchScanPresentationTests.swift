import XCTest
@testable import Scanbooks

final class BatchScanPresentationTests: XCTestCase {
    func testProcessingCountIgnoresTerminalStatuses() {
        let statuses: [PipelineStage] = [.crop, .done, .geo, .failed, .ill]
        XCTAssertEqual(BatchScanPresentation.processingCount(statuses: statuses), 3)
    }

    func testBadgeLabelNilWhenIdle() {
        XCTAssertNil(BatchScanPresentation.badgeLabel(processingCount: 0))
    }

    func testBadgeLabelShowsCount() {
        XCTAssertEqual(BatchScanPresentation.badgeLabel(processingCount: 2), "처리 중 2장")
    }

    func testSessionPageOrderMove() {
        XCTAssertEqual(SessionPageOrder.move(pageIds: ["a", "b", "c"], from: 0, to: 2), ["b", "c", "a"])
        XCTAssertEqual(SessionPageOrder.move(pageIds: ["a", "b", "c"], from: 2, to: 0), ["c", "a", "b"])
        XCTAssertNil(SessionPageOrder.move(pageIds: ["a"], from: 0, to: 1))
    }
}
