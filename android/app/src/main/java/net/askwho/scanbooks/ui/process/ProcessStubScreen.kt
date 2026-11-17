package net.askwho.scanbooks.ui.process

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.askwho.scanbooks.model.PageMetrics
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.process.OrtBackendFactory
import net.askwho.scanbooks.storage.SessionStore

@Composable
fun ProcessStubScreen(
    store: SessionStore,
    sessionId: String,
    pageId: String,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    var current by remember { mutableStateOf(PipelineStage.CROP) }
    var metrics by remember { mutableStateOf<PageMetrics?>(null) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var failedStage by remember { mutableStateOf<PipelineStage?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }
    val runner = remember { OrtBackendFactory.makeRunner() }

    LaunchedEffect(sessionId, pageId, retryToken) {
        errorCode = null
        failedStage = null
        current = PipelineStage.CROP
        try {
            runner.run(store, sessionId, pageId) { stage ->
                current = stage
                runCatching { store.loadPage(sessionId, pageId) }.getOrNull()?.let { page ->
                    metrics = page.metrics
                    errorCode = page.errorCode
                    failedStage = page.failedStage
                }
            }
        } catch (_: Exception) {
            runCatching { store.loadPage(sessionId, pageId) }.getOrNull()?.let { page ->
                current = PipelineStage.FAILED
                metrics = page.metrics
                errorCode = page.errorCode
                failedStage = page.failedStage
            }
        }
        if (current == PipelineStage.DONE) {
            delay(400)
            onFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (current == PipelineStage.FAILED) "보정 실패" else "보정 중",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "보통 10초 안팎",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PipelineStage.processingOrder.forEach { stage ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "${stage.koreanLabel} ${statusText(stage, current, failedStage)}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIcon(stage = stage, current = current, failedStage = failedStage)
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(text = stage.koreanLabel, color = Color.White)
                    Spacer(modifier = Modifier.weight(1f))
                    stageMs(stage, metrics)?.let { ms ->
                        Text(
                            text = "${ms}ms",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        metrics?.let { m ->
            PageMetricsPresentation.contractLine(m)?.let { contract ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = contract,
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .semantics {
                            contentDescription = PageMetricsPresentation.accessibilitySummary(m)
                        },
                )
            }
        }

        if (current == PipelineStage.DONE) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "펴진 페이지가 준비됐어요",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (current == PipelineStage.FAILED) {
            FailedRetryPresentation.errorLine(failedStage, errorCode)?.let { line ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = line,
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .semantics { contentDescription = line },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { retryToken += 1 },
                modifier = Modifier.semantics {
                    contentDescription = FailedRetryPresentation.retryButtonLabel
                },
            ) {
                Text(FailedRetryPresentation.retryButtonLabel)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text("취소", color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun StatusIcon(
    stage: PipelineStage,
    current: PipelineStage,
    failedStage: PipelineStage?,
) {
    val order = PipelineStage.processingOrder
    if (current == PipelineStage.FAILED && failedStage != null) {
        val index = order.indexOf(stage)
        val failedIndex = order.indexOf(failedStage)
        when {
            stage == failedStage -> Text("✗", color = Color(0xFFFF9800))
            index in 0 until failedIndex -> Text("✓", color = Color(0xFF4CAF50))
            else -> Text("○", color = Color.White.copy(alpha = 0.35f))
        }
        return
    }
    val index = order.indexOf(stage)
    val currentIndex = order.indexOf(current).coerceAtLeast(0)
    when {
        current == PipelineStage.DONE || index < currentIndex ->
            Text("✓", color = Color(0xFF4CAF50))
        stage == current ->
            CircularProgressIndicator(
                modifier = Modifier.height(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        else ->
            Text("○", color = Color.White.copy(alpha = 0.35f))
    }
}

private fun statusText(
    stage: PipelineStage,
    current: PipelineStage,
    failedStage: PipelineStage?,
): String {
    val order = PipelineStage.processingOrder
    if (current == PipelineStage.FAILED && failedStage != null) {
        val index = order.indexOf(stage)
        val failedIndex = order.indexOf(failedStage)
        return when {
            stage == failedStage -> "실패"
            index in 0 until failedIndex -> "완료"
            else -> "대기"
        }
    }
    val index = order.indexOf(stage)
    val currentIndex = order.indexOf(current).coerceAtLeast(0)
    return when {
        current == PipelineStage.DONE || index < currentIndex -> "완료"
        stage == current -> "진행 중"
        else -> "대기"
    }
}

private fun stageMs(stage: PipelineStage, metrics: PageMetrics?): Int? =
    when (stage) {
        PipelineStage.CROP -> metrics?.cropMs
        PipelineStage.GEO -> metrics?.geoMs
        PipelineStage.FINGER -> metrics?.fingerMs
        PipelineStage.ILL -> metrics?.illMs
        else -> null
    }
