package net.askwho.scanbooks.export

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import net.askwho.scanbooks.model.Page
import net.askwho.scanbooks.model.ScanSession
import net.askwho.scanbooks.storage.SessionStore
import java.io.File
import java.io.FileOutputStream

sealed class PdfExporterError : Exception() {
    data object NoCleanedPages : PdfExporterError()
}

fun interface PdfWriter {
    fun write(imageFiles: List<File>, output: File)
}

object PdfExportPages {
    fun cleanedUrisInOrder(session: ScanSession, loadPage: (String) -> Page): List<String> =
        session.pageIds.mapNotNull { loadPage(it).cleanedUri }
}

/** Default writer: Android [PdfDocument] one page per cleaned PNG/JPEG. */
object AndroidPdfWriter : PdfWriter {
    override fun write(imageFiles: List<File>, output: File) {
        val doc = PdfDocument()
        try {
            imageFiles.forEachIndexed { index, file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: error("decode failed: ${file.name}")
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                doc.finishPage(page)
                bitmap.recycle()
            }
            FileOutputStream(output).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}

/**
 * Builds `export.pdf` from pages with `cleanedUri` (ARCHITECTURE §6, UIUX §3.3).
 * [writer] is injectable for JVM unit tests (no Robolectric PdfDocument).
 */
class PdfExporter(
    private val writer: PdfWriter = AndroidPdfWriter,
) {
    fun export(sessionId: String, store: SessionStore): String {
        val session = store.loadSession(sessionId)
        val uris = PdfExportPages.cleanedUrisInOrder(session) { store.loadPage(sessionId, it) }
        if (uris.isEmpty()) throw PdfExporterError.NoCleanedPages

        val images = uris.map { store.absoluteFile(sessionId, it) }
        val relative = "export.pdf"
        val out = store.absoluteFile(sessionId, relative)
        writer.write(images, out)
        store.updateExportPdfUri(sessionId, relative)
        return relative
    }
}
