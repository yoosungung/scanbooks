import XCTest
@testable import Scanbooks

/// M3 temporary evidence (eric 2026-11-17 option 2): sim + 3 fixture pages → done + PDF.
/// Physical mid-range USB remains a follow-up gate (ROADMAP wording drift).
final class DeviceSmokeTempEvidenceTests: XCTestCase {
    private var root: URL!

    override func setUp() {
        super.setUp()
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("device-smoke-\(UUID().uuidString)", isDirectory: true)
        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: root)
        super.tearDown()
    }

    func testThreeFixturePagesReachDoneAndExportPdf() async throws {
        let store = SessionStore(rootDirectory: root)
        let session = try store.createSession(title: "m3-temp-smoke")
        let runner = PipelineRunner(
            geo: PassthroughStageBackend(ortEp: PipelineConstants.defaultOrtEpIOS),
            finger: PassthroughStageBackend(ortEp: PipelineConstants.defaultOrtEpIOS),
            ill: PassthroughStageBackend(ortEp: PipelineConstants.defaultOrtEpIOS),
            workLongEdge: 64
        )

        var pageIds: [String] = []
        for _ in 0 ..< 3 {
            let page = try store.appendPage(
                sessionId: session.id,
                originalJPEG: Self.tinyJPEG(),
                workingPNG: Self.tinyPNG(),
                quad: Quad.axisAligned(inset: 0.1)
            )
            pageIds.append(page.id)
            let stages = try await runner.runAsync(
                store: store,
                sessionId: session.id,
                pageId: page.id
            ) { _ in }
            XCTAssertEqual(stages.last, .done)
            let done = try store.loadPage(sessionId: session.id, pageId: page.id)
            XCTAssertEqual(done.status, .done)
            XCTAssertNotNil(done.cleanedUri)
            XCTAssertEqual(done.metrics?.ortEp, PipelineConstants.defaultOrtEpIOS)
        }

        let relative = try PdfExporter().export(sessionId: session.id, store: store)
        XCTAssertEqual(relative, "export.pdf")
        let pdfURL = store.absoluteFile(sessionId: session.id, relativeUri: relative)
        let data = try Data(contentsOf: pdfURL)
        XCTAssertEqual(String(data: data.prefix(5), encoding: .ascii), "%PDF-")
        XCTAssertGreaterThan(data.count, 100)
        XCTAssertEqual(try store.loadSession(id: session.id).exportPdfUri, "export.pdf")
        XCTAssertEqual(pageIds.count, 3)
    }

    private static func tinyJPEG() -> Data {
        Data(base64Encoded:
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGcP//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEABj8Cf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAT8hf//Z"
        )!
    }

    private static func tinyPNG() -> Data {
        Data(base64Encoded:
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        )!
    }
}
