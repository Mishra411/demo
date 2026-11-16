/*
 * EntrantPart4IntentTest.kt
 * UI (intent) tests for the entrant stories added in Part 4: choosing dates
 * and a campsite after winning (US 01.04.01, 01.04.02), confirming rules
 * (US 02.11.01), declining (US 01.04.03), past and cancelled reservations
 * (US 01.06.02), cancelling (US 01.07.01), rating a campsite (US 01.13.01),
 * the availability calendar and the notification switch, policy and profile
 * deletion (US 01.10.01, 01.11.01, 01.08.01).
 * Screens get canned data, so no Firestore is needed.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.code.User
import com.example.code.UserRole
import com.example.code.controller.ReviewController
import com.example.code.model.Days
import com.example.code.model.Reservation
import com.example.code.model.Review
import com.example.code.navigation.AppMode
import com.example.code.ui.common.PolicyScreen
import com.example.code.ui.common.ProfileScreen
import com.example.code.ui.entrant.AvailabilityCard
import com.example.code.ui.entrant.EntrantReservationsScreen
import com.example.code.ui.entrant.ReserveStayDialog
import com.example.code.ui.entrant.ReviewsSection
import com.example.code.ui.entrant.WinnerReservationCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntrantPart4IntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val nov14 = Days.of(2026, 10, 14)

    // US 01.04.01, 02.11.01
    @Test
    fun winnerChoosesDaysConfirmsRulesAndReserves() {
        var confirmed: Triple<String, Long, Long>? = null
        composeRule.setContent {
            ReserveStayDialog(
                lottery = Part4Fixtures.drawnLottery,
                campsites = listOf(Part4Fixtures.jasper.copy(availableFrom = "", availableTo = "", availableWeekdays = emptyList())),
                reservations = emptyList(),
                busy = false,
                error = null,
                onConfirm = { id, first, last -> confirmed = Triple(id, first, last) },
                onDismiss = {},
                timeZone = Part4Fixtures.mountain,
            )
        }

        // The campsite's rules and policy are shown before reserving.
        composeRule.onNodeWithText("No fires after 11 pm.").assertExists()
        composeRule.onNodeWithText("Free cancellation up to 7 days before.").assertExists()
        composeRule.onNodeWithText("Confirm reservation").assertIsNotEnabled()

        composeRule.onNodeWithTag("day-$nov14").performScrollTo().performClick()
        composeRule.onNodeWithTag("day-${nov14 + 1}").performScrollTo().performClick()
        composeRule.onNodeWithTag("stayChoice").performScrollTo().assertTextEquals("Nov 14, 2026 – Nov 15, 2026 · 2 days")

        // Still disabled until the rules are confirmed.
        composeRule.onNodeWithText("Confirm reservation").assertIsNotEnabled()
        composeRule.onNodeWithText("I have read and agree to the campsite rules and policies")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Confirm reservation").performScrollTo().assertIsEnabled().performClick()

        assertEquals(Triple("camp-jasper-riverside", nov14, nov14 + 1), confirmed)
    }

    // US 01.04.01: days booked by another winner cannot be chosen.
    @Test
    fun bookedDaysCannotBeChosen() {
        val booked = Part4Fixtures.priyaReservation.copy(
            id = "other",
            entrantId = "device-liam-02",
            startDate = Days.startOfDay(nov14, Part4Fixtures.mountain),
            endDate = Days.endOfDay(nov14, Part4Fixtures.mountain),
        )
        composeRule.setContent {
            ReserveStayDialog(
                lottery = Part4Fixtures.drawnLottery,
                campsites = emptyList(),
                reservations = listOf(booked),
                busy = false,
                error = null,
                onConfirm = { _, _, _ -> },
                onDismiss = {},
                timeZone = Part4Fixtures.mountain,
            )
        }

        composeRule.onNodeWithContentDescription("Nov 14, 2026, booked").assertExists()
        composeRule.onNodeWithContentDescription("Nov 15, 2026, available").assertExists()
        composeRule.onAllNodes(hasClickAction().and(hasTestTag("day-$nov14"))).assertCountEquals(0)
        composeRule.onNodeWithText("2 of 3 days can still be reserved.", substring = true).assertExists()
    }

    // US 01.04.02
    @Test
    fun winnerChoosesACampsiteOfAMultiCampsiteLottery() {
        var confirmed: Triple<String, Long, Long>? = null
        val multi = Part4Fixtures.drawnLottery.copy(
            extraCampsiteIds = listOf(Part4Fixtures.banff.id),
            extraCampsiteNames = listOf(Part4Fixtures.banff.name),
        )
        composeRule.setContent {
            ReserveStayDialog(
                lottery = multi,
                campsites = listOf(Part4Fixtures.banff),
                reservations = emptyList(),
                busy = false,
                error = null,
                onConfirm = { id, first, last -> confirmed = Triple(id, first, last) },
                onDismiss = {},
                timeZone = Part4Fixtures.mountain,
            )
        }

        composeRule.onNodeWithText("Banff Tunnel Mountain").performClick()
        // The rules follow the chosen campsite.
        composeRule.onNodeWithText("Store food in the bear locker.").assertExists()
        composeRule.onNodeWithTag("day-${nov14 + 2}").performScrollTo().performClick()
        composeRule.onNodeWithText("I have read and agree to the campsite rules and policies")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Confirm reservation").performScrollTo().performClick()

        assertEquals(Triple("camp-banff-tunnel", nov14 + 2, nov14 + 2), confirmed)
    }

    // US 01.04.03
    @Test
    fun winnerCanDecline() {
        var declined = false
        composeRule.setContent {
            WinnerReservationCard(
                myEntry = Part4Fixtures.priyaWon,
                reservation = null,
                busy = false,
                statusMessage = null,
                onAccept = {},
                onDecline = { declined = true },
            )
        }
        composeRule.onNodeWithText("Decline").performClick()
        assertTrue(declined)
    }

    @Test
    fun declinedWinShowsItsStatusInsteadOfButtons() {
        composeRule.setContent {
            WinnerReservationCard(
                myEntry = Part4Fixtures.priyaWon,
                reservation = Part4Fixtures.priyaReservation.copy(status = Reservation.STATUS_DECLINED),
                busy = false,
                statusMessage = null,
                onAccept = {},
                onDecline = {},
            )
        }
        composeRule.onNodeWithText("You declined this win.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Accept reservation").assertCountEquals(0)
    }

    // US 01.06.02
    @Test
    fun entrantSeesPastAndCancelledReservations() {
        val past = Part4Fixtures.priyaReservation.copy(id = "past", campsiteName = "Elk Island Astotin", startDate = 1_000, endDate = 2_000)
        val cancelled = Part4Fixtures.priyaReservation.copy(
            id = "cancelled", campsiteName = "Banff Tunnel Mountain", status = Reservation.STATUS_CANCELLED,
        )
        composeRule.setContent {
            EntrantReservationsScreen(
                reservations = listOf(Part4Fixtures.priyaReservation),
                past = listOf(past),
                cancelled = listOf(cancelled),
            )
        }

        composeRule.onNodeWithText("Jasper Riverside").assertIsDisplayed()
        composeRule.onNodeWithText("Past (1)").performClick()
        composeRule.onNodeWithText("Elk Island Astotin").assertIsDisplayed()
        composeRule.onAllNodesWithText("Jasper Riverside").assertCountEquals(0)
        composeRule.onNodeWithText("Cancelled (1)").performClick()
        composeRule.onNodeWithText("Banff Tunnel Mountain").assertIsDisplayed()
        composeRule.onNodeWithText("Download CSV").assertIsEnabled()
    }

    // US 01.07.01
    @Test
    fun entrantCancelsAReservationAfterConfirming() {
        var cancelled: Reservation? = null
        composeRule.setContent {
            EntrantReservationsScreen(
                reservations = listOf(Part4Fixtures.priyaReservation),
                onCancel = { cancelled = it },
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Cancel this reservation?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep it").performClick()
        assertNull(cancelled)

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Cancel reservation").performClick()
        assertEquals(Part4Fixtures.priyaReservation, cancelled)
    }

    // US 01.13.01
    @Test
    fun entrantRatesACampsite() {
        var submitted: Pair<Int, String>? = null
        composeRule.setContent {
            ReviewsSection(
                threads = emptyList(),
                myId = Part4Fixtures.PRIYA_ID,
                busy = false,
                message = null,
                onSubmit = { stars, text -> submitted = stars to text },
                onReply = { _, _ -> },
                onReact = { _, _ -> },
            )
        }

        composeRule.onNodeWithText("Post review").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("4 stars").performClick()
        composeRule.onNodeWithText("Write a review (optional)").performTextInput("Loved the river.")
        composeRule.onNodeWithText("Post review").performClick()

        assertEquals(4 to "Loved the river.", submitted)
    }

    // Advanced review system: reply threads and reactions.
    @Test
    fun reviewsShowRepliesAndTakeReactionsAndReplies() {
        val review = Review(
            id = "device-liam-02", campsiteId = Part4Fixtures.jasper.id, authorId = "device-liam-02",
            authorName = "Liam Chen", stars = 5, text = "Best site on the river.",
            reactions = mapOf("helpful" to listOf("device-sofia-03")), createdAt = 1_000,
        )
        val reply = Review(
            id = "reply-1", campsiteId = Part4Fixtures.jasper.id, authorId = Part4Fixtures.OWNER_ID,
            authorName = "Jasper Camps", text = "Thanks for staying!", parentId = review.id, createdAt = 2_000,
        )
        var reacted: Pair<String, String>? = null
        var replied: Pair<String, String>? = null
        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReviewsSection(
                    threads = ReviewController.threads(listOf(review, reply)),
                    myId = Part4Fixtures.PRIYA_ID,
                    busy = false,
                    message = null,
                    onSubmit = { _, _ -> },
                    onReply = { parent, text -> replied = parent.id to text },
                    onReact = { target, key -> reacted = target.id to key },
                )
            }
        }

        composeRule.onNodeWithText("Best site on the river.").assertExists()
        composeRule.onNodeWithText("Thanks for staying!").assertExists()
        composeRule.onNodeWithText("Helpful 1").assertExists()

        composeRule.onNodeWithTag("react-love-device-liam-02").performScrollTo().performClick()
        assertEquals("device-liam-02" to "love", reacted)

        composeRule.onNodeWithText("Reply").performScrollTo().performClick()
        composeRule.onNodeWithText("Your reply").performScrollTo().performTextInput("Was it busy?")
        composeRule.onNodeWithText("Send reply").performScrollTo().performClick()
        assertEquals("device-liam-02" to "Was it busy?", replied)
    }

    // Availability schedule: weekends in May 2027 on the calendar.
    @Test
    fun availabilityCalendarShowsOpenAndClosedDays() {
        composeRule.setContent {
            AvailabilityCard(Part4Fixtures.jasper, lotteries = emptyList(), reservations = emptyList())
        }
        composeRule.onNodeWithText("May 2027").assertExists()
        composeRule.onNodeWithContentDescription("May 1, 2027, available").assertExists()
        composeRule.onNodeWithContentDescription("May 2, 2027, available").assertExists()
        composeRule.onNodeWithContentDescription("May 3, 2027, not available").assertExists()
        composeRule.onNodeWithContentDescription("Next month").performClick()
        composeRule.onNodeWithText("Jun 2027").assertExists()
        composeRule.onNodeWithContentDescription("Jun 5, 2027, not available").assertExists()
    }

    private val priya = User(
        deviceId = Part4Fixtures.PRIYA_ID,
        name = "Priya Shah",
        email = "priya@example.com",
        phoneNumber = "7805550101",
        roles = setOf(UserRole.ENTRANT),
    )

    // US 01.10.01
    @Test
    fun entrantOptsOutOfNotifications() {
        var enabled: Boolean? = null
        composeRule.setContent {
            ProfileScreen(
                user = priya,
                activeMode = AppMode.ENTRANT,
                availableModes = listOf(AppMode.ENTRANT),
                isSaving = false,
                errorMessage = null,
                onSaveProfile = { _, _, _ -> },
                onSwitchMode = {},
                onSetNotifications = { enabled = it },
            )
        }
        composeRule.onNodeWithTag("notificationsSwitch").performScrollTo().assertIsOn().performClick()
        assertEquals(false, enabled)
    }

    @Test
    fun optedOutProfileShowsTheSwitchOff() {
        priya.updateNotificationPreference(false)
        composeRule.setContent {
            ProfileScreen(
                user = priya,
                activeMode = AppMode.ENTRANT,
                availableModes = listOf(AppMode.ENTRANT),
                isSaving = false,
                errorMessage = null,
                onSaveProfile = { _, _, _ -> },
                onSwitchMode = {},
                onSetNotifications = {},
            )
        }
        composeRule.onNodeWithTag("notificationsSwitch").performScrollTo().assertIsOff()
    }

    // US 01.08.01
    @Test
    fun entrantDeletesTheirProfileAfterConfirming() {
        var deleted = false
        composeRule.setContent {
            ProfileScreen(
                user = priya,
                activeMode = AppMode.ENTRANT,
                availableModes = listOf(AppMode.ENTRANT),
                isSaving = false,
                errorMessage = null,
                onSaveProfile = { _, _, _ -> },
                onSwitchMode = {},
                onDeleteProfile = { deleted = true },
            )
        }
        composeRule.onNodeWithText("Delete my profile").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete your profile?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep profile").performClick()
        assertFalse(deleted)

        composeRule.onNodeWithText("Delete my profile").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete profile").performClick()
        assertTrue(deleted)
    }

    // US 01.11.01
    @Test
    fun appPolicyIsReadableAndGoesBack() {
        var back = false
        composeRule.setContent { PolicyScreen(onBack = { back = true }) }
        composeRule.onNodeWithText("App policy and guidelines").assertIsDisplayed()
        composeRule.onNodeWithText("How the lottery works").assertExists()
        composeRule.onNodeWithText("Community standards").assertExists()
        composeRule.onNodeWithText("Back").performClick()
        assertTrue(back)
    }
}
