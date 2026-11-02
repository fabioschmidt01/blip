@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.vereda.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.vereda.app.R

val Night = Color(0xFF0E1014)
val Surface = Color(0xFF1A1E27)
val SurfaceHigh = Color(0xFF242A36)
val Bark = Color(0xFF3A4150)
val Bone = Color(0xFFF5F7FA)
val BoneDim = Color(0xFF9AA3B2)
val Phosphor = Color(0xFFD6FF4A)
val Ink = Color(0xFF12140A)
val Clay = Color(0xFFFF6B57)
val Soil = Surface

val Display = FontFamily(
    Font(
        R.font.besley,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(460)),
    ),
    Font(
        R.font.besley,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(620)),
    ),
)

val Mono = FontFamily(
    Font(R.font.fragment_mono, FontWeight.Normal),
)

private val scheme = darkColorScheme(
    primary = Phosphor,
    onPrimary = Night,
    secondary = Clay,
    onSecondary = Night,
    background = Night,
    onBackground = Bone,
    surface = Surface,
    onSurface = Bone,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = BoneDim,
    error = Clay,
)

@Composable
fun VeredaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
