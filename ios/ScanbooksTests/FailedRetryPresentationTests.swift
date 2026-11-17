import XCTest
@testable import Scanbooks

final class FailedRetryPresentationTests: XCTestCase {
    func testErrorLineUsesFailedStageLabelAndCode() {
        XCTAssertEqual(
            FailedRetryPresentation.errorLine(failedStage: .geo, errorCode: "geo_boom"),
            "곡면 펴기 실패 · geo_boom"
        )
    }

    func testErrorLineWithoutCodeUsesStageOnly() {
        XCTAssertEqual(
            FailedRetryPresentation.errorLine(failedStage: .finger, errorCode: nil),
            "손가락 지우기 실패"
        )
    }

    func testErrorLineNilWhenNoFailedStage() {
        XCTAssertNil(FailedRetryPresentation.errorLine(failedStage: nil, errorCode: "x"))
    }

    func testRetryButtonLabel() {
        XCTAssertEqual(FailedRetryPresentation.retryButtonLabel, "이 단계부터 재시도")
    }
}
