import XCTest
@testable import Scanbooks

final class ReleaseChecklistGateTests: XCTestCase {
    func testInternalDistributionPassesWhenAttributionAndNoticesClosed() {
        XCTAssertTrue(ReleaseChecklistGate.internalDistributionAllowed)
        XCTAssertEqual(ReleaseChecklistGate.internalDistributionStatus, "pass")
    }

    func testCommercialStoreBlockedUntilDocTrWrittenPermission() {
        XCTAssertFalse(ReleaseChecklistGate.commercialStoreAllowed)
        XCTAssertEqual(ReleaseChecklistGate.commercialStoreStatus, "blocked")
        XCTAssertTrue(ReleaseChecklistGate.openItemIds.contains("doctr_commercial"))
    }

    func testFingerSegFinalWeightsRemainOpen() {
        XCTAssertTrue(ReleaseChecklistGate.openItemIds.contains("fingerseg_final_weights"))
        let row = ReleaseChecklistGate.items.first { $0.id == "fingerseg_final_weights" }
        XCTAssertEqual(row?.status, "open")
    }

    func testClosedItemsCoverNonCommercialPathAndOssNotices() {
        let closed = ReleaseChecklistGate.items.filter { $0.status == "closed" }.map(\.id)
        XCTAssertTrue(closed.contains("doctr_noncommercial"))
        XCTAssertTrue(closed.contains("ort_opencv_notice"))
        XCTAssertTrue(closed.contains("attribution_ui"))
        XCTAssertTrue(closed.contains("privacy_notice"))
        XCTAssertTrue(closed.contains("ux_oss_reference_only"))
    }

    func testChecklistDocPathIsStable() {
        XCTAssertEqual(ReleaseChecklistGate.documentPath, "docs/RELEASE_CHECKLIST.md")
    }
}
