package dev.yomu.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun settingsAccentTextMeetsContrastOnBothThemesIncludingGlassAndChips() {
        val accents = listOf(Color(0xFF256EDB), Color(0xFF3E8BFF), Color.White, Color.Black,
            Color.Yellow, Color.Red, Color.Green, Color.Blue, Color(0xFF777777))
        for (dark in listOf(false, true)) for (accent in accents) {
            val displayed = readableAccent(accent, dark)
            for (background in accentTextSurfaces(accent, displayed, dark)) {
                assertTrue("Accent $accent, dark=$dark has insufficient text contrast", textContrast(displayed, background) >= 4.5)
            }
            assertTrue(textContrast(textOnFill(displayed), displayed) >= 4.5)
        }
    }
}
