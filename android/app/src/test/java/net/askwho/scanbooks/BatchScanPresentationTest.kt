package net.askwho.scanbooks

import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.session.BatchScanPresentation
import net.askwho.scanbooks.session.SessionPageOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchScanPresentationTest {
    @Test
    fun processingCountIgnoresTerminalStatuses() {
        val statuses = listOf(
            PipelineStage.CROP,
            PipelineStage.DONE,
            PipelineStage.GEO,
            PipelineStage.FAILED,
            PipelineStage.ILL,
        )
        assertEquals(3, BatchScanPresentation.processingCount(statuses))
    }

    @Test
    fun badgeLabelNilWhenIdle() {
        assertNull(BatchScanPresentation.badgeLabel(0))
    }

    @Test
    fun badgeLabelShowsCount() {
        assertEquals("처리 중 2장", BatchScanPresentation.badgeLabel(2))
    }

    @Test
    fun sessionPageOrderMove() {
        assertEquals(listOf("b", "c", "a"), SessionPageOrder.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), SessionPageOrder.move(listOf("a", "b", "c"), 2, 0))
        assertNull(SessionPageOrder.move(listOf("a"), 0, 1))
    }
}
