package net.askwho.scanbooks

import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.process.FailingStageBackend
import net.askwho.scanbooks.process.PassthroughStageBackend
import net.askwho.scanbooks.process.PipelineConstants
import net.askwho.scanbooks.process.PipelineRunner
import net.askwho.scanbooks.process.StageBackend
import net.askwho.scanbooks.process.StageBackendException
import net.askwho.scanbooks.process.StageOutput
import net.askwho.scanbooks.storage.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class PipelineRunnerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        store = SessionStore(tmp.newFolder("filesDir"))
    }

    @Test
    fun runWritesCropGeoFingerIllArtifactsAndMetrics() {
        val session = store.createSession("m3")
        val page = store.appendPage(session.id, tinyJpeg(), tinyPng(), Quad.axisAligned(0.1f))
        val visited = mutableListOf<PipelineStage>()
        val runner = PipelineRunner(
            geo = PassthroughStageBackend(ortEp = PipelineConstants.DEFAULT_ORT_EP_ANDROID),
            finger = PassthroughStageBackend(ortEp = PipelineConstants.DEFAULT_ORT_EP_ANDROID),
            ill = PassthroughStageBackend(ortEp = PipelineConstants.DEFAULT_ORT_EP_ANDROID),
            workLongEdge = 64,
        )
        runner.runBlockingCompatible(store, session.id, page.id) { visited += it }

        assertEquals(PipelineStage.processingOrder + PipelineStage.DONE, visited)
        val done = store.loadPage(session.id, page.id)
        assertEquals(PipelineStage.DONE, done.status)
        assertEquals("pages/${page.id}/cropped.png", done.croppedUri)
        assertEquals("pages/${page.id}/dewarped.png", done.dewarpedUri)
        assertEquals("pages/${page.id}/cleaned.png", done.cleanedUri)
        assertTrue(File(store.absoluteFile(session.id, "pages/${page.id}/finger.png").absolutePath).isFile)
        assertEquals(64, done.metrics?.workLongEdge)
        assertEquals(288, done.metrics?.geoNetSize)
        assertEquals(PipelineConstants.DEFAULT_ORT_EP_ANDROID, done.metrics?.ortEp)
        assertEquals(PipelineConstants.MODEL_VERSION, done.metrics?.modelVersion)
        assertNotNull(done.metrics?.cropMs)
        assertNotNull(done.metrics?.geoMs)
        assertNotNull(done.metrics?.fingerMs)
        assertNotNull(done.metrics?.illMs)
    }

    @Test
    fun failedStagePersistsErrorAndStops() {
        val session = store.createSession("fail")
        val page = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val visited = mutableListOf<PipelineStage>()
        val runner = PipelineRunner(
            geo = FailingStageBackend("geo_boom"),
            finger = PassthroughStageBackend(),
            ill = PassthroughStageBackend(),
            workLongEdge = 32,
        )
        try {
            runner.runBlockingCompatible(store, session.id, page.id) { visited += it }
            fail("expected StageBackendException")
        } catch (_: StageBackendException) {
        }
        assertTrue(visited.contains(PipelineStage.GEO))
        assertTrue(visited.contains(PipelineStage.FAILED))
        assertFalse(visited.contains(PipelineStage.FINGER))
        val failed = store.loadPage(session.id, page.id)
        assertEquals(PipelineStage.FAILED, failed.status)
        assertEquals(PipelineStage.GEO, failed.failedStage)
        assertEquals("geo_boom", failed.errorCode)
        assertNotNull(failed.croppedUri)
        assertNull(failed.dewarpedUri)
    }

    @Test
    fun retryFromFailedStage() {
        val session = store.createSession("retry")
        val page = store.appendPage(session.id, tinyJpeg(), tinyPng(), null)
        val flaky = FlakyThenPassBackend(1, "geo_once")
        val runner = PipelineRunner(
            geo = flaky,
            finger = PassthroughStageBackend(),
            ill = PassthroughStageBackend(),
            workLongEdge = 32,
        )
        try {
            runner.runBlockingCompatible(store, session.id, page.id) { }
            fail("expected first failure")
        } catch (_: StageBackendException) {
        }
        val visited = mutableListOf<PipelineStage>()
        // ARCHITECTURE §1.3 — default startFrom; runner resumes at persisted failedStage.
        runner.runBlockingCompatible(store, session.id, page.id) { visited += it }
        assertEquals(PipelineStage.GEO, visited.first())
        assertFalse(visited.contains(PipelineStage.CROP))
        assertEquals(PipelineStage.DONE, visited.last())
        assertEquals(PipelineStage.DONE, store.loadPage(session.id, page.id).status)
        assertNull(store.loadPage(session.id, page.id).failedStage)
    }

    @Test
    fun selectedEpMatchesLatencyBench() {
        assertEquals("CPUExecutionProvider", PipelineConstants.DEFAULT_ORT_EP_ANDROID)
        assertEquals(
            "CoreML_MLProgram_NeuralEngine+CPU_fallback",
            PipelineConstants.DEFAULT_ORT_EP_IOS,
        )
    }

    private class FlakyThenPassBackend(
        private var remaining: Int,
        private val code: String,
    ) : StageBackend {
        private val lock = Any()
        override fun run(inputPng: ByteArray): StageOutput {
            synchronized(lock) {
                if (remaining > 0) {
                    remaining -= 1
                    throw StageBackendException(code)
                }
            }
            return PassthroughStageBackend().run(inputPng)
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
