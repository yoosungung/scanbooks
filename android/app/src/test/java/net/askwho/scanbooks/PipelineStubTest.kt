package net.askwho.scanbooks

import kotlinx.coroutines.test.runTest
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.process.PipelineStub
import net.askwho.scanbooks.storage.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

class PipelineStubTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        store = SessionStore(tmp.newFolder("filesDir"))
    }

    @Test
    fun runPersistsCropGeoFingerIllThenDoneOnPage() = runTest {
        val session = store.createSession("stub")
        val page = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        assertEquals(PipelineStage.CROP, page.status)

        val visited = mutableListOf<PipelineStage>()
        val result = PipelineStub(stepDelayMs = 0).run(
            store = store,
            sessionId = session.id,
            pageId = page.id,
        ) { visited += it }

        assertEquals(PipelineStage.processingOrder + PipelineStage.DONE, result)
        assertEquals(result, visited)
        assertEquals(PipelineStage.DONE, store.loadPage(session.id, page.id).status)
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
