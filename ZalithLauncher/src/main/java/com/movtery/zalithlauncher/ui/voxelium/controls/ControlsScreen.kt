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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

/** Voxelium: the Controls page. The layout editor arrives with the iOS-style in-game controls (U2). */
@Composable
fun VoxeliumControlsScreen(backScreenViewModel: ScreenBackStackViewModel) {
    BaseScreen(screenKey = NormalNavKey.VoxeliumControls, currentKey = backScreenViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeader(title = stringResource(R.string.voxelium_ui_controls))
            Text(stringResource(R.string.voxelium_ui_controls_soon), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.voxelium_ui_controls_soon_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
