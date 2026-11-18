package net.askwho.scanbooks.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import net.askwho.scanbooks.model.PipelineStage

data class SessionPageRow(
    val id: String,
    val status: PipelineStage,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    title: String,
    onTitleChange: (String) -> Unit,
    pages: List<SessionPageRow>,
    cleanedPageCount: Int,
    onBack: () -> Unit,
    onAddScan: () -> Unit,
    onMove: (pageId: String, toIndex: Int) -> Unit,
    onDelete: (pageId: String) -> Unit,
    onExportPdf: () -> String,
) {
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("세션") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("카메라") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("세션 제목") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${pages.size}장 · 완료 ${cleanedPageCount}장",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (pages.isEmpty()) {
                Text(
                    text = "촬영한 페이지가 없습니다",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        val selected = page.id in selectedIds
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(3f / 4f)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(8.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable {
                                        selectedIds =
                                            if (selected) selectedIds - page.id else selectedIds + page.id
                                    },
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = statusBadge(page.status),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .background(
                                            statusColor(page.status).copy(alpha = 0.85f),
                                            RoundedCornerShape(50),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Row {
                                TextButton(
                                    onClick = { onMove(page.id, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.semantics { contentDescription = "앞으로 이동" },
                                ) { Text("▲") }
                                TextButton(
                                    onClick = { onMove(page.id, index + 1) },
                                    enabled = index < pages.lastIndex,
                                    modifier = Modifier.semantics { contentDescription = "뒤로 이동" },
                                ) { Text("▼") }
                            }
                        }
                    }
                }
            }

            if (selectedIds.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        selectedIds.forEach(onDelete)
                        selectedIds = emptySet()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "선택 삭제" },
                ) {
                    Text("선택 ${selectedIds.size}장 삭제")
                }
            }

            if (pages.isEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = onAddScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "추가 스캔" },
            ) {
                Text("추가 스캔")
            }

            Button(
                onClick = {
                    try {
                        val uri = onExportPdf()
                        exportFailed = false
                        exportMessage = "저장됨: $uri"
                    } catch (_: Exception) {
                        exportFailed = true
                        exportMessage = "완료된(cleanedUri) 페이지가 없어 PDF를 만들지 못했습니다."
                    }
                },
                enabled = cleanedPageCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "PDF로 내보내기" },
            ) {
                Text("PDF로 내보내기")
            }
        }
    }

    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) { Text("확인") }
            },
            title = { Text(if (exportFailed) "PDF 내보내기 실패" else "PDF 내보내기") },
            text = { Text(exportMessage ?: "") },
        )
    }
}

private fun statusBadge(status: PipelineStage): String =
    when (status) {
        PipelineStage.DONE -> "완료"
        PipelineStage.FAILED -> "실패"
        else -> "처리중"
    }

private fun statusColor(status: PipelineStage): Color =
    when (status) {
        PipelineStage.DONE -> Color(0xFF4CAF50)
        PipelineStage.FAILED -> Color(0xFFFF9800)
        else -> Color(0xFF2196F3)
    }
