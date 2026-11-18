package net.askwho.scanbooks.ui.settings

/**
 * Pre-store license release gate (ARCHITECTURE §7, ROADMAP §라이선스, docs/RELEASE_CHECKLIST.md).
 * Internal/non-commercial distribution may pass; commercial store stays blocked until DocTr
 * written permission. FingerSeg final weights remain open until selected.
 */
object ReleaseChecklistGate {
    data class Item(
        val id: String,
        val status: String,
        val summary: String,
    )

    const val documentPath = "docs/RELEASE_CHECKLIST.md"

    val items: List<Item> = listOf(
        Item(
            id = "doctr_noncommercial",
            status = "closed",
            summary = "DocTr 비상업(연구·개인·비영리) 전제 + attribution",
        ),
        Item(
            id = "doctr_commercial",
            status = "open",
            summary = "DocTr 상업 서면 허가 전 스토어 금지",
        ),
        Item(
            id = "ort_opencv_notice",
            status = "closed",
            summary = "ORT / OpenCV About·NOTICE 고지 (AttributionPresentation)",
        ),
        Item(
            id = "fingerseg_final_weights",
            status = "open",
            summary = "FingerSeg 최종 가중치 미선정 — 라이선스 행 open",
        ),
        Item(
            id = "attribution_ui",
            status = "closed",
            summary = "Settings attribution UI (PR #22)",
        ),
        Item(
            id = "privacy_notice",
            status = "closed",
            summary = "온디바이스 개인정보 고지 (PR #22)",
        ),
        Item(
            id = "ux_oss_reference_only",
            status = "closed",
            summary = "OSS Document Scanner UX 참고만 — 포크 없음",
        ),
    )

    val openItemIds: List<String>
        get() = items.filter { it.status == "open" }.map { it.id }

    const val internalDistributionStatus = "pass"
    const val commercialStoreStatus = "blocked"

    val internalDistributionAllowed: Boolean
        get() = internalDistributionStatus == "pass" &&
            items.any { it.id == "doctr_noncommercial" && it.status == "closed" } &&
            items.any { it.id == "attribution_ui" && it.status == "closed" } &&
            items.any { it.id == "privacy_notice" && it.status == "closed" } &&
            items.any { it.id == "ort_opencv_notice" && it.status == "closed" }

    val commercialStoreAllowed: Boolean
        get() = commercialStoreStatus == "pass" &&
            !openItemIds.contains("doctr_commercial")
}
