package net.askwho.scanbooks

import net.askwho.scanbooks.model.PageMetrics
import net.askwho.scanbooks.ui.process.PageMetricsPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMetricsPresentationTest {
    @Test
    fun contractLineIncludesWorkGeoEp() {
        val metrics =
            PageMetrics(
                workLongEdge = 1024,
                geoNetSize = 288,
                ortEp = "CPUExecutionProvider",
            )
        assertEquals(
            "workLongEdge 1024 · geoNet 288 · EP CPUExecutionProvider",
            PageMetricsPresentation.contractLine(metrics),
        )
    }

    @Test
    fun contractLineOmitsMissingFields() {
        val metrics = PageMetrics(workLongEdge = 768, geoNetSize = null, ortEp = "CPU")
        assertEquals(
            "workLongEdge 768 · EP CPU",
            PageMetricsPresentation.contractLine(metrics),
        )
        assertNull(PageMetricsPresentation.contractLine(PageMetrics()))
    }

    @Test
    fun latencyLineListsCompletedStages() {
        val metrics = PageMetrics(cropMs = 10, geoMs = 1200, fingerMs = null, illMs = 800)
        assertEquals(
            "자르기 10ms · 곡면 펴기 1200ms · 조명 보정 800ms",
            PageMetricsPresentation.latencyLine(metrics),
        )
        assertNull(PageMetricsPresentation.latencyLine(PageMetrics(workLongEdge = 1024)))
    }

    @Test
    fun accessibilitySummaryJoinsContractAndLatency() {
        val metrics =
            PageMetrics(
                cropMs = 5,
                workLongEdge = 64,
                geoNetSize = 288,
                ortEp = "CPUExecutionProvider",
            )
        val summary = PageMetricsPresentation.accessibilitySummary(metrics)
        assertTrue(summary.contains("workLongEdge 64"))
        assertTrue(summary.contains("geoNet 288"))
        assertTrue(summary.contains("EP CPUExecutionProvider"))
        assertTrue(summary.contains("자르기 5ms"))
    }
}
