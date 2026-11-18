package net.askwho.scanbooks.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.askwho.scanbooks.camera.CameraCaptureHolder
import net.askwho.scanbooks.model.OverlayKind
import net.askwho.scanbooks.model.PreviewEdgeState
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.session.BatchScanPresentation
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    pageCount: Int,
    processingCount: Int = 0,
    onCaptured: (ByteArray, Quad?) -> Unit,
    onOpenSession: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var edgeState by remember { mutableStateOf<PreviewEdgeState>(PreviewEdgeState.Undetected) }
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val captureHolder = remember { CameraCaptureHolder() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    var showDewarpTip by remember {
        mutableStateOf(
            !context.getSharedPreferences("scanbooks", 0)
                .getBoolean("didShowDewarpTip", false),
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(showDewarpTip) {
        if (showDewarpTip) {
            context.getSharedPreferences("scanbooks", 0)
                .edit()
                .putBoolean("didShowDewarpTip", true)
                .apply()
        }
    }

    if (captureError != null) {
        AlertDialog(
            onDismissRequest = { captureError = null },
            confirmButton = {
                TextButton(onClick = { captureError = null }) { Text("확인") }
            },
            title = { Text("촬영 실패") },
            text = { Text(captureError ?: "") },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (permissionGranted) {
            CameraPreviewBind(
                captureHolder = captureHolder,
                onEdgeState = { edgeState = it },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1C1C)),
            )
        }

        QuadOverlay(state = edgeState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "1페이지",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text("⋯", color = Color.White.copy(alpha = 0.5f))
            }

            BatchScanPresentation.badgeLabel(processingCount)?.let { badge ->
                Text(
                    text = badge,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .background(Color(0xFF2196F3).copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = badge },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (edgeState.hintCopy.isNotEmpty()) {
                Text(
                    text = edgeState.hintCopy,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (permissionDenied) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text("카메라 권한이 필요합니다", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                            context.startActivity(intent)
                        },
                    ) {
                        Text("설정 열기")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showDewarpTip) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .clickable { showDewarpTip = false },
                ) {
                    Text(
                        text = "촬영 후 곡면을 펴고 손가락을 지웁니다",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "손가락 지우기",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(8.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .background(
                            if (capturing) Color.White.copy(alpha = 0.5f) else Color.White,
                            CircleShape,
                        )
                        .semantics { contentDescription = "셔터" }
                        .clickable(enabled = !capturing) {
                            if (capturing) return@clickable
                            capturing = true
                            val quad = edgeState.quad
                            captureHolder.takeJpeg(captureExecutor) { result ->
                                // Callback may be off main; hop via PreviewView/main post is handled by Compose state setters on UI if needed.
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    capturing = false
                                    result
                                        .onSuccess { bytes -> onCaptured(bytes, quad) }
                                        .onFailure {
                                            captureError = "사진을 저장하지 못했습니다. 카메라 상태를 확인해 주세요."
                                        }
                                }
                            }
                        },
                )

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 52.dp, height = 64.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .semantics { contentDescription = "세션 보기" }
                        .clickable(onClick = onOpenSession),
                ) {
                    Text(
                        text = if (pageCount > 0) "$pageCount" else "□",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadOverlay(state: PreviewEdgeState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (state.overlayKind) {
            OverlayKind.GRAY_GUIDE -> {
                val insetX = size.width * 0.12f
                val insetY = size.height * 0.12f
                drawRect(
                    color = Color.Gray.copy(alpha = 0.7f),
                    topLeft = Offset(insetX, insetY),
                    size = Size(size.width - insetX * 2, size.height - insetY * 2),
                    style = Stroke(width = 3f),
                )
            }
            OverlayKind.GREEN_QUAD -> {
                val q = state.quad ?: return@Canvas
                val path = Path().apply {
                    moveTo(q.tl.x * size.width, q.tl.y * size.height)
                    lineTo(q.tr.x * size.width, q.tr.y * size.height)
                    lineTo(q.br.x * size.width, q.br.y * size.height)
                    lineTo(q.bl.x * size.width, q.bl.y * size.height)
                    close()
                }
                drawPath(path, Color(0xFF4CAF50).copy(alpha = 0.9f), style = Stroke(width = 4f))
            }
        }
    }
}
