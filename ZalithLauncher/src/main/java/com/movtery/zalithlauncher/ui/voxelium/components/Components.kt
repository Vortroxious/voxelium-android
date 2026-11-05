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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.voxelium.theme.Voxelium

/** A page's title row: optional back button, the title (and a subtitle), then [actions]. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(36.dp)) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.generic_back), modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** A small grey heading above a group ("Installed · 9"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The coloured loader tag on version rows ("FABRIC"). */
@Composable
fun LoaderBadge(loader: String?, isModpack: Boolean = false) {
    val c = Voxelium.colors
    val (bg, fg) = when {
        isModpack -> c.modpack
        loader == null -> c.vanilla
        loader.equals("Fabric", true) -> c.fabric
        loader.equals("Quilt", true) -> c.quilt
        loader.equals("Forge", true) -> c.forge
        loader.equals("NeoForge", true) -> c.neoForge
        else -> c.modpack
    }
    val text = when {
        isModpack -> stringResource(R.string.voxelium_ui_badge_modpack)
        loader == null -> stringResource(R.string.voxelium_ui_badge_vanilla)
        else -> loader.uppercase()
    }
    Box(modifier = Modifier.background(bg, MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** A rounded row on a container surface, optionally clickable and highlighted. */
@Composable
fun RowCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
    ) {
        Row(
            modifier = Modifier
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** The round "play" button on version rows. */
@Composable
fun RoundPlayButton(contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                painterResource(R.drawable.ic_play_arrow_filled),
                contentDescription = contentDescription,
                tint = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
