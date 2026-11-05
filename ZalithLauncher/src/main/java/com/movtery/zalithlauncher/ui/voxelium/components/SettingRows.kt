/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.voxelium.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.ui.components.DefaultSwitch
import kotlin.math.roundToInt

/** One setting on a container surface: title, an optional explanation, and its control. */
@Composable
fun SettingRow(title: String, summary: String? = null, modifier: Modifier = Modifier, control: @Composable () -> Unit = {}) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            control()
        }
    }
}

@Composable
fun SwitchSetting(title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, summary) { DefaultSwitch(checked = checked, onCheckedChange = onChange) }
}

/** A slider whose value is saved when the finger lifts. */
@Composable
fun SliderSetting(
    title: String,
    summary: String? = null,
    value: Int,
    range: IntRange,
    step: Int = 1,
    label: @Composable (Int) -> String,
    onChange: (Int) -> Unit
) {
    var live by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(label(((live / step).roundToInt() * step)), style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = live,
                onValueChange = { live = it },
                onValueChangeFinished = { onChange(((live / step).roundToInt() * step).coerceIn(range)) },
                valueRange = range.first.toFloat()..range.last.toFloat()
            )
        }
    }
}

/** A choice from a list, shown as a button that opens a menu. */
@Composable
fun <T> ChoiceSetting(
    title: String,
    summary: String? = null,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, summary) {
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.width(220.dp)) {
                Text(selected?.let { label(it) } ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (o in options) {
                    DropdownMenuItem(text = { Text(label(o)) }, onClick = { open = false; onPick(o) })
                }
            }
        }
    }
}

/** A text setting, saved when the field loses focus or Done is pressed (each keystroke here). */
@Composable
fun TextSetting(title: String, summary: String? = null, value: String, placeholder: String = "", onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onChange(it) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
