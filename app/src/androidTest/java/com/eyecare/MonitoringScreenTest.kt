package com.eyecare

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isToggleable
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Инструментальные тесты экрана мониторинга (требуют устройство/эмулятор:
 * `./gradlew :app:connectedDebugAndroidTest`).
 *
 * Проверяют, что: (1) тумблер отражает переданное состояние и прокидывает клик наверх;
 * (2) при активном мониторинге экран показывает «живую» дистанцию из [MonitoringStateHolder] —
 * это тот же источник, на который завязана синхронизация UI с сервисом.
 */
@RunWith(AndroidJUnit4::class)
class MonitoringScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        MonitoringStateHolder.reset()
    }

    @Test
    fun switch_reflects_state_and_propagates_toggle() {
        var toggledTo: Boolean? = null
        composeRule.setContent {
            EyeCareTheme(themeMode = ThemeMode.LIGHT) {
                MonitoringScreen(enabled = false, onToggle = { toggledTo = it })
            }
        }

        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.onNode(isToggleable()).performClick()
        assertEquals(true, toggledTo)
    }

    @Test
    fun shows_live_distance_from_state_holder() {
        MonitoringStateHolder.setDistance(37.8f)

        composeRule.setContent {
            EyeCareTheme(themeMode = ThemeMode.LIGHT) {
                MonitoringScreen(enabled = true, onToggle = {})
            }
        }

        val expected = String.format(Locale.getDefault(), "%.1f", 37.8f)
        composeRule.onNodeWithText(expected).assertExists()
    }
}
