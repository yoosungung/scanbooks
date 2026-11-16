import XCTest
@testable import Scanbooks

final class StubProcessRunnerTests: XCTestCase {
    private var root: URL!
    private var store: SessionStore!

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("scanbooks-stub-pipeline-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        store = SessionStore(rootDirectory: root)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: root)
        root = nil
        store = nil
    }

    func testStubRunPersistsCropGeoFingerIllDoneOnPage() throws {
        let session = try store.createSession(title: "stub")
        let page = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.minimalJPEG(),
            workingPNG: Self.minimalPNG(),
            quad: nil
        )
        XCTAssertEqual(page.status, .crop)

        var visited: [PipelineStage] = []
        let expectation = expectation(description: "stub persists done")
        let runner = StubProcessRunner(stepDurationNanoseconds: 0)
        runner.run(store: store, sessionId: session.id, pageId: page.id) { stage in
            visited.append(stage)
            if stage == .done {
                expectation.fulfill()
            }
        }
        wait(for: [expectation], timeout: 1.0)

        XCTAssertEqual(visited, PipelineStage.processingOrder + [.done])
        let loaded = try store.loadPage(sessionId: session.id, pageId: page.id)
        XCTAssertEqual(loaded.status, .done)
        XCTAssertEqual(runner.visited, PipelineStage.processingOrder + [.done])
    }

    private static func minimalJPEG() -> Data {
        Data(base64Encoded: "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGcP//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEABj8Cf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAT8hf//Z")!
    }

    private static func minimalPNG() -> Data {
        Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")!
    }
}
