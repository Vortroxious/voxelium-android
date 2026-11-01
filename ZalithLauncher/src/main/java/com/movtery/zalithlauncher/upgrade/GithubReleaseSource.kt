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
import com.movtery.zalithlauncher.path.GLOBAL_CLIENT
import com.movtery.zalithlauncher.utils.network.safeBodyAsJson
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Voxelium: launcher update checks read the latest published GitHub release of this project
 * ([BuildKeys.URL_HOME]). Drafts and pre-releases are never offered.
 *
 * Release tags are `vX.Y.Z` (or `X.Y.Z`); the matching version code is `X*10000 + Y*100 + Z`,
 * the scheme `launcher_version_code` in gradle.properties follows.
 */
object GithubReleaseSource {
    private val apiUrl: String
        get() = BuildKeys.URL_HOME.trimEnd('/')
            .replace("https://github.com/", "https://api.github.com/repos/") + "/releases/latest"

    /**
     * @return the latest release as [RemoteData]; a project without any release yet gives
     * code 0, which is never newer than the running launcher
     */
    suspend fun fetchLatest(): RemoteData {
        val release = try {
            GLOBAL_CLIENT.get(apiUrl) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }.safeBodyAsJson<GithubRelease>()
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 404) return noRelease()
            throw e
        }
        return RemoteData(
            code = versionCodeOf(release.tagName)
                ?: throw IllegalStateException("Release tag is not vX.Y.Z: ${release.tagName}"),
            version = release.tagName.removePrefix("v"),
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

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("published_at") val publishedAt: String? = null,
        @SerialName("body") val body: String? = null,
        @SerialName("assets") val assets: List<Asset> = emptyList()
    ) {
        @Serializable
        data class Asset(
            @SerialName("name") val name: String,
            @SerialName("browser_download_url") val browserDownloadUrl: String,
            @SerialName("size") val size: Long = 0L
        )
    }
}
