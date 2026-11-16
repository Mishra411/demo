/*
 * AdminPart4IntentTest.kt
 * UI (intent) tests for the administrator stories added in Part 4:
 * browsing and removing images (US 03.06.01, 03.03.01), reviewing the
 * notification and message logs (US 03.08.01, 03.12.01) and removing a
 * campsite owner (US 03.07.01).
 * Screens get canned data, so no Firestore is needed.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.code.admin.AdminProfile
import com.example.code.model.AppNotification
import com.example.code.model.Message
import com.example.code.model.StoredImage
import com.example.code.ui.Part4Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminPart4IntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = adminImageItems(
        uploaded = listOf(
            StoredImage(id = "photo-1", uploaderId = Part4Fixtures.PRIYA_ID, kind = StoredImage.KIND_PROFILE, label = "Priya Shah", createdAt = 2_000),
        ),
        campsites = listOf(Part4Fixtures.banff.copy(imageUrl = "https://example.com/banff.jpg")),
        dateText = { "Nov 9, 2026" },
    )

    // US 03.06.01, 03.03.01
    @Test
    fun adminBrowsesImagesAndRemovesOneAfterConfirming() {
        var removed: AdminImageItem? = null
        composeRule.setContent {
            MaterialTheme { AdminImagesScreen(items = items, message = null, busy = false, onRemove = { removed = it }) }
        }

        composeRule.onNodeWithText("2 images").assertIsDisplayed()
        composeRule.onNodeWithText("Profile photo: Priya Shah").assertIsDisplayed()

        composeRule.onNodeWithTag("removeImage-stored-photo-1").performClick()
        composeRule.onNodeWithText("Remove this image?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertNull(removed)

        composeRule.onNodeWithTag("removeImage-stored-photo-1").performClick()
        composeRule.onNodeWithText("Confirm removal").performClick()
        assertEquals("photo-1", removed!!.storedId)
    }

    @Test
    fun noImagesShowsAnEmptyState() {
        composeRule.setContent {
            MaterialTheme { AdminImagesScreen(items = emptyList(), message = "Image removed.", busy = false, onRemove = {}) }
        }
        composeRule.onNodeWithText("No images to review.").assertIsDisplayed()
        composeRule.onNodeWithText("Image removed.").assertIsDisplayed()
    }

    private val notifications = listOf(
        AppNotification(
            id = "n1", recipientId = Part4Fixtures.PRIYA_ID, senderId = Part4Fixtures.OWNER_ID,
            type = AppNotification.TYPE_LOTTERY_WON, title = "You won: Jasper Riverside",
            message = "Open the campsite to choose your dates and reserve.", createdAt = 1794232800000L,
        ),
        AppNotification(
            id = "n2", recipientId = "device-liam-02", senderId = Part4Fixtures.OWNER_ID,
            type = AppNotification.TYPE_LOTTERY_LOST, title = "Not selected for Jasper Riverside",
            message = "Thanks for entering.", createdAt = 1794232800000L,
        ),
    )
    private val messages = listOf(
        Message(
            id = "m1", reservationId = "r1", campsiteName = "Jasper Riverside", senderId = Part4Fixtures.OWNER_ID,
            senderName = "Jasper Camps", recipientId = Part4Fixtures.PRIYA_ID, fromOwner = true,
            text = "Gate code is 4521.", createdAt = 1794236400000L,
        ),
    )

    // US 03.08.01, 03.12.01
    @Test
    fun adminReviewsNotificationAndMessageLogs() {
        composeRule.setContent { MaterialTheme { AdminLogsScreen(notifications = notifications, messages = messages) } }

        composeRule.onNodeWithText("Notifications (2)").assertIsDisplayed()
        composeRule.onNodeWithText("Lottery result: selected · You won: Jasper Riverside").assertIsDisplayed()
        composeRule.onNodeWithText("Thanks for entering.").assertIsDisplayed()

        composeRule.onNodeWithText("Messages (1)").performClick()
        composeRule.onNodeWithText("Owner to guest · Jasper Riverside").assertIsDisplayed()
        composeRule.onNodeWithText("Gate code is 4521.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Thanks for entering.").assertCountEquals(0)
    }

    @Test
    fun adminSearchesTheLogs() {
        composeRule.setContent { MaterialTheme { AdminLogsScreen(notifications = notifications, messages = messages) } }

        composeRule.onNodeWithTag("logSearch").performTextInput("not selected")
        composeRule.onNodeWithText("Thanks for entering.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Open the campsite to choose your dates and reserve.").assertCountEquals(0)

        composeRule.onNodeWithTag("logSearch").performTextInput(" zzz")
        composeRule.onNodeWithText("No log entries match your search.").assertIsDisplayed()
    }

    // US 03.07.01
    @Test
    fun adminRemovesAnOwnerAfterConfirming() {
        val owners = ownerRows(
            profiles = listOf(
                AdminProfile(Part4Fixtures.OWNER_ID, "Jasper Camps", "camps@example.com", roles = setOf("CAMPSITE_OWNER")),
                AdminProfile("admin-device", "Avery Chen", roles = setOf("ADMIN", "CAMPSITE_OWNER")),
                AdminProfile(Part4Fixtures.PRIYA_ID, "Priya Shah", roles = setOf("ENTRANT")),
            ),
            campsites = listOf(Part4Fixtures.jasper, Part4Fixtures.banff),
        )
        var removed: String? = null
        var back = false
        composeRule.setContent {
            MaterialTheme {
                AdminOwnersScreen(
                    owners = owners,
                    currentProfileId = "admin-device",
                    busyId = null,
                    message = null,
                    onConfirmRemove = { removed = it },
                    onBack = { back = true },
                )
            }
        }

        composeRule.onNodeWithText("Jasper Camps").assertIsDisplayed()
        composeRule.onNodeWithText("Campsites (2): Banff Tunnel Mountain, Jasper Riverside").assertIsDisplayed()
        // Entrants are not listed, and the administrator cannot remove themselves.
        composeRule.onAllNodesWithText("Priya Shah").assertCountEquals(0)
        composeRule.onNodeWithText("Your profile").assertIsDisplayed()
        composeRule.onAllNodesWithText("Remove owner").assertCountEquals(1)

        composeRule.onNodeWithTag("removeOwner-owner-device-7f3a").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove this owner?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertNull(removed)

        composeRule.onNodeWithTag("removeOwner-owner-device-7f3a").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove owner and campsites").performClick()
        assertEquals(Part4Fixtures.OWNER_ID, removed)

        composeRule.onNodeWithText("Back").performClick()
        assertTrue(back)
    }
}
