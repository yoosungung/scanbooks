package net.askwho.scanbooks.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    title: String,
    onTitleChange: (String) -> Unit,
    pageCount: Int,
    cleanedPageCount: Int,
    onBack: () -> Unit,
    onExportPdf: () -> String,
) {
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }

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
                text = "${pageCount}장 · 완료 ${cleanedPageCount}장",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (pageCount == 0) {
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
                    items(pageCount) { index ->
                        val done = index < cleanedPageCount
                        Box(
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp),
                                ),
                        ) {
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (done) "완료" else "대기",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(
                                        (if (done) Color(0xFF4CAF50) else Color(0xFFFF9800))
                                            .copy(alpha = 0.85f),
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
