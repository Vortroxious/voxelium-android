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

package com.movtery.zalithlauncher.game.download.modpack.install

/**
 * Voxelium: a CurseForge file without a downloadUrl, which its author only allows to be
 * downloaded from CurseForge's website. It is never fetched another way.
 * @param websiteUrl the file's page on CurseForge, if known
 */
class WebsiteOnlyFileException(
    val name: String,
    val websiteUrl: String?
) : Exception("$name is only available on CurseForge's website")

/**
 * Voxelium: a modpack needs files that can only be downloaded from CurseForge's website.
 * The message lists each one with its link; the dialogs show it as is.
 */
class WebsiteOnlyModsException(
    val files: List<WebsiteOnlyFileException>
) : Exception(
    buildString {
        append("This modpack needs ${files.size} file(s) whose authors only allow downloads from CurseForge's website. ")
        append("Voxelium doesn't download them another way. Download them from CurseForge:")
        files.sortedBy { it.name.lowercase() }.forEach { file ->
            append("\n• ").append(file.name)
            file.websiteUrl?.let { append("\n  ").append(it) }
        }
    }
)
