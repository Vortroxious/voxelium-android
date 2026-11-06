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

package com.movtery.zalithlauncher.ui.voxelium.settings

import android.content.Context
import android.net.Uri
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.context.getFileName
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.multirt.RuntimesManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.file.checkExtensionOrThrow
import com.movtery.zalithlauncher.utils.string.getMessageOrToString
import com.movtery.zalithlauncher.utils.string.throwableToString
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import kotlinx.coroutines.Dispatchers

// Voxelium: kept from ZalithLauncher2's Java manager screen (removed): imports a .tar.xz runtime as a task.

internal fun progressRuntimeUri(
    context: Context,
    uri: Uri,
    callRefresh: () -> Unit,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    fun showError(
        title: AndroidStringText = androidText(R.string.multirt_runtime_import_failed),
        message: AndroidStringText
    ) {
        submitError(
            ErrorViewModel.ThrowableMessage(
                title = title,
                message = message
            )
        )
    }

    val name = context.getFileName(uri) ?: run {
        showError(message = androidText(R.string.multirt_runtime_import_failed_file_name))
        return
    }
    TaskSystem.submitTask(
        Task.runTask(
            id = name,
            dispatcher = Dispatchers.IO,
            task = { task ->
                name.checkExtensionOrThrow(listOf("xz"))

                val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                    showError(message = androidText(R.string.multirt_runtime_import_failed_input_stream))
                    return@runTask
                }
                RuntimesManager.installRuntime(
                    nativeLibDir = PathManager.DIR_NATIVE_LIB,
                    inputStream = inputStream,
                    name = name,
                    updateProgress = { textRes, textArg ->
                        task.updateMessage(androidText(
                            textRes, *textArg
                        ))
                    }
                )
            },
            onError = {
                showError(message = androidText(throwableToString(it)))
            },
            onFinally = callRefresh,
            onCancel = {
                runCatching {
                    RuntimesManager.removeRuntime(name)
                    callRefresh()
                }.onFailure { t ->
                    showError(
                        title = androidText(R.string.multirt_runtime_delete_failed),
                        message = androidText(t.getMessageOrToString())
                    )
                }
            }
        )
    )
}
