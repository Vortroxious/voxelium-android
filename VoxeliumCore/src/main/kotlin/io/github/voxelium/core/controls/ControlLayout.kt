/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Controls/ControlLayout.swift (zalithios e28b28b).
 * Android: sizes are dp where iOS uses points; files are written in the same bytes as
 * Swift's JSONEncoder (.prettyPrinted, .sortedKeys), so layout.json moves between the apps.
 */

package io.github.voxelium.core.controls

import io.github.voxelium.core.logging.Logger
import io.github.voxelium.core.paths.AppPaths
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * An on-screen control layout: the buttons and joystick drawn over the game.
 *
 * Stored as JSON in `controls/layout.json`; field names are part of the contract with the
 * iOS app. Positions are fractions of the landscape screen (top-left origin); sizes are in
 * points on iOS and dp on Android, as in Amethyst's control files. Missing keys take their
 * defaults, so older files keep loading after new fields are added.
 */
@Serializable
data class ControlLayout(
    val version: Int = 1,
    val buttons: List<ControlButton> = emptyList(),
    val joystick: Joystick = Joystick(x = 0.02326631, y = 0.5, size = 204.8),
    /** How visible the controls are (1 = Amethyst's look, lower = more see-through). */
    val opacity: Double = 1.0
) {
    @Serializable
    data class Joystick(val x: Double, val y: Double, val size: Double)

    /** Every control kept fully on a [width] × [height] screen. */
    fun clamped(width: Double, height: Double): ControlLayout {
        val size = clamp(joystick.size, JOYSTICK_SIZE_RANGE.start, min(JOYSTICK_SIZE_RANGE.endInclusive, height))
        return copy(
            buttons = buttons.map { it.clamped(width, height) },
            opacity = clamp(opacity, OPACITY_RANGE.start, OPACITY_RANGE.endInclusive),
            joystick = Joystick(
                x = clamp(joystick.x, 0.0, max(0.0, 1 - size / width)),
                y = clamp(joystick.y, 0.0, max(0.0, 1 - size / height)),
                size = size
            )
        )
    }

    /** A rectangle in points (dp). */
    data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
        fun intersects(o: Rect): Boolean =
            x < o.x + o.width && o.x < x + width && y < o.y + o.height && o.y < y + height
    }

    /**
     * Where a new [w] × [h] button can go without covering another control (or the
     * [avoiding] areas): the free spot closest to the middle of the screen, as fractions.
     * Falls back to the middle when the screen is full.
     */
    fun freeSpot(
        w: Double,
        h: Double,
        screenWidth: Double,
        screenHeight: Double,
        avoiding: List<Rect> = emptyList()
    ): Pair<Double, Double> {
        val sw = screenWidth
        val sh = screenHeight
        val gap = 4.0
        val taken = buttons.filter { !it.inDrawer }.map {
            Rect(it.x * sw - gap, it.y * sh - gap, it.width + 2 * gap, it.height + 2 * gap)
        } + Rect(joystick.x * sw, joystick.y * sh, joystick.size, joystick.size) + avoiding
        var best: Pair<Double, Double>? = null
        var bestDistance = Double.POSITIVE_INFINITY
        val step = 8.0
        // Same grid as Swift's stride(from: 0, through: H - h, by: 8).
        var py = 0.0
        while (py <= sh - h) {
            var px = 0.0
            while (px <= sw - w) {
                val r = Rect(px, py, w, h)
                if (taken.none { it.intersects(r) }) {
                    val d = hypot(px + w / 2 - sw / 2, py + h / 2 - sh / 2)
                    if (d < bestDistance) {
                        bestDistance = d
                        best = px to py
                    }
                }
                px += step
            }
            py += step
        }
        val (px, py2) = best ?: (((sw - w) / 2) to ((sh - h) / 2))
        return (px / sw) to (py2 / sh)
    }

    companion object {
        val OPACITY_RANGE = 0.2..1.0
        val JOYSTICK_SIZE_RANGE = 120.0..300.0

        internal fun clamp(v: Double, lo: Double, hi: Double): Double = min(max(v, lo), hi)

        /**
         * Amethyst's default layout (its `generateAndSaveDefaultControl`, evaluated in points
         * with button_scale 100 and margin 2 pt) for a landscape screen of [width] × [height],
         * with two changes made at the user's request: Inventory sits in the right-hand
         * cluster, and Player List uses the free drawer slot (Amethyst's own expression puts
         * it above the screen).
         */
        fun amethystDefault(width: Double, height: Double): ControlLayout {
            val sw = width
            val sh = height
            val m = 2.0
            val a = 69.6
            val b = 104.8
            val d = 96.0
            val dh = 40.0
            val dy = 0.0026855469
            fun at(px: Double, py: Double) = (px / sw) to (py / sh)
            fun button(
                name: String,
                p: Pair<Double, Double>,
                keys: List<Int> = emptyList(),
                mouse: Int = -1,
                special: ControlSpecial = ControlSpecial.NONE,
                w: Double = a,
                h: Double = a,
                toggle: Boolean = false,
                game: Boolean = true,
                menu: Boolean = false,
                drawer: Boolean = false
            ) = ControlButton(
                name = name, keys = keys, mouse = mouse, special = special, x = p.first, y = p.second,
                width = w, height = h, toggle = toggle, showInGame = game, showInMenu = menu, inDrawer = drawer
            )
            val k = ControlKeys
            val buttons = listOf(
                // Right-hand cluster.
                button("Jump", at(0.94878495 * sw - a, 0.31131014 * sh + a + m), keys = listOf(k.SPACE)),
                button("Shift", at(0.94878495 * sw - a, 0.31131014 * sh), keys = listOf(k.LEFT_SHIFT), toggle = true, menu = true),
                button("Sprint", at(0.94878495 * sw - 2 * a - m, 0.31131014 * sh), keys = listOf(k.LEFT_CONTROL), toggle = true),
                button("Right Click", at(0.8756154 * sw - a, 0.8178498 * sh - a), mouse = 1, menu = true),
                button("Left Click", at(0.7756397 * sw - a, 0.8224885 * sh - a), mouse = 0, menu = true),
                button("Middle Click", at(0.83548373 * sw - a, 0.67798775 * sh - a), mouse = 2, menu = true),
                button(
                    "Inventory",
                    at(0.8756154 * sw + m, max(0.8178498 * sh - a, 0.31131014 * sh + 2 * a + 2 * m)),
                    keys = listOf(k.E)
                ),
                // Top row, packed from the right edge.
                button("Runtime Menu", at(sw - a - b - m, 0.0), special = ControlSpecial.RUNTIME_MENU, w = b, h = 39.2, menu = true),
                button("Hide GUI", at(sw - a, 0.0), special = ControlSpecial.HIDE_GUI, h = 40.0, menu = true),
                button("Pause", at(sw - a - 2 * b - 2 * m, 0.0), keys = listOf(k.ESCAPE), w = b, h = 39.2),
                button("Exit Menu", at(sw - a - 2 * b - 2 * m, 0.0), keys = listOf(k.ESCAPE), w = b, h = 39.2, game = false, menu = true),
                // "More" drawer (top-left) and its items.
                button("More", at(0.0, 0.0), special = ControlSpecial.DRAWER, w = d, h = dh, menu = true),
                button("Third person", at(d + m, dy * sh), keys = listOf(k.F5), w = d, h = dh, menu = true, drawer = true),
                button("Debug Menu", at(3 * (d + m), dy * sh), keys = listOf(k.F3), w = d, h = dh, menu = true, drawer = true),
                button("Mouse Toggle", at(2 * (d + m), dy * sh + dh + m), special = ControlSpecial.VIRTUAL_MOUSE, w = d, h = dh, menu = true, drawer = true),
                button("Chat", at(0.0, dy * sh + dh + m), keys = listOf(k.T), w = d, h = dh, menu = true, drawer = true),
                button("Keyboard", at(d + m, dy * sh + dh + m), special = ControlSpecial.KEYBOARD, w = d, h = dh, menu = true, drawer = true),
                button("Player List", at(2 * (d + m), dy * sh), keys = listOf(k.TAB), w = d, h = dh, toggle = true, menu = true, drawer = true),
            )
            // Stable ids, so the default is the same value every time it is generated.
            val ided = buttons.mapIndexed { i, btn ->
                btn.copy(id = UUID.fromString(String.format("00000000-0000-4000-8000-%012d", i + 1)))
            }
            val stick = 204.8
            val joystick = Joystick(x = 0.02326631, y = (0.9753418 * sh - stick) / sh, size = stick)
            return ControlLayout(buttons = ided, joystick = joystick).clamped(sw, sh)
        }
    }
}

/** What a button does when pressed. */
@Serializable
enum class ControlSpecial(val title: String) {
    @SerialName("none") NONE("None"),
    @SerialName("runtimeMenu") RUNTIME_MENU("Runtime menu"),
    @SerialName("hideGUI") HIDE_GUI("Hide / show controls"),
    // iOS says "Show iOS keyboard"; the Android app shows its own string.
    @SerialName("keyboard") KEYBOARD("Show keyboard"),
    @SerialName("virtualMouse") VIRTUAL_MOUSE("Virtual mouse on/off"),
    @SerialName("drawer") DRAWER("Open / close drawer");
}

/** Reads any UUID spelling; writes it upper-case, as Foundation's `UUID` does. */
object UpperUuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString().uppercase())
}

@Serializable
data class ControlButton(
    @Serializable(with = UpperUuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    /** GLFW key codes pressed together (Amethyst allows up to 4). */
    val keys: List<Int> = emptyList(),
    /** GLFW mouse button (0 left, 1 right, 2 middle), or -1. */
    val mouse: Int = -1,
    val special: ControlSpecial = ControlSpecial.NONE,
    val x: Double = 0.5,
    val y: Double = 0.5,
    val width: Double = 69.6,
    val height: Double = 69.6,
    /** Press once to hold, press again to release. */
    val toggle: Boolean = false,
    val showInGame: Boolean = true,
    val showInMenu: Boolean = false,
    /** Only shown while the "More" drawer is open. */
    val inDrawer: Boolean = false
) {
    /** What the button sends, for lists: "Space", "Left Shift + E", "Right mouse", "Runtime menu". */
    val actionSummary: String
        get() {
            if (special != ControlSpecial.NONE) return special.title
            val parts = keys.map(ControlKeys::name).toMutableList()
            ControlKeys.mouseButtons.firstOrNull { it.code == mouse }?.let { parts += it.name }
            return if (parts.isEmpty()) "Nothing" else parts.joinToString(" + ")
        }

    fun clamped(width: Double, height: Double): ControlButton {
        val w = ControlLayout.clamp(this.width, 20.0, min(400.0, width))
        val h = ControlLayout.clamp(this.height, 20.0, min(400.0, height))
        return copy(
            width = w,
            height = h,
            x = ControlLayout.clamp(x, 0.0, max(0.0, 1 - w / width)),
            y = ControlLayout.clamp(y, 0.0, max(0.0, 1 - h / height)),
            keys = keys.take(MAX_KEYS)
        )
    }

    companion object {
        const val MAX_KEYS = 4
    }
}

/** Loads and saves the user's layout (controls/layout.json). */
class ControlLayoutStore(val file: File) {
    constructor(paths: AppPaths) : this(File(paths.controlsDir, "layout.json"))

    /**
     * The saved layout, or null when none was saved. A corrupt file is moved aside
     * (layout.json.corrupt) and null is returned.
     */
    fun load(log: Logger = Logger.shared): ControlLayout? {
        if (!file.isFile) return null
        val text = try {
            file.readText()
        } catch (_: IOException) {
            return null
        }
        return try {
            decode(text)
        } catch (e: Exception) {
            log.error("controls", "Corrupt control layout, using the default: $e")
            val aside = File(file.path + ".corrupt")
            aside.delete()
            file.renameTo(aside)
            null
        }
    }

    /** The saved layout, or the default for this screen. */
    fun effective(width: Double, height: Double, log: Logger = Logger.shared): ControlLayout =
        (load(log) ?: ControlLayout.amethystDefault(width, height)).clamped(width, height)

    fun save(layout: ControlLayout) = write(layout, file)

    /** Forgets the saved layout, so the default is used again. */
    fun reset() {
        if (file.exists() && !file.delete()) throw IOException("Can't delete $file")
    }

    companion object {
        /** Missing or null keys take their defaults and unknown `special` values read as `none`, as in Swift. */
        private val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            encodeDefaults = true
        }

        fun decode(json: String): ControlLayout = JSON.decodeFromString(ControlLayout.serializer(), json)

        /** The file's bytes: what Swift's JSONEncoder writes with `.prettyPrinted, .sortedKeys`. */
        fun encode(layout: ControlLayout): String =
            SwiftJson.prettySorted(JSON.encodeToJsonElement(ControlLayout.serializer(), layout))

        fun write(layout: ControlLayout, file: File) {
            val dir = file.absoluteFile.parentFile
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create $dir")
            val tmp = File(dir, ".${file.name}.tmp")
            tmp.writeText(encode(layout))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

/**
 * Writes JSON the way Foundation's JSONEncoder does with `.prettyPrinted, .sortedKeys`:
 * two-space indent, `"key" : value`, empty arrays as `[\n\n<indent>]`, `/` escaped, and
 * numbers in Swift's shortest form (`96`, `0.5`, `1e-05`).
 */
internal object SwiftJson {
    fun prettySorted(element: JsonElement): String = StringBuilder().also { write(it, element, 0) }.toString()

    private fun write(out: StringBuilder, e: JsonElement, depth: Int) {
        when (e) {
            is JsonObject -> {
                if (e.isEmpty()) {
                    out.append("{\n\n").append(indent(depth)).append('}')
                    return
                }
                out.append("{\n")
                e.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) out.append(",\n")
                    out.append(indent(depth + 1))
                    string(out, key)
                    out.append(" : ")
                    write(out, e.getValue(key), depth + 1)
                }
                out.append('\n').append(indent(depth)).append('}')
            }
            is JsonArray -> {
                if (e.isEmpty()) {
                    out.append("[\n\n").append(indent(depth)).append(']')
                    return
                }
                out.append("[\n")
                e.forEachIndexed { i, item ->
                    if (i > 0) out.append(",\n")
                    out.append(indent(depth + 1))
                    write(out, item, depth + 1)
                }
                out.append('\n').append(indent(depth)).append(']')
            }
            JsonNull -> out.append("null")
            is JsonPrimitive -> when {
                e.isString -> string(out, e.content)
                e.content == "true" || e.content == "false" -> out.append(e.content)
                else -> out.append(number(e.content))
            }
        }
    }

    private fun indent(depth: Int) = "  ".repeat(depth)

    private fun string(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '/' -> out.append("\\/")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    /** Swift's `Double.description`-style JSON number; integers stay as they are. */
    fun number(content: String): String {
        if (content.none { it == '.' || it == 'e' || it == 'E' }) return content
        val d = content.toDouble()
        if (d == 0.0) return if (1.0 / d < 0) "-0" else "0"
        val dec = BigDecimal(d.toString()).stripTrailingZeros()
        val digits = dec.unscaledValue().abs().toString()
        val exponent = digits.length - 1 - dec.scale()
        val sign = if (d < 0) "-" else ""
        return if (exponent < -4 || exponent >= 16) {
            val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
            val e = kotlin.math.abs(exponent).toString().padStart(2, '0')
            "$sign${mantissa}e${if (exponent < 0) "-" else "+"}$e"
        } else {
            dec.toPlainString()
        }
    }
}

/** GLFW key codes that a control can send, with display names. */
object ControlKeys {
    data class Key(val code: Int, val name: String)

    const val SPACE = 32
    const val ESCAPE = 256
    const val ENTER = 257
    const val TAB = 258
    const val BACKSPACE = 259
    const val LEFT_SHIFT = 340
    const val LEFT_CONTROL = 341
    const val E = 69
    const val T = 84
    const val F3 = 292
    const val F5 = 294
    const val W = 87
    const val A = 65
    const val S = 83
    const val D = 68
    const val KEY_1 = 49
    const val LAST = 348

    val mouseButtons: List<Key> = listOf(Key(0, "Left mouse"), Key(1, "Right mouse"), Key(2, "Middle mouse"))

    /** Keyboard keys, in the order the picker shows them. */
    val all: List<Key> = buildList {
        for (c in 65..90) add(Key(c, c.toChar().toString())) // A-Z
        for (c in 48..57) add(Key(c, c.toChar().toString())) // 0-9
        addAll(
            listOf(
                Key(32, "Space"), Key(256, "Escape"), Key(257, "Enter"),
                Key(258, "Tab"), Key(259, "Backspace"), Key(260, "Insert"),
                Key(261, "Delete"), Key(262, "Right arrow"), Key(263, "Left arrow"),
                Key(264, "Down arrow"), Key(265, "Up arrow"), Key(266, "Page up"),
                Key(267, "Page down"), Key(268, "Home"), Key(269, "End"),
                Key(280, "Caps lock"),
                Key(340, "Left Shift"), Key(341, "Left Control"), Key(342, "Left Alt"),
                Key(344, "Right Shift"), Key(345, "Right Control"), Key(346, "Right Alt"),
            )
        )
        for (i in 1..12) add(Key(289 + i, "F$i"))
        addAll(
            listOf(
                Key(39, "' (apostrophe)"), Key(44, ", (comma)"), Key(45, "- (minus)"),
                Key(46, ". (period)"), Key(47, "/ (slash)"), Key(59, "; (semicolon)"),
                Key(61, "= (equals)"), Key(91, "[ (left bracket)"), Key(92, "\\ (backslash)"),
                Key(93, "] (right bracket)"), Key(96, "` (grave)"),
            )
        )
        for (i in 0..9) add(Key(320 + i, "Keypad $i"))
    }

    fun name(code: Int): String = all.firstOrNull { it.code == code }?.name ?: "Key $code"
}
