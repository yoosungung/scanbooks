package net.askwho.scanbooks.camera

import androidx.camera.core.ImageProxy

/** Downsampled luma plane → IntArray grayscale for [RoughQuadDetector]. */
object YuvToGray {
    fun fromImageProxy(image: ImageProxy, targetLongEdge: Int = 64): Pair<IntArray, Pair<Int, Int>>? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val step = maxOf(1, minOf(width, height) / targetLongEdge)
        val dw = maxOf(1, width / step)
        val dh = maxOf(1, height / step)
        val out = IntArray(dw * dh)
        val row = ByteArray(rowStride)

        for (y in 0 until dh) {
            val sy = minOf(height - 1, y * step)
            buffer.position(sy * rowStride)
            buffer.get(row, 0, minOf(rowStride, buffer.remaining()))
            for (x in 0 until dw) {
                val sx = minOf(width - 1, x * step)
                val value = row[sx * pixelStride].toInt() and 0xFF
                out[y * dw + x] = value
            }
        }
        // Rewind for CameraX reuse safety (ImageProxy closes externally).
        buffer.rewind()
        return out to (dw to dh)
    }
}
