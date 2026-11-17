package net.askwho.scanbooks.process

import java.io.File

/** `models/` manifest + on-device cache (ARCHITECTURE §1.10 — binaries not in git). */
data class OrtModelCatalog(
    val version: String = PipelineConstants.MODEL_VERSION,
    val geotrFile: String = "geotr.onnx",
    val illtrFile: String = "illtr.onnx",
    val fingersegFile: String = "fingerseg.onnx",
    val defaultOrtEp: String = PipelineConstants.DEFAULT_ORT_EP_ANDROID,
    val modelsDirectory: File? = null,
) {
    fun file(name: String): File? {
        val dir = modelsDirectory ?: return null
        val f = File(dir, name)
        return f.takeIf { it.isFile }
    }

    val hasGeo: Boolean get() = file(geotrFile) != null
    val hasIll: Boolean get() = file(illtrFile) != null
    val hasFinger: Boolean get() = file(fingersegFile) != null

    companion object {
        val bundled = OrtModelCatalog()
    }
}

object OrtBackendFactory {
    fun makeRunner(catalog: OrtModelCatalog = OrtModelCatalog.bundled): PipelineRunner {
        val ep = catalog.defaultOrtEp
        val ver = catalog.version
        val geo: StageBackend =
            if (catalog.hasGeo) {
                OrtGeoStageBackend(catalog.file(catalog.geotrFile)!!, ep, ver)
            } else {
                PassthroughStageBackend(ep, ver)
            }
        val finger: StageBackend =
            if (catalog.hasFinger) {
                OrtFingerStageBackend(catalog.file(catalog.fingersegFile)!!, ep, ver)
            } else {
                PassthroughStageBackend(ep, ver)
            }
        val ill: StageBackend =
            if (catalog.hasIll) {
                OrtIllStageBackend(catalog.file(catalog.illtrFile)!!, ep, ver)
            } else {
                PassthroughStageBackend(ep, ver)
            }
        return PipelineRunner(geo = geo, finger = finger, ill = ill)
    }
}

class OrtGeoStageBackend(
    private val modelFile: File,
    private val ortEp: String,
    private val modelVersion: String,
) : StageBackend {
    override fun run(inputPng: ByteArray): StageOutput {
        throw StageBackendException("ort_geo_not_linked")
    }
}

class OrtFingerStageBackend(
    private val modelFile: File,
    private val ortEp: String,
    private val modelVersion: String,
) : StageBackend {
    override fun run(inputPng: ByteArray): StageOutput {
        throw StageBackendException("ort_finger_not_linked")
    }
}

class OrtIllStageBackend(
    private val modelFile: File,
    private val ortEp: String,
    private val modelVersion: String,
) : StageBackend {
    override fun run(inputPng: ByteArray): StageOutput {
        throw StageBackendException("ort_ill_not_linked")
    }
}
