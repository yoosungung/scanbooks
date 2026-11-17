import XCTest
@testable import Scanbooks

final class PipelineRunnerTests: XCTestCase {
    private var root: URL!

    override func setUp() {
        super.setUp()
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("pipeline-runner-\(UUID().uuidString)", isDirectory: true)
        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: root)
        super.tearDown()
    }

    func testRunWritesCroppedDewarpedCleanedAndMetricsWithSelectedEp() async throws {
        let store = SessionStore(rootDirectory: root)
        let session = try store.createSession(title: "m3")
        let page = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.tinyJPEG(),
            workingPNG: Self.tinyPNG(),
            quad: Quad.axisAligned(inset: 0.1)
        )

        var visited: [PipelineStage] = []
        let runner = PipelineRunner(
            geo: PassthroughStageBackend(ortEp: PipelineConstants.defaultOrtEpIOS),
            finger: PassthroughStageBackend(ortEp: PipelineConstants.defaultOrtEpIOS),
            ill: PassthroughStageBackend(ortEp: PipelineConstants.defaultOrtEpIOS),
            workLongEdge: 64
        )
        let result = try await runner.runAsync(store: store, sessionId: session.id, pageId: page.id) {
            visited.append($0)
        }

        XCTAssertEqual(result, PipelineStage.processingOrder + [.done])
        XCTAssertEqual(visited, result)
        let done = try store.loadPage(sessionId: session.id, pageId: page.id)
        XCTAssertEqual(done.status, .done)
        XCTAssertEqual(done.croppedUri, "pages/\(page.id)/cropped.png")
        XCTAssertEqual(done.dewarpedUri, "pages/\(page.id)/dewarped.png")
        XCTAssertEqual(done.cleanedUri, "pages/\(page.id)/cleaned.png")
        XCTAssertEqual(done.metrics?.ortEp, PipelineConstants.defaultOrtEpIOS)
        XCTAssertEqual(done.metrics?.geoNetSize, 288)
        XCTAssertEqual(done.metrics?.workLongEdge, 64)
        XCTAssertEqual(done.metrics?.illPatchSize, PipelineConstants.illPatchSize)
        XCTAssertEqual(done.metrics?.modelVersion, PipelineConstants.modelVersion)
        for name in ["cropped.png", "dewarped.png", "finger.png", "cleaned.png"] {
            let data = try store.readPageFile(sessionId: session.id, pageId: page.id, fileName: name)
            XCTAssertTrue(SessionStore.isPNG(data), name)
        }
    }

    func testFailedStagePersistsErrorAndStops() async throws {
        let store = SessionStore(rootDirectory: root)
        let session = try store.createSession(title: "fail")
        let page = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.tinyJPEG(),
            workingPNG: Self.tinyPNG(),
            quad: nil
        )
        var visited: [PipelineStage] = []
        let runner = PipelineRunner(
            geo: FailingStageBackend(code: "geo_boom"),
            finger: PassthroughStageBackend(),
            ill: PassthroughStageBackend(),
            workLongEdge: 32
        )
        do {
            _ = try await runner.runAsync(store: store, sessionId: session.id, pageId: page.id) {
                visited.append($0)
            }
            XCTFail("expected failure")
        } catch {
            // expected
        }
        XCTAssertTrue(visited.contains(.geo))
        XCTAssertTrue(visited.contains(.failed))
        XCTAssertFalse(visited.contains(.finger))
        let failed = try store.loadPage(sessionId: session.id, pageId: page.id)
        XCTAssertEqual(failed.status, .failed)
        XCTAssertEqual(failed.failedStage, .geo)
        XCTAssertEqual(failed.errorCode, "geo_boom")
        XCTAssertNotNil(failed.croppedUri)
        XCTAssertNil(failed.dewarpedUri)
    }

    func testRetryFromFailedStage() async throws {
        let store = SessionStore(rootDirectory: root)
        let session = try store.createSession(title: "retry")
        let page = try store.appendPage(
            sessionId: session.id,
            originalJPEG: Self.tinyJPEG(),
            workingPNG: Self.tinyPNG(),
            quad: nil
        )
        let flaky = FlakyThenPassBackend(remaining: 1, code: "geo_once")
        let runner = PipelineRunner(
            geo: flaky,
            finger: PassthroughStageBackend(),
            ill: PassthroughStageBackend(),
            workLongEdge: 32
        )
        do {
            _ = try await runner.runAsync(store: store, sessionId: session.id, pageId: page.id) { _ in }
            XCTFail("expected first failure")
        } catch {
            // expected
        }
        var visited: [PipelineStage] = []
        let result = try await runner.runAsync(
            store: store,
            sessionId: session.id,
            pageId: page.id,
            startFrom: .geo
        ) { visited.append($0) }
        XCTAssertEqual(visited.first, .geo)
        XCTAssertEqual(result.last, .done)
        let done = try store.loadPage(sessionId: session.id, pageId: page.id)
        XCTAssertEqual(done.status, .done)
        XCTAssertNil(done.failedStage)
    }

    func testSelectedEpMatchesLatencyBench() {
        XCTAssertEqual(
            PipelineConstants.defaultOrtEpIOS,
            "CoreML_MLProgram_NeuralEngine+CPU_fallback"
        )
        XCTAssertEqual(PipelineConstants.defaultOrtEpAndroid, "CPUExecutionProvider")
    }

    private final class FlakyThenPassBackend: StageBackend, @unchecked Sendable {
        private var remaining: Int
        private let code: String
        private let lock = NSLock()

        init(remaining: Int, code: String) {
            self.remaining = remaining
            self.code = code
        }

        func run(inputPNG: Data) throws -> StageOutput {
            lock.lock()
            defer { lock.unlock() }
            if remaining > 0 {
                remaining -= 1
                throw StageBackendError.failed(code)
            }
            return try PassthroughStageBackend().run(inputPNG: inputPNG)
        }
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
