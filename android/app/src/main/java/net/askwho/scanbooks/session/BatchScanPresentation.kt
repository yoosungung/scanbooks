package net.askwho.scanbooks.session

import net.askwho.scanbooks.model.PipelineStage

/** Continuous/batch scan badge copy (docs/UIUX.md §3.2). */
object BatchScanPresentation {
    fun isProcessing(status: PipelineStage): Boolean =
        when (status) {
            PipelineStage.CROP,
            PipelineStage.GEO,
            PipelineStage.FINGER,
            PipelineStage.ILL,
            -> true
            PipelineStage.DONE, PipelineStage.FAILED -> false
        }

    fun processingCount(statuses: List<PipelineStage>): Int =
        statuses.count { isProcessing(it) }

    /** Corner badge; null when nothing in flight. */
    fun badgeLabel(processingCount: Int): String? =
        if (processingCount > 0) "처리 중 ${processingCount}장" else null
}

/** Pure reorder helper for Session grid (docs/UIUX.md §4.4). */
object SessionPageOrder {
    fun move(pageIds: List<String>, from: Int, to: Int): List<String>? {
        if (from !in pageIds.indices || to !in pageIds.indices || from == to) return null
        val next = pageIds.toMutableList()
        val id = next.removeAt(from)
        next.add(to, id)
        return next
    }
}
