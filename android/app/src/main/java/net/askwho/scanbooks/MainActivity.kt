package net.askwho.scanbooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import net.askwho.scanbooks.ui.RootScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanbooksTheme {
                RootScreen()
            }
        }
    }
}

@Composable
private fun ScanbooksTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme() || true
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else darkColorScheme(),
        content = content,
    )
}
