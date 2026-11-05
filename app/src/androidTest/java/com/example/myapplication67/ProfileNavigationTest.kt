package com.example.myapplication67

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun avatarOpensProfileAndFeedSettings() {
        composeRule.setContent {
            NawHostScreen(
                previewTitle = "Гостевой режим",
                loginHint = "guest",
                profileKey = "navigation_test_guest",
                profileRepository = null,
                darkTheme = false,
                onDarkThemeChange = {},
            )
        }

        composeRule.onNodeWithContentDescription("Открыть профиль").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("@guest").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("@guest").assertExists()
        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.onNodeWithText("Настройки ленты").performClick()
        composeRule.onNodeWithText("Сделать архив публичным").assertExists()
    }
}
