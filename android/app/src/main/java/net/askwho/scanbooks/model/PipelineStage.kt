package net.askwho.scanbooks.model

/** Mirrors ARCHITECTURE §4.2 `PipelineStage`. Labels: docs/UIUX.md §4.2. */
enum class PipelineStage(val raw: String, val koreanLabel: String) {
    CROP("crop", "자르기"),
    GEO("geo", "곡면 펴기"),
    FINGER("finger", "손가락 지우기"),
    ILL("ill", "조명 보정"),
    DONE("done", "완료"),
    FAILED("failed", "실패");

    companion object {
        val processingOrder: List<PipelineStage> = listOf(CROP, GEO, FINGER, ILL)
    }
}
