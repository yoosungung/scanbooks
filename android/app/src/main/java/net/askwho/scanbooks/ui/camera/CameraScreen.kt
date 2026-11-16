package net.askwho.scanbooks.ui.camera

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun CameraScreen(
    pageCount: Int,
    onShutter: () -> Unit,
    onOpenSession: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1C)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val insetX = size.width * 0.12f
                val insetY = size.height * 0.12f
                val path = Path().apply {
                    moveTo(insetX, insetY * 1.2f)
                    lineTo(size.width - insetX, insetY)
                    lineTo(size.width - insetX * 0.9f, size.height - insetY)
                    lineTo(insetX * 1.1f, size.height - insetY * 0.85f)
                    close()
                }
                drawPath(path, Color(0xFF4CAF50).copy(alpha = 0.85f), style = Stroke(width = 4f))
            }
        }

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

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "문서를 화면에 맞춰 주세요",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                        .background(Color.White, CircleShape)
                        .semantics { contentDescription = "셔터" }
                        .clickable(onClick = onShutter),
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
