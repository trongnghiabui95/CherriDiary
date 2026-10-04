package com.cherri.diary.android.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CherriColors {
    val Cherry = Color(0xFFE53935)
    val Raspberry = Color(0xFFD81B60)
    val Cream = Color(0xFFFFF5F5)
    val Background = Color(0xFFF8F9FA)
    val Title = Color(0xFF212121)
    val Subtitle = Color(0xFF616161)
}

private val CherriScheme = lightColorScheme(
    primary = CherriColors.Raspberry, onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE4EC), onPrimaryContainer = Color(0xFF8B1240),
    secondary = CherriColors.Cherry, secondaryContainer = CherriColors.Cream,
    background = CherriColors.Background, onBackground = CherriColors.Title,
    surface = Color.White, onSurface = CherriColors.Title,
    surfaceVariant = CherriColors.Cream, onSurfaceVariant = CherriColors.Subtitle,
    outline = Color(0xFFB99BA4), outlineVariant = Color(0xFFF0DEE4),
    error = Color(0xFFB3261E)
)

@Composable
fun CherriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CherriScheme, typography = Typography(),
        shapes = Shapes(small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp)), content = content)
}
