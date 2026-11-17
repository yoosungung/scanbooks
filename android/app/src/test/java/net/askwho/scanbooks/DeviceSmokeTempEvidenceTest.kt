package net.askwho.scanbooks

import net.askwho.scanbooks.export.PdfExporter
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.process.PassthroughStageBackend
import net.askwho.scanbooks.process.PipelineConstants
import net.askwho.scanbooks.process.PipelineRunner
import net.askwho.scanbooks.storage.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

/**
 * M3 temporary evidence (eric 2026-11-17 option 2): AVD host-side fixture path —
 * 3 pages → done + PDF. Physical mid-range USB remains a follow-up gate.
 */
class DeviceSmokeTempEvidenceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        store = SessionStore(tmp.newFolder("filesDir"))
    }

    @Test
    fun threeFixturePagesReachDoneAndExportPdf() {
        val session = store.createSession("m3-temp-smoke")
        val runner =
            PipelineRunner(
                geo = PassthroughStageBackend(ortEp = PipelineConstants.DEFAULT_ORT_EP_ANDROID),
                finger = PassthroughStageBackend(ortEp = PipelineConstants.DEFAULT_ORT_EP_ANDROID),
                ill = PassthroughStageBackend(ortEp = PipelineConstants.DEFAULT_ORT_EP_ANDROID),
                workLongEdge = 64,
            )

        val pageIds = mutableListOf<String>()
        repeat(3) {
            val page = store.appendPage(session.id, tinyJpeg(), tinyPng(), Quad.axisAligned(0.1f))
            pageIds += page.id
            val visited = mutableListOf<PipelineStage>()
            runner.runBlockingCompatible(store, session.id, page.id) { visited += it }
            assertEquals(PipelineStage.DONE, visited.last())
            val done = store.loadPage(session.id, page.id)
            assertEquals(PipelineStage.DONE, done.status)
            assertNotNull(done.cleanedUri)
            assertEquals(PipelineConstants.DEFAULT_ORT_EP_ANDROID, done.metrics?.ortEp)
        }

        val writer = PdfWriterCounting(expectedImages = 3)
        val relative = PdfExporter(writer).export(session.id, store)
        assertEquals("export.pdf", relative)
        val pdf = store.absoluteFile(session.id, relative)
        assertTrue(pdf.exists())
        assertTrue(pdf.readBytes().decodeToString().startsWith("%PDF-"))
        assertEquals("export.pdf", store.loadSession(session.id).exportPdfUri)
        assertEquals(3, pageIds.size)
        assertEquals(3, writer.seenImages)
    }

    private class PdfWriterCounting(
        private val expectedImages: Int,
    ) : net.askwho.scanbooks.export.PdfWriter {
        var seenImages: Int = 0

        override fun write(
            imageFiles: List<java.io.File>,
            output: java.io.File,
        ) {
            seenImages = imageFiles.size
            assertEquals(expectedImages, imageFiles.size)
            imageFiles.forEach { assertTrue(it.exists()) }
            output.writeBytes("%PDF-1.4 temp-smoke-3pages\n".toByteArray())
        }
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
