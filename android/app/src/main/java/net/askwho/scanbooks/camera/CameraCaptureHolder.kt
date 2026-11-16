package net.askwho.scanbooks.camera

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.Executor

/** Holds the bound [ImageCapture] so Compose shutter can take a still JPEG. */
class CameraCaptureHolder {
    @Volatile
    var imageCapture: ImageCapture? = null

    fun takeJpeg(executor: Executor, onResult: (Result<ByteArray>) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onResult(Result.failure(IllegalStateException("ImageCapture not bound")))
            return
        }
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bytes = imageProxyToJpegBytes(image)
                        onResult(Result.success(bytes))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Result.failure(exception))
                }
            },
        )
    }

    companion object {
        fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
            // CameraX ImageCapture defaults to JPEG buffer in plane[0].
            val plane = image.planes.firstOrNull()
                ?: throw IllegalStateException("no image planes")
            val buffer: ByteBuffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return bytes
        }
    }
}
