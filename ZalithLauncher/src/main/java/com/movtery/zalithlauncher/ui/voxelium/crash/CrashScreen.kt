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

package com.movtery.zalithlauncher.ui.voxelium.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.voxelium.splash.VoxeliumMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The end of a log file, for the crash page (the full file is shared). */
private fun tailOf(file: File, lines: Int = 300): String = runCatching {
    file.bufferedReader().useLines { seq -> ArrayDeque<String>(lines).also { q -> seq.forEach { if (q.size == lines) q.removeFirst(); q.addLast(it) } } }
        .joinToString("\n")
}.getOrDefault("")

/**
 * Voxelium: what the player sees when the game or the launcher stops unexpectedly. The reason on
 * the left with what to do next; the error or the end of the game log on the right.
 */
@Composable
fun VoxeliumCrashScreen(
    gameCrash: Boolean,
    message: String,
    details: String?,
    logFile: File?,
    canUpload: Boolean,
    canRestart: Boolean,
    onShareLog: () -> Unit,
    onUpload: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    var text by remember { mutableStateOf(details.orEmpty()) }
    LaunchedEffect(logFile, details) {
        if (details == null && logFile != null) text = withContext(Dispatchers.IO) { tailOf(logFile) }
    }
    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VoxeliumMark(40)
            Text(
                stringResource(if (gameCrash) R.string.voxelium_ui_crash_game_title else R.string.voxelium_ui_crash_launcher_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (gameCrash) R.string.voxelium_ui_crash_game_hint else R.string.voxelium_ui_crash_launcher_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (logFile != null) {
                Button(onClick = onShareLog, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.voxelium_ui_crash_share_log)) }
                if (canUpload) {
                    OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.voxelium_ui_crash_share_link)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (canRestart) {
                    OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.voxelium_ui_crash_restart), maxLines = 1) }
                }
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.voxelium_ui_crash_close)) }
            }
        }
        Surface(modifier = Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    stringResource(if (details != null) R.string.voxelium_ui_crash_details else R.string.voxelium_ui_crash_log_end),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val logScroll = rememberScrollState()
                // Start at the end of the log, where the error is.
                LaunchedEffect(Unit) { snapshotFlow { logScroll.maxValue }.collect { logScroll.scrollTo(it) } }
                SelectionContainer(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    Text(
                        text.ifEmpty { stringResource(R.string.voxelium_ui_crash_no_log) },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.verticalScroll(logScroll).horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
