/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/ContentManager.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.launch.ModLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Things a player adds to a game folder (as ZalithLauncher2's mod and resource managers):
 * mods, resource packs and shader packs.
 */
enum class ContentKind(val folderName: String, val title: String) {
    MODS("mods", "Mods"),
    RESOURCE_PACKS("resourcepacks", "Resource packs"),
    SHADER_PACKS("shaderpacks", "Shader packs");

    /** The id stored in files such as `.voxelium-curseforge.json` (Swift's rawValue). */
    val id: String get() = folderName

    /** File extension of the packaged form (folders are also accepted for packs). */
    val fileExtension: String get() = if (this == MODS) "jar" else "zip"

    companion object {
        fun fromId(id: String): ContentKind? = entries.firstOrNull { it.id == id }
    }
}

/** Mod metadata read from inside the jar. */
data class ModMetadata(
    val id: String,
    val name: String,
    val version: String?,
    val description: String?,
    val authors: List<String>,
    /** "fabric", "quilt", "forge", "neoforge". */
    val loaders: List<String>
)

/** One mod, resource pack or shader pack in a game folder. */
data class ContentItem(
    val kind: ContentKind,
    /** The name on disk (with ".disabled" when switched off). */
    val fileName: String,
    val file: File,
    val isEnabled: Boolean,
    val isFolder: Boolean,
    val size: Long,
    /** Display name: the mod's or pack's own name, else the file name. */
    val name: String,
    val version: String?,
    val description: String?,
    val mod: ModMetadata?,
    /** Set when the file could not be read as this kind of content. */
    val problem: String?
) {
    /** Stable across enabling/disabling: the file name without ".disabled". */
    val id: String get() = ContentManager.baseName(fileName)
}

sealed class ContentException(message: String) : Exception(message) {
    data class WrongType(val detail: String) : ContentException(detail)
    data class NotReadable(val detail: String) : ContentException(detail)
    data class WrongLoader(val detail: String) : ContentException(detail)
}

/**
 * Lists and changes the mods, resource packs and shader packs of one game folder. Disabling
 * renames `x.jar` to `x.jar.disabled` (the convention ZalithLauncher2 and other launchers use),
 * which Minecraft and the mod loaders skip.
 */
class ContentManager(val gameDirectory: File) {
    fun folder(kind: ContentKind): File = File(gameDirectory, kind.folderName)

    /** Everything in the kind's folder, sorted by name. */
    fun list(kind: ContentKind): List<ContentItem> {
        val dir = folder(kind)
        val names = dir.list() ?: return emptyList()
        return names.mapNotNull { name ->
            if (name.startsWith(".")) return@mapNotNull null
            val file = File(dir, name)
            if (!file.exists()) return@mapNotNull null
            val isDir = file.isDirectory
            val base = baseName(name)
            if (!isDir && !base.lowercase().endsWith(".${kind.fileExtension}")) return@mapNotNull null
            if (isDir && kind == ContentKind.MODS) return@mapNotNull null
            inspect(file, kind, isDir)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Switches an item on or off by renaming it. */
    fun setEnabled(item: ContentItem, enabled: Boolean): ContentItem {
        if (item.isEnabled == enabled) return item
        val base = baseName(item.fileName)
        val target = File(folder(item.kind), if (enabled) base else base + DISABLED_SUFFIX)
        if (target.exists()) target.deleteRecursively()
        Files.move(item.file.toPath(), target.toPath())
        return inspect(target, item.kind, item.isFolder)
    }

    /** Deletes the item from the game folder. */
    fun remove(item: ContentItem) {
        if (!item.file.deleteRecursively()) throw java.io.IOException("Couldn't delete ${item.file}")
    }

    /**
     * Copies [source] in as [kind] (replacing one with the same name, enabled or disabled). Mods
     * must be jars; packs must be zips, and a shader pack must contain a `shaders/` folder. With
     * [loader] (the version's mod loader id), a mod whose metadata says it is only for other loaders
     * (a Fabric mod on NeoForge, a Forge mod on Fabric) is refused; mods without loader metadata are
     * let through.
     */
    fun add(source: File, kind: ContentKind, loader: String? = null): ContentItem {
        val name = source.name
        if (!name.lowercase().endsWith(".${kind.fileExtension}")) {
            throw ContentException.WrongType("$name is not a .${kind.fileExtension} file, so it can't be added to ${kind.title.lowercase()}.")
        }
        val (hasShaders, hasMcmeta) = try {
            ZipReader(source).use { zip -> zip.entries.any { it.startsWith("shaders/") } to zip.hasEntry("pack.mcmeta") }
        } catch (_: Exception) {
            throw ContentException.NotReadable("$name is not a valid ${if (kind == ContentKind.MODS) "jar" else "zip"} file.")
        }
        when {
            kind == ContentKind.SHADER_PACKS && !hasShaders -> throw ContentException.WrongType(
                "$name has no shaders/ folder, so it is not a shader pack" + (if (hasMcmeta) " (it looks like a resource pack)" else "") + "."
            )
            kind == ContentKind.RESOURCE_PACKS && !hasMcmeta -> throw ContentException.WrongType(
                "$name has no pack.mcmeta, so it is not a resource pack" + (if (hasShaders) " (it looks like a shader pack)" else "") + "."
            )
        }
        if (kind == ContentKind.MODS && loader != null) {
            compatibilityWarning(inspect(source, ContentKind.MODS, false), loader)?.let { warning ->
                throw ContentException.WrongLoader("$name: $warning Look for its ${loaderDisplayName(loader)} version under Browse.")
            }
        }
        val dir = folder(kind)
        dir.mkdirs()
        val dest = File(dir, name)
        for (existing in listOf(dest, File(dir, name + DISABLED_SUFFIX))) {
            if (existing.exists()) existing.deleteRecursively()
        }
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return inspect(dest, kind, false)
    }

    companion object {
        internal const val DISABLED_SUFFIX = ".disabled"

        fun baseName(fileName: String): String =
            if (fileName.endsWith(DISABLED_SUFFIX)) fileName.dropLast(DISABLED_SUFFIX.length) else fileName

        /** Whether a mod can load on a version's loader; null when it can (or is unknown). */
        fun compatibilityWarning(item: ContentItem, loader: String?): String? {
            val mod = item.mod
            if (item.kind != ContentKind.MODS || loader == null || mod == null || mod.loaders.isEmpty()) return null
            val accepted = if (loader == "quilt") setOf("quilt", "fabric") else setOf(loader)
            if (accepted.any { it in mod.loaders }) return null
            val names = mod.loaders.joinToString("/") { loaderDisplayName(it) }
            return "Made for $names, so ${loaderDisplayName(loader)} won't load it."
        }

        internal fun loaderDisplayName(id: String): String =
            ModLoader.fromId(id)?.displayName ?: id.replaceFirstChar { it.uppercase() }

        // Reading metadata

        /** Reads one file or folder as content of [kind]. */
        fun inspect(file: File, kind: ContentKind, isFolder: Boolean): ContentItem {
            val fileName = file.name
            val enabled = !fileName.endsWith(DISABLED_SUFFIX)
            val base = baseName(fileName)
            val fallbackName = if (base.contains('.')) base.substringBeforeLast('.') else base
            val size = if (isFolder) folderSize(file) else file.length()
            fun item(
                name: String? = null, version: String? = null, description: String? = null,
                mod: ModMetadata? = null, problem: String? = null
            ) = ContentItem(kind, fileName, file, enabled, isFolder, size, name ?: fallbackName, version, description, mod, problem)

            if (isFolder) {
                val meta = runCatching { File(file, "pack.mcmeta").readBytes() }.getOrNull()
                return item(description = meta?.let { packDescription(it) })
            }
            val zip = try {
                ZipReader(file)
            } catch (_: Exception) {
                return item(problem = "Not a readable ${if (kind == ContentKind.MODS) "jar" else "zip"} file")
            }
            return zip.use {
                when (kind) {
                    ContentKind.MODS -> {
                        val mod = modMetadata(zip) ?: return@use item(problem = "No mod metadata found (not a Fabric, Quilt or Forge mod?)")
                        item(name = mod.name, version = mod.version, description = mod.description, mod = mod)
                    }
                    ContentKind.RESOURCE_PACKS -> {
                        val meta = runCatching { zip.read("pack.mcmeta") }.getOrNull()
                        item(description = meta?.let { packDescription(it) }, problem = if (meta == null) "No pack.mcmeta" else null)
                    }
                    ContentKind.SHADER_PACKS ->
                        item(problem = if (zip.entries.any { it.startsWith("shaders/") }) null else "No shaders/ folder")
                }
            }
        }

        internal fun modMetadata(zip: ZipReader): ModMetadata? {
            fun read(name: String) = runCatching { zip.read(name) }.getOrNull()
            read("fabric.mod.json")?.let(::jsonObject)?.let { j ->
                val id = j.str("id")
                return ModMetadata(
                    id = id ?: "?", name = j.str("name") ?: id ?: "?", version = j.str("version"),
                    description = j.str("description"), authors = people(j["authors"]), loaders = listOf("fabric")
                )
            }
            read("quilt.mod.json")?.let(::jsonObject)?.let { j ->
                val q = j["quilt_loader"] as? JsonObject ?: return@let null
                val meta = q["metadata"] as? JsonObject ?: JsonObject(emptyMap())
                val contributors = (meta["contributors"] as? JsonObject)?.keys?.sorted() ?: emptyList()
                val id = q.str("id")
                return ModMetadata(
                    id = id ?: "?", name = meta.str("name") ?: id ?: "?", version = q.str("version"),
                    description = meta.str("description"), authors = contributors, loaders = listOf("quilt")
                )
            }
            for ((file, loader) in listOf("META-INF/neoforge.mods.toml" to "neoforge", "META-INF/mods.toml" to "forge")) {
                val toml = read(file)?.toString(Charsets.UTF_8) ?: continue
                val mod = tomlFirstMod(toml)
                var version = mod["version"]
                if (version == null || version.contains("\${")) {
                    read("META-INF/MANIFEST.MF")?.toString(Charsets.UTF_8)?.let { text ->
                        version = manifestValue(text, "Implementation-Version") ?: version
                    }
                }
                val id = mod["modId"] ?: "?"
                return ModMetadata(
                    id = id, name = mod["displayName"] ?: id, version = version, description = mod["description"],
                    authors = mod["authors"]?.let { listOf(it) } ?: emptyList(), loaders = listOf(loader)
                )
            }
            read("mcmod.info")?.let { data ->
                val list = runCatching { Json.parseToJsonElement(data.toString(Charsets.UTF_8)) as? JsonArray }.getOrNull()
                val j = list?.firstOrNull() as? JsonObject ?: return@let null
                return ModMetadata(
                    id = j.str("modid") ?: "?", name = j.str("name") ?: "?", version = j.str("version"),
                    description = j.str("description"), authors = people(j["authorList"]), loaders = listOf("forge")
                )
            }
            return null
        }

        internal fun jsonObject(data: ByteArray): JsonObject? {
            val text = data.toString(Charsets.UTF_8)
            runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()?.let { return it }
            // Some fabric.mod.json files contain raw control characters in strings.
            val cleaned = text.replace("\t", " ").lines().joinToString(" ")
            return runCatching { Json.parseToJsonElement(cleaned) as? JsonObject }.getOrNull()
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        /** Author lists: strings or `{ "name": … }` objects. */
        internal fun people(element: JsonElement?): List<String> =
            (element as? JsonArray ?: return emptyList()).mapNotNull { e ->
                (e as? JsonPrimitive)?.takeIf { it.isString }?.content ?: (e as? JsonObject)?.str("name")
            }

        /**
         * The first `[[mods]]` table's `key = value` strings (enough for display), including
         * multi-line `'''…'''` / `"""…"""` strings.
         */
        internal fun tomlFirstMod(toml: String): Map<String, String> {
            var inMods = false
            var seenMods = false
            val out = mutableMapOf<String, String>()
            val topLevel = mutableMapOf<String, String>()
            data class Pending(val key: String, val delimiter: String, val text: String)
            var pending: Pending? = null
            fun store(key: String, value: String) {
                if (inMods) out[key] = value else if (!seenMods) topLevel[key] = value
            }
            for (raw in toml.lines()) {
                pending?.let { p ->
                    val end = raw.indexOf(p.delimiter)
                    pending = if (end >= 0) {
                        store(p.key, (p.text + raw.substring(0, end)).trim())
                        null
                    } else {
                        p.copy(text = p.text + raw + "\n")
                    }
                    continue
                }
                val line = raw.trim()
                if (line.startsWith("[")) {
                    if (inMods) break
                    // Table headers may carry a comment: `[[mods]] #mandatory` (JEI).
                    val header = line.split("#", limit = 2).first().trim()
                    inMods = header == "[[mods]]"
                    seenMods = seenMods || inMods
                    continue
                }
                val eq = line.indexOf('=')
                if (eq < 0 || line.startsWith("#")) continue
                val key = line.substring(0, eq).trim()
                var value = line.substring(eq + 1).trim()
                val triple = listOf("'''", "\"\"\"").firstOrNull { value.startsWith(it) }
                if (triple != null) {
                    val rest = value.drop(triple.length)
                    val end = rest.indexOf(triple)
                    if (end >= 0) store(key, rest.substring(0, end).trim())
                    else pending = Pending(key, triple, if (rest.isEmpty()) "" else rest + "\n")
                    continue
                }
                for (q in listOf("\"", "'")) {
                    if (value.startsWith(q)) {
                        value = value.drop(1)
                        val end = value.indexOf(q)
                        if (end >= 0) value = value.substring(0, end)
                        break
                    }
                }
                if (value.isNotEmpty()) {
                    val hash = value.indexOf(" #")
                    if (hash >= 0) value = value.substring(0, hash)
                }
                store(key, value)
            }
            if (out["authors"] == null) topLevel["authors"]?.let { out["authors"] = it }
            return out
        }

        internal fun manifestValue(manifest: String, key: String): String? =
            manifest.lines().firstOrNull { it.startsWith("$key:") }?.drop(key.length + 1)?.trim()

        /** A pack's description as plain text: a string, a text component, or a list of them. */
        internal fun packDescription(data: ByteArray): String? {
            val pack = jsonObject(data)?.get("pack") as? JsonObject ?: return null
            val d = pack["description"] ?: return null
            val text = componentText(d).trim()
            return if (text.isEmpty()) null else stripFormatting(text)
        }

        internal fun componentText(element: JsonElement): String = when (element) {
            is JsonPrimitive -> if (element.isString) element.content else ""
            is JsonArray -> element.joinToString("") { componentText(it) }
            is JsonObject -> (element.str("text") ?: element.str("translate") ?: "") +
                ((element["extra"] as? JsonArray)?.joinToString("") { componentText(it) } ?: "")
        }

        /** Removes Minecraft's `§x` colour/format codes. */
        internal fun stripFormatting(s: String): String {
            val out = StringBuilder()
            var skip = false
            for (ch in s) {
                if (skip) {
                    skip = false
                    continue
                }
                if (ch == '§') {
                    skip = true
                    continue
                }
                out.append(ch)
            }
            return out.toString()
        }

        internal fun folderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
