import XCTest
@testable import Scanbooks

final class AttributionPresentationTests: XCTestCase {
    func testPrivacyShortStatesOnDeviceNoUpload() {
        let short = AttributionPresentation.privacyShort
        XCTAssertTrue(short.contains("기기"))
        XCTAssertTrue(short.contains("서버"))
    }

    func testPrivacyBodyStatesOnDeviceProcessing() {
        let body = AttributionPresentation.privacyBody
        XCTAssertTrue(body.contains("온디바이스") || body.contains("이 기기"))
        XCTAssertTrue(body.contains("업로드") || body.contains("전송"))
    }

    func testFirstScanTipUsesPrivacyShort() {
        XCTAssertEqual(
            AttributionPresentation.firstScanTipTitle,
            AttributionPresentation.privacyShort
        )
        XCTAssertFalse(AttributionPresentation.firstScanTipDetail.isEmpty)
    }

    func testAttributionCreditsIncludeDocTrOrtOpenCV() {
        let lines = AttributionPresentation.creditLines
        XCTAssertEqual(lines.count, 3)
        XCTAssertTrue(lines[0].contains("DocTr"))
        XCTAssertTrue(lines[1].contains("ONNX Runtime"))
        XCTAssertTrue(lines[2].contains("OpenCV"))
    }

    func testDocTrLinksMatchArchitecture() {
        XCTAssertEqual(
            AttributionPresentation.doctrRepoURL.absoluteString,
            "https://github.com/fh2019ustc/DocTr"
        )
        XCTAssertEqual(
            AttributionPresentation.doctrLicenseURL.absoluteString,
            "https://github.com/fh2019ustc/DocTr-Plus/blob/master/LICENSE.md"
        )
    }

    func testAboutModelVersionFormatsManifestString() {
        XCTAssertEqual(
            AttributionPresentation.aboutModelVersion("doctr-geotr-fp16-v1"),
            "모델: doctr-geotr-fp16-v1"
        )
        XCTAssertEqual(
            AttributionPresentation.defaultModelVersion,
            "doctr-geotr-fp16-v1+fingerseg-pending+illtr-pending"
        )
    }

    func testNonCommercialNoticePresent() {
        XCTAssertTrue(AttributionPresentation.nonCommercialNotice.contains("비상업"))
    }
}
