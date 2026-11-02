/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Manifest/VersionManifest.swift (MojangJSON) (zalithios e28b28b).
 */

package io.github.voxelium.core.manifest

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** The JSON configuration for Mojang and mod loader metadata: unknown keys are ignored. */
val MojangJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Mojang timestamps look like `2023-06-12T13:25:51+00:00`, sometimes with fractional seconds.
 * NeoForge writes `2026-10-15T01:40:32.034268352` (nanoseconds, no zone): read to the second, as UTC.
 */
object MojangInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("MojangInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val s = decoder.decodeString()
        try {
            return OffsetDateTime.parse(s).toInstant()
        } catch (_: DateTimeParseException) {
        }
        if (s.length >= 19) {
            try {
                return LocalDateTime.parse(s.take(19)).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
            }
        }
        throw SerializationException("Bad date $s")
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.atOffset(ZoneOffset.UTC).toString())
    }
}
