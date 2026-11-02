/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Manifest/VersionInfo.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.manifest

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * A single file that can be downloaded and checked: `{sha1, size, url}`
 * (plus `path` for library artifacts, `id` for asset index/logging files).
 */
@Serializable(with = RemoteFile.Serializer::class)
data class RemoteFile(
    val sha1: String?,
    val size: Long?,
    val url: String,
    val path: String? = null,
    val id: String? = null
) {
    /** True for files a Forge/NeoForge installer creates: nothing to download; the loader's processors create them. */
    val isProducedByInstaller: Boolean get() = url == PRODUCED_BY_INSTALLER

    companion object {
        /**
         * Forge and NeoForge list files their installer creates (e.g. the patched
         * `forge:…:client` jar) with an empty `url`; they decode to this URL.
         */
        const val PRODUCED_BY_INSTALLER = "voxelium-installer:"
    }

    @Serializable
    private class Surrogate(
        val sha1: String? = null,
        val size: Long? = null,
        val url: String,
        val path: String? = null,
        val id: String? = null
    )

    object Serializer : KSerializer<RemoteFile> {
        override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

        override fun deserialize(decoder: Decoder): RemoteFile {
            val s = decoder.decodeSerializableValue(Surrogate.serializer())
            return RemoteFile(s.sha1, s.size, s.url.ifEmpty { PRODUCED_BY_INSTALLER }, s.path, s.id)
        }

        override fun serialize(encoder: Encoder, value: RemoteFile) {
            val url = if (value.isProducedByInstaller) "" else value.url
            encoder.encodeSerializableValue(Surrogate.serializer(), Surrogate(value.sha1, value.size, url, value.path, value.id))
        }
    }
}

/** A version JSON such as `1.20.1.json`. */
@Serializable
data class VersionInfo(
    val id: String,
    val type: VersionType? = null,
    val mainClass: String,
    /**
     * Set by mod loader profiles: the vanilla (or other) version this one builds on.
     * The resolver merges the parent in ([merged]) before planning.
     */
    val inheritsFrom: String? = null,
    /** Which version's client jar to run (`versions/<jar>/<jar>.jar`); set when merging. */
    val jar: String? = null,
    val assets: String? = null,
    val assetIndex: AssetIndexRef? = null,
    val downloads: Downloads? = null,
    val libraries: List<Library>,
    val javaVersion: JavaVersion? = null,
    /** Modern (1.13+) argument format. */
    val arguments: Arguments? = null,
    /** Legacy (<1.13) single-string game arguments. */
    val minecraftArguments: String? = null,
    val logging: Logging? = null,
    @Serializable(with = MojangInstantSerializer::class) val releaseTime: Instant? = null
) {
    @Serializable
    data class AssetIndexRef(
        val id: String,
        val sha1: String,
        val size: Long,
        val totalSize: Long? = null,
        val url: String
    )

    @Serializable
    data class JavaVersion(val component: String, val majorVersion: Int)

    @Serializable
    data class Downloads(val client: RemoteFile? = null, val server: RemoteFile? = null)

    @Serializable
    data class Arguments(val game: List<Argument>? = null, val jvm: List<Argument>? = null)

    @Serializable
    data class Logging(val client: Config? = null) {
        @Serializable
        data class Config(val argument: String, val file: RemoteFile, val type: String)
    }

    /** The version whose client jar is launched: [jar], else this version's own id. */
    val clientJarId: String get() = jar ?: id

    /**
     * This profile with its parent merged in, following the vanilla launcher's inheritance rules:
     * the child's id, main class and type win; the parent's libraries come first and the child's
     * after them, so the child's newer copy wins the planner's later-entry de-duplication
     * (e.g. Fabric's ASM); argument lists are concatenated parent-first; everything else falls
     * back to the parent, including the client jar.
     */
    fun merged(parent: VersionInfo): VersionInfo {
        val args = if (arguments != null || parent.arguments != null) {
            fun join(a: List<Argument>?, b: List<Argument>?): List<Argument>? =
                if (a == null && b == null) null else (a ?: emptyList()) + (b ?: emptyList())
            Arguments(
                game = join(parent.arguments?.game, arguments?.game),
                jvm = join(parent.arguments?.jvm, arguments?.jvm)
            )
        } else null
        return VersionInfo(
            id = id, type = type ?: parent.type, mainClass = mainClass,
            inheritsFrom = parent.inheritsFrom, jar = jar ?: parent.clientJarId,
            assets = assets ?: parent.assets, assetIndex = assetIndex ?: parent.assetIndex,
            downloads = downloads ?: parent.downloads, libraries = parent.libraries + libraries,
            javaVersion = javaVersion ?: parent.javaVersion, arguments = args,
            minecraftArguments = minecraftArguments ?: parent.minecraftArguments,
            logging = logging ?: parent.logging, releaseTime = releaseTime ?: parent.releaseTime
        )
    }

    companion object {
        fun decode(json: String): VersionInfo = MojangJson.decodeFromString(serializer(), json)
    }
}

/**
 * An argument entry: either a plain string or `{rules, value}` where value is a string or an
 * array of strings.
 */
@Serializable(with = Argument.Serializer::class)
sealed class Argument {
    data class Plain(val value: String) : Argument()
    data class Conditional(val rules: List<Rule>, val values: List<String>) : Argument()

    fun resolve(platform: RulePlatform): List<String> = when (this) {
        is Plain -> listOf(value)
        is Conditional -> if (RuleEvaluator.allows(rules, platform)) values else emptyList()
    }

    object Serializer : KSerializer<Argument> {
        override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

        override fun deserialize(decoder: Decoder): Argument {
            val json = (decoder as? JsonDecoder)?.json ?: throw SerializationException("Argument needs JSON")
            return when (val element = decoder.decodeJsonElement()) {
                is JsonPrimitive -> if (element.isString) Plain(element.content) else throw SerializationException("Bad argument $element")
                is JsonObject -> {
                    val rules = element["rules"]?.let { json.decodeFromJsonElement(ListSerializer(Rule.serializer()), it) } ?: emptyList()
                    val values = when (val value = element["value"]) {
                        is JsonPrimitive -> listOf(value.content)
                        is JsonArray -> value.map { it.jsonPrimitive.content }
                        else -> throw SerializationException("Argument without value")
                    }
                    Conditional(rules, values)
                }
                else -> throw SerializationException("Bad argument $element")
            }
        }

        override fun serialize(encoder: Encoder, value: Argument) {
            val json = (encoder as? JsonEncoder)?.json ?: throw SerializationException("Argument needs JSON")
            when (value) {
                is Plain -> encoder.encodeString(value.value)
                is Conditional -> encoder.encodeJsonElement(buildJsonObject {
                    put("rules", json.encodeToJsonElement(ListSerializer(Rule.serializer()), value.rules))
                    put("value", JsonArray(value.values.map { JsonPrimitive(it) }))
                })
            }
        }
    }
}

@Serializable
data class Library(
    /** Maven coordinate, e.g. `com.mojang:brigadier:1.1.8`. */
    val name: String,
    val downloads: Downloads? = null,
    /** Maven repository base used by mod loader libraries without `downloads`. */
    val url: String? = null,
    /** Checksum and size that mod loader metadata (Fabric, Quilt) gives next to `url`. */
    val sha1: String? = null,
    val size: Long? = null,
    /** OS name → classifier (legacy natives format, may contain `${arch}`). */
    val natives: Map<String, String>? = null,
    val rules: List<Rule>? = null,
    val extract: Extract? = null
) {
    @Serializable
    data class Downloads(val artifact: RemoteFile? = null, val classifiers: Map<String, RemoteFile>? = null)

    @Serializable
    data class Extract(val exclude: List<String>? = null)

    val coordinate: MavenCoordinate? get() = MavenCoordinate.parse(name)

    /** True for legacy natives entries (`natives` map) and for 1.19+ `...:natives-<os>` coordinates. */
    val isNative: Boolean
        get() = natives != null || (coordinate?.classifier?.startsWith("natives-") ?: false)

    /**
     * The main artifact file, falling back to deriving it from the Maven coordinate and [url]
     * (or libraries.minecraft.net) when `downloads` is absent.
     */
    val artifact: RemoteFile?
        get() {
            downloads?.artifact?.let { return it }
            if (downloads != null) return null
            val coord = coordinate ?: return null
            val base = url ?: "https://libraries.minecraft.net/"
            return RemoteFile(sha1 = sha1, size = size, url = joinUrl(base, coord.path), path = coord.path, id = null)
        }
}

/** `group:artifact:version[:classifier][@ext]` */
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String?,
    val ext: String
) {
    /** Identity used for de-duplication: same group/artifact/classifier. */
    val key: String get() = "$group:$artifact" + (classifier?.let { ":$it" } ?: "")

    val path: String
        get() {
            val file = "$artifact-$version" + (classifier?.let { "-$it" } ?: "") + ".$ext"
            return "${group.replace('.', '/')}/$artifact/$version/$file"
        }

    companion object {
        fun parse(name: String): MavenCoordinate? {
            var body = name
            var ext = "jar"
            val at = name.lastIndexOf('@')
            if (at >= 0) {
                ext = name.substring(at + 1)
                body = name.substring(0, at)
            }
            val parts = body.split(":")
            if ((parts.size != 3 && parts.size != 4) || parts.any { it.isEmpty() }) return null
            return MavenCoordinate(parts[0], parts[1], parts[2], parts.getOrNull(3), ext)
        }
    }
}
