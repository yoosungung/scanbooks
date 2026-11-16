package net.askwho.scanbooks.model

/** ARCHITECTURE §4.1 — local-only session. */
data class ScanSession(
    val id: String,
    val createdAt: String,
    val title: String,
    val pageIds: List<String>,
    val exportPdfUri: String? = null,
)
