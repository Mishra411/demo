// Form where an owner registers a new campsite, or edits one they own (US 02.01.03).
// The owner can upload a promotional photo, and set the price per night, rules,
// cancellation policy and the availability schedule shown on the campsite calendar.
// AI use: Claude (Anthropic) helped find and fix the double-tap duplicate save bug (isSaving).
// The edit mode, photo upload and the fields below "Tags" were written with Claude
// (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.

package com.example.code.ui.owner

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.code.model.Campsite
import com.example.code.model.Days
import com.example.code.model.campsiteDetailsFromForm
import com.example.code.model.campsiteFromForm
import com.example.code.ui.common.RemoteImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Displays the campsite registration form, or the edit form when [initial] is given.
 *
 * @param onRegister called with the new or edited campsite and a way to show an error if saving fails
 * @param onBack called when the owner goes back without saving
 * @param initial the campsite being edited (US 02.01.03), or null to register a new one
 * @param onUploadPhoto uploads a photo picked by the owner and returns its image
 * reference; null hides the upload button
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterCampsiteScreen(
    onRegister: (Campsite, (String) -> Unit) -> Unit,
    onBack: () -> Unit,
    initial: Campsite? = null,
    onUploadPhoto: (suspend (Uri) -> String)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var maxOccupancy by remember { mutableStateOf(initial?.maxOccupancy?.toString().orEmpty()) }
    var latitude by remember { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var longitude by remember { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    var tags by remember { mutableStateOf(initial?.tags?.joinToString(", ").orEmpty()) }
    var imageUrl by remember { mutableStateOf(initial?.imageUrl.orEmpty()) }
    var price by remember { mutableStateOf(initial?.price?.let(::priceInput).orEmpty()) }
    var rules by remember { mutableStateOf(initial?.rules.orEmpty()) }
    var policy by remember { mutableStateOf(initial?.cancellationPolicy.orEmpty()) }
    var availableFrom by remember { mutableStateOf(initial?.availableFrom.orEmpty()) }
    var availableTo by remember { mutableStateOf(initial?.availableTo.orEmpty()) }
    var weekdays by remember { mutableStateOf(initial?.availableWeekdays.orEmpty()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // True while the campsite is being saved, so it can't be saved twice
    var isSaving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && onUploadPhoto != null) {
            uploading = true
            scope.launch {
                try {
                    imageUrl = onUploadPhoto(uri)
                    errorMessage = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorMessage = "Could not upload the photo. Try again."
                } finally {
                    uploading = false
                }
            }
        }
    }
    val title = if (initial == null) "Register campsite" else "Edit campsite"

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().testTag("nameInput"), // for testing
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth().testTag("locationInput"), // for testing
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / amenities") },
                modifier = Modifier.fillMaxWidth().testTag("descriptionInput"), // for testing
            )

            OutlinedTextField(
                value = maxOccupancy,
                onValueChange = { maxOccupancy = it },
                label = { Text("Maximum occupancy") },
                modifier = Modifier.fillMaxWidth().testTag("maxOccupancyInput"), // for testing
            )

            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text("Latitude") },
                modifier = Modifier.fillMaxWidth().testTag("latitudeInput"), // for testing
            )

            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text("Longitude") },
                modifier = Modifier.fillMaxWidth().testTag("longitudeInput"), // for testing
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated, optional)") },
                modifier = Modifier.fillMaxWidth().testTag("tagsInput"), // for testing
            )

            Text("Promotional image", style = MaterialTheme.typography.titleMedium)
            if (imageUrl.isNotBlank()) {
                RemoteImage(
                    imageUrl,
                    "Promotional image preview",
                    Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium),
                )
            }
            if (onUploadPhoto != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !uploading && !isSaving,
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) { Text(if (uploading) "Uploading…" else "Upload photo") }
                    if (imageUrl.isNotBlank()) {
                        OutlinedButton(onClick = { imageUrl = "" }, enabled = !uploading) { Text("Remove image") }
                    }
                }
            }
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Image URL (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("imageUrlInput"), // for testing
            )

            Text("Price and policies", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Price per night in CAD (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("priceInput"),
            )
            OutlinedTextField(
                value = rules,
                onValueChange = { rules = it },
                label = { Text("Campsite rules (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("rulesInput"),
            )
            OutlinedTextField(
                value = policy,
                onValueChange = { policy = it },
                label = { Text("Cancellation policy (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("policyInput"),
            )

            Text("Availability schedule", style = MaterialTheme.typography.titleMedium)
            Text(
                "The dates guests can stay, shown on the campsite calendar. Leave empty if not decided.",
                style = MaterialTheme.typography.bodySmall,
            )
            ScheduleDateField("First available date", "availableFromInput", availableFrom) { availableFrom = it }
            ScheduleDateField("Last available date", "availableToInput", availableTo) { availableTo = it }
            Text("Open on (none selected means every day)", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { weekday ->
                    val selected = weekday in weekdays
                    FilterChip(
                        selected = selected,
                        onClick = { weekdays = if (selected) weekdays - weekday else weekdays + weekday },
                        label = { Text(Days.weekdayName(weekday).take(3)) },
                        modifier = Modifier.testTag("weekday-$weekday"),
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                val campsite = campsiteFromForm(
                    name, location, description, maxOccupancy,
                    latitude, longitude, tags, imageUrl,
                )

                if (campsite == null) {
                    errorMessage = "Please complete all required fields with valid values."
                } else {
                    val detailed = try {
                        campsiteDetailsFromForm(campsite, price, rules, policy, availableFrom, availableTo, weekdays)
                    } catch (e: IllegalArgumentException) {
                        errorMessage = e.message
                        null
                    }
                    if (detailed != null) {
                        errorMessage = null
                        isSaving = true

                        // Keep the identity and ratings of an edited campsite
                        val toSave = if (initial == null) {
                            detailed
                        } else {
                            detailed.copy(
                                id = initial.id,
                                ownerId = initial.ownerId,
                                rating = initial.rating,
                                ratingCount = initial.ratingCount,
                            )
                        }

                        // Show the error if saving fails and let them try again
                        onRegister(toSave) { message ->
                            errorMessage = message
                            isSaving = false
                        }
                    }
                }
            },
            enabled = !isSaving && !uploading,
            modifier = Modifier.fillMaxWidth().testTag("registerButton"), // for testing
        ) {
            Text(if (initial == null) "Register campsite" else "Save changes")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().testTag("backButton"), // for testing
        ) {
            Text("Back")
        }
    }
}

/** Price shown in the edit form without a trailing ".0". */
private fun priceInput(price: Double): String =
    if (price == Math.floor(price)) price.toLong().toString() else price.toString()

/** A YYYY-MM-DD field the owner can type in or fill from the date picker. */
@Composable
private fun ScheduleDateField(label: String, tag: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag(tag),
        )
        IconButton(
            onClick = {
                val start = Calendar.getInstance()
                Days.parse(value)?.let { day ->
                    val (year, month) = Days.monthOf(day)
                    start.set(year, month, Days.dayOfMonth(day))
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onChange(Days.format(Days.of(year, month, day))) },
                    start.get(Calendar.YEAR),
                    start.get(Calendar.MONTH),
                    start.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
        ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pick ${label.lowercase()}") }
    }
}
