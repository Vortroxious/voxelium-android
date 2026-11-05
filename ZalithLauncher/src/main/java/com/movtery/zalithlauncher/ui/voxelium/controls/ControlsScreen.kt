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

package com.movtery.zalithlauncher.ui.voxelium.controls

import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import io.github.voxelium.core.controls.ControlButton
import io.github.voxelium.core.controls.ControlKeys
import io.github.voxelium.core.controls.ControlLayout
import io.github.voxelium.core.controls.ControlLayoutStore
import io.github.voxelium.core.controls.ControlSpecial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "VoxeliumControls"

/** Which buttons the preview shows, as the iOS editor's modes. */
private enum class PreviewMode(val label: Int) {
    GAME(R.string.voxelium_ui_controls_mode_game),
    MENU(R.string.voxelium_ui_controls_mode_menu),
    DRAWER(R.string.voxelium_ui_controls_mode_drawer)
}

/** How a button acts: keys, a mouse button, or a special. */
private enum class ActionKind(val label: Int) {
    KEYS(R.string.voxelium_ui_controls_kind_keys),
    MOUSE(R.string.voxelium_ui_controls_kind_mouse),
    SPECIAL(R.string.voxelium_ui_controls_kind_special)
}

/** The full landscape screen the game runs on, in dp (the game is fullscreen). */
private fun gameScreenDp(context: Context): Pair<Double, Double> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        m.widthPixels to m.heightPixels
    }
    val d = context.resources.displayMetrics.density
    return (max(w, h) / d).toDouble() to (min(w, h) / d).toDouble()
}

/**
 * Voxelium: the Controls page, the touch layout editor (mirrors the iOS app's Controls tab and
 * on-screen editor). The layout is VoxeliumCore's `controls/layout.json`, the same file format as
 * iOS; every change is saved at once and used the next time Minecraft starts.
 */
@Composable
fun VoxeliumControlsScreen(backScreenViewModel: ScreenBackStackViewModel) {
    BaseScreen(screenKey = NormalNavKey.VoxeliumControls, currentKey = backScreenViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val store = remember { ControlLayoutStore(VoxeliumGame.paths()) }
        val (sw, sh) = remember { gameScreenDp(context) }

        var layout by remember { mutableStateOf<ControlLayout?>(null) }
        var custom by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(PreviewMode.GAME) }
        var selected by remember { mutableStateOf<UUID?>(null) }
        var message by remember { mutableStateOf<String?>(null) }
        var confirmReset by remember { mutableStateOf(false) }

        fun reload() {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { store.load() }
                custom = saved != null
                layout = (saved ?: ControlLayout.amethystDefault(sw, sh)).clamped(sw, sh)
            }
        }
        LaunchedEffect(Unit) { reload() }

        /** Applies a change, clamps it to the screen and saves it. */
        fun change(next: ControlLayout) {
            val clamped = next.clamped(sw, sh)
            layout = clamped
            custom = true
            scope.launch(Dispatchers.IO) {
                runCatching { store.save(clamped) }.onFailure {
                    Logger.error(TAG, "Saving the control layout failed", it)
                    message = it.message ?: it.toString()
                }
            }
        }
        fun updateButton(b: ControlButton) {
            val l = layout ?: return
            change(l.copy(buttons = l.buttons.map { if (it.id == b.id) b.clamped(sw, sh) else it }))
        }

        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                        ControlLayoutStore.decode(text)
                    }
                }
                imported.onSuccess {
                    change(it)
                    selected = null
                    message = context.getString(R.string.voxelium_ui_controls_imported, it.buttons.size)
                }.onFailure {
                    Logger.warning(TAG, "Importing a layout failed", it)
                    message = context.getString(R.string.voxelium_ui_controls_import_failed)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageHeader(title = stringResource(R.string.voxelium_ui_controls)) {
                OutlinedButton(onClick = {
                    val l = layout ?: return@OutlinedButton
                    val size = 69.6
                    val (fx, fy) = l.freeSpot(size, size, sw, sh)
                    val b = ControlButton(
                        name = context.getString(R.string.voxelium_ui_controls_new_button), x = fx, y = fy,
                        width = size, height = size,
                        showInGame = mode != PreviewMode.MENU, showInMenu = mode != PreviewMode.GAME,
                        inDrawer = mode == PreviewMode.DRAWER
                    )
                    change(l.copy(buttons = l.buttons + b))
                    selected = b.id
                }) { Text(stringResource(R.string.voxelium_ui_controls_add)) }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text(stringResource(R.string.voxelium_ui_controls_import))
                }
                OutlinedButton(onClick = { confirmReset = true }, enabled = custom) { Text(stringResource(R.string.voxelium_ui_controls_reset)) }
            }

            val l = layout
            if (l == null) return@Column
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in PreviewMode.entries) {
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(stringResource(m.label)) })
                        }
                    }
                    Preview(
                        layout = l, sw = sw, sh = sh, mode = mode, selected = selected,
                        onSelect = { selected = it },
                        onMoveButton = { id, dx, dy ->
                            l.buttons.firstOrNull { it.id == id }?.let { b -> updateButton(b.copy(x = b.x + dx / sw, y = b.y + dy / sh)) }
                        },
                        onMoveJoystick = { dx, dy ->
                            change(l.copy(joystick = l.joystick.copy(x = l.joystick.x + dx / sw, y = l.joystick.y + dy / sh)))
                        }
                    )
                    Text(
                        stringResource(if (custom) R.string.voxelium_ui_controls_hint_custom else R.string.voxelium_ui_controls_hint_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val b = l.buttons.firstOrNull { it.id == selected }
                    if (b == null) {
                        LayoutPanel(l, onOpacity = { change(l.copy(opacity = it)) }, onJoystickSize = { size ->
                            val j = l.joystick
                            // Resized around its centre, as on iOS.
                            change(l.copy(joystick = j.copy(x = j.x - (size - j.size) / 2 / sw, y = j.y - (size - j.size) / 2 / sh, size = size)))
                        })
                    } else {
                        ButtonPanel(
                            b,
                            onChange = ::updateButton,
                            onDelete = {
                                change(l.copy(buttons = l.buttons.filter { it.id != b.id }))
                                selected = null
                            },
                            onDone = { selected = null }
                        )
                    }
                }
            }
        }

        if (confirmReset) {
            SimpleAlertDialog(
                title = stringResource(R.string.voxelium_ui_controls_reset_title),
                text = stringResource(R.string.voxelium_ui_controls_reset_text),
                confirmText = stringResource(R.string.voxelium_ui_controls_reset),
                onConfirm = {
                    confirmReset = false
                    selected = null
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { store.reset() } }
                        reload()
                    }
                },
                onDismiss = { confirmReset = false }
            )
        }
    }
}

/** The layout drawn on a scaled copy of the game screen; drag to move, tap a button to edit it. */
@Composable
private fun Preview(
    layout: ControlLayout,
    sw: Double,
    sh: Double,
    mode: PreviewMode,
    selected: UUID?,
    onSelect: (UUID?) -> Unit,
    onMoveButton: (UUID, Double, Double) -> Unit,
    onMoveJoystick: (Double, Double) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val previewW = maxWidth
        val scale = previewW.value / sw.toFloat()
        val previewH = (sh * scale).dp
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .width(previewW)
                .size(previewW, previewH)
                .background(Color(0xFF3E5A35), RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures { onSelect(null) } }
        ) {
            // A sky and dirt band, so the preview reads as a game screen.
            Box(Modifier.fillMaxWidth().size(previewW, previewH * 0.28f).background(Color(0xFF6FA7D8), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
            Box(Modifier.offset(y = previewH * 0.72f).size(previewW, previewH * 0.28f).background(Color(0xFF5C4330), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)))

            if (mode == PreviewMode.GAME) {
                val j = layout.joystick
                val size = (j.size * scale).dp
                var drag by remember(j) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(((j.x * sw * scale).dp.toPx() + drag.x).roundToInt(), ((j.y * sh * scale).dp.toPx() + drag.y).roundToInt()) }
                        .size(size)
                        .alpha(layout.opacity.toFloat())
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .pointerInput(j) {
                            detectDragGestures(
                                onDrag = { change, amount -> change.consume(); drag += amount },
                                onDragEnd = {
                                    val dxDp = with(density) { drag.x.toDp().value } / scale
                                    val dyDp = with(density) { drag.y.toDp().value } / scale
                                    drag = androidx.compose.ui.geometry.Offset.Zero
                                    onMoveJoystick(dxDp.toDouble(), dyDp.toDouble())
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.voxelium_ui_controls_joystick), fontSize = 9.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }

            val visible = layout.buttons.filter { b ->
                when (mode) {
                    PreviewMode.GAME -> b.showInGame && !b.inDrawer
                    PreviewMode.MENU -> b.showInMenu && !b.inDrawer
                    PreviewMode.DRAWER -> b.inDrawer || (b.special == ControlSpecial.DRAWER && b.showInGame)
                }
            }
            for (b in visible) {
                key(b.id) {
                    var drag by remember(b) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    val isSelected = b.id == selected
                    val shape = RoundedCornerShape((min(b.width, b.height) * scale / 2).dp)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(((b.x * sw * scale).dp.toPx() + drag.x).roundToInt(), ((b.y * sh * scale).dp.toPx() + drag.y).roundToInt()) }
                            .size((b.width * scale).dp, (b.height * scale).dp)
                            .alpha(layout.opacity.toFloat().coerceAtLeast(0.35f))
                            .background(Color.Black.copy(alpha = 0.3f), shape)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f), shape)
                            .pointerInput(b.id) { detectTapGestures { onSelect(b.id) } }
                            .pointerInput(b) {
                                detectDragGestures(
                                    onDragStart = { onSelect(b.id) },
                                    onDrag = { change, amount -> change.consume(); drag += amount },
                                    onDragEnd = {
                                        val dxDp = with(density) { drag.x.toDp().value } / scale
                                        val dyDp = with(density) { drag.y.toDp().value } / scale
                                        drag = androidx.compose.ui.geometry.Offset.Zero
                                        onMoveButton(b.id, dxDp.toDouble(), dyDp.toDouble())
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(b.name, fontSize = 8.sp, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutPanel(layout: ControlLayout, onOpacity: (Double) -> Unit, onJoystickSize: (Double) -> Unit) {
    Column(modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.voxelium_ui_controls_layout), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.voxelium_ui_controls_layout_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.voxelium_ui_controls_opacity, (layout.opacity * 100).roundToInt()), style = MaterialTheme.typography.labelLarge)
        var opacity by remember(layout.opacity) { mutableStateOf(layout.opacity.toFloat()) }
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            onValueChangeFinished = { onOpacity(opacity.toDouble()) },
            valueRange = ControlLayout.OPACITY_RANGE.start.toFloat()..ControlLayout.OPACITY_RANGE.endInclusive.toFloat()
        )
        Text(stringResource(R.string.voxelium_ui_controls_joystick_size, layout.joystick.size.roundToInt()), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onJoystickSize(layout.joystick.size - 8) }) { Text("−") }
            OutlinedButton(onClick = { onJoystickSize(layout.joystick.size + 8) }) { Text("+") }
        }
        Text(stringResource(R.string.voxelium_ui_controls_button_count, layout.buttons.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ButtonPanel(b: ControlButton, onChange: (ControlButton) -> Unit, onDelete: () -> Unit, onDone: () -> Unit) {
    val kind = when {
        b.special != ControlSpecial.NONE -> ActionKind.SPECIAL
        b.mouse >= 0 -> ActionKind.MOUSE
        else -> ActionKind.KEYS
    }
    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.actionSummary, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDone) { Text(stringResource(R.string.voxelium_ui_controls_done)) }
        }
        var name by remember(b.id) { mutableStateOf(b.name) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onChange(b.copy(name = it)) },
            label = { Text(stringResource(R.string.voxelium_ui_controls_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (k in ActionKind.entries) {
                FilterChip(selected = kind == k, onClick = {
                    onChange(
                        when (k) {
                            ActionKind.KEYS -> b.copy(special = ControlSpecial.NONE, mouse = -1)
                            ActionKind.MOUSE -> b.copy(special = ControlSpecial.NONE, keys = emptyList(), mouse = max(b.mouse, 0))
                            ActionKind.SPECIAL -> b.copy(keys = emptyList(), mouse = -1, special = if (b.special == ControlSpecial.NONE) ControlSpecial.RUNTIME_MENU else b.special)
                        }
                    )
                }, label = { Text(stringResource(k.label)) })
            }
        }
        when (kind) {
            ActionKind.KEYS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    b.keys.forEachIndexed { i, code ->
                        InputChip(selected = true, onClick = { onChange(b.copy(keys = b.keys.filterIndexed { j, _ -> j != i })) }, label = { Text(ControlKeys.name(code) + " ×") })
                    }
                }
                if (b.keys.size < ControlButton.MAX_KEYS) {
                    Picker(
                        label = stringResource(if (b.keys.isEmpty()) R.string.voxelium_ui_controls_choose_key else R.string.voxelium_ui_controls_add_key),
                        options = ControlKeys.all.map { it.code to it.name },
                        onPick = { onChange(b.copy(keys = b.keys + it)) }
                    )
                }
            }
            ActionKind.MOUSE -> Picker(
                label = ControlKeys.mouseButtons.firstOrNull { it.code == b.mouse }?.name ?: "",
                options = ControlKeys.mouseButtons.map { it.code to it.name },
                onPick = { onChange(b.copy(mouse = it)) }
            )
            ActionKind.SPECIAL -> Picker(
                label = b.special.title,
                options = ControlSpecial.entries.filter { it != ControlSpecial.NONE }.mapIndexed { i, s -> i to s.title },
                onPick = { i -> onChange(b.copy(special = ControlSpecial.entries.filter { it != ControlSpecial.NONE }[i])) }
            )
        }
        CheckRow(stringResource(R.string.voxelium_ui_controls_toggle), b.toggle) { onChange(b.copy(toggle = it)) }
        CheckRow(stringResource(R.string.voxelium_ui_controls_in_game), b.showInGame) { onChange(b.copy(showInGame = it)) }
        CheckRow(stringResource(R.string.voxelium_ui_controls_in_menu), b.showInMenu) { onChange(b.copy(showInMenu = it)) }
        CheckRow(stringResource(R.string.voxelium_ui_controls_in_drawer), b.inDrawer) { onChange(b.copy(inDrawer = it)) }
        SizeRow(stringResource(R.string.voxelium_ui_controls_width, b.width.roundToInt()), { onChange(b.copy(width = b.width - 4)) }, { onChange(b.copy(width = b.width + 4)) })
        SizeRow(stringResource(R.string.voxelium_ui_controls_height, b.height.roundToInt()), { onChange(b.copy(height = b.height - 4)) }, { onChange(b.copy(height = b.height + 4)) })
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voxelium_ui_controls_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((code, name) in options) {
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(code) })
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SizeRow(label: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = minus) { Text("−", style = MaterialTheme.typography.titleMedium) }
        IconButton(onClick = plus) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}
