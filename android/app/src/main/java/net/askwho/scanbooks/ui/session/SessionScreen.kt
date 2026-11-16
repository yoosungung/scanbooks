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
    onBack: () -> Unit,
) {
    var showPdfStub by remember { mutableStateOf(false) }

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
                text = "${pageCount}장",
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
                                text = "완료",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.85f), RoundedCornerShape(50))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showPdfStub = true },
                enabled = pageCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "PDF로 내보내기" },
            ) {
                Text("PDF로 내보내기")
            }
        }
    }

    if (showPdfStub) {
        AlertDialog(
            onDismissRequest = { showPdfStub = false },
            confirmButton = {
                TextButton(onClick = { showPdfStub = false }) { Text("확인") }
            },
            title = { Text("PDF 경로 (stub)") },
            text = {
                Text("완료된 ${pageCount}장을 PDF로 내보내는 경로는 연결됐습니다. 실제 PdfDocument 쓰기는 후속 M2 슬라이스에서 구현합니다.")
            },
        )
    }
}
