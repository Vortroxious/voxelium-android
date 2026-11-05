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

package com.movtery.zalithlauncher.ui.voxelium.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R

/** The five places in Voxelium, in rail order. */
enum class VoxeliumPlace(@DrawableRes val icon: Int, val label: Int) {
    PLAY(R.drawable.ic_play_arrow_filled, R.string.voxelium_ui_play),
    INSTALL(R.drawable.ic_download_2_outlined, R.string.voxelium_ui_install),
    ACCOUNTS(R.drawable.ic_person_outlined, R.string.voxelium_ui_accounts),
    CONTROLS(R.drawable.ic_sports_esports_outlined, R.string.voxelium_ui_controls),
    SETTINGS(R.drawable.ic_settings_filled, R.string.voxelium_ui_settings)
}

/** Screens from Zalith Launcher 2 that stay reachable until Voxelium replaces or drops them. */
enum class MoreItem(val label: Int) {
    DOWNLOAD(R.string.voxelium_ui_more_download),
    MULTIPLAYER(R.string.voxelium_ui_more_multiplayer),
    FILES(R.string.voxelium_ui_more_files),
    CLASSIC_HOME(R.string.voxelium_ui_more_classic_home)
}

/**
 * Voxelium: the slim navigation rail on the left (the app runs in landscape). The Voxelium mark on
 * top, the five places, then the running tasks and "More".
 */
@Composable
fun VoxeliumRail(
    selected: VoxeliumPlace?,
    taskCount: Int,
    onSelect: (VoxeliumPlace) -> Unit,
    onTasks: () -> Unit,
    onMore: (MoreItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(76.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_package_2_outlined),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        for (place in VoxeliumPlace.entries) {
            RailItem(icon = place.icon, label = stringResource(place.label), selected = place == selected) { onSelect(place) }
        }
        Spacer(Modifier.weight(1f))
        if (taskCount > 0) {
            RailItem(
                icon = R.drawable.ic_list_alt_check_outlined,
                label = stringResource(R.string.voxelium_ui_tasks),
                selected = false,
                badge = taskCount,
                onClick = onTasks
            )
        }
        var more by remember { mutableStateOf(false) }
        Box {
            RailItem(icon = R.drawable.ic_more_horiz, label = stringResource(R.string.voxelium_ui_more), selected = false) { more = true }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                for (item in MoreItem.entries) {
                    DropdownMenuItem(text = { Text(stringResource(item.label)) }, onClick = { more = false; onMore(item) })
                }
            }
        }
    }
}

@Composable
private fun RailItem(@DrawableRes icon: Int, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            if (badge > 0) {
                BadgedBox(badge = { Badge { Text(badge.toString()) } }) {
                    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
