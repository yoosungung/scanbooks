package net.askwho.scanbooks.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.askwho.scanbooks.export.PdfExporter
import net.askwho.scanbooks.export.PdfExporterError
import net.askwho.scanbooks.model.AppRoute
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.process.OrtBackendFactory
import net.askwho.scanbooks.session.BatchScanPresentation
import net.askwho.scanbooks.session.SessionPageOrder
import net.askwho.scanbooks.storage.SessionStore
import net.askwho.scanbooks.ui.camera.CameraScreen
import net.askwho.scanbooks.ui.session.SessionPageRow
import net.askwho.scanbooks.ui.session.SessionScreen
import net.askwho.scanbooks.ui.settings.SettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RootScreen() {
    val context = LocalContext.current
    val store = remember { SessionStore(context.filesDir) }
    val runner = remember { OrtBackendFactory.makeRunner() }
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf(AppRoute.CAMERA) }
    var pageCount by remember { mutableIntStateOf(0) }
    var cleanedPageCount by remember { mutableIntStateOf(0) }
    var processingCount by remember { mutableIntStateOf(0) }
    var sessionTitle by remember { mutableStateOf(defaultTitle()) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var sessionPages by remember { mutableStateOf<List<SessionPageRow>>(emptyList()) }
    var storeError by remember { mutableStateOf<String?>(null) }

    fun refreshSessionState(sessionId: String?) {
        if (sessionId == null) {
            pageCount = 0
            cleanedPageCount = 0
            processingCount = 0
            sessionPages = emptyList()
            return
        }
        val session = store.loadSession(sessionId)
        pageCount = session.pageIds.size
        val rows = mutableListOf<SessionPageRow>()
        val statuses = mutableListOf<PipelineStage>()
        var cleaned = 0
        for (pageId in session.pageIds) {
            val page = store.loadPage(sessionId, pageId)
            rows += SessionPageRow(page.id, page.status)
            statuses += page.status
            if (page.cleanedUri != null) cleaned++
        }
        sessionPages = rows
        cleanedPageCount = cleaned
        processingCount = BatchScanPresentation.processingCount(statuses)
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
            processingCount = processingCount,
            onCaptured = { jpeg, quad ->
                try {
                    val sessionId = activeSessionId ?: store.createSession(title = sessionTitle).id
                    activeSessionId = sessionId
                    val page = store.appendPage(sessionId = sessionId, originalJpeg = jpeg, quad = quad)
                    refreshSessionState(sessionId)
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            runner.runBlockingCompatible(store, sessionId, page.id) {
                                scope.launch(Dispatchers.Main.immediate) {
                                    refreshSessionState(sessionId)
                                }
                            }
                        }
                        refreshSessionState(sessionId)
                    }
                } catch (_: Exception) {
                    storeError = "ScanSession/Page를 저장하지 못했습니다."
                }
            },
            onOpenSession = {
                refreshSessionState(activeSessionId)
                route = AppRoute.SESSION
            },
            onOpenSettings = { route = AppRoute.SETTINGS },
        )
        AppRoute.PROCESS -> {
            // Continuous batch stays on camera (UIUX §3.2); keep enum for AppRoute tests.
            CameraScreen(
                pageCount = pageCount,
                processingCount = processingCount,
                onCaptured = { _, _ -> },
                onOpenSession = { route = AppRoute.CAMERA },
            )
        }
        AppRoute.SESSION -> SessionScreen(
            title = sessionTitle,
            onTitleChange = { sessionTitle = it },
            pages = sessionPages,
            cleanedPageCount = cleanedPageCount,
            onBack = { route = AppRoute.CAMERA },
            onAddScan = { route = AppRoute.CAMERA },
            onMove = { pageId, toIndex ->
                val sessionId = activeSessionId ?: return@SessionScreen
                val session = store.loadSession(sessionId)
                val from = session.pageIds.indexOf(pageId)
                val next = SessionPageOrder.move(session.pageIds, from, toIndex) ?: return@SessionScreen
                try {
                    store.reorderPages(sessionId, next)
                    refreshSessionState(sessionId)
                } catch (_: Exception) {
                    storeError = "페이지 순서를 바꾸지 못했습니다."
                }
            },
            onDelete = { pageId ->
                val sessionId = activeSessionId ?: return@SessionScreen
                try {
                    store.deletePage(sessionId, pageId)
                    refreshSessionState(sessionId)
                } catch (_: Exception) {
                    storeError = "페이지를 삭제하지 못했습니다."
                }
            },
            onExportPdf = {
                val sessionId = activeSessionId ?: throw PdfExporterError.NoCleanedPages
                PdfExporter().export(sessionId, store)
            },
        )
        AppRoute.SETTINGS -> SettingsScreen(
            onBack = { route = AppRoute.CAMERA },
        )
    }
}

private fun defaultTitle(): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date())
