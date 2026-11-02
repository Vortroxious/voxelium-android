/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/ZipReader.swift (zalithios e28b28b). Android uses
 * java.util.zip instead of the hand-written reader and Apple's Compression framework.
 */

package io.github.voxelium.core.content

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** A read-only ZIP reader for mod jars and resource/shader packs. Close it after use. */
class ZipReader(file: File) : Closeable {
    sealed class ZipError(message: String) : IOException(message) {
        data object NotAZip : ZipError("Not a ZIP file")
        data class Unsupported(val what: String) : ZipError("Unsupported: $what")
        data class Corrupt(val what: String) : ZipError("Corrupt: $what")
    }

    private val zip: ZipFile = try {
        ZipFile(file)
    } catch (_: ZipException) {
        throw ZipError.NotAZip
    }

    /** Entry names, in the order the archive lists them. */
    val entries: List<String> = zip.entries().asSequence().map { it.name }.toList()

    fun hasEntry(name: String): Boolean = zip.getEntry(name) != null

    /**
     * The uncompressed bytes of [name], or null if there is no such entry. Entries larger than
     * [limit] are refused (metadata files are small).
     */
    fun read(name: String, limit: Long = 4L shl 20): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        if (entry.size > limit) throw ZipError.Unsupported("$name is too large")
        return try {
            zip.getInputStream(entry).use { it.readBytes() }
        } catch (e: ZipException) {
            throw ZipError.Corrupt("data of $name (${e.message})")
        }
    }

    override fun close() = zip.close()
}
