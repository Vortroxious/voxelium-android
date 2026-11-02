/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Accounts/MicrosoftAuth.swift (JSONResponse, HTTPClient,
 * URLSessionHTTPClient) (zalithios e28b28b).
 */

package io.github.voxelium.core.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A request as the auth code builds it; the client sends it. */
data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
) {
    val bodyText: String get() = body?.toString(Charsets.UTF_8) ?: ""
}

class JsonResponse(val status: Int, val data: ByteArray) {
    /** The body as a JSON object, or empty when it isn't one. */
    val obj: JsonObject by lazy {
        runCatching { Json.parseToJsonElement(data.toString(Charsets.UTF_8)) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    fun string(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun int(key: String): Int? = (obj[key] as? JsonPrimitive)?.intOrNull
    val oauthError: String? get() = string("error")
}

/** Small JSON-over-HTTP client, injectable for tests. */
fun interface HttpClient {
    suspend fun send(request: HttpRequest): JsonResponse
}

/** `application/x-www-form-urlencoded` POST, fields sorted by key as the Swift client does. */
suspend fun HttpClient.form(url: String, fields: Map<String, String>): JsonResponse {
    val body = fields.toSortedMap().entries.joinToString("&") { (k, v) -> "$k=${formEncode(v)}" }
    return send(
        HttpRequest(url, "POST", mapOf("Content-Type" to "application/x-www-form-urlencoded"), body.toByteArray())
    )
}

/** JSON POST with sorted keys. */
suspend fun HttpClient.json(url: String, body: JsonObject): JsonResponse =
    send(
        HttpRequest(
            url, "POST",
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            sortedKeys(body).toString().toByteArray()
        )
    )

suspend fun HttpClient.get(url: String, bearer: String): JsonResponse =
    send(HttpRequest(url, "GET", mapOf("Authorization" to "Bearer $bearer", "Accept" to "application/json")))

/** Percent-encodes everything except ASCII letters, digits and `-._~`. */
internal fun formEncode(value: String): String = buildString {
    for (b in value.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xff
        val ch = c.toChar()
        if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-._~")) append(ch) else append("%%%02X".format(c))
    }
}

private fun sortedKeys(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.toSortedMap().mapValues { sortedKeys(it.value) })
    is JsonArray -> JsonArray(element.map { sortedKeys(it) })
    else -> element
}

/** The platform client: OkHttp, 30 s timeout. */
class OkHttpJsonClient(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
) : HttpClient {
    override suspend fun send(request: HttpRequest): JsonResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        val contentType = request.headers["Content-Type"] ?: "application/octet-stream"
        builder.method(request.method, request.body?.toRequestBody(contentType.toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            JsonResponse(response.code, response.body.bytes())
        }
    }
}

internal fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonElement.primitiveText(): String? = (this as? JsonPrimitive)?.jsonPrimitive?.content
