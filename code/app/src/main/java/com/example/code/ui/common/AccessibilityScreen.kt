/*
 * AccessibilityScreen.kt
 * Accessibility mode WOW feature: the user turns on colour-blind friendly
 * colours and chooses the size of text and of buttons and other
 * components. Changes apply to the whole app at once.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.code.model.AccessibilitySettings

/**
 * Accessibility settings.
 *
 * @param settings the current choices.
 * @param onChange called with the new choices each time the user changes one.
 * @param onBack called when the user goes back.
 */
@Composable
fun AccessibilityScreen(
    settings: AccessibilitySettings,
    onChange: (AccessibilitySettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackButton(onBack)
        Text("Accessibility", style = MaterialTheme.typography.headlineSmall)

        Row(
            Modifier.fillMaxWidth().toggleable(
                value = settings.colorBlind,
                onValueChange = { onChange(settings.copy(colorBlind = it)) },
                role = Role.Switch,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Colour-blind friendly colours", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Uses blue and orange instead of green and red, and adds text or patterns wherever colour has a meaning.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.colorBlind, onCheckedChange = null)
        }

        ScaleChoice("Text size", "text", settings.textScale) { onChange(settings.copy(textScale = it)) }
        ScaleChoice("Button and component size", "ui", settings.uiScale) { onChange(settings.copy(uiScale = it)) }

        Text("Preview", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Jasper Riverside", style = MaterialTheme.typography.titleMedium)
                Text("Quiet riverside site with a fire pit.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        "Available",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    StatusChip(
                        "Booked",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Button(onClick = {}) { Text("Join Waiting List") }
            }
        }
        TextButton(onClick = { onChange(AccessibilitySettings()) }) { Text("Reset to defaults") }
    }
}

/** Radio choices for one scale; [tag] makes each choice findable in tests. */
@Composable
private fun ScaleChoice(title: String, tag: String, current: Float, onPick: (Float) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        AccessibilitySettings.SCALE_CHOICES.forEach { (label, scale) ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = current == scale, onClick = { onPick(scale) }, role = Role.RadioButton)
                    .testTag("$tag-$label")
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == scale, onClick = null)
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
