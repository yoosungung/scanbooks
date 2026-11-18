package net.askwho.scanbooks.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import net.askwho.scanbooks.model.Page
import net.askwho.scanbooks.model.PageMetrics
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.model.ScanSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID

/** Local `filesDir/sessions/{id}/` layout (ARCHITECTURE §6, android/DESIGN.md). */
class SessionStore(filesDir: File) {
    private val root: File = File(filesDir, "sessions").also { it.mkdirs() }

    fun sessionDirectory(id: String): File = File(root, id)

    fun createSession(
        title: String,
        id: String = UUID.randomUUID().toString(),
        createdAt: String = Instant.now().toString(),
    ): ScanSession {
        val session = ScanSession(id = id, createdAt = createdAt, title = title, pageIds = emptyList())
        File(sessionDirectory(id), "pages").mkdirs()
        persist(session)
        return session
    }

    fun loadSession(id: String): ScanSession {
        val text = File(sessionDirectory(id), "session.json").readText()
        return decodeSession(JSONObject(text))
    }

    fun loadPage(sessionId: String, pageId: String): Page {
        val text = File(pageDirectory(sessionId, pageId), "page.json").readText()
        return decodePage(JSONObject(text))
    }

    /** Appends capture: `original.jpg` + mid `working.png` (ARCHITECTURE §11). */
    fun appendPage(
        sessionId: String,
        originalJpeg: ByteArray,
        workingPng: ByteArray? = null,
        quad: Quad? = null,
        pageId: String = UUID.randomUUID().toString(),
    ): Page {
        val session = loadSession(sessionId)
        val index = session.pageIds.size
        val dir = pageDirectory(sessionId, pageId)
        dir.mkdirs()
        val png = workingPng ?: pngFromJpeg(originalJpeg)
        require(isPng(png)) { "working buffer must be PNG" }
        File(dir, "original.jpg").writeBytes(originalJpeg)
        File(dir, "working.png").writeBytes(png)

        val page = Page(
            id = pageId,
            sessionId = sessionId,
            index = index,
            originalUri = "pages/$pageId/original.jpg",
            status = PipelineStage.CROP,
            quad = quad?.clamped(),
        )
        persist(page)
        persist(session.copy(pageIds = session.pageIds + pageId))
        return page
    }

    fun updatePageStatus(sessionId: String, pageId: String, status: PipelineStage) {
        persist(loadPage(sessionId, pageId).copy(status = status))
    }

    /** Persist a full [Page] snapshot (URIs, metrics, status, errorCode). */
    fun savePage(page: Page) {
        persist(page)
    }

    /** Writes a lossless PNG under `pages/{pageId}/` and returns the relative URI. */
    fun writePagePng(sessionId: String, pageId: String, fileName: String, data: ByteArray): String {
        require(isPng(data)) { "artifact must be PNG" }
        val dir = pageDirectory(sessionId, pageId)
        dir.mkdirs()
        File(dir, fileName).writeBytes(data)
        return "pages/$pageId/$fileName"
    }

    fun readPageFile(sessionId: String, pageId: String, fileName: String): ByteArray {
        val file = File(pageDirectory(sessionId, pageId), fileName)
        require(file.exists()) { "missing $fileName for $pageId" }
        return file.readBytes()
    }

    /** M2 stub: copy `working.png` → `cleaned.png` and set `cleanedUri` (ARCHITECTURE §6 PDF source). */
    fun finalizeStubCleaned(sessionId: String, pageId: String) {
        val dir = pageDirectory(sessionId, pageId)
        val working = File(dir, "working.png")
        require(working.exists()) { "working.png missing for $pageId" }
        val cleaned = File(dir, "cleaned.png")
        working.copyTo(cleaned, overwrite = true)
        persist(
            loadPage(sessionId, pageId).copy(
                cleanedUri = "pages/$pageId/cleaned.png",
                status = PipelineStage.DONE,
            ),
        )
    }

    fun updateExportPdfUri(sessionId: String, relativeUri: String?) {
        persist(loadSession(sessionId).copy(exportPdfUri = relativeUri))
    }

    fun updateSessionTitle(id: String, title: String) {
        persist(loadSession(id).copy(title = title))
    }

    /** Reorders `pageIds` (must be a permutation) and rewrites each `Page.index` (UIUX §4.4). */
    fun reorderPages(sessionId: String, pageIds: List<String>) {
        val session = loadSession(sessionId)
        require(pageIds.toSet() == session.pageIds.toSet() && pageIds.size == session.pageIds.size) {
            "invalidPageOrder"
        }
        persist(session.copy(pageIds = pageIds))
        reindexPages(sessionId, pageIds)
    }

    /** Deletes page files and removes id from session order; remaining pages reindexed. */
    fun deletePage(sessionId: String, pageId: String) {
        val session = loadSession(sessionId)
        require(session.pageIds.contains(pageId)) { "pageNotFound:$pageId" }
        val dir = pageDirectory(sessionId, pageId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        val nextIds = session.pageIds.filterNot { it == pageId }
        persist(session.copy(pageIds = nextIds))
        reindexPages(sessionId, nextIds)
    }

    private fun reindexPages(sessionId: String, pageIds: List<String>) {
        pageIds.forEachIndexed { index, pageId ->
            val page = loadPage(sessionId, pageId)
            if (page.index != index) {
                persist(page.copy(index = index))
            }
        }
    }

    fun absoluteFile(sessionId: String, relativeUri: String): File =
        File(sessionDirectory(sessionId), relativeUri)

    companion object {
        fun isPng(data: ByteArray): Boolean {
            if (data.size < 8) return false
            val sig = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            )
            return data.copyOfRange(0, 8).contentEquals(sig)
        }

        fun pngFromJpeg(jpeg: ByteArray): ByteArray {
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                ?: error("invalid JPEG")
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        }
    }

    private fun pageDirectory(sessionId: String, pageId: String): File =
        File(File(sessionDirectory(sessionId), "pages"), pageId)

    private fun persist(session: ScanSession) {
        val obj = JSONObject()
            .put("id", session.id)
            .put("createdAt", session.createdAt)
            .put("title", session.title)
            .put("pageIds", JSONArray(session.pageIds))
            .put("exportPdfUri", session.exportPdfUri ?: JSONObject.NULL)
        File(sessionDirectory(session.id), "session.json").writeText(obj.toString(2))
    }

    private fun persist(page: Page) {
        val obj = JSONObject()
            .put("id", page.id)
            .put("sessionId", page.sessionId)
            .put("index", page.index)
            .put("originalUri", page.originalUri)
            .put("croppedUri", page.croppedUri ?: JSONObject.NULL)
            .put("dewarpedUri", page.dewarpedUri ?: JSONObject.NULL)
            .put("cleanedUri", page.cleanedUri ?: JSONObject.NULL)
            .put("status", page.status.raw)
            .put("errorCode", page.errorCode ?: JSONObject.NULL)
            .put("failedStage", page.failedStage?.raw ?: JSONObject.NULL)
        if (page.quad != null) obj.put("quad", encodeQuad(page.quad)) else obj.put("quad", JSONObject.NULL)
        if (page.metrics != null) obj.put("metrics", encodeMetrics(page.metrics)) else obj.put("metrics", JSONObject.NULL)
        File(pageDirectory(page.sessionId, page.id), "page.json").writeText(obj.toString(2))
    }

    private fun decodeSession(obj: JSONObject): ScanSession {
        val ids = obj.getJSONArray("pageIds")
        return ScanSession(
            id = obj.getString("id"),
            createdAt = obj.getString("createdAt"),
            title = obj.getString("title"),
            pageIds = List(ids.length()) { ids.getString(it) },
            exportPdfUri = obj.optStringOrNull("exportPdfUri"),
        )
    }

    private fun decodePage(obj: JSONObject): Page {
        val status = PipelineStage.entries.first { it.raw == obj.getString("status") }
        return Page(
            id = obj.getString("id"),
            sessionId = obj.getString("sessionId"),
            index = obj.getInt("index"),
            originalUri = obj.getString("originalUri"),
            croppedUri = obj.optStringOrNull("croppedUri"),
            dewarpedUri = obj.optStringOrNull("dewarpedUri"),
            cleanedUri = obj.optStringOrNull("cleanedUri"),
            status = status,
            quad = obj.optJSONObject("quad")?.let { decodeQuad(it) },
            metrics = obj.optJSONObject("metrics")?.let { decodeMetrics(it) },
            errorCode = obj.optStringOrNull("errorCode"),
            failedStage = obj.optStringOrNull("failedStage")?.let { raw ->
                PipelineStage.entries.firstOrNull { it.raw == raw }
            },
        )
    }

    private fun encodeMetrics(m: PageMetrics): JSONObject =
        JSONObject()
            .put("cropMs", m.cropMs ?: JSONObject.NULL)
            .put("geoMs", m.geoMs ?: JSONObject.NULL)
            .put("fingerMs", m.fingerMs ?: JSONObject.NULL)
            .put("illMs", m.illMs ?: JSONObject.NULL)
            .put("workLongEdge", m.workLongEdge ?: JSONObject.NULL)
            .put("geoNetSize", m.geoNetSize ?: JSONObject.NULL)
            .put("illPatchSize", m.illPatchSize ?: JSONObject.NULL)
            .put("modelVersion", m.modelVersion ?: JSONObject.NULL)
            .put("ortEp", m.ortEp ?: JSONObject.NULL)

    private fun decodeMetrics(obj: JSONObject): PageMetrics =
        PageMetrics(
            cropMs = obj.optIntOrNull("cropMs"),
            geoMs = obj.optIntOrNull("geoMs"),
            fingerMs = obj.optIntOrNull("fingerMs"),
            illMs = obj.optIntOrNull("illMs"),
            workLongEdge = obj.optIntOrNull("workLongEdge"),
            geoNetSize = obj.optIntOrNull("geoNetSize"),
            illPatchSize = obj.optIntOrNull("illPatchSize"),
            modelVersion = obj.optStringOrNull("modelVersion"),
            ortEp = obj.optStringOrNull("ortEp"),
        )

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return getInt(key)
    }

    private fun encodeQuad(q: Quad): JSONObject =
        JSONObject()
            .put("tl", encodePoint(q.tl))
            .put("tr", encodePoint(q.tr))
            .put("br", encodePoint(q.br))
            .put("bl", encodePoint(q.bl))

    private fun encodePoint(p: net.askwho.scanbooks.model.NormPoint): JSONObject =
        JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble())

    private fun decodeQuad(obj: JSONObject): Quad =
        Quad(
            tl = decodePoint(obj.getJSONObject("tl")),
            tr = decodePoint(obj.getJSONObject("tr")),
            br = decodePoint(obj.getJSONObject("br")),
            bl = decodePoint(obj.getJSONObject("bl")),
        )

    private fun decodePoint(obj: JSONObject) =
        net.askwho.scanbooks.model.NormPoint(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").ifEmpty { null }
    }
}
