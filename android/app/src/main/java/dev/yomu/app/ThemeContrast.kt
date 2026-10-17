package dev.yomu.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal fun textContrast(foreground: Color, background: Color): Double {
    val a = foreground.compositeOver(background).luminance().toDouble()
    val b = background.luminance().toDouble()
    return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
}

internal fun textOnFill(fill: Color): Color =
    if (textContrast(Color.White, fill) >= textContrast(Color.Black, fill)) Color.White else Color.Black

/** Preserve the selected hue, changing only its displayed tone for readable UI text. */
internal fun readableAccent(accent: Color, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    val original = accent.copy(alpha = 1f)
    for (step in 0..100) {
        val candidate = lerp(original, target, step / 100f)
        if (accentTextSurfaces(original, candidate, dark).all { textContrast(candidate, it) >= 4.6 }) return candidate
    }
    return target
}

internal fun accentTextSurfaces(accent: Color, displayed: Color, dark: Boolean): List<Color> {
    val background = if (dark) Color(0xFF090B10) else Color(0xFFF6F8FB)
    val surface = if (dark) Color(0xFF11151C) else Color.White
    val variant = if (dark) Color(0xFF1C222C) else Color(0xFFE8EDF5)
    val bases = listOf(background, surface, variant,
        displayed.copy(alpha = .16f).compositeOver(background),
        displayed.copy(alpha = .09f).compositeOver(background))
    return bases + bases.flatMap { base ->
        val glass = surface.copy(alpha = .32f).compositeOver(Color.White.copy(alpha = .14f).compositeOver(base))
        listOf(glass, displayed.copy(alpha = .20f).compositeOver(base))
    } + accent.copy(alpha = if (dark) .22f else .16f).compositeOver(surface)
}
