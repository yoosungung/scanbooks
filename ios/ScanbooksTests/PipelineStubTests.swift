import XCTest
@testable import Scanbooks

final class PipelineStubTests: XCTestCase {
    private var root: URL!

    override func setUp() {
        super.setUp()
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("pipeline-stub-\(UUID().uuidString)", isDirectory: true)
        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: root)
        super.tearDown()
    }

    func testRunPersistsCropGeoFingerIllThenDoneOnPage() throws {
        let store = SessionStore(rootDirectory: root)
        let session = try store.createSession(title: "stub")
        let page = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.tinyJPEG(),
            workingPNG: Self.tinyPNG(),
            quad: nil
        )
        XCTAssertEqual(page.status, .crop)

        var visited: [PipelineStage] = []
        let expectation = expectation(description: "pipeline stub done")
        let stub = PipelineStub(stepDurationNanoseconds: 0)
        stub.run(store: store, sessionId: session.id, pageId: page.id) { stage in
            visited.append(stage)
            if stage == .done { expectation.fulfill() }
        }
        wait(for: [expectation], timeout: 1.0)

        XCTAssertEqual(visited, PipelineStage.processingOrder + [.done])
        XCTAssertEqual(try store.loadPage(sessionId: session.id, pageId: page.id).status, .done)
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
