import Foundation

/// Rough luminance blob → axis-aligned Quad. Shared algorithm with Android for M2.
/// Live iOS path prefers Vision; this backs unit tests + Vision-miss fallback.
struct RoughQuadDetector: Sendable {
    var paperThreshold: UInt8 = 160
    var minAreaRatio: Double = 0.08

    func detect(pixels: [UInt8], width: Int, height: Int) -> Quad? {
        guard width > 0, height > 0, pixels.count >= width * height else { return nil }

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var count = 0

        for y in 0..<height {
            let row = y * width
            for x in 0..<width {
                if pixels[row + x] >= paperThreshold {
                    count += 1
                    if x < minX { minX = x }
                    if y < minY { minY = y }
                    if x > maxX { maxX = x }
                    if y > maxY { maxY = y }
                }
            }
        }

        let areaRatio = Double(count) / Double(width * height)
        guard count > 0, areaRatio >= minAreaRatio, maxX > minX, maxY > minY else { return nil }

        let w = Double(width)
        let h = Double(height)
        // Expand slightly so overlay sits outside the paper blob (rough).
        let padX = Double(maxX - minX) * 0.02
        let padY = Double(maxY - minY) * 0.02
        return Quad(
            tl: NormPoint(x: (Double(minX) - padX) / w, y: (Double(minY) - padY) / h),
            tr: NormPoint(x: (Double(maxX) + padX) / w, y: (Double(minY) - padY) / h),
            br: NormPoint(x: (Double(maxX) + padX) / w, y: (Double(maxY) + padY) / h),
            bl: NormPoint(x: (Double(minX) - padX) / w, y: (Double(maxY) + padY) / h)
        ).clamped()
    }
}
