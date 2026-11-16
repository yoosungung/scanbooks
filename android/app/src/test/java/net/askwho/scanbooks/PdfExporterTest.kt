package net.askwho.scanbooks

import net.askwho.scanbooks.export.PdfExportPages
import net.askwho.scanbooks.export.PdfExporter
import net.askwho.scanbooks.export.PdfExporterError
import net.askwho.scanbooks.export.PdfWriter
import net.askwho.scanbooks.storage.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class PdfExporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        store = SessionStore(tmp.newFolder("filesDir"))
    }

    @Test
    fun cleanedUrisInOrderSkipsPagesWithoutCleanedUri() {
        val session = store.createSession("pdf")
        val withCleaned = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        store.finalizeStubCleaned(session.id, withCleaned.id)
        val without = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)

        val uris = PdfExportPages.cleanedUrisInOrder(store.loadSession(session.id)) { pageId ->
            store.loadPage(session.id, pageId)
        }
        assertEquals(listOf("pages/${withCleaned.id}/cleaned.png"), uris)
        assertTrue(store.loadPage(session.id, without.id).cleanedUri == null)
    }

    @Test
    fun exportWritesViaWriterAndPersistsExportPdfUri() {
        val session = store.createSession("pdf")
        val page = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        store.finalizeStubCleaned(session.id, page.id)

        val writer = PdfWriter { imageFiles, output ->
            assertEquals(1, imageFiles.size)
            assertTrue(imageFiles[0].exists())
            output.writeBytes("%PDF-1.4 fake".toByteArray())
        }
        val relative = PdfExporter(writer).export(session.id, store)
        assertEquals("export.pdf", relative)

        val pdf = store.absoluteFile(session.id, relative)
        assertTrue(pdf.exists())
        assertTrue(pdf.readText().startsWith("%PDF-"))
        assertEquals("export.pdf", store.loadSession(session.id).exportPdfUri)
    }

    @Test
    fun exportThrowsWhenNoCleanedPages() {
        val session = store.createSession("pdf")
        store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        try {
            PdfExporter { _, _ -> }.export(session.id, store)
            fail("expected noCleanedPages")
        } catch (e: PdfExporterError) {
            assertEquals(PdfExporterError.NoCleanedPages, e)
        }
        assertEquals(null, store.loadSession(session.id).exportPdfUri)
    }

    private fun tinyJpeg(): ByteArray =
        Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGcP//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEABj8Cf//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAT8hf//Z",
        )

    private fun tinyPng(): ByteArray =
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
}
