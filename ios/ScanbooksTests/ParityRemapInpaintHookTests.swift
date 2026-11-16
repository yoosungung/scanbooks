import XCTest
@testable import Scanbooks

/// M2 hooks: engine parity fixtures → CvBridge remap (live) + inpaint entry (Telea backend M3).
final class ParityRemapInpaintHookTests: XCTestCase {
    func testRemapMatchesWarpedFixtureWithinTolerance() throws {
        let fixtures = try ParityFixtures.loadFromBundle(bundle: Bundle(for: Self.self))
        let got = CvBridge.remap(
            imageRGB01: fixtures.imageRGB01,
            flowHW2: fixtures.flowHW2,
            height: fixtures.imageHW.height,
            width: fixtures.imageHW.width
        )
        let maxAbs = Self.maxAbsDiff(got, fixtures.warpedRGB01)
        let tol = fixtures.tolerances.remapMaxAbs + 1.0 / 255.0
        XCTAssertLessThanOrEqual(maxAbs, tol, "remap max abs \(maxAbs) > \(tol)")
    }

    func testInpaintHookLoadsFixtureAndExposesTeleaEntryPoint() throws {
        let fixtures = try ParityFixtures.loadFromBundle(bundle: Bundle(for: Self.self))
        XCTAssertGreaterThan(fixtures.mask.filter { $0 > 0 }.count, 0)
        // Masked pixels in the Telea reference must differ from the source (hole filled).
        var changed = 0
        for i in fixtures.mask.indices where fixtures.mask[i] > 0 {
            let o = i * 3
            if fixtures.imageRGB[o] != fixtures.inpaintedRGB[o]
                || fixtures.imageRGB[o + 1] != fixtures.inpaintedRGB[o + 1]
                || fixtures.imageRGB[o + 2] != fixtures.inpaintedRGB[o + 2]
            {
                changed += 1
            }
        }
        XCTAssertGreaterThan(changed, 0)

        // Hook surface: same signature OpenCV Telea will fill in M3 (ARCHITECTURE §1.12).
        XCTAssertThrowsError(
            try CvBridge.inpaintTelea(
                imageRGB: fixtures.imageRGB,
                mask: fixtures.mask,
                radius: fixtures.inpaintRadius
            )
        ) { error in
            guard case CvBridgeError.openCvInpaintPending = error else {
                return XCTFail("expected openCvInpaintPending, got \(error)")
            }
        }
        XCTAssertEqual(fixtures.tolerances.inpaintAtol, 1.0 / 255.0, accuracy: 1e-6)
    }

    func testParityManifestTolerancesMatchContract() throws {
        let fixtures = try ParityFixtures.loadFromBundle(bundle: Bundle(for: Self.self))
        XCTAssertEqual(fixtures.imageHW.height, 96)
        XCTAssertEqual(fixtures.imageHW.width, 128)
        XCTAssertEqual(fixtures.inpaintRadius, 3)
        XCTAssertEqual(fixtures.tolerances.remapMaxAbs, 3.0 / 255.0, accuracy: 1e-6)
        XCTAssertEqual(fixtures.flowHW2.count, 96 * 128 * 2)
    }

    private static func maxAbsDiff(_ a: [Float], _ b: [Float]) -> Float {
        zip(a, b).reduce(Float(0)) { partial, pair in
            max(partial, abs(pair.0 - pair.1))
        }
    }
}
