package com.example.wordsapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color.DarkGray,
    secondary = Color.Red,
    tertiary = Color.Red,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color.DarkGray,
    secondary = Color.Red,
    tertiary = Color.Red,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val CustomShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),    // ← buttons use this
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp)
)

@Composable
fun WordsAppTheme(
    darkTheme: Boolean = true,  // ← always use dark theme
    dynamicColor: Boolean = false,  // ← disable dynamic color
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = DarkColorScheme // ← always use dark theme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CustomShapes,
        content = content
    )
}
