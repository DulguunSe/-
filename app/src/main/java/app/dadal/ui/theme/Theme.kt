package app.dadal.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Dadal {
    val Background = Color(0xFF0F0F11)
    val Surface = Color(0xFF18181B)
    val SurfaceHigh = Color(0xFF222226)
    val SurfaceHighest = Color(0xFF2C2C31)
    val Outline = Color(0xFF2E2E34)
    val OutlineSoft = Color(0x17FFFFFF)
    val Text = Color(0xFFF4F4F6)
    val TextMuted = Color(0xFF8E8E97)
    val TextFaint = Color(0xFF55555D)
    val Primary = Color(0xFF8B8BF7)
    val PrimaryDim = Color(0xFF4B4B8F)
    val Flame = Color(0xFFFF9F43)
    val Slip = Color(0xFFFF6B6B)
    val Success = Color(0xFF4ADE80)

    val Mono = FontFamily.Monospace
}

/** The 21 habit colors, in picker order. */
object HabitPalette {
    val colors = listOf(
        Color(0xFFF87171), Color(0xFFFB923C), Color(0xFFFBBF24), Color(0xFFFACC15), Color(0xFFA3E635), Color(0xFF4ADE80), Color(0xFF34D399),
        Color(0xFF2DD4BF), Color(0xFF22D3EE), Color(0xFF38BDF8), Color(0xFF60A5FA), Color(0xFF818CF8), Color(0xFFA78BFA), Color(0xFFC084FC),
        Color(0xFFE879F9), Color(0xFFF472B6), Color(0xFFFB7185), Color(0xFF94A3B8), Color(0xFF9CA3AF), Color(0xFFA3A3A3), Color(0xFFA8A29E),
    )

    fun get(index: Int): Color = colors[index.mod(colors.size)]
}

private val scheme = darkColorScheme(
    primary = Dadal.Primary,
    onPrimary = Color.White,
    primaryContainer = Dadal.PrimaryDim,
    onPrimaryContainer = Color.White,
    secondary = Dadal.Primary,
    background = Dadal.Background,
    onBackground = Dadal.Text,
    surface = Dadal.Surface,
    onSurface = Dadal.Text,
    surfaceVariant = Dadal.SurfaceHigh,
    onSurfaceVariant = Dadal.TextMuted,
    surfaceContainerLowest = Dadal.Background,
    surfaceContainerLow = Dadal.Surface,
    surfaceContainer = Dadal.Surface,
    surfaceContainerHigh = Dadal.SurfaceHigh,
    surfaceContainerHighest = Dadal.SurfaceHighest,
    outline = Dadal.Outline,
    outlineVariant = Dadal.Outline,
    error = Dadal.Slip,
)

private val base = Typography()
private val typography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

val MonoSmall = TextStyle(fontFamily = Dadal.Mono, fontSize = 13.sp, letterSpacing = 1.sp)

@Composable
fun DadalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Default text and icon color for everything not inside a Material Surface.
        CompositionLocalProvider(LocalContentColor provides Dadal.Text, content = content)
    }
}
