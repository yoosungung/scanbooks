import XCTest
@testable import Scanbooks

final class SessionStoreTests: XCTestCase {
    private var root: URL!
    private var store: SessionStore!

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("scanbooks-store-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        store = SessionStore(rootDirectory: root)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: root)
        root = nil
        store = nil
    }

    func testCreateSessionPersistsArchitectureFields() throws {
        let session = try store.createSession(title: "테스트 스캔")
        XCTAssertFalse(session.id.isEmpty)
        XCTAssertEqual(session.title, "테스트 스캔")
        XCTAssertTrue(session.pageIds.isEmpty)
        XCTAssertNil(session.exportPdfUri)

        let loaded = try store.loadSession(id: session.id)
        XCTAssertEqual(loaded.id, session.id)
        XCTAssertTrue(FileManager.default.fileExists(atPath: store.sessionDirectory(id: session.id).path))
    }

    func testAppendPageWritesOriginalJpegAndWorkingPng() throws {
        let session = try store.createSession(title: "책")
        let jpeg = Self.minimalJPEG()
        let png = Self.minimalPNG()
        let quad = Quad.axisAligned(inset: 0.1)
        let page = try store.appendPage(
            sessionId: session.id,
            originalJPEG: jpeg,
            workingPNG: png,
            quad: quad
        )

        XCTAssertEqual(page.sessionId, session.id)
        XCTAssertEqual(page.index, 0)
        XCTAssertEqual(page.status, .crop)
        XCTAssertEqual(page.quad, quad)
        XCTAssertEqual(page.originalUri, "pages/\(page.id)/original.jpg")
        XCTAssertNil(page.croppedUri)

        let pageDir = store.sessionDirectory(id: session.id)
            .appendingPathComponent("pages/\(page.id)", isDirectory: true)
        XCTAssertTrue(FileManager.default.fileExists(atPath: pageDir.appendingPathComponent("original.jpg").path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: pageDir.appendingPathComponent("working.png").path))
        let diskPNG = try Data(contentsOf: pageDir.appendingPathComponent("working.png"))
        XCTAssertTrue(SessionStore.isPNG(diskPNG))

        let updatedSession = try store.loadSession(id: session.id)
        XCTAssertEqual(updatedSession.pageIds, [page.id])
    }

    func testAppendPageRejectsNonPngWorkingBuffer() throws {
        let session = try store.createSession(title: "책")
        XCTAssertThrowsError(
            try store.appendPage(
                sessionId: session.id,
                originalJPEG: Self.minimalJPEG(),
                workingPNG: Self.minimalJPEG(),
                quad: nil
            )
        )
    }

    func testSecondPageIncrementsIndex() throws {
        let session = try store.createSession(title: "책")
        let p0 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        let p1 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        XCTAssertEqual(p0.index, 0)
        XCTAssertEqual(p1.index, 1)
        XCTAssertEqual(try store.loadSession(id: session.id).pageIds, [p0.id, p1.id])
    }

    func testReorderPagesPersistsOrderAndIndexes() throws {
        let session = try store.createSession(title: "정렬")
        let p0 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        let p1 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        let p2 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        try store.reorderPages(sessionId: session.id, pageIds: [p2.id, p0.id, p1.id])
        XCTAssertEqual(try store.loadSession(id: session.id).pageIds, [p2.id, p0.id, p1.id])
        XCTAssertEqual(try store.loadPage(sessionId: session.id, pageId: p2.id).index, 0)
        XCTAssertEqual(try store.loadPage(sessionId: session.id, pageId: p0.id).index, 1)
        XCTAssertEqual(try store.loadPage(sessionId: session.id, pageId: p1.id).index, 2)
    }

    func testReorderPagesRejectsNonPermutation() throws {
        let session = try store.createSession(title: "거부")
        let p0 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        XCTAssertThrowsError(try store.reorderPages(sessionId: session.id, pageIds: [p0.id, "missing"])) { error in
            XCTAssertEqual(error as? SessionStoreError, .invalidPageOrder)
        }
    }

    func testDeletePageRemovesFilesAndReindexes() throws {
        let session = try store.createSession(title: "삭제")
        let p0 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        let p1 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        let p2 = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        let removedDir = store.pageDirPublic(sessionId: session.id, pageId: p1.id)
        try store.deletePage(sessionId: session.id, pageId: p1.id)
        XCTAssertEqual(try store.loadSession(id: session.id).pageIds, [p0.id, p2.id])
        XCTAssertFalse(FileManager.default.fileExists(atPath: removedDir.path))
        XCTAssertEqual(try store.loadPage(sessionId: session.id, pageId: p0.id).index, 0)
        XCTAssertEqual(try store.loadPage(sessionId: session.id, pageId: p2.id).index, 1)
    }

    private static func minimalJPEG() -> Data {
        Data(base64Encoded: "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGcP//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEABj8Cf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAT8hf//Z")!
    }

    private static func minimalPNG() -> Data {
        Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")!
    }
}
