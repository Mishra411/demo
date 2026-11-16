/*
 * BottomBar.kt
 * Bottom navigation bar. Shows the tabs of the active mode (mockups E02, O01, A01)
 * with a badge for unread notifications.
 * Known issues: none.
 */
package com.example.code.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp

/**
 * Bottom bar with one item per tab.
 *
 * @param tabs tabs of the active mode, from [Destination.tabsFor].
 * @param currentRoute route on screen now, used to highlight the selected tab.
 * @param onTabSelected called with the tapped tab.
 * @param badges number of unread items to show on a tab; zero shows no badge.
 */
@Composable
fun BottomBar(
    tabs: List<Destination>,
    currentRoute: String?,
    onTabSelected: (Destination) -> Unit,
    badges: Map<Destination, Int> = emptyMap(),
) {
    NavigationBar {
        tabs.forEach { tab ->
            val unread = badges[tab] ?: 0
            NavigationBarItem(
                selected = tab.route == currentRoute,
                onClick = { onTabSelected(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (unread > 0) {
                                Badge(Modifier.semantics { contentDescription = "$unread unread" }) {
                                    Text(if (unread > 99) "99+" else unread.toString())
                                }
                            }
                        },
                    ) {
                        Icon(
                            iconFor(tab),
                            contentDescription = tab.label,
                        )
                    }
                },
                label = {
                    // Small text so 5 labels fit on narrow phones.
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.sp
                        ),
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
}

/**
 * Icon shown for [destination] on the bottom bar.
 *
 * Destinations that are not bottom-bar tabs still need an icon here because
 * this when-expression must handle every Destination value.
 */
private fun iconFor(destination: Destination): ImageVector =
    when (destination) {
        Destination.ENTRANT_EXPLORE ->
            Icons.Filled.Explore

        Destination.ENTRANT_SCAN ->
            Icons.Filled.QrCodeScanner

        Destination.ENTRANT_RESERVATIONS,
        Destination.OWNER_RESERVATIONS ->
            Icons.Filled.EventAvailable

        Destination.ENTRANT_NOTIFICATIONS ->
            Icons.Filled.Notifications

        Destination.OWNER_CAMPSITES,
        Destination.OWNER_REGISTER_CAMPSITE,
        Destination.ADMIN_CAMPSITES ->
            Icons.Filled.Cabin

        Destination.OWNER_LOTTERIES ->
            Icons.Filled.ConfirmationNumber

        Destination.ADMIN_IMAGES ->
            Icons.Filled.Image

        Destination.ADMIN_LOGS ->
            Icons.AutoMirrored.Filled.ListAlt

        Destination.ADMIN_PROFILES ->
            Icons.Filled.People

        Destination.ENTRANT_PROFILE,
        Destination.OWNER_PROFILE,
        Destination.ADMIN_PROFILE ->
            Icons.Filled.Person
    }
