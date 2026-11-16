package net.askwho.scanbooks.cvbridge

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParityTolerances(val remapMaxAbs: Float, val inpaintAtol: Float)

data class ParityFixtures(
    val height: Int,
    val width: Int,
    val inpaintRadius: Int,
    val tolerances: ParityTolerances,
    val imageRgb01: FloatArray,
    val warpedRgb01: FloatArray,
    val imageRgb: ByteArray,
    val inpaintedRgb: ByteArray,
    val mask: ByteArray,
    val flowHw2: FloatArray,
) {
    companion object {
        fun loadFromClasspath(
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader
                ?: ParityFixtures::class.java.classLoader!!,
        ): ParityFixtures {
            val manifest =
                JSONObject(
                    classLoader.getResourceAsStream("parity/manifest.json")?.bufferedReader()?.readText()
                        ?: throw CvBridgeError.Fixture("missing parity/manifest.json"),
                )
            val tol = manifest.getJSONObject("tolerances")
            val hw = manifest.getJSONArray("image_hw")
            val h = hw.getInt(0)
            val w = hw.getInt(1)
            val radius = manifest.getInt("inpaint_radius")
            val pixels = h * w

            // Raw .rgb/.gray/.f32 — JVM unit tests without ImageIO/Bitmap/npy.
            val imageRgb = readBytes(classLoader, "parity/synth_page_image.rgb", pixels * 3)
            val warpedRgb = readBytes(classLoader, "parity/synth_page_warped.rgb", pixels * 3)
            val inpaintedRgb = readBytes(classLoader, "parity/synth_page_inpainted.rgb", pixels * 3)
            val mask = readBytes(classLoader, "parity/synth_page_raw_mask.gray", pixels)
            val flow = loadF32(classLoader, "parity/synth_page_flow_hw2.f32", pixels * 2)

            return ParityFixtures(
                height = h,
                width = w,
                inpaintRadius = radius,
                tolerances =
                    ParityTolerances(
                        remapMaxAbs = tol.getDouble("remap_max_abs").toFloat(),
                        inpaintAtol = tol.getDouble("inpaint_atol").toFloat(),
                    ),
                imageRgb01 = FloatArray(imageRgb.size) { i -> (imageRgb[i].toInt() and 0xff) / 255f },
                warpedRgb01 = FloatArray(warpedRgb.size) { i -> (warpedRgb[i].toInt() and 0xff) / 255f },
                imageRgb = imageRgb,
                inpaintedRgb = inpaintedRgb,
                mask = mask,
                flowHw2 = flow,
            )
        }

        private fun readBytes(cl: ClassLoader, path: String, expected: Int): ByteArray {
            val bytes =
                cl.getResourceAsStream(path)?.readBytes()
                    ?: throw CvBridgeError.Fixture("missing $path")
            require(bytes.size == expected) { "$path size ${bytes.size} != $expected" }
            return bytes
        }

        private fun loadF32(cl: ClassLoader, path: String, count: Int): FloatArray {
            val data = readBytes(cl, path, count * 4)
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val out = FloatArray(count)
            buf.get(out)
            return out
        }
    }
}
