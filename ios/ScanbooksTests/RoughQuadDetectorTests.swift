import XCTest
@testable import Scanbooks

final class RoughQuadDetectorTests: XCTestCase {
    func testFindsBrightPaperRectangle() {
        let w = 64
        let h = 80
        var pixels = [UInt8](repeating: 20, count: w * h)
        for y in 16..<64 {
            for x in 12..<52 {
                pixels[y * w + x] = 220
            }
        }
        let quad = RoughQuadDetector().detect(pixels: pixels, width: w, height: h)
        XCTAssertNotNil(quad)
        let q = try! XCTUnwrap(quad)
        XCTAssertEqual(q.tl.x, 12.0 / Double(w), accuracy: 0.08)
        XCTAssertEqual(q.tl.y, 16.0 / Double(h), accuracy: 0.08)
        XCTAssertEqual(q.br.x, 52.0 / Double(w), accuracy: 0.08)
        XCTAssertEqual(q.br.y, 64.0 / Double(h), accuracy: 0.08)
    }

    func testReturnsNilOnUniformFrame() {
        let w = 32
        let h = 32
        let pixels = [UInt8](repeating: 40, count: w * h)
        XCTAssertNil(RoughQuadDetector().detect(pixels: pixels, width: w, height: h))
    }
}
