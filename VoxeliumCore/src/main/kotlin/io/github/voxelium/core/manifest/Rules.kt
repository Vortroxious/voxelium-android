/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Manifest/Rules.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A Mojang `rules` entry, used on libraries and on conditional arguments. */
@Serializable
data class Rule(
    val action: Action,
    val os: OS? = null,
    val features: Map<String, Boolean>? = null
) {
    @Serializable
    enum class Action {
        @SerialName("allow") ALLOW,
        @SerialName("disallow") DISALLOW
    }

    @Serializable
    data class OS(
        val name: String? = null,
        val arch: String? = null,
        /** A regular expression matched against the OS version. */
        val version: String? = null
    )
}

/**
 * The platform that rules are evaluated against.
 *
 * Android and iOS have no Mojang OS key. ZalithLauncher2 evaluates rules as a non-osx platform
 * and replaces every `org.lwjgl` library with its own bundled LWJGL, so rules are evaluated as
 * `linux` on `aarch64` by default.
 */
data class RulePlatform(
    val osName: String,
    val osArch: String,
    val osVersion: String = "",
    val features: Map<String, Boolean> = emptyMap()
) {
    companion object {
        /** Swift's `RulePlatform.iOSDefault`; the same on Android. */
        val DEFAULT = RulePlatform(osName = "linux", osArch = "aarch64")
    }
}

object RuleEvaluator {
    /**
     * Standard launcher semantics: no rules means allowed; otherwise start disallowed and let
     * each matching rule set the outcome (last match wins).
     */
    fun allows(rules: List<Rule>?, platform: RulePlatform): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, platform)) allowed = rule.action == Rule.Action.ALLOW
        }
        return allowed
    }

    internal fun matches(rule: Rule, p: RulePlatform): Boolean {
        rule.os?.let { os ->
            if (os.name != null && os.name != p.osName) return false
            if (os.arch != null && !archMatches(os.arch, p.osArch)) return false
            if (os.version != null) {
                val re = runCatching { Regex(os.version) }.getOrNull() ?: return false
                if (!re.containsMatchIn(p.osVersion)) return false
            }
        }
        rule.features?.forEach { (key, value) ->
            if ((p.features[key] ?: false) != value) return false
        }
        return true
    }

    /** Mojang uses `x86` for 32-bit Intel; treat x86/x64 aliases loosely. */
    internal fun archMatches(ruleArch: String, arch: String): Boolean {
        if (ruleArch == arch) return true
        val aliases = mapOf(
            "x86" to setOf("x86", "i386", "i686"),
            "arm64" to setOf("arm64", "aarch64"),
            "aarch64" to setOf("arm64", "aarch64")
        )
        return aliases[ruleArch]?.contains(arch) ?: false
    }
}
