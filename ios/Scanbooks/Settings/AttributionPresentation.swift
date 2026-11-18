import Foundation

/// Settings · first-scan copy for on-device privacy + OSS attribution (ARCHITECTURE §7, docs/UIUX.md §1.7 · §4.7).
enum AttributionPresentation {
    static let privacyShort =
        "스캔 이미지는 이 기기에서만 처리되며 서버로 전송되지 않습니다."

    static let privacyBody =
        "촬영·곡면 보정·손가락 제거·조명 보정·PDF 생성은 온디바이스에서만 수행합니다. "
        + "스캔 이미지를 서버로 업로드하지 않으며, 계정·클라우드 OCR은 MVP 범위 밖입니다."

    /// First-scan tip title (UIUX §1.7).
    static let firstScanTipTitle = privacyShort

    /// Secondary tip line — processing expectation (existing Camera tip).
    static let firstScanTipDetail = "촬영 후 곡면을 펴고 손가락을 지웁니다."

    static let sectionPrivacy = "개인정보"
    static let sectionAttribution = "오픈소스·모델"
    static let sectionAbout = "About"

    static let creditLines: [String] = [
        "DocTr (GeoTr / IllTr) — Feng Hao et al.",
        "ONNX Runtime — Microsoft",
        "OpenCV — Apache License / OpenCV",
    ]

    static let doctrRepoURL = URL(string: "https://github.com/fh2019ustc/DocTr")!
    static let doctrLicenseURL = URL(string: "https://github.com/fh2019ustc/DocTr-Plus/blob/master/LICENSE.md")!

    static let nonCommercialNotice =
        "DocTr 계열은 비상업(연구·개인·비영리) 사용이 기본입니다. 상업 스토어 배포 전 저자 서면 허가가 필요합니다."

    /// Matches `models/manifest.json` `modelVersion` until runtime catalog is wired.
    static let defaultModelVersion =
        "doctr-geotr-fp16-v1+fingerseg-pending+illtr-pending"

    static func aboutModelVersion(_ version: String) -> String {
        "모델: \(version)"
    }
}
