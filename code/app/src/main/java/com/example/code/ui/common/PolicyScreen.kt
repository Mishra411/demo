/*
 * PolicyScreen.kt
 * App policy and guidelines that every user can read from the Profile
 * screen (US 01.11.01).
 * AI use: wording drafted with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One titled section of the app policy. */
data class PolicySection(val title: String, val body: String)

/** The app policy and guidelines shown to every user (US 01.11.01). */
val AppPolicy: List<PolicySection> = listOf(
    PolicySection(
        "How the lottery works",
        "Popular campsites are booked by lottery, not first come first served. Join a waiting list while " +
            "registration is open. When it closes, winners are drawn at random and everyone on the list is " +
            "notified. Each entrant can join a waiting list once.",
    ),
    PolicySection(
        "Reserving and cancelling",
        "Winners choose free dates in the booking period and must confirm the campsite's rules before " +
            "reserving. You can decline a win or cancel an upcoming stay from the app. Each campsite's own " +
            "cancellation policy also applies.",
    ),
    PolicySection(
        "Your identity and data",
        "You are identified by this device, so there is no username or password. Your name is shown to the " +
            "owners of campsites you enter or reserve. You can turn off notifications or delete your profile " +
            "from the Profile screen at any time.",
    ),
    PolicySection(
        "Campsite owner guidelines",
        "Owners must list real campsites they manage, describe them honestly, keep rules and policies up " +
            "to date, and message guests only about their reservation. Lotteries must be run for the dates shown.",
    ),
    PolicySection(
        "Community standards",
        "No offensive, misleading or unrelated images, reviews, messages or listings. Administrators may " +
            "remove campsites, images, profiles or owners that break this policy, and can review notification " +
            "and message logs.",
    ),
)

/**
 * Shows the app policy and guidelines.
 *
 * @param onBack called when the user goes back.
 */
@Composable
fun PolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackButton(onBack)
        Text("App policy and guidelines", style = MaterialTheme.typography.headlineSmall)
        AppPolicy.forEach { section ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
