import Foundation

enum CvBridgeError: Error, Equatable {
    case fixture(String)
    case openCvInpaintPending
}

/// M2 CvBridge hooks: remap (live bilinear) + Telea entry (OpenCV backend = M3).
enum CvBridge {
    /// align_corners flow HxWx2 → remapped RGB float [0,1] (ARCHITECTURE §5.1).
    static func remap(imageRGB01: [Float], flowHW2: [Float]) -> [Float] {
        let pixels = imageRGB01.count / 3
        precondition(flowHW2.count == pixels * 2)
        let (h, w) = inferHW(pixelCount: pixels)
        return remap(imageRGB01: imageRGB01, flowHW2: flowHW2, height: h, width: w)
    }

    static func remap(imageRGB01: [Float], flowHW2: [Float], height: Int, width: Int) -> [Float] {
        precondition(imageRGB01.count == height * width * 3)
        precondition(flowHW2.count == height * width * 2)
        var out = [Float](repeating: 0, count: height * width * 3)
        let wMinus = Float(width - 1)
        let hMinus = Float(height - 1)
        for y in 0..<height {
            for x in 0..<width {
                let fi = (y * width + x) * 2
                let srcX = (flowHW2[fi] + 1) * wMinus / 2
                let srcY = (flowHW2[fi + 1] + 1) * hMinus / 2
                let rgb = sampleBilinear(imageRGB01, width: width, height: height, x: srcX, y: srcY)
                let oi = (y * width + x) * 3
                out[oi] = rgb.0
                out[oi + 1] = rgb.1
                out[oi + 2] = rgb.2
            }
        }
        return out
    }

    /// Telea inpaint hook — OpenCV implementation deferred to M3.
    static func inpaintTelea(imageRGB: [UInt8], mask: [UInt8], radius: Int) throws -> [UInt8] {
        _ = imageRGB
        _ = mask
        _ = radius
        throw CvBridgeError.openCvInpaintPending
    }

    private static func inferHW(pixelCount: Int) -> (Int, Int) {
        // Prefer 96×128 (parity fixture); else square-ish factorization.
        if pixelCount == 96 * 128 { return (96, 128) }
        let w = Int(Double(pixelCount).squareRoot().rounded())
        let h = pixelCount / max(w, 1)
        return (h, w)
    }

    /// OpenCV INTER_LINEAR + BORDER_CONSTANT(0): OOB neighbors contribute zero.
    private static func sampleBilinear(
        _ image: [Float],
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ) -> (Float, Float, Float) {
        let x0 = Int(floor(x))
        let y0 = Int(floor(y))
        let x1 = x0 + 1
        let y1 = y0 + 1
        let dx = x - Float(x0)
        let dy = y - Float(y0)
        func pix(_ xx: Int, _ yy: Int) -> (Float, Float, Float) {
            guard yy >= 0, yy < height, xx >= 0, xx < width else { return (0, 0, 0) }
            let i = (yy * width + xx) * 3
            return (image[i], image[i + 1], image[i + 2])
        }
        func lerp(_ a: Float, _ b: Float, _ t: Float) -> Float { a * (1 - t) + b * t }
        let p00 = pix(x0, y0)
        let p10 = pix(x1, y0)
        let p01 = pix(x0, y1)
        let p11 = pix(x1, y1)
        return (
            lerp(lerp(p00.0, p10.0, dx), lerp(p01.0, p11.0, dx), dy),
            lerp(lerp(p00.1, p10.1, dx), lerp(p01.1, p11.1, dx), dy),
            lerp(lerp(p00.2, p10.2, dx), lerp(p01.2, p11.2, dx), dy)
        )
    }
}
