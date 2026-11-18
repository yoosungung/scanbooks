import XCTest
@testable import Scanbooks

final class AppRouteTests: XCTestCase {
    func testShellRoutesAreDistinct() {
        let routes: [AppRoute] = [.camera, .process, .session, .settings]
        XCTAssertEqual(Set(routes.map { String(describing: $0) }).count, 4)
    }

    func testStubProcessAdvancesThroughProcessingOrderThenDone() {
        var visited: [PipelineStage] = []
        let runner = StubProcessRunner(stepDurationNanoseconds: 0)
        let expectation = expectation(description: "stub finishes")
        runner.run { current in
            visited.append(current)
            if current == .done {
                expectation.fulfill()
            }
        }
        wait(for: [expectation], timeout: 1.0)
        XCTAssertEqual(visited, PipelineStage.processingOrder + [.done])
    }
}
