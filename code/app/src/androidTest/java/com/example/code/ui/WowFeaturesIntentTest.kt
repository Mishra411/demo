/*
 * WowFeaturesIntentTest.kt
 * UI (intent) tests for the WOW features: match score, featured carousel,
 * map view within a radius, reservation map, accessibility mode and the
 * messaging system (US 02.12.01). The map itself is replaced by a stand-in
 * that lists its pins, so no map tiles or location are needed.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.code.model.AccessibilitySettings
import com.example.code.model.Campsite
import com.example.code.model.Featured
import com.example.code.model.MapPoint
import com.example.code.model.Message
import com.example.code.ui.common.AccessibilityScreen
import com.example.code.ui.common.ConversationScreen
import com.example.code.ui.entrant.CampsiteRadiusMap
import com.example.code.ui.entrant.ExploreScreen
import com.example.code.ui.entrant.FeaturedCarousel
import com.example.code.ui.entrant.MapRenderer
import com.example.code.ui.entrant.ReservationMapScreen
import com.example.code.ui.theme.CodeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WowFeaturesIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Stand-in for the map: lists each pin so tests can see and tap it. */
    private val listMap: MapRenderer = { points, _, radiusKm, onPointClick ->
        Column {
            Text("map radius ${radiusKm?.toInt() ?: "none"}")
            points.forEach { point ->
                TextButton(onClick = { onPointClick(point) }) {
                    Text("pin ${point.title} ${point.subtitle}")
                }
            }
        }
    }

    // Edmonton
    private val home = 53.5461 to -113.4938
    private val elkIsland = Campsite(
        id = "camp-elk-island", name = "Elk Island Astotin", location = "Elk Island, AB",
        latitude = 53.6836, longitude = -112.8650, maxOccupancy = 8,
    )

    // Match score
    @Test
    fun filteredCampsitesShowAMatchScoreBestFirst() {
        val sites = listOf(
            Part4Fixtures.jasper.copy(location = "Near Jasper townsite", maxOccupancy = 12, rating = null),
            Part4Fixtures.jasper.copy(id = "camp-jasper-pines", name = "Jasper Pines", location = "Jasper", rating = 5.0),
        )
        composeRule.setContent { MaterialTheme { ExploreScreen(sites, {}) } }
        composeRule.onAllNodesWithText("% match", substring = true).assertCountEquals(0)

        composeRule.onNodeWithText("Filters").performClick()
        composeRule.onNodeWithText("Location").performScrollTo().performTextInput("Jasper")
        composeRule.onNodeWithText("Guests").performScrollTo().performTextInput("4")
        composeRule.onNodeWithText("Apply filters").performClick()

        composeRule.onNodeWithText("Sorted by best match for your filters").assertIsDisplayed()
        // Exact location, exact group size and a 5-star rating: listed first.
        composeRule.onNodeWithText("100% match").assertIsDisplayed()
        composeRule.onNodeWithText("2 campsites found").assertIsDisplayed()
    }

    // Featured carousel
    @Test
    fun featuredCarouselShowsCampsitesMatchingTheEntrantsInterests() {
        var opened: Campsite? = null
        val featured = Featured.pick(listOf(Part4Fixtures.banff, Part4Fixtures.jasper), setOf("river"), emptySet())
        composeRule.setContent { MaterialTheme { FeaturedCarousel(featured, { opened = it }) } }

        composeRule.onNodeWithText("Featured for you").assertIsDisplayed()
        composeRule.onNodeWithText("Featured: Jasper Riverside").assertIsDisplayed()
        composeRule.onNodeWithText("Matches: river").assertIsDisplayed()
        composeRule.onNodeWithText("Featured: Jasper Riverside").performClick()
        assertEquals(Part4Fixtures.jasper, opened)
    }

    @Test
    fun exploreShowsTheCarouselAboveTheList() {
        val featured = Featured.pick(listOf(Part4Fixtures.jasper), setOf("river"), emptySet())
        composeRule.setContent {
            MaterialTheme { ExploreScreen(listOf(Part4Fixtures.jasper, Part4Fixtures.banff), {}, featured = featured) }
        }
        composeRule.onNodeWithTag("featuredCarousel").assertIsDisplayed()
        composeRule.onNodeWithText("2 campsites found").assertIsDisplayed()
    }

    // Map view
    @Test
    fun mapViewShowsCampsitesWithinTheChosenRadius() {
        var radius by mutableStateOf(100f)
        var opened: Campsite? = null
        composeRule.setContent {
            MaterialTheme {
                CampsiteRadiusMap(
                    campsites = listOf(Part4Fixtures.jasper, elkIsland),
                    origin = home,
                    radiusKm = radius,
                    onRadiusChange = { radius = it },
                    locationMessage = null,
                    onUseMyLocation = {},
                    onCampsiteClick = { opened = it },
                    map = listMap,
                )
            }
        }

        composeRule.onNodeWithText("Within 100 km of you").assertIsDisplayed()
        composeRule.onNodeWithText("1 campsite on the map").assertIsDisplayed()
        composeRule.onNodeWithText("pin Elk Island Astotin", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("pin Jasper Riverside", substring = true).assertCountEquals(0)

        // Jasper is about 313 km from Edmonton.
        composeRule.runOnIdle { radius = 400f }
        composeRule.onNodeWithText("2 campsites on the map").assertIsDisplayed()
        composeRule.onNodeWithText("map radius 400").assertIsDisplayed()
        composeRule.onNodeWithText("pin Jasper Riverside", substring = true).performClick()
        assertEquals(Part4Fixtures.jasper, opened)
    }

    @Test
    fun mapViewWithoutALocationShowsEveryCampsiteAndAsksForLocation() {
        var asked = false
        composeRule.setContent {
            MaterialTheme {
                CampsiteRadiusMap(
                    campsites = listOf(Part4Fixtures.jasper, elkIsland),
                    origin = null,
                    radiusKm = 100f,
                    onRadiusChange = {},
                    locationMessage = null,
                    onUseMyLocation = { asked = true },
                    onCampsiteClick = {},
                    map = listMap,
                )
            }
        }
        composeRule.onNodeWithText("Showing all campsites.").assertIsDisplayed()
        composeRule.onNodeWithText("2 campsites on the map").assertIsDisplayed()
        composeRule.onNodeWithText("map radius none").assertIsDisplayed()
        composeRule.onNodeWithText("Use my location").performClick()
        assertTrue(asked)
    }

    // Reservation map
    @Test
    fun reservationMapShowsActiveAndPastStays() {
        val points = listOf(
            MapPoint("r1", "Jasper Riverside", "Nov 14 – Nov 16", 52.8737, -118.0814, highlight = true),
            MapPoint("r2", "Banff Tunnel Mountain", "Aug 1 – Aug 3", 51.1905, -115.5370, highlight = false),
        )
        var back = false
        composeRule.setContent {
            MaterialTheme { ReservationMapScreen(points, missing = 1, onBack = { back = true }, map = listMap) }
        }
        composeRule.onNodeWithText("Reservation map").assertIsDisplayed()
        composeRule.onNodeWithText("1 active and 1 past stays").assertIsDisplayed()
        composeRule.onNodeWithText("pin Jasper Riverside Nov 14 – Nov 16").assertIsDisplayed()
        composeRule.onNodeWithText("1 reservation is not shown because the campsite has no map location.").assertExists()
        composeRule.onNodeWithText("Back").performClick()
        assertTrue(back)
    }

    @Test
    fun reservationMapWithNoStaysSaysSo() {
        composeRule.setContent {
            MaterialTheme { ReservationMapScreen(emptyList(), missing = 0, onBack = {}, map = listMap) }
        }
        composeRule.onNodeWithText("You have no reservations to show on the map yet.").assertIsDisplayed()
    }

    // Accessibility mode
    @Test
    fun accessibilityChoicesAreReportedAndApplied() {
        var settings by mutableStateOf(AccessibilitySettings())
        composeRule.setContent {
            CodeTheme(settings = settings, darkTheme = false) {
                AccessibilityScreen(settings = settings, onChange = { settings = it }, onBack = {})
            }
        }

        composeRule.onNodeWithText("Colour-blind friendly colours").performClick()
        composeRule.onNodeWithTag("text-Large").performScrollTo().performClick()
        composeRule.onNodeWithTag("ui-Extra large").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(AccessibilitySettings(colorBlind = true, textScale = 1.2f, uiScale = 1.4f), settings)
        }
        composeRule.onNodeWithTag("text-Large").assertIsSelected()

        composeRule.onNodeWithText("Reset to defaults").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(AccessibilitySettings(), settings) }
        composeRule.onNodeWithTag("text-Default").assertIsSelected()
    }

    @Test
    fun largerComponentSizeMakesButtonsBigger() {
        var settings by mutableStateOf(AccessibilitySettings())
        composeRule.setContent {
            CodeTheme(settings = settings, darkTheme = false) {
                AccessibilityScreen(settings = settings, onChange = { settings = it }, onBack = {})
            }
        }
        val before = composeRule.onNodeWithText("Back").fetchSemanticsNode().size.height
        composeRule.runOnIdle { settings = AccessibilitySettings(uiScale = 1.4f) }
        val after = composeRule.onNodeWithText("Back").fetchSemanticsNode().size.height
        assertTrue("expected $after > $before", after > before)
    }

    // Messaging system, US 02.12.01
    @Test
    fun ownerAndGuestMessageEachOther() {
        var messages by mutableStateOf(
            listOf(
                Message(
                    id = "m1", reservationId = Part4Fixtures.priyaReservation.id, campsiteName = "Jasper Riverside",
                    senderId = Part4Fixtures.PRIYA_ID, senderName = "Priya Shah", recipientId = Part4Fixtures.OWNER_ID,
                    fromOwner = false, text = "Is there firewood on site?", createdAt = 1794232800000L,
                ),
            ),
        )
        var sent: String? = null
        composeRule.setContent {
            MaterialTheme {
                ConversationScreen(
                    reservation = Part4Fixtures.priyaReservation,
                    messages = messages,
                    myId = Part4Fixtures.OWNER_ID,
                    error = null,
                    onSend = { text ->
                        sent = text
                        messages = messages + Message(
                            id = "m2", reservationId = Part4Fixtures.priyaReservation.id, senderId = Part4Fixtures.OWNER_ID,
                            senderName = "Jasper Camps", recipientId = Part4Fixtures.PRIYA_ID, fromOwner = true,
                            text = text, createdAt = 1794232860000L,
                        )
                    },
                    onBack = {},
                )
            }
        }

        // The owner sees the guest's name and message.
        composeRule.onAllNodesWithText("Priya Shah").assertCountEquals(2)
        composeRule.onNodeWithText("Is there firewood on site?").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()

        composeRule.onNodeWithText("Write a message").performTextInput("Yes, $8 a bundle at the gate.")
        composeRule.onNodeWithContentDescription("Send message").performClick()

        assertEquals("Yes, $8 a bundle at the gate.", sent)
        composeRule.onNodeWithText("Yes, $8 a bundle at the gate.").assertIsDisplayed()
        composeRule.onNodeWithText("You").assertIsDisplayed()
        // The box is cleared for the next message.
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()
    }

    @Test
    fun emptyConversationInvitesAFirstMessage() {
        composeRule.setContent {
            MaterialTheme {
                ConversationScreen(
                    reservation = Part4Fixtures.priyaReservation,
                    messages = emptyList(),
                    myId = Part4Fixtures.PRIYA_ID,
                    error = "Could not send: offline",
                    onSend = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Owner of Jasper Riverside").assertIsDisplayed()
        composeRule.onNodeWithText("No messages yet. Say hello or ask about the stay.").assertIsDisplayed()
        composeRule.onNodeWithText("Could not send: offline").assertIsDisplayed()
    }
}
