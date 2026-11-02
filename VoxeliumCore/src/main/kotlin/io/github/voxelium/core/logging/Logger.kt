/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Logging/Log.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.logging

import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Severity levels shared by the in-memory/file log and the platform log. */
enum class LogLevel(val label: String) {
    DEBUG("DEBUG"), INFO("INFO"), WARNING("WARN"), ERROR("ERROR");

    override fun toString(): String = label
}

data class LogEntry(
    val date: Instant,
    val level: LogLevel,
    val category: String,
    val message: String
) {
    val formatted: String
        get() = "${DateTimeFormatter.ISO_INSTANT.format(date)} [$level] $category: $message"
}

/**
 * Thread-safe logger that mirrors entries to the platform log ([sink], android.util.Log in the app)
 * and, optionally, an append-only file (the launcher log the user can export).
 */
class Logger(private val maxEntries: Int = 5000) {
    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>()
    private var file: FileOutputStream? = null

    @Volatile
    var minimumLevel: LogLevel = LogLevel.DEBUG

    /** Where entries also go, e.g. android.util.Log; set by the app. */
    @Volatile
    var sink: ((LogEntry) -> Unit)? = null

    /** Starts mirroring entries to [target], creating the file if needed. */
    fun attachFile(target: File) {
        target.parentFile?.mkdirs()
        val stream = FileOutputStream(target, true)
        synchronized(lock) {
            runCatching { file?.close() }
            file = stream
        }
    }

    fun log(level: LogLevel, category: String, message: String) {
        if (level < minimumLevel) return
        val entry = LogEntry(Instant.now(), level, category, message)
        sink?.invoke(entry)
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > maxEntries) entries.removeFirst()
            runCatching { file?.write((entry.formatted + "\n").toByteArray()) }
        }
    }

    fun debug(category: String, message: String) = log(LogLevel.DEBUG, category, message)
    fun info(category: String, message: String) = log(LogLevel.INFO, category, message)
    fun warning(category: String, message: String) = log(LogLevel.WARNING, category, message)
    fun error(category: String, message: String) = log(LogLevel.ERROR, category, message)

    val snapshot: List<LogEntry>
        get() = synchronized(lock) { entries.toList() }

    companion object {
        val shared = Logger()
    }
}
