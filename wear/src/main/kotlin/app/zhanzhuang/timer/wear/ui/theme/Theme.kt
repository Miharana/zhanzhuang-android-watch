package app.zhanzhuang.timer.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Semantic Wear Material 3 mapping of the approved Urticad earth/gold palette. */
val ZhanZhuangColorScheme = ColorScheme(
    primary = ReflectiveGold,
    primaryDim = DeepGold,
    primaryContainer = GoldHighlight,
    onPrimary = DeepSoil,
    onPrimaryContainer = DeepSoil,
    secondary = GoldSheen,
    secondaryDim = DeepGold,
    secondaryContainer = RaisedSoil,
    onSecondary = DeepSoil,
    onSecondaryContainer = WarmPaper,
    tertiary = Sparkle,
    tertiaryDim = GoldHighlight,
    tertiaryContainer = RaisedSoil,
    onTertiary = DeepSoil,
    onTertiaryContainer = WarmPaper,
    surfaceContainerLow = DeepSoil,
    surfaceContainer = RaisedSoil,
    surfaceContainerHigh = RaisedSoil,
    onSurface = WarmPaper,
    onSurfaceVariant = GoldHighlight,
    outline = DeepGold,
    outlineVariant = GoldSheen,
    background = DeepSoil,
    onBackground = WarmPaper,
    error = DeepGold,
    errorDim = DeepGold,
    errorContainer = RaisedSoil,
    onError = WarmPaper,
    onErrorContainer = WarmPaper,
)

@Composable
fun ZhanZhuangWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ZhanZhuangColorScheme, content = content)
}
