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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.askwho.scanbooks.model.PipelineStage
import net.askwho.scanbooks.process.PipelineStub
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
    val stub = remember { PipelineStub() }

    LaunchedEffect(sessionId, pageId) {
        stub.run(store, sessionId, pageId) { stage -> current = stage }
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
            text = "보정 중",
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
                            contentDescription = "${stage.koreanLabel} ${statusText(stage, current)}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIcon(stage = stage, current = current)
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(text = stage.koreanLabel, color = Color.White)
                }
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

        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text("취소", color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun StatusIcon(stage: PipelineStage, current: PipelineStage) {
    val order = PipelineStage.processingOrder
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

private fun statusText(stage: PipelineStage, current: PipelineStage): String {
    val order = PipelineStage.processingOrder
    val index = order.indexOf(stage)
    val currentIndex = order.indexOf(current).coerceAtLeast(0)
    return when {
        current == PipelineStage.DONE || index < currentIndex -> "완료"
        stage == current -> "진행 중"
        else -> "대기"
    }
}
