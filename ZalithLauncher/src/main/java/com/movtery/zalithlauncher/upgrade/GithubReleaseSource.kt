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

package com.movtery.zalithlauncher.upgrade

import com.movtery.zalithlauncher.BuildKeys
import io.github.voxelium.core.accounts.OkHttpJsonClient
import io.github.voxelium.core.launch.UpdateCheck

/**
 * Voxelium: launcher update checks use VoxeliumCore's [UpdateCheck] (as Voxelium for iOS does) on
 * the latest published GitHub release of this project ([BuildKeys.URL_HOME]). Drafts and
 * pre-releases are never offered. The release becomes upstream's [RemoteData] for its update dialog.
 *
 * Release tags are `vX.Y.Z`; versions are compared by number (2.10 > 2.9). [RemoteData.code] is
 * `X*10000 + Y*100 + Z`, the scheme `launcher_version_code` in gradle.properties follows; it only
 * identifies a release the user chose to ignore.
 */
object GithubReleaseSource {
    private val repository: String
        get() = BuildKeys.URL_HOME.trimEnd('/').removePrefix("https://github.com/")

    /**
     * @return the latest release as [RemoteData]; a project without any release yet gives an empty
     * version, which is never newer than the running launcher
     */
    suspend fun fetchLatest(): RemoteData {
        val release = try {
            UpdateCheck(OkHttpJsonClient(), repository).latest()
        } catch (e: UpdateCheck.CheckException.Http) {
            if (e.status == 404) return noRelease()
            throw e
        }
        return RemoteData(
            code = versionCodeOf(release.tagName) ?: 0,
            version = release.version,
            createdAt = release.publishedAt ?: "",
            files = release.assets
                .filter { it.name.endsWith(".apk", ignoreCase = true) }
                .map { asset ->
                    RemoteData.RemoteFile(
                        fileName = asset.name,
                        uri = asset.browserDownloadUrl,
                        arch = archOf(asset.name),
                        size = asset.size
                    )
                },
            defaultBody = RemoteData.RemoteBody(language = "en", markdown = release.body.orEmpty()),
            bodies = emptyList()
        )
    }

    fun versionCodeOf(tag: String): Int? {
        val parts = tag.removePrefix("v").substringBefore('-').split('.')
        if (parts.size != 3) return null
        val (major, minor, patch) = parts.map { it.toIntOrNull() ?: return null }
        if (minor !in 0..99 || patch !in 0..99) return null
        return major * 10000 + minor * 100 + patch
    }

    private fun archOf(fileName: String): RemoteData.RemoteFile.Arch {
        val name = fileName.lowercase()
        return when {
            name.contains("arm64-v8a") -> RemoteData.RemoteFile.Arch.ARM64
            name.contains("armeabi-v7a") -> RemoteData.RemoteFile.Arch.ARM
            name.contains("x86_64") -> RemoteData.RemoteFile.Arch.X86_64
            name.contains("x86") -> RemoteData.RemoteFile.Arch.X86
            else -> RemoteData.RemoteFile.Arch.ALL
        }
    }

    private fun noRelease() = RemoteData(
        code = 0,
        version = "",
        createdAt = "",
        files = emptyList(),
        defaultBody = RemoteData.RemoteBody(language = "en", markdown = ""),
        bodies = emptyList()
    )
}
