/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Android counterpart of URLSessionTransport in VoxeliumCore/Download/DownloadManager.swift
 * (zalithios e28b28b).
 */

package io.github.voxelium.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Downloads with OkHttp into [tempDir] (the system temp directory by default). */
class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient(),
    private val tempDir: File? = null
) : HttpTransport {
    override suspend fun download(url: String): TransportResponse = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).build())
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val tmp = File.createTempFile("vx-", ".download", tempDir)
                try {
                    response.body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                }
                TransportResponse(tmp, response.code)
            }
        } finally {
            cancelHandle.dispose()
        }
    }
}
