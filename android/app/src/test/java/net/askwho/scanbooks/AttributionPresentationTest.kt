package net.askwho.scanbooks

import net.askwho.scanbooks.ui.settings.AttributionPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionPresentationTest {
    @Test
    fun privacyShortStatesOnDeviceNoUpload() {
        val short = AttributionPresentation.privacyShort
        assertTrue(short.contains("기기"))
        assertTrue(short.contains("서버"))
    }

    @Test
    fun privacyBodyStatesOnDeviceProcessing() {
        val body = AttributionPresentation.privacyBody
        assertTrue(body.contains("온디바이스") || body.contains("이 기기"))
        assertTrue(body.contains("업로드") || body.contains("전송"))
    }

    @Test
    fun firstScanTipUsesPrivacyShort() {
        assertEquals(
            AttributionPresentation.privacyShort,
            AttributionPresentation.firstScanTipTitle,
        )
        assertFalse(AttributionPresentation.firstScanTipDetail.isEmpty())
    }

    @Test
    fun attributionCreditsIncludeDocTrOrtOpenCV() {
        val lines = AttributionPresentation.creditLines
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("DocTr"))
        assertTrue(lines[1].contains("ONNX Runtime"))
        assertTrue(lines[2].contains("OpenCV"))
    }

    @Test
    fun docTrLinksMatchArchitecture() {
        assertEquals(
            "https://github.com/fh2019ustc/DocTr",
            AttributionPresentation.doctrRepoUrl,
        )
        assertEquals(
            "https://github.com/fh2019ustc/DocTr-Plus/blob/master/LICENSE.md",
            AttributionPresentation.doctrLicenseUrl,
        )
    }

    @Test
    fun aboutModelVersionFormatsManifestString() {
        assertEquals(
            "모델: doctr-geotr-fp16-v1",
            AttributionPresentation.aboutModelVersion("doctr-geotr-fp16-v1"),
        )
        assertEquals(
            "doctr-geotr-fp16-v1+fingerseg-pending+illtr-pending",
            AttributionPresentation.defaultModelVersion,
        )
    }

    @Test
    fun nonCommercialNoticePresent() {
        assertTrue(AttributionPresentation.nonCommercialNotice.contains("비상업"))
    }
}
