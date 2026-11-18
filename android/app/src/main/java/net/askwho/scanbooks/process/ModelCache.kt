package net.askwho.scanbooks.process

import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest

/** Resident vs on-demand deploy kind (ARCHITECTURE §1.1 / §5.4; M1 latency → IllTr on-demand). */
enum class ModelDeployKind {
    RESIDENT_PREFERRED,
    ON_DEMAND,
}

object ModelDeployPolicy {
    fun kind(artifactId: String): ModelDeployKind =
        when (artifactId) {
            "illtr" -> ModelDeployKind.ON_DEMAND
            else -> ModelDeployKind.RESIDENT_PREFERRED
        }

    /** Launch/warmup fetch only for resident-preferred artifacts. */
    fun fetchOnLaunch(kind: ModelDeployKind): Boolean = kind == ModelDeployKind.RESIDENT_PREFERRED
}

data class ModelArtifactSpec(
    val id: String,
    val file: String,
    val kind: ModelDeployKind,
    val checksumSha256: String?,
    val downloadURL: String?,
)

data class ModelManifest(
    val version: String,
    val artifacts: List<ModelArtifactSpec>,
) {
    fun artifact(id: String): ModelArtifactSpec? = artifacts.firstOrNull { it.id == id }

    companion object {
        fun parse(json: String): ModelManifest {
            val root = JSONObject(json)
            val version = root.optString("modelVersion", "unknown")
            val models = root.getJSONObject("models")
            val artifacts = mutableListOf<ModelArtifactSpec>()
            val keys = models.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = models.getJSONObject(id)
                val file = obj.getString("file")
                val deployRaw = obj.optString("deploy", "")
                val kind =
                    when (deployRaw) {
                        "residentPreferred" -> ModelDeployKind.RESIDENT_PREFERRED
                        "onDemand" -> ModelDeployKind.ON_DEMAND
                        else -> ModelDeployPolicy.kind(id)
                    }
                val checksum =
                    if (obj.isNull("checksumSha256")) {
                        null
                    } else {
                        obj.optString("checksumSha256").ifEmpty { null }
                    }
                val downloadURL =
                    if (obj.isNull("downloadURL") || !obj.has("downloadURL")) {
                        null
                    } else {
                        obj.optString("downloadURL").ifEmpty { null }
                    }
                artifacts +=
                    ModelArtifactSpec(
                        id = id,
                        file = file,
                        kind = kind,
                        checksumSha256 = checksum,
                        downloadURL = downloadURL,
                    )
            }
            artifacts.sortBy { it.id }
            return ModelManifest(version, artifacts)
        }
    }
}

enum class ModelCacheStatus {
    PRESENT,
    MISSING,
    CHECKSUM_MISMATCH,
}

class ModelCacheException(message: String) : Exception(message)

/**
 * On-device ONNX cache: ensure/checksum + OrtModelCatalog (ARCHITECTURE §1.10).
 * @param fetcher downloads [source] URL into [dest] file (injectable for tests).
 */
class ModelCache(
    private val cacheDirectory: File,
    private val manifest: ModelManifest,
    private val defaultOrtEp: String = PipelineConstants.DEFAULT_ORT_EP_ANDROID,
    private val fetcher: (source: URL, dest: File) -> Unit = Companion::defaultFetcher,
) {
    fun status(artifact: ModelArtifactSpec): ModelCacheStatus {
        val file = File(cacheDirectory, artifact.file)
        if (!file.isFile) return ModelCacheStatus.MISSING
        val expected = artifact.checksumSha256
        if (expected.isNullOrEmpty()) return ModelCacheStatus.PRESENT
        val actual = sha256Hex(file.readBytes())
        return if (actual.equals(expected, ignoreCase = true)) {
            ModelCacheStatus.PRESENT
        } else {
            ModelCacheStatus.CHECKSUM_MISMATCH
        }
    }

    fun ensure(artifactId: String): File {
        val artifact =
            manifest.artifact(artifactId)
                ?: throw ModelCacheException("unknownArtifact:$artifactId")
        val dest = File(cacheDirectory, artifact.file)
        when (status(artifact)) {
            ModelCacheStatus.PRESENT -> return dest
            ModelCacheStatus.CHECKSUM_MISMATCH -> {
                dest.delete()
            }
            ModelCacheStatus.MISSING -> Unit
        }
        val urlString =
            artifact.downloadURL
                ?: throw ModelCacheException("missingDownloadURL:$artifactId")
        cacheDirectory.mkdirs()
        try {
            fetcher(URL(urlString), dest)
        } catch (e: Exception) {
            throw ModelCacheException("fetchFailed:$artifactId")
        }
        if (status(artifact) == ModelCacheStatus.CHECKSUM_MISMATCH) {
            dest.delete()
            throw ModelCacheException("checksumMismatch:$artifactId")
        }
        return dest
    }

    /**
     * Warm resident-preferred weights only (IllTr stays on-demand).
     * Missing file + null `downloadURL` → soft-skip (passthrough until CDN/LFS wired).
     */
    fun ensureResidentPreferred() {
        for (artifact in manifest.artifacts) {
            if (!ModelDeployPolicy.fetchOnLaunch(artifact.kind)) continue
            if (status(artifact) == ModelCacheStatus.PRESENT) continue
            if (artifact.downloadURL.isNullOrEmpty()) continue
            ensure(artifact.id)
        }
    }

    fun catalog(): OrtModelCatalog =
        OrtModelCatalog(
            version = manifest.version,
            geotrFile = manifest.artifact("geotr")?.file ?: "geotr.onnx",
            illtrFile = manifest.artifact("illtr")?.file ?: "illtr.onnx",
            fingersegFile = manifest.artifact("fingerseg")?.file ?: "fingerseg.onnx",
            defaultOrtEp = defaultOrtEp,
            modelsDirectory = cacheDirectory,
        )

    companion object {
        fun defaultFetcher(source: URL, dest: File) {
            source.openStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { b -> "%02x".format(b) }
        }
    }
}
