/*
 * NewIssuesIntentTest.kt
 * UI (intent) tests for issues #141, #142, #144, #145 and #146: removing a
 * profile photo (US 01.08.02), reviewing only after a stay (US 01.13.02),
 * shared lotteries with other owners (US 02.04.08) and administrators
 * removing ratings and reviews (US 03.09.01, 03.10.01).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.code.User
import com.example.code.UserRole
import com.example.code.controller.ReviewController
import com.example.code.model.Campsite
import com.example.code.model.Lottery
import com.example.code.model.Review
import com.example.code.navigation.AppMode
import com.example.code.ui.common.ProfileScreen
import com.example.code.ui.entrant.ReviewsSection
import com.example.code.ui.owner.CreateLotteryScreen
import com.example.code.ui.owner.OwnerLotteriesScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewIssuesIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    // US 01.08.02
    @Test
    fun entrantRemovesTheirProfilePhoto() {
        var removed = false
        val user = User(
            deviceId = Part4Fixtures.PRIYA_ID,
            name = "Priya Shah",
            email = "priya@example.com",
            roles = setOf(UserRole.ENTRANT),
            profilePhotoUrl = "img:photo-1",
        )
        composeRule.setContent {
            ProfileScreen(
                user = user,
                activeMode = AppMode.ENTRANT,
                availableModes = listOf(AppMode.ENTRANT),
                isSaving = false,
                errorMessage = null,
                onSaveProfile = { _, _, _ -> },
                onSwitchMode = {},
                onPhotoPicked = {},
                onRemovePhoto = { removed = true },
            )
        }
        composeRule.onNodeWithText("Change profile photo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("removePhoto").performScrollTo().performClick()
        assertTrue(removed)
    }

    @Test
    fun noRemoveButtonWithoutAPhoto() {
        composeRule.setContent {
            ProfileScreen(
                user = User(deviceId = Part4Fixtures.PRIYA_ID, name = "Priya Shah", roles = setOf(UserRole.ENTRANT)),
                activeMode = AppMode.ENTRANT,
                availableModes = listOf(AppMode.ENTRANT),
                isSaving = false,
                errorMessage = null,
                onSaveProfile = { _, _, _ -> },
                onSwitchMode = {},
                onPhotoPicked = {},
                onRemovePhoto = {},
            )
        }
        composeRule.onNodeWithText("Add profile photo").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Remove photo").assertCountEquals(0)
    }

    private val review = Review(
        id = "device-liam-02", campsiteId = Part4Fixtures.jasper.id, authorId = "device-liam-02",
        authorName = "Liam Chen", stars = 1, text = "Terrible, avoid!!!", createdAt = 1_000,
    )
    private val reply = Review(
        id = "reply-1", campsiteId = Part4Fixtures.jasper.id, authorId = Part4Fixtures.OWNER_ID,
        authorName = "Jasper Camps", text = "Sorry to hear that.", parentId = review.id, createdAt = 2_000,
    )

    // US 01.13.02
    @Test
    fun entrantWithoutAStaySeesWhyTheyCannotReview() {
        composeRule.setContent {
            ReviewsSection(
                threads = ReviewController.threads(listOf(review)),
                myId = Part4Fixtures.PRIYA_ID,
                busy = false,
                message = null,
                onSubmit = { _, _ -> },
                onReply = { _, _ -> },
                onReact = { _, _ -> },
                canReview = false,
                notAllowedMessage = ReviewController.NEEDS_STAY,
            )
        }
        composeRule.onNodeWithText(ReviewController.NEEDS_STAY).assertIsDisplayed()
        composeRule.onAllNodesWithText("Post review").assertCountEquals(0)
        // Other reviews are still readable.
        composeRule.onNodeWithText("Terrible, avoid!!!").assertIsDisplayed()
    }

    // US 03.09.01, 03.10.01
    @Test
    fun adminRemovesARatingAReviewAndAReplyAfterConfirming() {
        var removedRating: Review? = null
        var removedReview: Review? = null
        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReviewsSection(
                    threads = ReviewController.threads(listOf(review, reply)),
                    myId = "admin-device",
                    busy = false,
                    message = null,
                    onSubmit = { _, _ -> },
                    onReply = { _, _ -> },
                    onReact = { _, _ -> },
                    canReview = false,
                    onRemoveRating = { removedRating = it },
                    onRemoveReview = { removedReview = it },
                )
            }
        }

        composeRule.onNodeWithTag("removeRating-device-liam-02").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove this rating?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertNull(removedRating)

        composeRule.onNodeWithTag("removeRating-device-liam-02").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove").performClick()
        assertEquals(review, removedRating)

        composeRule.onNodeWithTag("removeReview-device-liam-02").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove this review?").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()
        assertEquals(review, removedReview)

        composeRule.onNodeWithTag("removeReview-reply-1").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove this reply?").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()
        assertEquals(reply, removedReview)
        // Replies have no rating to remove.
        composeRule.onAllNodesWithText("Remove rating").assertCountEquals(1)
    }

    @Test
    fun nonAdminsSeeNoRemoveButtons() {
        composeRule.setContent {
            ReviewsSection(
                threads = ReviewController.threads(listOf(review, reply)),
                myId = Part4Fixtures.PRIYA_ID,
                busy = false,
                message = null,
                onSubmit = { _, _ -> },
                onReply = { _, _ -> },
                onReact = { _, _ -> },
            )
        }
        composeRule.onAllNodesWithText("Remove rating").assertCountEquals(0)
        composeRule.onAllNodesWithText("Remove review").assertCountEquals(0)
    }

    private val sharedLottery = Part4Fixtures.drawnLottery.copy(
        id = "lottery-jasper-may",
        status = Lottery.STATUS_OPEN,
        shared = true,
        registrationStart = 1900000000000L,
        registrationEnd = 1900600000000L,
        bookingStart = 1901000000000L,
        bookingEnd = 1901200000000L,
    )
    private val lakeside = Campsite(id = "camp-lakeside", ownerId = "owner-device-b2c1", name = "Lakeside Pines")

    // US 02.04.08
    @Test
    fun ownerCreatesASharedLottery() {
        var saved: Lottery? = null
        var closed = false
        composeRule.setContent {
            MaterialTheme {
                CreateLotteryScreen(
                    campsite = Part4Fixtures.jasper,
                    onPublish = { settings ->
                        saved = settings
                        "lottery-jasper-may"
                    },
                    onPublished = { closed = true },
                    onCancel = {},
                    initial = sharedLottery.copy(shared = false),
                )
            }
        }
        composeRule.onNodeWithText("Let other campsite owners add their campsites").performScrollTo().performClick()
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { closed }
        assertTrue(saved!!.shared)
    }

    @Test
    fun updatingASharedLotteryKeepsOtherOwnersCampsites() {
        var saved: Lottery? = null
        var closed = false
        val joined = sharedLottery.copy(
            extraCampsiteIds = listOf(lakeside.id),
            extraCampsiteNames = listOf(lakeside.name),
            extraCampsiteOwnerIds = listOf(lakeside.ownerId),
        )
        composeRule.setContent {
            MaterialTheme {
                CreateLotteryScreen(
                    campsite = Part4Fixtures.jasper,
                    onPublish = { settings ->
                        saved = settings
                        joined.id
                    },
                    onPublished = { closed = true },
                    onCancel = {},
                    initial = joined,
                    otherCampsites = listOf(Part4Fixtures.banff),
                )
            }
        }
        composeRule.onNodeWithText("Added by other owners: Lakeside Pines").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Banff Tunnel Mountain").performScrollTo().performClick()
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { closed }
        assertEquals(listOf("camp-banff-tunnel", "camp-lakeside"), saved!!.extraCampsiteIds)
        assertEquals(listOf(Part4Fixtures.OWNER_ID, "owner-device-b2c1"), saved!!.extraCampsiteOwnerIds)
    }

    @Test
    fun anotherOwnerAddsTheirCampsiteToASharedLottery() {
        var added: Pair<Lottery, Campsite>? = null
        composeRule.setContent {
            MaterialTheme {
                OwnerLotteriesScreen(
                    lotteries = emptyList(),
                    onOpenWaitingList = {},
                    ownerId = lakeside.ownerId,
                    joinable = listOf(sharedLottery),
                    myCampsites = listOf(lakeside),
                    onJoin = { lottery, campsite -> added = lottery to campsite },
                )
            }
        }
        composeRule.onNodeWithText("Shared lotteries you can join").assertIsDisplayed()
        composeRule.onNodeWithText("Jasper Riverside").assertIsDisplayed()
        composeRule.onNodeWithText("Add my campsite").performClick()
        composeRule.onNodeWithText("Add a campsite to this lottery").assertIsDisplayed()
        composeRule.onNodeWithText("Add campsite").performClick()
        assertEquals(sharedLottery to lakeside, added)
    }

    @Test
    fun ownerWithNoFreeCampsiteCannotJoin() {
        composeRule.setContent {
            MaterialTheme {
                OwnerLotteriesScreen(
                    lotteries = emptyList(),
                    onOpenWaitingList = {},
                    ownerId = lakeside.ownerId,
                    joinable = listOf(sharedLottery),
                    myCampsites = emptyList(),
                    onJoin = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Add my campsite").assertIsNotEnabled()
        composeRule.onNodeWithText("All your campsites are already in a lottery.").assertIsDisplayed()
    }

    @Test
    fun onlyTheCreatorCanEditASharedLottery() {
        val joined = sharedLottery.copy(
            extraCampsiteIds = listOf(lakeside.id),
            extraCampsiteNames = listOf(lakeside.name),
            extraCampsiteOwnerIds = listOf(lakeside.ownerId),
        )
        composeRule.setContent {
            MaterialTheme {
                OwnerLotteriesScreen(
                    lotteries = listOf(joined),
                    onOpenWaitingList = {},
                    onEdit = {},
                    ownerId = lakeside.ownerId,
                )
            }
        }
        composeRule.onNodeWithText("Shared").assertIsDisplayed()
        composeRule.onNodeWithText("Also for: Lakeside Pines").assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit lottery").assertCountEquals(0)
    }
}
