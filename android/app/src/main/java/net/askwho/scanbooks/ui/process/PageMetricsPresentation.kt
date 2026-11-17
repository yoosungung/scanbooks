package net.askwho.scanbooks.ui.process

import net.askwho.scanbooks.model.PageMetrics

/** Formats [PageMetrics] for Process progress UI (ARCHITECTURE §4.4). */
object PageMetricsPresentation {
    fun contractLine(metrics: PageMetrics): String? {
        val parts = mutableListOf<String>()
        metrics.workLongEdge?.let { parts += "workLongEdge $it" }
        metrics.geoNetSize?.let { parts += "geoNet $it" }
        metrics.ortEp?.takeIf { it.isNotEmpty() }?.let { parts += "EP $it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun latencyLine(metrics: PageMetrics): String? {
        val parts = mutableListOf<String>()
        metrics.cropMs?.let { parts += "자르기 ${it}ms" }
        metrics.geoMs?.let { parts += "곡면 펴기 ${it}ms" }
        metrics.fingerMs?.let { parts += "손가락 지우기 ${it}ms" }
        metrics.illMs?.let { parts += "조명 보정 ${it}ms" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun accessibilitySummary(metrics: PageMetrics): String =
        listOfNotNull(contractLine(metrics), latencyLine(metrics)).joinToString(". ")
}
