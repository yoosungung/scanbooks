package net.askwho.scanbooks.ui.camera

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.askwho.scanbooks.camera.CameraCaptureHolder
import net.askwho.scanbooks.camera.EdgeStabilityTracker
import net.askwho.scanbooks.camera.RoughQuadDetector
import net.askwho.scanbooks.camera.YuvToGray
import net.askwho.scanbooks.model.PreviewEdgeState
import net.askwho.scanbooks.model.Quad
import java.util.concurrent.Executors

@Composable
fun CameraPreviewBind(
    captureHolder: CameraCaptureHolder,
    onEdgeState: (PreviewEdgeState) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnEdgeState by rememberUpdatedState(onEdgeState)
    val tracker = remember { EdgeStabilityTracker(requiredHits = 3) }
    val detector = remember { RoughQuadDetector() }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, captureHolder) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (_: Exception) {
                return@Runnable
            }
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            captureHolder.imageCapture = imageCapture
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analysisExecutor) { image ->
                        val gray = YuvToGray.fromImageProxy(image)
                        image.close()
                        val candidate: Quad? = gray?.let { (pixels, size) ->
                            detector.detect(pixels, size.first, size.second)
                        }
                        val state = synchronized(tracker) { tracker.ingest(candidate) }
                        previewView.post { latestOnEdgeState(state) }
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                    imageCapture,
                )
            } catch (_: Exception) {
                // Emulator / missing camera — UI keeps gray guide.
                captureHolder.imageCapture = null
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            captureHolder.imageCapture = null
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // ignore
            }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}
