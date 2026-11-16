/*
 * ProfileScreen.kt
 * Profile screen shared by all app modes.
 * Allows the user to edit personal information and switch between available modes.
 * Available modes are derived from the loaded user's assigned roles.
 * It also holds the user's settings: profile photo, camping interests, notification
 * opt-out (US 01.10.01), hosting campsites, accessibility mode (WOW feature), the app
 * policy (US 01.11.01) and deleting the profile (US 01.08.01).
 * AI use: the settings below the mode list were written with Claude (Anthropic),
 * Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.code.User
import com.example.code.UserRole
import com.example.code.navigation.AppMode

/**
 * Profile screen for viewing and editing the current user's information.
 *
 * @param user currently loaded user profile.
 * @param activeMode mode the app is in now.
 * @param availableModes modes the user may switch to.
 * @param isSaving whether a profile save is in progress.
 * @param errorMessage validation or persistence error to display.
 * @param onSaveProfile called when the user saves profile information.
 * @param onSwitchMode called when the user taps a different mode.
 * @param onSetNotifications turns notifications on or off (US 01.10.01); null hides the switch.
 * @param onSetHosting turns hosting campsites (owner mode) on or off; null hides the switch.
 * @param onSetEntering turns entering lotteries (entrant mode) on or off for an
 * administrator (US 03.11.01); null hides the switch.
 * @param onPhotoPicked called with a chosen profile photo; null hides the photo row.
 * @param availableTags campsite tags the user can mark as interests for the featured carousel.
 * @param onSetPreferredTags saves the user's interests; null hides them.
 * @param onOpenAccessibility opens accessibility mode; null hides the link.
 * @param onOpenPolicy opens the app policy (US 01.11.01); null hides the link.
 * @param onDeleteProfile called after the user confirms deleting the profile (US 01.08.01); null hides it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    user: User,
    activeMode: AppMode,
    availableModes: List<AppMode>,
    isSaving: Boolean,
    errorMessage: String?,
    onSaveProfile: (String, String, String) -> Unit,
    onSwitchMode: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
    onSetNotifications: ((Boolean) -> Unit)? = null,
    onSetHosting: ((Boolean) -> Unit)? = null,
    onSetEntering: ((Boolean) -> Unit)? = null,
    onPhotoPicked: ((Uri) -> Unit)? = null,
    availableTags: List<String> = emptyList(),
    onSetPreferredTags: ((Set<String>) -> Unit)? = null,
    onOpenAccessibility: (() -> Unit)? = null,
    onOpenPolicy: (() -> Unit)? = null,
    onDeleteProfile: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotoPicked?.invoke(uri)
    }
    var name by remember(user.deviceId, user.name) {
        mutableStateOf(user.name)
    }

    var email by remember(user.deviceId, user.email) {
        mutableStateOf(user.email)
    }

    var phoneNumber by remember(user.deviceId, user.phoneNumber) {
        mutableStateOf(user.phoneNumber)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                onSaveProfile(
                    name,
                    email,
                    phoneNumber,
                )
            },
            enabled = !isSaving,
        ) {
            if (isSaving) {
                CircularProgressIndicator()
            } else {
                Text("Save")
            }
        }

        Text(
            text = "Mode",
            style = MaterialTheme.typography.titleMedium,
        )

        Column(
            modifier = Modifier.selectableGroup(),
        ) {
            availableModes.forEach { mode ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == activeMode,
                                onClick = { onSwitchMode(mode) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = mode == activeMode,
                        onClick = null,
                    )

                    Text(
                        text = mode.label,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        Text(
            text = "Admin mode only shows for admin profiles.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (onPhotoPicked != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (user.profilePhotoUrl.isNullOrBlank()) {
                        Text(initialsOf(user.name), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        RemoteImage(user.profilePhotoUrl, "Profile photo", Modifier.fillMaxSize())
                    }
                }
                TextButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) { Text(if (user.profilePhotoUrl.isNullOrBlank()) "Add profile photo" else "Change profile photo") }
            }
        }

        if (onSetPreferredTags != null && availableTags.isNotEmpty()) {
            Text("Camping interests", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick what you like and Explore features matching campsites.",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableTags.forEach { tag ->
                    val selected = tag in user.preferredTags
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSetPreferredTags(if (selected) user.preferredTags - tag else user.preferredTags + tag)
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }

        if (onSetNotifications != null || onSetHosting != null || onSetEntering != null || onOpenAccessibility != null || onOpenPolicy != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
                if (onSetNotifications != null) {
                    ListItem(
                        headlineContent = { Text("Receive notifications") },
                        supportingContent = { Text("Lottery results, owner messages and confirmations") },
                        leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = user.notificationsEnabled,
                                onCheckedChange = onSetNotifications,
                                modifier = Modifier.testTag("notificationsSwitch"),
                            )
                        },
                        colors = clear,
                    )
                    HorizontalDivider()
                }
                if (onSetHosting != null) {
                    ListItem(
                        headlineContent = { Text("Host campsites") },
                        supportingContent = { Text("Adds campsite owner mode so you can list campsites") },
                        leadingContent = { Icon(Icons.Outlined.Cabin, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = UserRole.CAMPSITE_OWNER in user.roles,
                                onCheckedChange = onSetHosting,
                                modifier = Modifier.testTag("hostingSwitch"),
                            )
                        },
                        colors = clear,
                    )
                    HorizontalDivider()
                }
                if (onSetEntering != null) {
                    ListItem(
                        headlineContent = { Text("Enter lotteries") },
                        supportingContent = { Text("Adds entrant mode so you can browse and join lotteries") },
                        leadingContent = { Icon(Icons.Outlined.Explore, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = UserRole.ENTRANT in user.roles,
                                onCheckedChange = onSetEntering,
                                modifier = Modifier.testTag("enteringSwitch"),
                            )
                        },
                        colors = clear,
                    )
                    HorizontalDivider()
                }
                if (onOpenAccessibility != null) {
                    ListItem(
                        headlineContent = { Text("Accessibility") },
                        supportingContent = { Text("Colour-blind colours, text size and button size") },
                        leadingContent = { Icon(Icons.Outlined.AccessibilityNew, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenAccessibility),
                        colors = clear,
                    )
                    HorizontalDivider()
                }
                if (onOpenPolicy != null) {
                    ListItem(
                        headlineContent = { Text("App policy and guidelines") },
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenPolicy),
                        colors = clear,
                    )
                }
            }
        }

        if (onDeleteProfile != null) {
            OutlinedButton(onClick = { confirmDelete = true }) {
                Text("Delete my profile", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your profile?") },
            text = {
                Text(
                    "Your name, contact details and photo are deleted and you leave any open waiting lists. " +
                        "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteProfile?.invoke()
                    },
                ) { Text("Delete profile", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep profile") } },
        )
    }
}
