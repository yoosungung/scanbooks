package net.askwho.scanbooks.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import net.askwho.scanbooks.model.AppRoute
import net.askwho.scanbooks.model.Quad
import net.askwho.scanbooks.storage.SessionStore
import net.askwho.scanbooks.ui.camera.CameraScreen
import net.askwho.scanbooks.ui.process.ProcessStubScreen
import net.askwho.scanbooks.ui.session.SessionScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RootScreen() {
    val context = LocalContext.current
    val store = remember { SessionStore(context.filesDir) }
    var route by remember { mutableStateOf(AppRoute.CAMERA) }
    var pageCount by remember { mutableIntStateOf(0) }
    var sessionTitle by remember { mutableStateOf(defaultTitle()) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activePageId by remember { mutableStateOf<String?>(null) }
    var storeError by remember { mutableStateOf<String?>(null) }

    if (storeError != null) {
        AlertDialog(
            onDismissRequest = { storeError = null },
            confirmButton = {
                TextButton(onClick = { storeError = null }) { Text("확인") }
            },
            title = { Text("저장 실패") },
            text = { Text(storeError ?: "") },
        )
    }

    when (route) {
        AppRoute.CAMERA -> CameraScreen(
            pageCount = pageCount,
            onCaptured = { jpeg, quad ->
                persistCapture(
                    store = store,
                    activeSessionId = activeSessionId,
                    sessionTitle = sessionTitle,
                    jpeg = jpeg,
                    quad = quad,
                    onSuccess = { sessionId, pageId, count ->
                        activeSessionId = sessionId
                        activePageId = pageId
                        pageCount = count
                        route = AppRoute.PROCESS
                    },
                    onError = { storeError = "ScanSession/Page를 저장하지 못했습니다." },
                )
            },
            onOpenSession = { route = AppRoute.SESSION },
        )
        AppRoute.PROCESS -> {
            val sessionId = activeSessionId
            val pageId = activePageId
            if (sessionId != null && pageId != null) {
                ProcessStubScreen(
                    store = store,
                    sessionId = sessionId,
                    pageId = pageId,
                    onFinished = { route = AppRoute.CAMERA },
                    onCancel = { route = AppRoute.CAMERA },
                )
            }
        }
        AppRoute.SESSION -> SessionScreen(
            title = sessionTitle,
            onTitleChange = { sessionTitle = it },
            pageCount = pageCount,
            onBack = { route = AppRoute.CAMERA },
        )
    }
}

private fun persistCapture(
    store: SessionStore,
    activeSessionId: String?,
    sessionTitle: String,
    jpeg: ByteArray,
    quad: Quad?,
    onSuccess: (sessionId: String, pageId: String, pageCount: Int) -> Unit,
    onError: () -> Unit,
) {
    try {
        val sessionId = activeSessionId ?: store.createSession(title = sessionTitle).id
        val page = store.appendPage(sessionId = sessionId, originalJpeg = jpeg, quad = quad)
        val count = store.loadSession(sessionId).pageIds.size
        onSuccess(sessionId, page.id, count)
    } catch (_: Exception) {
        onError()
    }
}

private fun defaultTitle(): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date())
