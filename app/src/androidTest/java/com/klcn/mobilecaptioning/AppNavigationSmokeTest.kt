package com.klcn.mobilecaptioning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun guestNavigationLanguageAndPasswordRecoveryEntryRemainUsable() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Chụp").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(
            "Lịch sử",
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNodeWithText("Đăng nhập để sử dụng Lịch sử").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(
            "Cá nhân",
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNodeWithText("Chào mừng trở lại").assertIsDisplayed()
        composeRule.onNodeWithText("Quên mật khẩu?").performClick()
        composeRule.onNodeWithText("Gửi mã xác minh").assertIsDisplayed()
        composeRule.onNodeWithText("Quay lại đăng nhập").performClick()

        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithText("🇻🇳\n🇺🇸").performClick()
        composeRule.onAllNodesWithText("Capture").onFirst().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Profile",
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
    }
}
