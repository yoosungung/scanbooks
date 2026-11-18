package net.askwho.scanbooks.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Settings — privacy notice + OSS attribution + About (docs/UIUX.md §4.7). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modelVersion: String = AttributionPresentation.defaultModelVersion,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("닫기") }
        Text("설정", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = AttributionPresentation.sectionPrivacy,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AttributionPresentation.privacyBody,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics {
                contentDescription = AttributionPresentation.privacyBody
            },
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = AttributionPresentation.sectionAttribution,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AttributionPresentation.creditLines.forEach { line ->
            Text(text = line, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AttributionPresentation.nonCommercialNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "DocTr 리포지토리",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AttributionPresentation.doctrRepoUrl)),
                    )
                }
                .padding(vertical = 4.dp),
        )
        Text(
            text = "DocTr-Plus LICENSE",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(AttributionPresentation.doctrLicenseUrl),
                        ),
                    )
                }
                .padding(vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = AttributionPresentation.sectionAbout,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val about = AttributionPresentation.aboutModelVersion(modelVersion)
        Text(
            text = about,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = about },
        )
    }
}
