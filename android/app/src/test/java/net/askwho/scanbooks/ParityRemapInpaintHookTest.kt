package net.askwho.scanbooks

import net.askwho.scanbooks.cvbridge.CvBridge
import net.askwho.scanbooks.cvbridge.CvBridgeError
import net.askwho.scanbooks.cvbridge.ParityFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** M2 hooks: engine parity fixtures → CvBridge remap (live) + inpaint entry (Telea backend M3). */
class ParityRemapInpaintHookTest {
    @Test
    fun remapMatchesWarpedFixtureWithinTolerance() {
        val fixtures = ParityFixtures.loadFromClasspath()
        val got = CvBridge.remap(fixtures.imageRgb01, fixtures.flowHw2, fixtures.height, fixtures.width)
        val maxAbs = maxAbsDiff(got, fixtures.warpedRgb01)
        val tol = fixtures.tolerances.remapMaxAbs + 1f / 255f
        assertTrue("remap max abs $maxAbs > $tol", maxAbs <= tol)
    }

    @Test
    fun inpaintHookLoadsFixtureAndExposesTeleaEntryPoint() {
        val fixtures = ParityFixtures.loadFromClasspath()
        assertTrue(fixtures.mask.any { it != 0.toByte() })
        var changed = 0
        for (i in fixtures.mask.indices) {
            if (fixtures.mask[i] == 0.toByte()) continue
            val o = i * 3
            if (fixtures.imageRgb[o] != fixtures.inpaintedRgb[o] ||
                fixtures.imageRgb[o + 1] != fixtures.inpaintedRgb[o + 1] ||
                fixtures.imageRgb[o + 2] != fixtures.inpaintedRgb[o + 2]
            ) {
                changed++
            }
        }
        assertTrue(changed > 0)

        val err =
            assertThrows(CvBridgeError.OpenCvInpaintPending::class.java) {
                CvBridge.inpaintTelea(
                    fixtures.imageRgb,
                    fixtures.mask,
                    fixtures.height,
                    fixtures.width,
                    fixtures.inpaintRadius,
                )
            }
        assertTrue(err.message!!.contains("OpenCV"))
        assertEquals(1f / 255f, fixtures.tolerances.inpaintAtol, 1e-7f)
    }

    @Test
    fun parityManifestTolerancesMatchContract() {
        val fixtures = ParityFixtures.loadFromClasspath()
        assertEquals(96, fixtures.height)
        assertEquals(128, fixtures.width)
        assertEquals(3, fixtures.inpaintRadius)
        assertEquals(3f / 255f, fixtures.tolerances.remapMaxAbs, 1e-7f)
        assertEquals(96 * 128 * 2, fixtures.flowHw2.size)
    }

    private fun maxAbsDiff(a: FloatArray, b: FloatArray): Float {
        var m = 0f
        for (i in a.indices) m = max(m, abs(a[i] - b[i]))
        return m
    }
}
