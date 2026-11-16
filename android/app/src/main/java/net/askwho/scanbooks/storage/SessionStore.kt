package net.askwho.scanbooks.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import net.askwho.scanbooks.model.Page
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

    fun updateSessionTitle(id: String, title: String) {
        persist(loadSession(id).copy(title = title))
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
        if (page.quad != null) obj.put("quad", encodeQuad(page.quad)) else obj.put("quad", JSONObject.NULL)
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
            errorCode = obj.optStringOrNull("errorCode"),
        )
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
