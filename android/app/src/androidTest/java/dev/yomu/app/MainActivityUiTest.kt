package dev.yomu.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.yomu.app.data.ReadingItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun insertReadyRow() = runBlocking {
        val application = compose.activity.application as YomuApplication
        application.database.libraryDao().upsertItem(
            ReadingItemEntity(
                id = "ui-play-now-test-item",
                title = "UI test article",
                sourceType = "text",
                sourceName = "Test fixture",
                normalizedText = "A short test article for checking the accessible library actions.",
                importedAt = System.currentTimeMillis(),
                estimatedSecondsAtOneX = 30,
            ),
        )
    }

    @Test fun launchesOnToReadAndExposesAccessiblePlayNowAction() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("To read").fetchSemanticsNodes().size == 1
        }
        compose.onAllNodesWithText("To read").assertCountEquals(1)
        compose.onNodeWithContentDescription("Yomu wordmark").assertIsDisplayed()
        compose.onNodeWithContentDescription("Yomu book and headphones logo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add a link or document").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("UI test article").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tap to play · hold to add to Up next").assertIsDisplayed()
        compose.onNodeWithContentDescription("More actions for UI test article").performClick()
        compose.onNodeWithText("Play now").assertIsDisplayed()
    }

    @Test fun toReadOmitsEstimatedListeningTimes() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("UI test article").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("1 min").assertCountEquals(0)
        compose.onAllNodesWithText("min total").assertCountEquals(0)
    }

    @Test fun settingsExposeOfflineModeAndConfigurableSwipeActions() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Defaults").performClick()
        val settingsList = compose.onNodeWithTag("settings-list")
        settingsList.performScrollToNode(hasText("Swipe right", substring = true))
        compose.onNodeWithText("Swipe right", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("Swipe left", substring = true).performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Swipe left", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("List swipe actions").assertIsDisplayed()
        compose.onNodeWithText("Playback speed").fetchSemanticsNode()
        compose.onNodeWithText("Voices").performClick()
        settingsList.performScrollToNode(hasText("Speech mode"))
        compose.onNodeWithText("Speech mode").assertIsDisplayed()
        compose.onNodeWithText("Offline only").assertIsDisplayed()
        settingsList.performScrollToNode(hasText("Offline voice"))
        compose.onNodeWithText("Offline voice").fetchSemanticsNode()
        compose.onNodeWithText("Advanced").performClick()
        settingsList.performScrollToNode(hasText("Manual backup"))
        compose.onNodeWithText("Manual backup").fetchSemanticsNode()
    }

    @Test fun collapsedVoiceSettingsShowCurrentVoiceSelections() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Yomu wordmark").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Azure ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("ElevenLabs ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Offline · Kokoro · Emma", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand Voices").assertIsDisplayed()
    }
}
