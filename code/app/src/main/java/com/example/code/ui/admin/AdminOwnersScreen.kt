/*
 * AdminOwnersScreen.kt
 * Lists campsite owners so an administrator can remove one who violates
 * the app policy (US 03.07.01). Removing an owner also removes their
 * campsites and open lotteries.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.code.admin.AdminProfile
import com.example.code.model.Campsite
import com.example.code.ui.common.BackButton
import com.example.code.ui.common.EmptyState

/**
 * A campsite owner and what they have listed.
 *
 * @property profile the owner's profile; only the ID is known if the profile was deleted.
 * @property campsiteNames names of the owner's campsites.
 */
data class OwnerRow(val profile: AdminProfile, val campsiteNames: List<String>)

/**
 * Campsite owners among [profiles]: everyone with the owner role or with a
 * campsite in [campsites], including owners whose profile no longer exists.
 * Sorted by name.
 */
fun ownerRows(profiles: List<AdminProfile>, campsites: List<Campsite>): List<OwnerRow> {
    val byOwner = campsites.filter { it.ownerId.isNotBlank() }.groupBy { it.ownerId }
    val known = profiles.filter { "CAMPSITE_OWNER" in it.roles || it.id in byOwner }
    val orphans = (byOwner.keys - profiles.map { it.id }.toSet()).map { AdminProfile(id = it) }
    return (known + orphans)
        .map { OwnerRow(it, byOwner[it.id].orEmpty().map { c -> c.name }.sorted()) }
        .sortedBy { it.profile.name.ifBlank { it.profile.id }.lowercase() }
}

/**
 * Lists campsite owners with a Remove owner action.
 *
 * @param owners owners to show, from [ownerRows].
 * @param currentProfileId the administrator's own profile, which cannot be removed here.
 * @param busyId owner being removed, or null.
 * @param message result of the last removal, or null.
 * @param onConfirmRemove called with the owner's ID after the administrator confirms.
 * @param onBack returns to the profile list.
 */
@Composable
fun AdminOwnersScreen(
    owners: List<OwnerRow>,
    currentProfileId: String,
    busyId: String?,
    message: String?,
    onConfirmRemove: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = owners.firstOrNull { it.profile.id == pendingId }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text("Campsite owners", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Remove an owner who violates the app policy. Their campsites and open lotteries are removed too.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (message != null) Text(message)
        if (owners.isEmpty()) {
            EmptyState(Icons.Outlined.Cabin, "No campsite owners yet.")
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(owners, key = { it.profile.id }) { owner ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("ownerCard-${owner.profile.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            owner.profile.name.ifBlank { "Unnamed owner" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (owner.profile.email.isNotBlank()) Text(owner.profile.email)
                        Text(
                            if (owner.campsiteNames.isEmpty()) "No campsites listed"
                            else "Campsites (${owner.campsiteNames.size}): ${owner.campsiteNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (owner.profile.id == currentProfileId) {
                            Text("Your profile")
                        } else {
                            OutlinedButton(
                                enabled = busyId == null,
                                onClick = { pendingId = owner.profile.id },
                                modifier = Modifier.testTag("removeOwner-${owner.profile.id}"),
                            ) { Text(if (busyId == owner.profile.id) "Removing…" else "Remove owner") }
                        }
                    }
                }
            }
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingId = null },
            title = { Text("Remove this owner?") },
            text = {
                Text(
                    "${pending.profile.name.ifBlank { "This owner" }}, " +
                        "${pending.campsiteNames.size} campsite${if (pending.campsiteNames.size == 1) "" else "s"} " +
                        "and their open lotteries will be removed. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingId = null
                        onConfirmRemove(pending.profile.id)
                    },
                ) { Text("Remove owner and campsites") }
            },
            dismissButton = { TextButton(onClick = { pendingId = null }) { Text("Cancel") } },
        )
    }
}
