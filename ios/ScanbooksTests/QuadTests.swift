import XCTest
@testable import Scanbooks

final class QuadTests: XCTestCase {
    func testClampsNormalizedCornersToUnitSquare() {
        let raw = Quad(
            tl: .init(x: -0.2, y: -0.1),
            tr: .init(x: 1.4, y: 0.0),
            br: .init(x: 1.1, y: 1.2),
            bl: .init(x: 0.0, y: 2.0)
        )
        let q = raw.clamped()
        XCTAssertEqual(q.tl.x, 0, accuracy: 1e-9)
        XCTAssertEqual(q.tl.y, 0, accuracy: 1e-9)
        XCTAssertEqual(q.tr.x, 1, accuracy: 1e-9)
        XCTAssertEqual(q.br.y, 1, accuracy: 1e-9)
        XCTAssertEqual(q.bl.y, 1, accuracy: 1e-9)
    }

    func testAxisAlignedFactoryMatchesArchitectureOrigin() {
        let q = Quad.axisAligned(inset: 0.1)
        XCTAssertEqual(q.tl.x, 0.1, accuracy: 1e-9)
        XCTAssertEqual(q.tl.y, 0.1, accuracy: 1e-9)
        XCTAssertEqual(q.br.x, 0.9, accuracy: 1e-9)
        XCTAssertEqual(q.br.y, 0.9, accuracy: 1e-9)
    }
}
