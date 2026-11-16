/*
 * AdminImagesScreen.kt
 * Admin Images tab: browse every uploaded image and every campsite image
 * link (US 03.06.01) and remove one (US 03.03.01). Removing an image also
 * clears it from the campsite or profile that showed it.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.code.model.Campsite
import com.example.code.model.StoredImage
import com.example.code.ui.common.EmptyState
import com.example.code.ui.common.RemoteImage

/**
 * One image an administrator can review.
 *
 * @property key unique key in the list.
 * @property ref what to show: an uploaded image reference or a web link.
 * @property title what the image is, e.g. "Campsite photo: Jasper Riverside".
 * @property detail who uploaded it and when, or that it is a web link.
 * @property storedId ID of the uploaded image, or blank for a web link.
 * @property campsiteId campsite to clear when a web link is removed, or null.
 */
data class AdminImageItem(
    val key: String,
    val ref: String,
    val title: String,
    val detail: String,
    val storedId: String = "",
    val campsiteId: String? = null,
)

/**
 * Builds the images an administrator can browse: every uploaded image
 * (newest first), then every campsite image that is a web link.
 *
 * @param uploaded images in the images collection.
 * @param campsites every campsite.
 * @param dateText formats an upload time for display.
 */
fun adminImageItems(
    uploaded: List<StoredImage>,
    campsites: List<Campsite>,
    dateText: (Long) -> String,
): List<AdminImageItem> {
    val stored = uploaded.sortedByDescending { it.createdAt }.map { image ->
        val ref = StoredImage.refFor(image.id)
        val campsite = campsites.firstOrNull { it.imageUrl == ref }
        val kind = if (image.kind == StoredImage.KIND_PROFILE) "Profile photo" else "Campsite photo"
        val subject = campsite?.name ?: image.label
        AdminImageItem(
            key = "stored-${image.id}",
            ref = ref,
            title = if (subject.isBlank()) kind else "$kind: $subject",
            detail = "Uploaded by ${image.uploaderId.take(8).ifBlank { "unknown" }}" +
                if (image.createdAt > 0) " on ${dateText(image.createdAt)}" else "",
            storedId = image.id,
        )
    }
    val links = campsites
        .filter { !it.imageUrl.isNullOrBlank() && StoredImage.idFromRef(it.imageUrl) == null }
        .sortedBy { it.name.lowercase() }
        .map {
            AdminImageItem(
                key = "link-${it.id}",
                ref = it.imageUrl.orEmpty(),
                title = "Campsite photo: ${it.name}",
                detail = "Web link added by the owner",
                campsiteId = it.id,
            )
        }
    return stored + links
}

/**
 * Lists images with a Remove action.
 *
 * @param items images to review, from [adminImageItems].
 * @param message result of the last removal, or null.
 * @param busy true while an image is being removed.
 * @param onRemove called with the image after the administrator confirms.
 */
@Composable
fun AdminImagesScreen(
    items: List<AdminImageItem>,
    message: String?,
    busy: Boolean,
    onRemove: (AdminImageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf<AdminImageItem?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Images", style = MaterialTheme.typography.headlineSmall)
        if (message != null) Text(message)
        if (items.isEmpty()) {
            EmptyState(Icons.Outlined.Image, "No images to review.", message = "Uploaded photos appear here.")
            return@Column
        }
        Text("${items.size} image${if (items.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.key }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("imageCard-${item.key}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column {
                        RemoteImage(
                            item.ref,
                            item.title,
                            Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium),
                        )
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { pending = item },
                                enabled = !busy,
                                modifier = Modifier.testTag("removeImage-${item.key}"),
                            ) { Text("Remove image") }
                        }
                    }
                }
            }
        }
    }

    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Remove this image?") },
            text = { Text("${item.title} will be removed from the app. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        pending = null
                        onRemove(item)
                    },
                ) { Text("Confirm removal") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
