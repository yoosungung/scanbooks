import CoreGraphics
import Foundation

struct NormPoint: Equatable, Sendable {
    var x: Double
    var y: Double
}

/// ARCHITECTURE §4.3 — normalized [0,1], top-left origin.
struct Quad: Equatable, Sendable {
    var tl: NormPoint
    var tr: NormPoint
    var br: NormPoint
    var bl: NormPoint

    func clamped() -> Quad {
        Quad(
            tl: NormPoint(x: tl.x.clamped01(), y: tl.y.clamped01()),
            tr: NormPoint(x: tr.x.clamped01(), y: tr.y.clamped01()),
            br: NormPoint(x: br.x.clamped01(), y: br.y.clamped01()),
            bl: NormPoint(x: bl.x.clamped01(), y: bl.y.clamped01())
        )
    }

    static func axisAligned(inset: Double) -> Quad {
        let i = inset.clamped01()
        let o = (1 - inset).clamped01()
        return Quad(
            tl: NormPoint(x: i, y: i),
            tr: NormPoint(x: o, y: i),
            br: NormPoint(x: o, y: o),
            bl: NormPoint(x: i, y: o)
        )
    }

    func path(in size: CGSize) -> CGPath {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: tl.x * size.width, y: tl.y * size.height))
        path.addLine(to: CGPoint(x: tr.x * size.width, y: tr.y * size.height))
        path.addLine(to: CGPoint(x: br.x * size.width, y: br.y * size.height))
        path.addLine(to: CGPoint(x: bl.x * size.width, y: bl.y * size.height))
        path.closeSubpath()
        return path
    }
}

private extension Double {
    func clamped01() -> Double { min(1, max(0, self)) }
}
