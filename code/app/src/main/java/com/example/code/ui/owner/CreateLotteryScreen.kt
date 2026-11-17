package com.example.code.ui.owner
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.code.controller.LotteryController
import com.example.code.model.Campsite
import com.example.code.model.Lottery
import com.example.code.ui.common.formatDateTime
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


/** AI USAGE
 * The basis code for the following file, CreateLotteryScreen by GPT6-Sol
 * "Based on the required project specifications provided and the existing code,
 * I need help adding in code for US 2.04.01, 03,04, 05, and 07. Ensure that the new added features for the lottery
 * do not interfere with the reservation, waitingList, etc.
 * Also, understand from the specifications that many other classes will also depend on new features of the Lottery.
 * Currently, there is a model class, controller, and a LOtteryRepository", 11-05-2026
 */

/**
 * Owner form for one saved campsite. Dates are chosen in the device time zone
 * and submitted as epoch milliseconds; campsite identity is never editable.
 * [onPublish] must return only after persistence succeeds. A failed save keeps
 * the form open with its values, while [onPublished] closes it on success.
 *
 * AI use: [initial] (update an open lottery, US 02.04.06) and [otherCampsites]
 * (one lottery for several campsites, US 02.04.02) were added with Claude
 * (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 *
 * @param initial the open lottery being updated, or null to create one.
 * @param otherCampsites the owner's other campsites that can share this lottery.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateLotteryScreen(
    campsite: Campsite,
    onPublish: suspend (Lottery) -> String,
    onPublished: () -> Unit,
    onCancel: () -> Unit,
    initial: Lottery? = null,
    otherCampsites: List<Campsite> = emptyList(),
) {
    var registrationStart by rememberSaveable(campsite.id) { mutableStateOf(initial?.registrationStart) }
    var registrationEnd by rememberSaveable(campsite.id) { mutableStateOf(initial?.registrationEnd) }
    var bookingStart by rememberSaveable(campsite.id) { mutableStateOf(initial?.bookingStart) }
    var bookingEnd by rememberSaveable(campsite.id) { mutableStateOf(initial?.bookingEnd) }
    var winners by rememberSaveable(campsite.id) { mutableStateOf(initial?.winnerCount?.toString() ?: "1") }
    var capacity by rememberSaveable(campsite.id) {
        mutableStateOf(initial?.waitlistCapacity?.toString().orEmpty())
    }
    var extraIds by rememberSaveable(campsite.id) { mutableStateOf(initial?.extraCampsiteIds.orEmpty()) }
    var shared by rememberSaveable(campsite.id) { mutableStateOf(initial?.shared ?: false) }
    // Campsites other owners added to a shared lottery are kept on update (US 02.04.08).
    val othersExtras = initial?.let { lottery ->
        lottery.extraCampsiteIds.zip(lottery.extraCampsiteNames)
            .map { (id, name) -> Triple(id, name, LotteryController.ownerOf(lottery, id)) }
            .filter { it.third != campsite.ownerId }
    }.orEmpty()
    val editing = initial != null
    var error by rememberSaveable(campsite.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler {
        if (!busy) onCancel()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (editing) "Update lottery" else "Create lottery", style = MaterialTheme.typography.headlineSmall)
        Text(campsite.name, style = MaterialTheme.typography.titleMedium)
        Text("All dates and times use ${TimeZone.getDefault().id}.")

        Text("Registration period", style = MaterialTheme.typography.titleMedium)
        Text("Entrants can join from the start time until registration closes.")
        LotteryDateTimeField("Registration starts", registrationStart, !busy) { registrationStart = it }
        LotteryDateTimeField("Registration ends", registrationEnd, !busy) { registrationEnd = it }

        Text("Booking period", style = MaterialTheme.typography.titleMedium)
        Text("The stay dates offered to lottery winners.")
        LotteryDateTimeField("Booking starts", bookingStart, !busy) { bookingStart = it }
        LotteryDateTimeField("Booking ends", bookingEnd, !busy) { bookingEnd = it }

        OutlinedTextField(
            value = winners,
            onValueChange = { winners = it },
            label = { Text("Number of winners") },
            supportingText = { Text("At least 1. If fewer entrants join, all are selected.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = capacity,
            onValueChange = { capacity = it },
            label = { Text("Waiting-list capacity (optional)") },
            supportingText = { Text("Leave blank for unlimited; otherwise at least the number of winners.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        val choices = otherCampsites.filter { it.id != campsite.id && it.ownerId == campsite.ownerId }
        if (choices.isNotEmpty()) {
            Text("More campsites in this lottery (optional)", style = MaterialTheme.typography.titleMedium)
            Text("Winners choose any campsite in the lottery when they reserve.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { other ->
                    val selected = other.id in extraIds
                    FilterChip(
                        selected = selected,
                        enabled = !busy,
                        onClick = { extraIds = if (selected) extraIds - other.id else extraIds + other.id },
                        label = { Text(other.name) },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().toggleable(value = shared, enabled = !busy, role = Role.Checkbox) { shared = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = shared, onCheckedChange = null, modifier = Modifier.testTag("sharedLottery"))
            Text("Let other campsite owners add their campsites", modifier = Modifier.padding(start = 8.dp))
        }
        if (othersExtras.isNotEmpty()) {
            Text("Added by other owners: ${othersExtras.joinToString(", ") { it.second }}")
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) {
            CircularProgressIndicator()
            Text(if (editing) "Saving lottery…" else "Publishing lottery…")
        }
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // Parse and validate before launching a write. Blank capacity is
                // unlimited; malformed or overflowing integers are errors.
                val draft = try {
                    val winnerCount = requireNotNull(winners.trim().toIntOrNull()) {
                        "Enter a whole number of winners."
                    }
                    val cap = if (capacity.isBlank()) null else {
                        requireNotNull(capacity.trim().toIntOrNull()) {
                            "Enter a whole-number capacity or leave it blank."
                        }
                    }
                    Lottery(
                        campsiteId = campsite.id,
                        campsiteName = campsite.name,
                        ownerId = campsite.ownerId,
                        registrationStart = requireNotNull(registrationStart) { "Choose a registration start." },
                        registrationEnd = requireNotNull(registrationEnd) { "Choose a registration end." },
                        bookingStart = requireNotNull(bookingStart) { "Choose a booking start." },
                        bookingEnd = requireNotNull(bookingEnd) { "Choose a booking end." },
                        winnerCount = winnerCount,
                        waitlistCapacity = cap,
                        extraCampsiteIds = choices.filter { it.id in extraIds }.map { it.id } + othersExtras.map { it.first },
                        extraCampsiteNames = choices.filter { it.id in extraIds }.map { it.name } + othersExtras.map { it.second },
                        extraCampsiteOwnerIds = if (othersExtras.isEmpty()) {
                            emptyList()
                        } else {
                            choices.filter { it.id in extraIds }.map { campsite.ownerId } + othersExtras.map { it.third }
                        },
                        shared = shared,
                    ).also {
                        if (initial == null) {
                            LotteryController.validateCreation(it)
                        } else {
                            LotteryController.validateUpdate(initial, initial.copy(
                                registrationStart = it.registrationStart,
                                registrationEnd = it.registrationEnd,
                                bookingStart = it.bookingStart,
                                bookingEnd = it.bookingEnd,
                                winnerCount = it.winnerCount,
                                waitlistCapacity = it.waitlistCapacity,
                                extraCampsiteIds = it.extraCampsiteIds,
                                extraCampsiteNames = it.extraCampsiteNames,
                                extraCampsiteOwnerIds = it.extraCampsiteOwnerIds,
                                shared = it.shared,
                            ))
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    error = e.message
                    null
                }
                if (draft != null && !busy) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            onPublish(draft)
                            onPublished()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Could not ${if (editing) "save" else "publish"} lottery: ${e.message ?: "Please try again."}"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
        ) { Text(if (editing) "Save changes" else "Publish") }
        TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
    }
}

/** Native pickers avoid requiring an additional date library or Material opt-in. */
@Composable
private fun LotteryDateTimeField(
    label: String,
    value: Long?,
    enabled: Boolean,
    onSelected: (Long) -> Unit,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { dialog?.dismiss() }
    }
    OutlinedTextField(
        value = value?.let(::formatDateTime).orEmpty(),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(
                enabled = enabled,
                onClick = {
                    val initial = Calendar.getInstance().apply {
                        if (value != null) timeInMillis = value
                    }
                    val dateDialog = DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val timeDialog = TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val selected = Calendar.getInstance().apply {
                                        isLenient = false
                                        clear()
                                        set(year, month, day, hour, minute, 0)
                                    }
                                    try {
                                        onSelected(selected.timeInMillis)
                                        error = null
                                    } catch (e: IllegalArgumentException) {
                                        error = "That local time does not exist. Choose another time."
                                    }
                                },
                                initial.get(Calendar.HOUR_OF_DAY),
                                initial.get(Calendar.MINUTE),
                                DateFormat.is24HourFormat(context),
                            )
                            timeDialog.setTitle(label)
                            dialog = timeDialog
                            timeDialog.show()
                        },
                        initial.get(Calendar.YEAR),
                        initial.get(Calendar.MONTH),
                        initial.get(Calendar.DAY_OF_MONTH),
                    )
                    dateDialog.setTitle(label)
                    dialog = dateDialog
                    dateDialog.show()
                },
            ) { Text("Choose") }
        },
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
