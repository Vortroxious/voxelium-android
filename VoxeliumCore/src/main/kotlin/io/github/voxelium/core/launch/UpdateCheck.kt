/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/UpdateCheck.swift (zalithios e28b28b).
 * Android additions: the repository is a parameter, and releases carry their body, date and
 * assets so the app can offer the APK.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.accounts.HttpClient
import io.github.voxelium.core.accounts.OkHttpJsonClient
import io.github.voxelium.core.accounts.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Asks GitHub whether a newer Voxelium release exists. It only reads the public releases API
 * (no account, nothing about the player is sent).
 */
class UpdateCheck(
    private val http: HttpClient = OkHttpJsonClient(),
    repository: String = ANDROID_REPOSITORY
) {
    val latestUrl = "https://api.github.com/repos/$repository/releases/latest"

    @Serializable
    data class Release(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<Asset> = emptyList()
    ) {
        @Serializable
        data class Asset(
            val name: String,
            @SerialName("browser_download_url") val browserDownloadUrl: String,
            val size: Long = 0
        )

        /** The version from the tag: "v2.4" → "2.4". */
        val version: String get() = trimmed(tagName)
    }

    sealed class Result {
        data class UpToDate(val latest: String) : Result()
        data class Available(val release: Release) : Result()
    }

    sealed class CheckException(message: String) : Exception(message) {
        data class Http(val status: Int) : CheckException("GitHub answered with HTTP $status.")
        data object BadResponse : CheckException("Unexpected response from GitHub.")
    }

    /**
     * The latest published release. GitHub's "latest" already leaves out drafts and pre-releases;
     * they are checked again in case that changes.
     */
    suspend fun latest(): Release {
        val r = http.get(latestUrl)
        if (r.status != 200) throw CheckException.Http(r.status)
        val release = runCatching { JSON.decodeFromString(Release.serializer(), r.data.toString(Charsets.UTF_8)) }
            .getOrNull() ?: throw CheckException.BadResponse
        if (release.draft || release.prerelease) throw CheckException.BadResponse
        return release
    }

    /** Compares the latest release with the running version. */
    suspend fun check(current: String): Result {
        val release = latest()
        return if (isNewer(release.version, current)) Result.Available(release) else Result.UpToDate(release.version)
    }

    companion object {
        const val ANDROID_REPOSITORY = "Vortroxious/Voxelium-android"

        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Whether [version] is newer than [current], numerically by part: 2.10 > 2.9, 2.4 > 2.3.1,
         * and 2.4 equals 2.4.0. A leading "v" is ignored.
         */
        fun isNewer(version: String, current: String): Boolean {
            val a = parts(version)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        internal fun trimmed(tag: String): String =
            if (tag.startsWith("v") || tag.startsWith("V")) tag.drop(1) else tag

        /** The numeric parts; anything after the numbers ("2.4-beta") is ignored. */
        internal fun parts(version: String): List<Int> =
            trimmed(version).split(".").map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}
