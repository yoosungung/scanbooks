package net.askwho.scanbooks

import net.askwho.scanbooks.process.ModelArtifactSpec
import net.askwho.scanbooks.process.ModelCache
import net.askwho.scanbooks.process.ModelCacheStatus
import net.askwho.scanbooks.process.ModelDeployKind
import net.askwho.scanbooks.process.ModelDeployPolicy
import net.askwho.scanbooks.process.ModelManifest
import net.askwho.scanbooks.process.PipelineConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ModelCacheTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("scanbooks-model-cache").toFile()
    }

    @Test
    fun deployPolicyMarksIlltrOnDemand() {
        assertEquals(ModelDeployKind.ON_DEMAND, ModelDeployPolicy.kind("illtr"))
        assertEquals(ModelDeployKind.RESIDENT_PREFERRED, ModelDeployPolicy.kind("geotr"))
        assertEquals(ModelDeployKind.RESIDENT_PREFERRED, ModelDeployPolicy.kind("seg"))
        assertEquals(ModelDeployKind.RESIDENT_PREFERRED, ModelDeployPolicy.kind("fingerseg"))
        assertTrue(ModelDeployPolicy.fetchOnLaunch(ModelDeployKind.RESIDENT_PREFERRED))
        assertFalse(ModelDeployPolicy.fetchOnLaunch(ModelDeployKind.ON_DEMAND))
    }

    @Test
    fun workLongEdgeMitigatedConstant() {
        assertEquals(1024, PipelineConstants.WORK_LONG_EDGE)
        assertEquals(768, PipelineConstants.WORK_LONG_EDGE_MITIGATED)
    }

    @Test
    fun statusPresentAndChecksumMismatch() {
        val bytes = "geo-weights".toByteArray()
        File(tempDir, "geotr.onnx").writeBytes(bytes)
        val good =
            ModelArtifactSpec(
                id = "geotr",
                file = "geotr.onnx",
                kind = ModelDeployKind.RESIDENT_PREFERRED,
                checksumSha256 = ModelCache.sha256Hex(bytes),
                downloadURL = null,
            )
        val bad =
            ModelArtifactSpec(
                id = "geotr",
                file = "geotr.onnx",
                kind = ModelDeployKind.RESIDENT_PREFERRED,
                checksumSha256 = "0".repeat(64),
                downloadURL = null,
            )
        val cache = ModelCache(tempDir, ModelManifest("t", listOf(good)))
        assertEquals(ModelCacheStatus.PRESENT, cache.status(good))
        assertEquals(ModelCacheStatus.CHECKSUM_MISMATCH, cache.status(bad))
        assertEquals(
            ModelCacheStatus.MISSING,
            cache.status(
                ModelArtifactSpec(
                    id = "illtr",
                    file = "illtr.onnx",
                    kind = ModelDeployKind.ON_DEMAND,
                    checksumSha256 = null,
                    downloadURL = null,
                ),
            ),
        )
    }

    @Test
    fun ensureFetchesOnDemandWhenMissing() {
        val payload = "illtr-body".toByteArray()
        val expectedHex = ModelCache.sha256Hex(payload)
        val ill =
            ModelArtifactSpec(
                id = "illtr",
                file = "illtr.onnx",
                kind = ModelDeployKind.ON_DEMAND,
                checksumSha256 = expectedHex,
                downloadURL = "https://example.test/illtr.onnx",
            )
        var fetchCount = 0
        val cache =
            ModelCache(tempDir, ModelManifest("t", listOf(ill))) { _, dest ->
                fetchCount += 1
                dest.writeBytes(payload)
            }
        val file = cache.ensure("illtr")
        assertEquals(1, fetchCount)
        assertTrue(file.isFile)
        cache.ensure("illtr")
        assertEquals(1, fetchCount)
    }

    @Test
    fun ensureResidentSkipsOnDemand() {
        val geoBytes = "geo".toByteArray()
        val geo =
            ModelArtifactSpec(
                id = "geotr",
                file = "geotr.onnx",
                kind = ModelDeployKind.RESIDENT_PREFERRED,
                checksumSha256 = ModelCache.sha256Hex(geoBytes),
                downloadURL = "https://example.test/geotr.onnx",
            )
        val ill =
            ModelArtifactSpec(
                id = "illtr",
                file = "illtr.onnx",
                kind = ModelDeployKind.ON_DEMAND,
                checksumSha256 = null,
                downloadURL = "https://example.test/illtr.onnx",
            )
        val fetched = mutableListOf<String>()
        val cache =
            ModelCache(tempDir, ModelManifest("t", listOf(geo, ill))) { source, dest ->
                val name = source.path.substringAfterLast('/')
                fetched += name
                if (name == "geotr.onnx") {
                    dest.writeBytes(geoBytes)
                } else {
                    dest.writeBytes("ill".toByteArray())
                }
            }
        cache.ensureResidentPreferred()
        assertEquals(listOf("geotr.onnx"), fetched)
        val catalog = cache.catalog()
        assertTrue(catalog.hasGeo)
        assertFalse(catalog.hasIll)
    }

    @Test
    fun ensureResidentSoftSkipsMissingWithoutUrl() {
        val geo =
            ModelArtifactSpec(
                id = "geotr",
                file = "geotr.onnx",
                kind = ModelDeployKind.RESIDENT_PREFERRED,
                checksumSha256 = null,
                downloadURL = null,
            )
        var fetchCount = 0
        val cache =
            ModelCache(tempDir, ModelManifest("t", listOf(geo))) { _, dest ->
                fetchCount += 1
                dest.writeBytes("x".toByteArray())
            }
        cache.ensureResidentPreferred()
        assertEquals(0, fetchCount)
        assertFalse(cache.catalog().hasGeo)
    }

    @Test
    fun parseManifestDeployFields() {
        val json =
            """
            {
              "schema": 1,
              "modelVersion": "v-test",
              "models": {
                "geotr": { "file": "geotr.onnx", "deploy": "residentPreferred", "checksumSha256": null },
                "illtr": { "file": "illtr.onnx", "deploy": "onDemand", "downloadURL": "https://cdn.example/illtr.onnx", "checksumSha256": null }
              }
            }
            """.trimIndent()
        val m = ModelManifest.parse(json)
        assertEquals("v-test", m.version)
        assertEquals(ModelDeployKind.RESIDENT_PREFERRED, m.artifact("geotr")?.kind)
        assertEquals(ModelDeployKind.ON_DEMAND, m.artifact("illtr")?.kind)
        assertEquals("https://cdn.example/illtr.onnx", m.artifact("illtr")?.downloadURL)
    }
}
