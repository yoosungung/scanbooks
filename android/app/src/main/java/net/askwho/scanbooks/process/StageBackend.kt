package net.askwho.scanbooks.process

import net.askwho.scanbooks.storage.SessionStore

data class StageOutput(
    val png: ByteArray,
    val elapsedMs: Int,
    val ortEp: String? = null,
    val modelVersion: String? = null,
)

fun interface StageBackend {
    fun run(inputPng: ByteArray): StageOutput
}

class PassthroughStageBackend(
    private val ortEp: String = PipelineConstants.DEFAULT_ORT_EP_ANDROID,
    private val modelVersion: String = PipelineConstants.MODEL_VERSION,
) : StageBackend {
    override fun run(inputPng: ByteArray): StageOutput {
        require(SessionStore.isPng(inputPng)) { "notPNG" }
        return StageOutput(png = inputPng, elapsedMs = 0, ortEp = ortEp, modelVersion = modelVersion)
    }
}

class FailingStageBackend(private val code: String) : StageBackend {
    override fun run(inputPng: ByteArray): StageOutput {
        throw StageBackendException(code)
    }
}

class StageBackendException(val code: String) : Exception(code)
