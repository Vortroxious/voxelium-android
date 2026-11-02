/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Download/FileHash.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.download

import java.io.File
import java.io.IOException
import java.security.MessageDigest

object FileHash {
    /** Streams the file through SHA-1 (Mojang's integrity hash) in 1 MiB chunks. */
    fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return hex(digest.digest())
    }

    fun sha1(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(data))

    fun sha256(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** Checks size (cheap) before hash. A null expectation is not checked. */
    fun verify(file: File, sha1: String?, size: Long?): IntegrityResult {
        if (!file.isFile) return IntegrityResult.Missing
        val actualSize = file.length()
        if (size != null && size != actualSize) return IntegrityResult.SizeMismatch(expected = size, actual = actualSize)
        if (sha1 == null) return IntegrityResult.Unverified
        val actual = try {
            sha1(file)
        } catch (_: IOException) {
            return IntegrityResult.Missing
        }
        return if (actual.equals(sha1, ignoreCase = true)) IntegrityResult.Valid
        else IntegrityResult.HashMismatch(expected = sha1, actual = actual)
    }
}

sealed class IntegrityResult {
    data object Valid : IntegrityResult()
    /** File exists and matches size, but no hash was available to check. */
    data object Unverified : IntegrityResult()
    data object Missing : IntegrityResult()
    data class SizeMismatch(val expected: Long, val actual: Long) : IntegrityResult()
    data class HashMismatch(val expected: String, val actual: String) : IntegrityResult()

    val isAcceptable: Boolean get() = this == Valid || this == Unverified
}
