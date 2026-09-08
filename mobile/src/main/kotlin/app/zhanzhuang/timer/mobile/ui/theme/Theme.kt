package app.zhanzhuang.timer.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ZhanZhuangLightScheme = lightColorScheme(
    primary = Gold,
    onPrimary = SoilInk,
    primaryContainer = PaleGold,
    onPrimaryContainer = SoilInk,
    secondary = SoftSoil,
    onSecondary = Surface,
    secondaryContainer = SunkenEarth,
    onSecondaryContainer = SoilInk,
    tertiary = DeepGold,
    onTertiary = Surface,
    background = Paper,
    onBackground = SoilInk,
    surface = Surface,
    onSurface = SoilInk,
    surfaceVariant = SunkenEarth,
    onSurfaceVariant = SoilInk,
    outline = SoftSoil,
    error = DeepGold,
)

@Composable
fun ZhanZhuangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ZhanZhuangLightScheme, content = content)
}
