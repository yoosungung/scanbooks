import CoreGraphics
import Foundation
import UIKit

enum BBoxCropperError: Error, Equatable {
    case invalidPNG
    case emptyCrop
}

/// ARCHITECTURE §3.1 — quad → axis-aligned BBox + margin, then workLongEdge resize.
enum BBoxCropper {
    static func cropPNG(
        workingPNG: Data,
        quad: Quad?,
        marginRatio: Double = PipelineConstants.cropMarginRatio,
        workLongEdge: Int = PipelineConstants.workLongEdge
    ) throws -> Data {
        guard let image = UIImage(data: workingPNG), let cg = image.cgImage else {
            throw BBoxCropperError.invalidPNG
        }
        let w = cg.width
        let h = cg.height
        let q = (quad ?? Quad.axisAligned(inset: 0)).clamped()
        let corners: [(Double, Double)] = [
            (q.tl.x * Double(w - 1), q.tl.y * Double(h - 1)),
            (q.tr.x * Double(w - 1), q.tr.y * Double(h - 1)),
            (q.br.x * Double(w - 1), q.br.y * Double(h - 1)),
            (q.bl.x * Double(w - 1), q.bl.y * Double(h - 1)),
        ]
        let xs = corners.map(\.0)
        let ys = corners.map(\.1)
        let pad = marginRatio * Double(max(w, h))
        var x0 = Int(floor((xs.min() ?? 0) - pad))
        var y0 = Int(floor((ys.min() ?? 0) - pad))
        var x1 = Int(ceil((xs.max() ?? Double(w)) + pad))
        var y1 = Int(ceil((ys.max() ?? Double(h)) + pad))
        x0 = max(0, x0)
        y0 = max(0, y0)
        x1 = min(w, x1)
        y1 = min(h, y1)
        guard x1 > x0, y1 > y0 else { throw BBoxCropperError.emptyCrop }

        guard let cropped = cg.cropping(to: CGRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0)) else {
            throw BBoxCropperError.emptyCrop
        }
        let croppedUI = UIImage(cgImage: cropped)
        let resized = resizeLongEdge(croppedUI, longEdge: workLongEdge)
        guard let png = resized.pngData() else { throw BBoxCropperError.invalidPNG }
        return png
    }

    private static func resizeLongEdge(_ image: UIImage, longEdge: Int) -> UIImage {
        let size = image.size
        let long = max(size.width, size.height)
        guard long > 0, Int(long.rounded()) != longEdge else { return image }
        let scale = CGFloat(longEdge) / long
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
