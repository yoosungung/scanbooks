package net.askwho.scanbooks.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.askwho.scanbooks.model.AppRoute
import net.askwho.scanbooks.ui.camera.CameraScreen
import net.askwho.scanbooks.ui.process.ProcessStubScreen
import net.askwho.scanbooks.ui.session.SessionScreen

@Composable
fun RootScreen() {
    var route by remember { mutableStateOf(AppRoute.CAMERA) }
    var pageCount by remember { mutableIntStateOf(0) }
    var sessionTitle by remember { mutableStateOf("새 스캔") }

    when (route) {
        AppRoute.CAMERA -> CameraScreen(
            pageCount = pageCount,
            onShutter = { route = AppRoute.PROCESS },
            onOpenSession = { route = AppRoute.SESSION },
        )
        AppRoute.PROCESS -> ProcessStubScreen(
            onFinished = {
                pageCount += 1
                route = AppRoute.CAMERA
            },
            onCancel = { route = AppRoute.CAMERA },
        )
        AppRoute.SESSION -> SessionScreen(
            title = sessionTitle,
            onTitleChange = { sessionTitle = it },
            pageCount = pageCount,
            onBack = { route = AppRoute.CAMERA },
        )
    }
}
