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
import net.askwho.scanbooks.export.PdfExporter
import net.askwho.scanbooks.export.PdfExporterError
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
    var cleanedPageCount by remember { mutableIntStateOf(0) }
    var sessionTitle by remember { mutableStateOf(defaultTitle()) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activePageId by remember { mutableStateOf<String?>(null) }
    var storeError by remember { mutableStateOf<String?>(null) }

    fun refreshCounts(sessionId: String?) {
        if (sessionId == null) {
            pageCount = 0
            cleanedPageCount = 0
            return
        }
        val session = store.loadSession(sessionId)
        pageCount = session.pageIds.size
        cleanedPageCount = session.pageIds.count { store.loadPage(sessionId, it).cleanedUri != null }
    }

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
                    onSuccess = { sessionId, pageId, _, cleaned ->
                        activeSessionId = sessionId
                        activePageId = pageId
                        pageCount = store.loadSession(sessionId).pageIds.size
                        cleanedPageCount = cleaned
                        route = AppRoute.PROCESS
                    },
                    onError = { storeError = "ScanSession/Page를 저장하지 못했습니다." },
                )
            },
            onOpenSession = {
                refreshCounts(activeSessionId)
                route = AppRoute.SESSION
            },
        )
        AppRoute.PROCESS -> {
            val sessionId = activeSessionId
            val pageId = activePageId
            if (sessionId != null && pageId != null) {
                ProcessStubScreen(
                    store = store,
                    sessionId = sessionId,
                    pageId = pageId,
                    onFinished = {
                        refreshCounts(sessionId)
                        route = AppRoute.CAMERA
                    },
                    onCancel = { route = AppRoute.CAMERA },
                )
            }
        }
        AppRoute.SESSION -> SessionScreen(
            title = sessionTitle,
            onTitleChange = { sessionTitle = it },
            pageCount = pageCount,
            cleanedPageCount = cleanedPageCount,
            onBack = { route = AppRoute.CAMERA },
            onExportPdf = {
                val sessionId = activeSessionId ?: throw PdfExporterError.NoCleanedPages
                PdfExporter().export(sessionId, store)
            },
        )
    }
}

private fun persistCapture(
    store: SessionStore,
    activeSessionId: String?,
    sessionTitle: String,
    jpeg: ByteArray,
    quad: Quad?,
    onSuccess: (sessionId: String, pageId: String, pageCount: Int, cleanedPageCount: Int) -> Unit,
    onError: () -> Unit,
) {
    try {
        val sessionId = activeSessionId ?: store.createSession(title = sessionTitle).id
        val page = store.appendPage(sessionId = sessionId, originalJpeg = jpeg, quad = quad)
        val session = store.loadSession(sessionId)
        val cleaned = session.pageIds.count { store.loadPage(sessionId, it).cleanedUri != null }
        onSuccess(sessionId, page.id, session.pageIds.size, cleaned)
    } catch (_: Exception) {
        onError()
    }
}

private fun defaultTitle(): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date())
