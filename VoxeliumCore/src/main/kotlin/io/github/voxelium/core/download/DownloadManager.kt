/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Download/DownloadManager.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.download

import io.github.voxelium.core.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** What a transport returns: a temporary file owned by the caller, and the HTTP status. */
data class TransportResponse(val file: File, val statusCode: Int)

/** Abstracts the network so download logic is testable without it. */
fun interface HttpTransport {
    /** Downloads [url] to a temporary file owned by the caller. */
    suspend fun download(url: String): TransportResponse
}

data class DownloadItem(
    val url: String,
    val destination: File,
    val sha1: String?,
    val size: Long?
)

sealed class DownloadException(message: String) : Exception(message) {
    data class HttpStatus(val code: Int, val url: String) : DownloadException("HTTP $code for $url")
    data class Integrity(val result: IntegrityResult, val url: String) :
        DownloadException("Integrity check failed ($result) for $url")
    data class Transport(val reason: String, val url: String) : DownloadException("Network error for $url: $reason")
    data class Exhausted(val attempts: Int, val last: String, val url: String) :
        DownloadException("Gave up after $attempts attempts for $url: $last")

    internal val isRetryable: Boolean
        get() = when (this) {
            is HttpStatus -> code == 408 || code == 429 || code >= 500
            is Integrity, is Transport -> true
            is Exhausted -> false
        }
}

sealed class DownloadOutcome {
    /** The file was already present and passed verification. */
    data object AlreadyValid : DownloadOutcome()
    data class Downloaded(val attempts: Int) : DownloadOutcome()
}

class RetryPolicy(
    maxAttempts: Int = 4,
    val baseDelay: Duration = 500.milliseconds,
    val maxDelay: Duration = 8.seconds
) {
    val maxAttempts: Int = maxOf(1, maxAttempts)

    /** Exponential backoff: base * 2^(attempt-1), capped. */
    fun delay(afterAttempt: Int): Duration {
        val scaled = baseDelay * (1L shl minOf(afterAttempt - 1, 16)).toDouble()
        return minOf(scaled, maxDelay)
    }
}

class DownloadReport {
    val outcomes: MutableMap<DownloadItem, DownloadOutcome> = mutableMapOf()
    val failures: MutableMap<DownloadItem, DownloadException> = mutableMapOf()
    val succeeded: Boolean get() = failures.isEmpty()
}

data class DownloadProgress(val completed: Int, val total: Int, val failed: Int)

/**
 * Downloads files with integrity verification, retry with backoff, and atomic placement
 * (a corrupt or partial file never replaces a good one).
 */
class DownloadManager(
    val transport: HttpTransport = OkHttpTransport(),
    val retry: RetryPolicy = RetryPolicy(),
    private val log: Logger = Logger.shared,
    private val sleep: suspend (Duration) -> Unit = { delay(it) }
) {
    /** Ensures `item.destination` exists and matches its expected hash/size. */
    suspend fun ensure(item: DownloadItem): DownloadOutcome {
        val existing = FileHash.verify(item.destination, item.sha1, item.size)
        if (existing.isAcceptable) return DownloadOutcome.AlreadyValid
        if (existing != IntegrityResult.Missing) {
            log.warning("download", "Existing file is invalid ($existing), re-downloading: ${item.destination.name}")
        }

        var lastError: DownloadException = DownloadException.Transport("not attempted", item.url)
        for (attempt in 1..retry.maxAttempts) {
            currentCoroutineContext().ensureActive()
            try {
                attemptOnce(item)
                return DownloadOutcome.Downloaded(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                lastError = e
                log.warning("download", "Attempt $attempt/${retry.maxAttempts} failed: ${e.message}")
                if (!e.isRetryable) throw e
            } catch (e: Exception) {
                lastError = DownloadException.Transport(e.toString(), item.url)
                log.warning("download", "Attempt $attempt/${retry.maxAttempts} failed: ${lastError.message}")
            }
            if (attempt < retry.maxAttempts) sleep(retry.delay(attempt))
        }
        throw DownloadException.Exhausted(retry.maxAttempts, lastError.message ?: "", item.url)
    }

    private suspend fun attemptOnce(item: DownloadItem) {
        val (tmp, status) = transport.download(item.url)
        try {
            if (status !in 200..299) throw DownloadException.HttpStatus(status, item.url)

            val check = FileHash.verify(tmp, item.sha1, item.size)
            if (!check.isAcceptable) throw DownloadException.Integrity(check, item.url)

            val dest = item.destination
            dest.parentFile?.mkdirs()
            // Stage next to the destination first (the temp file may be on another file system),
            // then swap it in atomically.
            val staged = File(dest.parentFile, ".${dest.name}.part")
            Files.move(tmp.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(staged.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                staged.delete()
            }
        } finally {
            tmp.delete()
        }
    }

    /**
     * Runs many downloads with bounded concurrency. Individual failures are collected in the
     * report rather than aborting the batch.
     */
    suspend fun run(
        items: List<DownloadItem>,
        concurrency: Int = 8,
        progress: ((DownloadProgress) -> Unit)? = null
    ): DownloadReport = coroutineScope {
        val unique = items.toSet().sortedBy { it.destination.path }
        val report = DownloadReport()
        val lock = Mutex()
        val semaphore = Semaphore(maxOf(1, concurrency))
        var completed = 0

        unique.map { item ->
            async {
                semaphore.withPermit {
                    val result: Result<DownloadOutcome> = try {
                        Result.success(ensure(item))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: DownloadException) {
                        Result.failure(e)
                    } catch (e: Exception) {
                        Result.failure(DownloadException.Transport(e.toString(), item.url))
                    }
                    lock.withLock {
                        result.fold(
                            onSuccess = { report.outcomes[item] = it },
                            onFailure = { report.failures[item] = it as DownloadException }
                        )
                        completed += 1
                        progress?.invoke(DownloadProgress(completed, unique.size, report.failures.size))
                    }
                }
            }
        }.awaitAll()
        report
    }
}
