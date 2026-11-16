package net.askwho.scanbooks.camera

import net.askwho.scanbooks.model.NormPoint
import net.askwho.scanbooks.model.Quad

/**
 * Rough luminance blob → axis-aligned Quad (M2 realtime border).
 * [pixels] are grayscale 0..255, row-major.
 */
class RoughQuadDetector(
    private val paperThreshold: Int = 160,
    private val minAreaRatio: Float = 0.08f,
) {
    fun detect(pixels: IntArray, width: Int, height: Int): Quad? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var count = 0

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (pixels[row + x] >= paperThreshold) {
                    count++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        val areaRatio = count.toFloat() / (width * height).toFloat()
        if (count == 0 || areaRatio < minAreaRatio || maxX <= minX || maxY <= minY) return null

        val w = width.toFloat()
        val h = height.toFloat()
        val padX = (maxX - minX) * 0.02f
        val padY = (maxY - minY) * 0.02f
        return Quad(
            tl = NormPoint((minX - padX) / w, (minY - padY) / h),
            tr = NormPoint((maxX + padX) / w, (minY - padY) / h),
            br = NormPoint((maxX + padX) / w, (maxY + padY) / h),
            bl = NormPoint((minX - padX) / w, (maxY + padY) / h),
        ).clamped()
    }
}
