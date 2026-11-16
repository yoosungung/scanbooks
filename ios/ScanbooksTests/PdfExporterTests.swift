import XCTest
@testable import Scanbooks

final class PdfExporterTests: XCTestCase {
    private var root: URL!

    override func setUp() {
        super.setUp()
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("pdf-export-\(UUID().uuidString)", isDirectory: true)
        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: root)
        super.tearDown()
    }

    func testExportWritesPdfFromCleanedUrisAndPersistsExportPdfUri() throws {
        let store = SessionStore(rootDirectory: root)
        let session = try store.createSession(title: "pdf")
        let page = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.tinyJPEG(),
            workingPNG: Self.tinyPNG(),
            quad: nil
        )
        try store.finalizeStubCleaned(sessionId: session.id, pageId: page.id)

        let relative = try PdfExporter().export(sessionId: session.id, store: store)
        XCTAssertEqual(relative, "export.pdf")

        let pdfURL = store.absoluteFile(sessionId: session.id, relativeUri: relative)
        XCTAssertTrue(FileManager.default.fileExists(atPath: pdfURL.path))
        let data = try Data(contentsOf: pdfURL)
        XCTAssertTrue(data.count > 8)
        XCTAssertEqual(String(data: data.prefix(5), encoding: .ascii), "%PDF-")

        let updated = try store.loadSession(id: session.id)
        XCTAssertEqual(updated.exportPdfUri, "export.pdf")
    }

    func testExportSkipsPagesWithoutCleanedUri() throws {
        let store = SessionStore(rootDirectory: root)
        let session = try store.createSession(title: "pdf")
        _ = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.tinyJPEG(),
            workingPNG: Self.tinyPNG(),
            quad: nil
        )

        XCTAssertThrowsError(try PdfExporter().export(sessionId: session.id, store: store)) { error in
            XCTAssertEqual(error as? PdfExporterError, .noCleanedPages)
        }
        XCTAssertNil(try store.loadSession(id: session.id).exportPdfUri)
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
