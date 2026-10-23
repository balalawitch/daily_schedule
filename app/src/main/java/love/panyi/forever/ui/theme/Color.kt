package love.panyi.forever.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 天空蓝 + 杏橙：蓝色系负责日程本身，杏橙（tertiary）只表示后备活动和掐点。
// 与设计系统「日程表」的 tokens.json 一一对应。

val LightColors = lightColorScheme(
    primary = Color(0xFF2F5FB3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF0F2F66),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF8B4A10),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF4A2500),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF4A5160),
    outline = Color(0xFF737A88),
    outlineVariant = Color(0xFFC3C8D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4FB),
    surfaceContainer = Color(0xFFEEF1F8),
    surfaceContainerHigh = Color(0xFFE9EDF5),
    surfaceContainerHighest = Color(0xFFE1E2E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF244A8F),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4A2500),
    tertiaryContainer = Color(0xFF6B3A10),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
