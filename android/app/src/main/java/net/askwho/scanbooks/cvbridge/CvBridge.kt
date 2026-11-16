package net.askwho.scanbooks.cvbridge

import kotlin.math.floor

sealed class CvBridgeError(message: String) : Exception(message) {
    class Fixture(message: String) : CvBridgeError(message)

    /** Telea/NS require OpenCV (or equal); M2 ships the call site + fixture hook only. */
    class OpenCvInpaintPending :
        CvBridgeError("OpenCV Telea/NS inpaint pending — hook ready for M3")
}

/**
 * Mobile post-process bridge. Remap is pure Kotlin (align_corners); inpaint awaits OpenCV.
 */
object CvBridge {
    /**
     * OpenCV `remap` equivalent: [flowHw2] is HxWx2 grid_sample coords (`align_corners=True`).
     * Interleaved RGB float [0,1] in / out.
     */
    fun remap(
        imageRgb01: FloatArray,
        flowHw2: FloatArray,
        height: Int,
        width: Int,
    ): FloatArray {
        val h = height
        val w = width
        require(imageRgb01.size == h * w * 3)
        require(flowHw2.size == h * w * 2)
        val out = FloatArray(h * w * 3)
        val w1 = (w - 1).toFloat()
        val h1 = (h - 1).toFloat()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val fi = (y * w + x) * 2
                val mapX = (flowHw2[fi] + 1f) * w1 / 2f
                val mapY = (flowHw2[fi + 1] + 1f) * h1 / 2f
                val (r, g, b) = bilinear(imageRgb01, h, w, mapX, mapY)
                val oi = (y * w + x) * 3
                out[oi] = r
                out[oi + 1] = g
                out[oi + 2] = b
            }
        }
        return out
    }

    fun inpaintTelea(
        imageRgb: ByteArray,
        mask: ByteArray,
        height: Int,
        width: Int,
        radius: Int,
    ): ByteArray {
        // Keep signature stable for OpenCV Telea swap (ARCHITECTURE §1.12).
        @Suppress("UNUSED_PARAMETER")
        val unused = Triple(imageRgb, mask, radius to (height to width))
        throw CvBridgeError.OpenCvInpaintPending()
    }

    /** OpenCV INTER_LINEAR + BORDER_CONSTANT(0): OOB neighbors contribute zero. */
    private fun bilinear(
        img: FloatArray,
        h: Int,
        w: Int,
        x: Float,
        y: Float,
    ): FloatArray {
        val x0 = floor(x.toDouble()).toInt()
        val y0 = floor(y.toDouble()).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1
        val dx = x - x0
        val dy = y - y0

        fun pix(yy: Int, xx: Int): FloatArray {
            if (yy !in 0 until h || xx !in 0 until w) {
                return floatArrayOf(0f, 0f, 0f)
            }
            val i = (yy * w + xx) * 3
            return floatArrayOf(img[i], img[i + 1], img[i + 2])
        }

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        val p00 = pix(y0, x0)
        val p10 = pix(y0, x1)
        val p01 = pix(y1, x0)
        val p11 = pix(y1, x1)
        return floatArrayOf(
            lerp(lerp(p00[0], p10[0], dx), lerp(p01[0], p11[0], dx), dy),
            lerp(lerp(p00[1], p10[1], dx), lerp(p01[1], p11[1], dx), dy),
            lerp(lerp(p00[2], p10[2], dx), lerp(p01[2], p11[2], dx), dy),
        )
    }
}
