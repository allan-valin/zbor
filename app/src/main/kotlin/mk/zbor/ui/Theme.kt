package mk.zbor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFFFC107),       // Macedonian sun yellow
    onPrimary = Color(0xFF1B1B1B),
    background = Color(0xFF151515),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFF2F2F2),
    onBackground = Color(0xFFF2F2F2),
    onSurface = Color(0xFFF2F2F2),
)

@Composable
fun ZborTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme) {
    // The Surface sets the light content colour every Text inherits.
    Surface(Modifier.fillMaxSize(), color = Scheme.background, content = content)
}
