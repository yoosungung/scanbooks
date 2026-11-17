package net.askwho.scanbooks

import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.ui.process.FailedRetryPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailedRetryPresentationTest {
    @Test
    fun errorLineUsesFailedStageLabelAndCode() {
        assertEquals(
            "곡면 펴기 실패 · geo_boom",
            FailedRetryPresentation.errorLine(PipelineStage.GEO, "geo_boom"),
        )
    }

    @Test
    fun errorLineWithoutCodeUsesStageOnly() {
        assertEquals(
            "손가락 지우기 실패",
            FailedRetryPresentation.errorLine(PipelineStage.FINGER, null),
        )
    }

    @Test
    fun errorLineNilWhenNoFailedStage() {
        assertNull(FailedRetryPresentation.errorLine(null, "x"))
    }

    @Test
    fun retryButtonLabel() {
        assertEquals("이 단계부터 재시도", FailedRetryPresentation.retryButtonLabel)
    }
}
