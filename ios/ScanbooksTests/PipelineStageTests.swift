import XCTest
@testable import Scanbooks

final class PipelineStageTests: XCTestCase {
    func testProcessingOrderMatchesArchitectureContract() {
        XCTAssertEqual(
            PipelineStage.processingOrder.map(\.rawValue),
            ["crop", "geo", "finger", "ill"]
        )
    }

    func testKoreanLabelsMatchUIUX() {
        XCTAssertEqual(PipelineStage.crop.koreanLabel, "자르기")
        XCTAssertEqual(PipelineStage.geo.koreanLabel, "곡면 펴기")
        XCTAssertEqual(PipelineStage.finger.koreanLabel, "손가락 지우기")
        XCTAssertEqual(PipelineStage.ill.koreanLabel, "조명 보정")
        XCTAssertEqual(PipelineStage.done.koreanLabel, "완료")
        XCTAssertEqual(PipelineStage.failed.koreanLabel, "실패")
    }
}
