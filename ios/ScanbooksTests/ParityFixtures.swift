import Foundation
@testable import Scanbooks

struct ParityTolerances {
    let remapMaxAbs: Float
    let inpaintAtol: Float
}

struct ParityFixtures {
    let imageHW: (height: Int, width: Int)
    let inpaintRadius: Int
    let tolerances: ParityTolerances
    let imageRGB01: [Float]
    let warpedRGB01: [Float]
    let imageRGB: [UInt8]
    let inpaintedRGB: [UInt8]
    let mask: [UInt8]
    let flowHW2: [Float]

    static func loadFromBundle(bundle: Bundle) throws -> ParityFixtures {
        let root = try resourceDirectory(in: bundle)
        let manifestData = try Data(contentsOf: root.appendingPathComponent("manifest.json"))
        guard let manifest = try JSONSerialization.jsonObject(with: manifestData) as? [String: Any],
              let hw = manifest["image_hw"] as? [Int], hw.count == 2,
              let radius = manifest["inpaint_radius"] as? Int,
              let tol = manifest["tolerances"] as? [String: Any]
        else {
            throw CvBridgeError.fixture("bad manifest.json")
        }
        let h = hw[0], w = hw[1]
        let pixels = h * w
        let imageRGB = try loadU8(root.appendingPathComponent("synth_page_image.rgb"), count: pixels * 3)
        let warpedRGB = try loadU8(root.appendingPathComponent("synth_page_warped.rgb"), count: pixels * 3)
        let inpaintedRGB = try loadU8(root.appendingPathComponent("synth_page_inpainted.rgb"), count: pixels * 3)
        let mask = try loadU8(root.appendingPathComponent("synth_page_raw_mask.gray"), count: pixels)
        let flowHW2 = try loadF32(root.appendingPathComponent("synth_page_flow_hw2.f32"), count: pixels * 2)

        return ParityFixtures(
            imageHW: (h, w),
            inpaintRadius: radius,
            tolerances: ParityTolerances(
                remapMaxAbs: floatVal(tol["remap_max_abs"]),
                inpaintAtol: floatVal(tol["inpaint_atol"])
            ),
            imageRGB01: imageRGB.map { Float($0) / 255.0 },
            warpedRGB01: warpedRGB.map { Float($0) / 255.0 },
            imageRGB: imageRGB,
            inpaintedRGB: inpaintedRGB,
            mask: mask,
            flowHW2: flowHW2
        )
    }

    private static func resourceDirectory(in bundle: Bundle) throws -> URL {
        if let url = bundle.resourceURL?.appendingPathComponent("parity", isDirectory: true),
           FileManager.default.fileExists(atPath: url.appendingPathComponent("manifest.json").path)
        {
            return url
        }
        if let manifest = bundle.url(forResource: "manifest", withExtension: "json") {
            return manifest.deletingLastPathComponent()
        }
        throw CvBridgeError.fixture("parity fixtures not in test bundle")
    }

    private static func floatVal(_ any: Any?) -> Float {
        if let d = any as? Double { return Float(d) }
        if let n = any as? NSNumber { return n.floatValue }
        return 0
    }

    private static func loadU8(_ url: URL, count: Int) throws -> [UInt8] {
        let data = try Data(contentsOf: url)
        guard data.count == count else {
            throw CvBridgeError.fixture("\(url.lastPathComponent) size \(data.count) != \(count)")
        }
        return [UInt8](data)
    }

    private static func loadF32(_ url: URL, count: Int) throws -> [Float] {
        let data = try Data(contentsOf: url)
        guard data.count == count * 4 else {
            throw CvBridgeError.fixture("\(url.lastPathComponent) size \(data.count) != \(count * 4)")
        }
        return data.withUnsafeBytes { buf in
            Array(buf.bindMemory(to: Float.self).prefix(count))
        }
    }
}
