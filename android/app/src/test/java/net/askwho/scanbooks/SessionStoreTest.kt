package net.askwho.scanbooks

import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.storage.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class SessionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        root = tmp.newFolder("filesDir")
        store = SessionStore(root)
    }

    @Test
    fun createSessionPersistsSchemaFields() {
        val session = store.createSession("테스트 책")
        assertFalse(session.id.isEmpty())
        assertEquals("테스트 책", session.title)
        assertTrue(session.pageIds.isEmpty())
        assertNull(session.exportPdfUri)

        val loaded = store.loadSession(session.id)
        assertEquals(session.id, loaded.id)
        assertTrue(store.sessionDirectory(session.id).isDirectory)
    }

    @Test
    fun appendPageWritesOriginalJpegAndWorkingPng() {
        val session = store.createSession("캡처")
        val jpeg = tinyJpeg()
        val png = tinyPng()
        val quad = Quad.axisAligned(0.1f)

        val page = store.appendPage(session.id, jpeg, png, quad)

        assertEquals(session.id, page.sessionId)
        assertEquals(0, page.index)
        assertEquals(PipelineStage.CROP, page.status)
        assertEquals("pages/${page.id}/original.jpg", page.originalUri)
        assertNull(page.croppedUri)
        assertEquals(quad, page.quad)

        val pageDir = File(store.sessionDirectory(session.id), "pages/${page.id}")
        assertTrue(File(pageDir, "original.jpg").isFile)
        assertTrue(File(pageDir, "working.png").isFile)

        val reloaded = store.loadSession(session.id)
        assertEquals(listOf(page.id), reloaded.pageIds)
        assertEquals(page.originalUri, store.loadPage(session.id, page.id).originalUri)
    }

    @Test
    fun appendPageIncrementsIndex() {
        val session = store.createSession("연속")
        val p0 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val p1 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        assertEquals(0, p0.index)
        assertEquals(1, p1.index)
        assertEquals(listOf(p0.id, p1.id), store.loadSession(session.id).pageIds)
    }

    @Test
    fun updatePageStatusPersists() {
        val session = store.createSession("상태")
        val page = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        store.updatePageStatus(session.id, page.id, PipelineStage.DONE)
        assertEquals(PipelineStage.DONE, store.loadPage(session.id, page.id).status)
    }

    @Test
    fun appendPageRejectsNonPngWorkingBuffer() {
        val session = store.createSession("거부")
        try {
            store.appendPage(session.id, tinyJpeg(), tinyJpeg(), null)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun reorderPagesPersistsOrderAndIndexes() {
        val session = store.createSession("정렬")
        val p0 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val p1 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val p2 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        store.reorderPages(session.id, listOf(p2.id, p0.id, p1.id))
        assertEquals(listOf(p2.id, p0.id, p1.id), store.loadSession(session.id).pageIds)
        assertEquals(0, store.loadPage(session.id, p2.id).index)
        assertEquals(1, store.loadPage(session.id, p0.id).index)
        assertEquals(2, store.loadPage(session.id, p1.id).index)
    }

    @Test
    fun reorderPagesRejectsNonPermutation() {
        val session = store.createSession("거부")
        val p0 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        try {
            store.reorderPages(session.id, listOf(p0.id, "missing"))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun deletePageRemovesFilesAndReindexes() {
        val session = store.createSession("삭제")
        val p0 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val p1 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val p2 = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val removedDir = File(store.sessionDirectory(session.id), "pages/${p1.id}")
        store.deletePage(session.id, p1.id)
        assertEquals(listOf(p0.id, p2.id), store.loadSession(session.id).pageIds)
        assertFalse(removedDir.exists())
        assertEquals(0, store.loadPage(session.id, p0.id).index)
        assertEquals(1, store.loadPage(session.id, p2.id).index)
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
