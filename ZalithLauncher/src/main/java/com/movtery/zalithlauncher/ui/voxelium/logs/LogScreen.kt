/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.zalithlauncher.ui.voxelium.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.code_editor.EditorState
import com.movtery.zalithlauncher.ui.code_editor.SoraEditor
import com.movtery.zalithlauncher.ui.code_editor.TextMateRegistry
import com.movtery.zalithlauncher.ui.code_editor.scheme.SchemeIDEADark
import com.movtery.zalithlauncher.ui.code_editor.scheme.SchemeIDEALight
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.navigateTo
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.utils.file.formatFileSize
import com.movtery.zalithlauncher.utils.file.shareFile
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Opens a log file in the log page. */
fun NavBackStack<TitledNavKey>.navigateToLogView(
    logPath: String,
) = this.navigateTo(
    screenKey = NormalNavKey.LogView(logPath = logPath),
    useClassEquality = true
)

/** Logs bigger than this are read from the end. */
private const val MAX_LOG_VIEW_SIZE: Long = 8L * 1024 * 1024

private fun readLog(file: File): String {
    val size = file.length()
    if (size <= MAX_LOG_VIEW_SIZE) return file.readText()
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(size - MAX_LOG_VIEW_SIZE)
        val bytes = ByteArray(MAX_LOG_VIEW_SIZE.toInt())
        raf.readFully(bytes)
        val text = String(bytes, Charsets.UTF_8)
            // 截断位置可能留下不完整的字符，解码成替换字符后去掉
            .trimStart('\uFFFD')

        // 从首个换行处开始，避免截断处留下半个字符导致首行乱码
        // 窗口内没有换行时（超长单行）则不截断，保留内容
        val firstNewline = text.indexOf('\n')
        return if (firstNewline >= 0 && firstNewline < text.length - 1) {
            text.substring(firstNewline + 1)
        } else {
            text
        }
    }
}

/**
 * Voxelium: a log file, read-only and highlighted, with its name, size and Share at the top.
 * The editor is the launcher's Sora editor component.
 */
@Composable
fun VoxeliumLogScreen(
    key: NormalNavKey.LogView,
    backStackViewModel: ScreenBackStackViewModel,
    onBack: () -> Unit
) {
    val isDark = isLauncherInDarkTheme()
    val context = LocalContext.current
    val file = remember(key) { File(key.logPath) }

    var editorState by remember { mutableStateOf<EditorState>(EditorState.Loading) }
    LaunchedEffect(key) {
        editorState = EditorState.Loading
        val content = withContext(Dispatchers.IO) {
            runCatching { readLog(file) }.getOrElse { e ->
                Logger.warning("ViewLog", "Unable to read log file!", e)
                e.message
            }
        }
        editorState = EditorState.Success(Content(content))
    }

    BaseScreen(screenKey = key, currentKey = backStackViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)) {
            PageHeader(
                title = file.name,
                subtitle = stringResource(R.string.voxelium_ui_log_subtitle, formatFileSize(file.length())),
                onBack = onBack
            ) {
                OutlinedButton(onClick = { shareFile(context, file) }) { Text(stringResource(R.string.voxelium_ui_crash_share_log)) }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                val fallbackScheme = remember(isDark) { if (isDark) SchemeIDEADark() else SchemeIDEALight() }
                var language by remember { mutableStateOf<Language?>(null) }
                var scheme by remember { mutableStateOf<EditorColorScheme?>(null) }
                LaunchedEffect(isDark) {
                    language = TextMateRegistry.languageFor("text.log", context)
                    scheme = TextMateRegistry.colorScheme(isDark, context)
                }
                SoraEditor(
                    state = editorState,
                    scheme = scheme ?: fallbackScheme,
                    language = language,
                    isReadOnly = true,
                    onSaveClick = {}
                )
            }
        }
    }
}
