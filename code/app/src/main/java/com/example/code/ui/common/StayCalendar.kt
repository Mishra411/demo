/*
 * StayCalendar.kt
 * Month calendar that shows which days a campsite is available, booked or
 * not offered (the availability schedule feature) and lets a lottery winner
 * tap the first and last day of a stay (US 01.04.01). Booked days are
 * struck through as well as coloured, so the calendar also works in
 * colour-blind mode.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.code.model.DayState
import com.example.code.model.Days

/**
 * Month calendar with one cell per day.
 *
 * @param stateOf state of a day number: open, booked or closed.
 * @param initialDay a day in the month shown first.
 * @param selectedFirst first day of the chosen stay, or null.
 * @param selectedLast last day of the chosen stay, or null while only the first is chosen.
 * @param onDayClick called with an open day the user tapped; null makes the calendar read-only.
 */
@Composable
fun StayCalendar(
    stateOf: (Long) -> DayState,
    initialDay: Long,
    modifier: Modifier = Modifier,
    selectedFirst: Long? = null,
    selectedLast: Long? = null,
    onDayClick: ((Long) -> Unit)? = null,
) {
    val (startYear, startMonth) = Days.monthOf(initialDay)
    // Months since year 0, so "next" and "previous" are simple arithmetic.
    var monthIndex by rememberSaveable(initialDay) { mutableIntStateOf(startYear * 12 + startMonth) }
    val year = monthIndex / 12
    val month = monthIndex % 12

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthIndex-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                Days.monthLabel(year, month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { monthIndex++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { weekday ->
                Text(
                    Days.weekdayName(weekday).take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Days.monthGrid(year, month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val selected = selectedFirst != null && day in selectedFirst..(selectedLast ?: selectedFirst)
                            DayCell(day, stateOf(day), selected, onDayClick)
                        }
                    }
                }
                // Keep the last week's cells the same width as the others.
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(MaterialTheme.colorScheme.primaryContainer, "Available")
            Legend(MaterialTheme.colorScheme.errorContainer, "Booked")
            Legend(Color.Transparent, "Not available")
        }
    }
}

@Composable
private fun DayCell(day: Long, state: DayState, selected: Boolean, onDayClick: ((Long) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        selected -> scheme.primary
        state == DayState.OPEN -> scheme.primaryContainer
        state == DayState.BOOKED -> scheme.errorContainer
        else -> Color.Transparent
    }
    val textColor = when {
        selected -> scheme.onPrimary
        state == DayState.OPEN -> scheme.onPrimaryContainer
        state == DayState.BOOKED -> scheme.onErrorContainer
        else -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    val description = Days.label(day) + ", " + when (state) {
        DayState.OPEN -> "available"
        DayState.BOOKED -> "booked"
        DayState.CLOSED -> "not available"
    } + if (selected) ", selected" else ""
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .testTag("day-$day")
            .semantics { contentDescription = description }
            .then(
                if (onDayClick != null && state == DayState.OPEN) Modifier.clickable { onDayClick(day) } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Days.dayOfMonth(day).toString(),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (state == DayState.OPEN || selected) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (state == DayState.BOOKED) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
