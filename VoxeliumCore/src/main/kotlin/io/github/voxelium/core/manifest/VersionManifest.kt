/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Manifest/VersionManifest.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.manifest

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/** Mojang's version list: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json */
@Serializable
data class VersionManifest(
    val latest: Latest,
    val versions: List<Entry>
) {
    @Serializable
    data class Latest(val release: String, val snapshot: String)

    @Serializable
    data class Entry(
        val id: String,
        val type: VersionType,
        val url: String,
        @Serializable(with = MojangInstantSerializer::class) val time: Instant,
        @Serializable(with = MojangInstantSerializer::class) val releaseTime: Instant,
        /** SHA-1 of the version JSON (v2 manifest only). */
        val sha1: String? = null,
        val complianceLevel: Int? = null
    )

    fun entry(id: String): Entry? = versions.firstOrNull { it.id == id }

    fun versions(types: Set<VersionType>): List<Entry> = versions.filter { it.type in types }

    companion object {
        const val OFFICIAL_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        fun decode(json: String): VersionManifest = MojangJson.decodeFromString(serializer(), json)
    }
}

/** `release`, `snapshot`, `old_beta`, `old_alpha`, or anything else Mojang adds later. */
@Serializable(with = VersionType.Serializer::class)
@JvmInline
value class VersionType(val rawValue: String) {
    companion object {
        val RELEASE = VersionType("release")
        val SNAPSHOT = VersionType("snapshot")
        val OLD_BETA = VersionType("old_beta")
        val OLD_ALPHA = VersionType("old_alpha")
    }

    object Serializer : KSerializer<VersionType> {
        override val descriptor = PrimitiveSerialDescriptor("VersionType", PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder) = VersionType(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: VersionType) = encoder.encodeString(value.rawValue)
    }
}
