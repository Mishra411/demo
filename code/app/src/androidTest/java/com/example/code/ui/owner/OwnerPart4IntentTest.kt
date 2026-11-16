/*
 * OwnerPart4IntentTest.kt
 * UI (intent) tests for the owner stories added in Part 4: editing a
 * campsite (US 02.01.03), availability schedule and price on the form,
 * updating an open lottery (US 02.04.06), one lottery for several
 * campsites (US 02.04.02), past and cancelled reservations (US 02.07.02,
 * 02.07.03), sent notifications (US 02.10.02) and messaging a guest
 * (US 02.12.01).
 * Screens get canned data, so no Firestore is needed.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui.owner

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.code.model.AppNotification
import com.example.code.model.Campsite
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.ui.Part4Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerPart4IntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    // US 02.01.03
    @Test
    fun ownerEditsACampsiteAndKeepsItsIdentity() {
        var saved: Campsite? = null
        composeRule.setContent {
            MaterialTheme {
                RegisterCampsiteScreen(
                    onRegister = { campsite, _ -> saved = campsite },
                    onBack = {},
                    initial = Part4Fixtures.jasper,
                )
            }
        }

        composeRule.onNodeWithText("Edit campsite").assertIsDisplayed()
        composeRule.onNodeWithTag("nameInput").assertTextContains("Jasper Riverside")
        composeRule.onNodeWithTag("priceInput").performScrollTo().assertTextContains("65")

        composeRule.onNodeWithTag("descriptionInput").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("descriptionInput").performTextInput("Riverside site with showers.")
        composeRule.onNodeWithTag("priceInput").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("priceInput").performTextInput("70")
        composeRule.onNodeWithText("Save changes").performClick()

        val edited = saved!!
        assertEquals("Riverside site with showers.", edited.description)
        assertEquals(70.0, edited.price!!, 0.0)
        // Identity, rating and everything not edited stay the same.
        assertEquals(Part4Fixtures.jasper.copy(description = edited.description, price = 70.0), edited)
    }

    // Availability schedule and price: weekends in May 2027 at $65 per night.
    @Test
    fun ownerSetsPriceAndWeekendAvailabilityWhenRegistering() {
        var saved: Campsite? = null
        composeRule.setContent {
            MaterialTheme { RegisterCampsiteScreen(onRegister = { campsite, _ -> saved = campsite }, onBack = {}) }
        }

        composeRule.onNodeWithTag("nameInput").performTextInput("Jasper Riverside")
        composeRule.onNodeWithTag("locationInput").performTextInput("Jasper, AB")
        composeRule.onNodeWithTag("descriptionInput").performTextInput("Quiet riverside site.")
        composeRule.onNodeWithTag("maxOccupancyInput").performTextInput("4")
        composeRule.onNodeWithTag("latitudeInput").performScrollTo().performTextInput("52.8737")
        composeRule.onNodeWithTag("longitudeInput").performScrollTo().performTextInput("-118.0814")
        composeRule.onNodeWithTag("priceInput").performScrollTo().performTextInput("65")
        composeRule.onNodeWithTag("availableFromInput").performScrollTo().performTextInput("2027-05-01")
        composeRule.onNodeWithTag("availableToInput").performScrollTo().performTextInput("2027-05-31")
        composeRule.onNodeWithTag("weekday-7").performScrollTo().performClick()
        composeRule.onNodeWithTag("weekday-1").performScrollTo().performClick()
        composeRule.onNodeWithTag("registerButton").performClick()

        val campsite = saved!!
        assertEquals(65.0, campsite.price!!, 0.0)
        assertEquals("CAD", campsite.currency)
        assertEquals("2027-05-01", campsite.availableFrom)
        assertEquals("2027-05-31", campsite.availableTo)
        assertEquals(listOf(1, 7), campsite.availableWeekdays)
    }

    @Test
    fun badAvailabilityDatesKeepTheFormOpenWithAMessage() {
        var saved: Campsite? = null
        composeRule.setContent {
            MaterialTheme {
                RegisterCampsiteScreen(
                    onRegister = { campsite, _ -> saved = campsite },
                    onBack = {},
                    initial = Part4Fixtures.jasper,
                )
            }
        }
        composeRule.onNodeWithTag("availableToInput").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("availableToInput").performTextInput("2027-04-01")
        composeRule.onNodeWithText("Save changes").performClick()

        composeRule.onNodeWithText("The last available date must be on or after the first.").assertIsDisplayed()
        assertNull(saved)
    }

    private val openLottery = Part4Fixtures.drawnLottery.copy(
        status = Lottery.STATUS_OPEN,
        registrationStart = 1900000000000L,
        registrationEnd = 1900600000000L,
        bookingStart = 1901000000000L,
        bookingEnd = 1901200000000L,
        winnerCount = 2,
        waitlistCapacity = 4,
        waitingListCount = 3,
    )

    // US 02.04.06
    @Test
    fun ownerUpdatesAnOpenLottery() {
        var saved: Lottery? = null
        var closed = false
        composeRule.setContent {
            MaterialTheme {
                CreateLotteryScreen(
                    campsite = Part4Fixtures.jasper,
                    onPublish = { settings ->
                        saved = settings
                        "lottery-jasper-nov"
                    },
                    onPublished = { closed = true },
                    onCancel = {},
                    initial = openLottery,
                )
            }
        }

        composeRule.onNodeWithText("Update lottery").assertIsDisplayed()
        composeRule.onNodeWithText("Number of winners").performScrollTo().performTextClearance()
        composeRule.onNodeWithText("Number of winners").performTextInput("3")
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { closed }

        assertEquals(3, saved!!.winnerCount)
        // The dates the owner did not touch are kept.
        assertEquals(openLottery.registrationEnd, saved!!.registrationEnd)
        assertEquals(openLottery.bookingStart, saved!!.bookingStart)
    }

    @Test
    fun capacityBelowEntrantsAlreadyJoinedIsRejected() {
        var saved: Lottery? = null
        composeRule.setContent {
            MaterialTheme {
                CreateLotteryScreen(
                    campsite = Part4Fixtures.jasper,
                    onPublish = { settings ->
                        saved = settings
                        "lottery-jasper-nov"
                    },
                    onPublished = {},
                    onCancel = {},
                    initial = openLottery,
                )
            }
        }
        composeRule.onNodeWithText("Waiting-list capacity", substring = true).performScrollTo().performTextClearance()
        composeRule.onNodeWithText("Waiting-list capacity", substring = true).performTextInput("2")
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()

        composeRule.onNodeWithText("3 entrants already joined, so capacity must be at least 3.")
            .performScrollTo().assertIsDisplayed()
        assertNull(saved)
    }

    // US 02.04.02
    @Test
    fun ownerAddsAnotherCampsiteToALottery() {
        var saved: Lottery? = null
        var closed = false
        composeRule.setContent {
            MaterialTheme {
                CreateLotteryScreen(
                    campsite = Part4Fixtures.jasper,
                    onPublish = { settings ->
                        saved = settings
                        "lottery-jasper-nov"
                    },
                    onPublished = { closed = true },
                    onCancel = {},
                    initial = openLottery,
                    otherCampsites = listOf(Part4Fixtures.jasper, Part4Fixtures.banff),
                )
            }
        }
        composeRule.onNodeWithText("More campsites in this lottery (optional)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Banff Tunnel Mountain").performScrollTo().performClick()
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { closed }

        assertEquals(listOf("camp-banff-tunnel"), saved!!.extraCampsiteIds)
        assertEquals(listOf("Banff Tunnel Mountain"), saved!!.extraCampsiteNames)
    }

    @Test
    fun lotteriesTabOffersEditOnlyWhileOpen() {
        var edited: Lottery? = null
        var openedSent = false
        val drawn = Part4Fixtures.drawnLottery.copy(id = "lottery-banff-nov", campsiteName = "Banff Tunnel Mountain")
        composeRule.setContent {
            MaterialTheme {
                OwnerLotteriesScreen(
                    lotteries = listOf(openLottery, drawn),
                    onOpenWaitingList = {},
                    onEdit = { edited = it },
                    onOpenSent = { openedSent = true },
                )
            }
        }
        composeRule.onAllNodesWithText("Edit lottery").assertCountEquals(1)
        composeRule.onNodeWithText("Edit lottery").performClick()
        assertEquals(openLottery, edited)
        composeRule.onNodeWithText("Sent notifications").performClick()
        assertTrue(openedSent)
    }

    // US 02.07.02, 02.07.03, 02.12.01
    @Test
    fun ownerSeesPastAndCancelledReservationsAndMessagesAGuest() {
        val past = Part4Fixtures.priyaReservation.copy(id = "past", entrantName = "Liam Chen", startDate = 1_000, endDate = 2_000)
        val cancelled = Part4Fixtures.priyaReservation.copy(
            id = "cancelled", entrantName = "Sofia Martins", status = Reservation.STATUS_REJECTED,
            rejectionReason = "Site flooded",
        )
        var messaged: Reservation? = null
        composeRule.setContent {
            MaterialTheme {
                OwnerReservationsScreen(
                    bookedDates = emptyList(),
                    reservations = listOf(Part4Fixtures.priyaReservation),
                    statusMessage = null,
                    onReject = { _, _ -> },
                    past = listOf(past),
                    cancelled = listOf(cancelled),
                    onMessage = { messaged = it },
                )
            }
        }

        composeRule.onNodeWithText("Priya Shah").assertIsDisplayed()
        composeRule.onNodeWithText("Message guest").performClick()
        assertEquals(Part4Fixtures.priyaReservation, messaged)

        composeRule.onNodeWithText("Past (1)").performClick()
        composeRule.onNodeWithText("Liam Chen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Priya Shah").assertCountEquals(0)

        composeRule.onNodeWithText("Cancelled (1)").performClick()
        composeRule.onNodeWithText("Sofia Martins").assertIsDisplayed()
        composeRule.onNodeWithText("Site flooded", substring = true).assertIsDisplayed()
    }

    @Test
    fun ownerInboxShowsGuestUpdatesAndOpensAConversation() {
        val fromGuest = AppNotification(
            id = "n1", recipientId = Part4Fixtures.OWNER_ID, senderId = Part4Fixtures.PRIYA_ID,
            reservationId = Part4Fixtures.priyaReservation.id, campsiteName = "Jasper Riverside",
            type = AppNotification.TYPE_DIRECT_MESSAGE, title = "Priya Shah · Jasper Riverside",
            message = "Is there firewood on site?", createdAt = 1794232800000L,
        )
        var opened: AppNotification? = null
        var shown = false
        composeRule.setContent {
            MaterialTheme {
                OwnerReservationsScreen(
                    bookedDates = emptyList(),
                    reservations = emptyList(),
                    statusMessage = null,
                    onReject = { _, _ -> },
                    inbox = listOf(fromGuest),
                    onOpenInboxMessage = { opened = it },
                    onInboxShown = { shown = true },
                )
            }
        }
        composeRule.onNodeWithText("Inbox (1)").performClick()
        composeRule.onNodeWithText("Is there firewood on site?").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(shown) }
        composeRule.onNodeWithText("Reply").performClick()
        assertEquals(fromGuest, opened)
    }

    // US 02.10.02
    @Test
    fun ownerSeesTheNotificationsTheySent() {
        fun toWaitingList(id: String, recipient: String) = AppNotification(
            id = id, recipientId = recipient, senderId = Part4Fixtures.OWNER_ID, lotteryId = "lottery-jasper-nov",
            campsiteName = "Jasper Riverside", type = AppNotification.TYPE_WAITLIST_MESSAGE,
            title = "Jasper Riverside", message = "The draw is on Friday.", createdAt = 1794000000000L,
        )
        var back = false
        composeRule.setContent {
            MaterialTheme {
                SentNotificationsScreen(
                    sent = listOf(toWaitingList("n1", "device-priya-01"), toWaitingList("n2", "device-liam-02")),
                    onBack = { back = true },
                )
            }
        }
        composeRule.onNodeWithText("Sent notifications").assertIsDisplayed()
        // One message to two entrants is listed once.
        composeRule.onAllNodesWithText("The draw is on Friday.").assertCountEquals(1)
        composeRule.onNodeWithText("Waiting list message · 2 recipients", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        assertTrue(back)
    }

    @Test
    fun noSentNotificationsShowsAnEmptyState() {
        composeRule.setContent { MaterialTheme { SentNotificationsScreen(sent = emptyList(), onBack = {}) } }
        composeRule.onNodeWithText("You have not sent any notifications yet.").assertIsDisplayed()
    }
}
