package com.example

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.EqPreferencesRepository
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.contrastInkFor
import com.example.ui.theme.dynamicSchemeToNothingColors
import com.example.ui.theme.toMonochrome
import com.example.ui.theme.toMonochromePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeAndPresetTest {

    @Test
    fun `toMonochrome desaturates colors while preserving alpha`() {
        val sample = Color(red = 0.85f, green = 0.25f, blue = 0.15f, alpha = 0.7f)
        val mono = sample.toMonochrome()
        assertEquals(mono.red, mono.green, 0.005f)
        assertEquals(mono.green, mono.blue, 0.005f)
        assertEquals(sample.alpha, mono.alpha, 0.001f)
    }

    @Test
    fun `dynamicSchemeToNothingColors applies system palette and monochrome converts accents`() {
        val systemScheme = lightColorScheme(
            primary = Color(0xFF3366CC),
            onPrimary = Color.White,
            secondary = Color(0xFF44AA88),
            tertiary = Color(0xFFDD8822),
            background = Color(0xFFF5F7FB),
            onBackground = Color(0xFF101318),
            surfaceContainer = Color(0xFFE9EEF7),
            surfaceContainerHigh = Color(0xFFDDE5F2)
        )
        val mapped = dynamicSchemeToNothingColors(systemScheme, useDark = false)
        assertFalse(mapped.isMonochrome)
        assertEquals(Color(0xFFF5F7FB), mapped.canvas)
        assertEquals(Color(0xFF3366CC), mapped.pillActiveContainer)

        val monoPalette = mapped.toMonochromePalette()
        assertTrue(monoPalette.isMonochrome)
        assertEquals(monoPalette.red.red, monoPalette.red.blue, 0.001f)
        assertEquals(monoPalette.yellow.red, monoPalette.yellow.green, 0.001f)
        assertEquals(monoPalette.blue.red, monoPalette.blue.blue, 0.001f)
    }

    @Test
    fun `contrastInkFor returns dark ink on bright surfaces and white ink on dark surfaces`() {
        assertEquals(Color(0xFF15171A), contrastInkFor(Color(0xFFF5C922)))
        assertEquals(Color.White, contrastInkFor(Color(0xFF181A1F)))
    }

    @Test
    fun `preferences repository persists SYSTEM_UI and monochrome state`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EqPreferencesRepository(context)

        repo.saveThemeMode(ThemeMode.SYSTEM_UI)
        repo.saveMonochromeEnabled(true)

        assertEquals(ThemeMode.SYSTEM_UI, repo.themeModeFlow.first())
        assertTrue(repo.monochromeFlow.first())

        // Clean up back to defaults
        repo.saveThemeMode(ThemeMode.LIGHT)
        repo.saveMonochromeEnabled(false)
        assertEquals(ThemeMode.LIGHT, repo.themeModeFlow.first())
        assertFalse(repo.monochromeFlow.first())
    }
}
