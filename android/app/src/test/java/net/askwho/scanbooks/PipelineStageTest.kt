package net.askwho.scanbooks

import kotlinx.coroutines.test.runTest
import net.askwho.scanbooks.model.AppRoute
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.process.StubProcessRunner
import org.junit.Assert.assertEquals
import org.junit.Test

class PipelineStageTest {
    @Test
    fun processingOrderMatchesArchitectureContract() {
        assertEquals(
            listOf("crop", "geo", "finger", "ill"),
            PipelineStage.processingOrder.map { it.raw },
        )
    }

    @Test
    fun koreanLabelsMatchUIUX() {
        assertEquals("자르기", PipelineStage.CROP.koreanLabel)
        assertEquals("곡면 펴기", PipelineStage.GEO.koreanLabel)
        assertEquals("손가락 지우기", PipelineStage.FINGER.koreanLabel)
        assertEquals("조명 보정", PipelineStage.ILL.koreanLabel)
        assertEquals("완료", PipelineStage.DONE.koreanLabel)
        assertEquals("실패", PipelineStage.FAILED.koreanLabel)
    }

    @Test
    fun m2ShellRoutesAreDistinct() {
        assertEquals(3, AppRoute.entries.size)
    }

    @Test
    fun stubProcessAdvancesThroughProcessingOrderThenDone() = runTest {
        val visited = mutableListOf<PipelineStage>()
        val result = StubProcessRunner(stepDelayMs = 0).run { visited += it }
        assertEquals(PipelineStage.processingOrder + PipelineStage.DONE, result)
        assertEquals(result, visited)
    }
}
