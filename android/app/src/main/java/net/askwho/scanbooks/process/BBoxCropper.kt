package net.askwho.scanbooks.process

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.storage.SessionStore
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** ARCHITECTURE §3.1 — quad → BBox + margin, then workLongEdge resize. */
object BBoxCropper {
    fun cropPng(
        workingPng: ByteArray,
        quad: Quad?,
        marginRatio: Double = PipelineConstants.CROP_MARGIN_RATIO,
        workLongEdge: Int = PipelineConstants.WORK_LONG_EDGE,
    ): ByteArray {
        // JVM unit tests: BitmapFactory is unmocked (throws) or returns null → identity crop.
        val bitmap = try {
            BitmapFactory.decodeByteArray(workingPng, 0, workingPng.size)
        } catch (_: Throwable) {
            null
        } ?: run {
            require(SessionStore.isPng(workingPng)) { "invalidPNG" }
            return workingPng
        }
        val w = bitmap.width
        val h = bitmap.height
        val q = (quad ?: Quad.axisAligned(0f)).clamped()
        val corners = listOf(
            q.tl.x * (w - 1) to q.tl.y * (h - 1),
            q.tr.x * (w - 1) to q.tr.y * (h - 1),
            q.br.x * (w - 1) to q.br.y * (h - 1),
            q.bl.x * (w - 1) to q.bl.y * (h - 1),
        )
        val pad = marginRatio * max(w, h)
        var x0 = floor((corners.minOf { it.first }) - pad).toInt()
        var y0 = floor((corners.minOf { it.second }) - pad).toInt()
        var x1 = ceil((corners.maxOf { it.first }) + pad).toInt()
        var y1 = ceil((corners.maxOf { it.second }) + pad).toInt()
        x0 = x0.coerceAtLeast(0)
        y0 = y0.coerceAtLeast(0)
        x1 = x1.coerceAtMost(w)
        y1 = y1.coerceAtMost(h)
        require(x1 > x0 && y1 > y0) { "emptyCrop" }
        val cropped = Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0)
        val resized = resizeLongEdge(cropped, workLongEdge)
        val out = ByteArrayOutputStream()
        check(resized.compress(Bitmap.CompressFormat.PNG, 100, out)) { "pngEncode" }
        val bytes = out.toByteArray()
        require(SessionStore.isPng(bytes)) { "notPNG" }
        return bytes
    }

    private fun resizeLongEdge(image: Bitmap, longEdge: Int): Bitmap {
        val long = max(image.width, image.height)
        if (long <= 0 || long == longEdge) return image
        val scale = longEdge.toFloat() / long.toFloat()
        val nw = (image.width * scale).roundToInt().coerceAtLeast(1)
        val nh = (image.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(image, nw, nh, true)
    }
}
