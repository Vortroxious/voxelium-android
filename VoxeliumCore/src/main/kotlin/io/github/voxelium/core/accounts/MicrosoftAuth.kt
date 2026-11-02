/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Accounts/MicrosoftAuth.swift (zalithios e28b28b).
 * Android addition: the optional `onStep` callback for progress messages.
 */

package io.github.voxelium.core.accounts

import io.github.voxelium.core.launch.LaunchIdentity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.URLDecoder
import java.time.Instant

/**
 * Microsoft account sign-in for Minecraft: Microsoft sign-in → Xbox Live → XSTS → Minecraft services.
 * Two ways to get the Microsoft token:
 *
 * - **Live web sign-in** (default, [MicrosoftAuth.live]): the flow Amethyst and PojavLauncher use —
 *   login.live.com `oauth20_authorize.srf` in a web view with Microsoft's Minecraft launcher client ID
 *   `00000000402b5328`, the authorization code redeemed at `oauth20_token.srf`, scope
 *   `service::user.auth.xboxlive.com::MBI_SSL`, and the token sent to Xbox Live as a bare RPS ticket.
 * - **Device code** with your own Azure app ID approved by Mojang (ZalithLauncher2's
 *   MicrosoftAuthenticator.kt: tenant "consumers", RPS ticket "d=<token>").
 *
 * Tokens are secrets: callers keep them in encrypted storage, never in logs.
 */
class MicrosoftAuth(
    val clientId: String,
    private val http: HttpClient = OkHttpJsonClient(),
    private val sleep: suspend (Double) -> Unit = { delay((it * 1000).toLong()) }
) {
    data class DeviceCode(
        val userCode: String,
        val deviceCode: String,
        val verificationUrl: String,
        val expiresIn: Int,
        val interval: Int,
        val message: String
    )

    data class MicrosoftTokens(val accessToken: String, val refreshToken: String)

    /** Steps of [minecraftAccount], for progress messages. */
    enum class Step { XBOX_LIVE, XSTS, MINECRAFT_LOGIN, OWNERSHIP, PROFILE }

    internal val isLive: Boolean get() = clientId == LIVE_CLIENT_ID

    // Microsoft identity platform

    /** Step 1: a code the user enters at microsoft.com/link. */
    suspend fun requestDeviceCode(): DeviceCode {
        val j = http.form("$MS_BASE/devicecode", mapOf("client_id" to clientId, "scope" to SCOPE))
        if (j.status != 200) throw AuthException.Microsoft(j.oauthError ?: "HTTP ${j.status}")
        val user = j.string("user_code")
        val device = j.string("device_code")
        val uri = j.string("verification_uri")
        if (user == null || device == null || uri == null) throw AuthException.BadResponse("device code")
        return DeviceCode(
            userCode = user, deviceCode = device, verificationUrl = uri,
            expiresIn = j.int("expires_in") ?: 900, interval = maxOf(j.int("interval") ?: 5, 1),
            message = j.string("message") ?: ""
        )
    }

    /** Step 2: waits until the user has approved the code (or it expires / is declined). */
    suspend fun pollForTokens(code: DeviceCode): MicrosoftTokens {
        var interval = code.interval.toDouble()
        val deadline = Instant.now().plusSeconds(code.expiresIn.toLong())
        while (Instant.now().isBefore(deadline)) {
            sleep(interval)
            currentCoroutineContext().ensureActive()
            val j = http.form(
                "$MS_BASE/token",
                mapOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to clientId, "device_code" to code.deviceCode
                )
            )
            if (j.status == 200) tokens(j)?.let { return it }
            when (val e = j.oauthError) {
                "authorization_pending" -> continue
                "slow_down" -> interval += 5
                "authorization_declined" -> throw AuthException.Declined
                "expired_token", "bad_verification_code" -> throw AuthException.Expired
                else -> throw AuthException.Microsoft(e ?: "HTTP ${j.status}")
            }
        }
        throw AuthException.Expired
    }

    /** Live web sign-in: redeems the authorization code. */
    suspend fun redeemLiveCode(code: String): MicrosoftTokens {
        val j = http.form(
            LIVE_TOKEN,
            mapOf(
                "client_id" to clientId, "code" to code, "grant_type" to "authorization_code",
                "redirect_url" to LIVE_REDIRECT, "scope" to LIVE_SCOPE
            )
        )
        if (j.status != 200) throw AuthException.Microsoft(j.oauthError ?: "HTTP ${j.status}")
        return tokens(j) ?: throw AuthException.Microsoft(j.oauthError ?: "HTTP ${j.status}")
    }

    /** New Microsoft tokens from a saved refresh token. */
    suspend fun refresh(refreshToken: String): MicrosoftTokens {
        val j = if (isLive) {
            http.form(
                LIVE_TOKEN,
                mapOf(
                    "client_id" to clientId, "refresh_token" to refreshToken, "grant_type" to "refresh_token",
                    "redirect_url" to LIVE_REDIRECT, "scope" to LIVE_SCOPE
                )
            )
        } else {
            http.form(
                "$MS_BASE/token",
                mapOf("grant_type" to "refresh_token", "client_id" to clientId, "refresh_token" to refreshToken, "scope" to SCOPE)
            )
        }
        val tokens = if (j.status == 200) tokens(j) else null
        if (tokens == null) {
            if (j.oauthError == "invalid_grant") throw AuthException.SignInAgain
            throw AuthException.Microsoft(j.oauthError ?: "HTTP ${j.status}")
        }
        return tokens
    }

    // Xbox Live → XSTS → Minecraft

    /** Steps 3–6: Xbox Live, XSTS, Minecraft login, game ownership, profile. */
    suspend fun minecraftAccount(
        ms: MicrosoftTokens,
        now: Instant = Instant.now(),
        onStep: (Step) -> Unit = {}
    ): MicrosoftAccount {
        onStep(Step.XBOX_LIVE)
        val (xbl, uhs) = xboxLive(ms.accessToken)
        onStep(Step.XSTS)
        val xsts = xsts(xbl)
        onStep(Step.MINECRAFT_LOGIN)
        val mc = http.json(
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            buildJsonObject { put("identityToken", "XBL3.0 x=$uhs;$xsts") }
        )
        when (mc.status) {
            200 -> Unit
            403 -> throw AuthException.AppNotApproved
            429 -> throw AuthException.RateLimited
            else -> throw AuthException.Minecraft("login_with_xbox HTTP ${mc.status}")
        }
        val token = mc.string("access_token") ?: throw AuthException.BadResponse("Minecraft login")
        val expires = now.plusSeconds((mc.int("expires_in") ?: 86400).toLong())

        onStep(Step.OWNERSHIP)
        val owned = http.get("https://api.minecraftservices.com/entitlements/mcstore", token)
        if (owned.status != 200) throw AuthException.Minecraft("entitlements HTTP ${owned.status}")
        if ((owned.obj["items"] as? JsonArray).isNullOrEmpty()) throw AuthException.DoesNotOwnMinecraft

        onStep(Step.PROFILE)
        val profile = http.get("https://api.minecraftservices.com/minecraft/profile", token)
        if (profile.status == 404) throw AuthException.NoProfile
        val id = profile.string("id")
        val name = profile.string("name")
        if (profile.status != 200 || id == null || name == null) throw AuthException.Minecraft("profile HTTP ${profile.status}")
        return MicrosoftAccount(
            username = name, uuid = id.replace("-", "").lowercase(), xuid = uhs,
            minecraftAccessToken = token, minecraftTokenExpiry = expires,
            microsoftRefreshToken = ms.refreshToken, clientId = clientId
        )
    }

    /**
     * The account with a Minecraft token that's good for more than [marginSeconds], refreshed if
     * needed. The app refreshes early (12 hours) while it's online, so Play rarely needs the network.
     */
    suspend fun validAccount(
        account: MicrosoftAccount,
        now: Instant = Instant.now(),
        marginSeconds: Long = 3600,
        onStep: (Step) -> Unit = {}
    ): MicrosoftAccount {
        if (account.minecraftTokenExpiry.epochSecond - now.epochSecond > marginSeconds) return account
        return minecraftAccount(refresh(account.microsoftRefreshToken), now, onStep)
    }

    internal suspend fun xboxLive(msAccessToken: String): Pair<String, String> {
        suspend fun request(ticket: String) = http.json(
            "https://user.auth.xboxlive.com/user/authenticate",
            buildJsonObject {
                putJsonObject("Properties") {
                    put("AuthMethod", "RPS")
                    put("SiteName", "user.auth.xboxlive.com")
                    put("RpsTicket", ticket)
                }
                put("RelyingParty", "http://auth.xboxlive.com")
                put("TokenType", "JWT")
            }
        )
        // Live tokens go as they are (Amethyst); Azure tokens as "d=<token>", retried
        // without the prefix on HTTP 400 (ZalithLauncher2).
        var j = request(if (isLive) msAccessToken else "d=$msAccessToken")
        if (j.status == 400 && !isLive) j = request(msAccessToken)
        val token = j.string("Token")
        val uhs = ((j.obj["DisplayClaims"] as? JsonObject)?.get("xui") as? JsonArray)
            ?.firstOrNull()?.let { (it as? JsonObject)?.get("uhs")?.stringOrNull() }
        if (j.status != 200 || token == null || uhs == null) throw AuthException.Xbox("Xbox Live HTTP ${j.status}")
        return token to uhs
    }

    internal suspend fun xsts(xblToken: String): String {
        val j = http.json(
            "https://xsts.auth.xboxlive.com/xsts/authorize",
            buildJsonObject {
                putJsonObject("Properties") {
                    put("SandboxId", "RETAIL")
                    put("UserTokens", buildJsonArray { add(JsonPrimitive(xblToken)) })
                }
                put("RelyingParty", "rp://api.minecraftservices.com/")
                put("TokenType", "JWT")
            }
        )
        if (j.status == 200) j.string("Token")?.let { return it }
        val xerr = j.obj["XErr"]?.primitiveText()
        throw AuthException.Xbox(
            xstsReason(xerr) ?: ("XSTS HTTP ${j.status}" + (xerr?.let { " XErr $it" } ?: ""))
        )
    }

    companion object {
        const val SCOPE = "XboxLive.signin offline_access"
        internal const val MS_BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0"

        // Live web sign-in (as Amethyst-iOS AccountListViewController / MicrosoftAuthenticator.m).
        const val LIVE_CLIENT_ID = "00000000402b5328"
        const val LIVE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
        const val LIVE_REDIRECT = "https://login.live.com/oauth20_desktop.srf"

        /** login.live.com sends the browser back to this scheme with `?code=…`. */
        const val LIVE_CALLBACK_SCHEME = "ms-xal-00000000402b5328"
        const val LIVE_AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf?client_id=$LIVE_CLIENT_ID" +
            "&response_type=code&scope=$LIVE_SCOPE&redirect_url=$LIVE_REDIRECT"
        internal const val LIVE_TOKEN = "https://login.live.com/oauth20_token.srf"

        /** The Live web sign-in flow. */
        fun live(http: HttpClient = OkHttpJsonClient()) = MicrosoftAuth(LIVE_CLIENT_ID, http)

        /**
         * Live web sign-in: the `code` from the `ms-xal-…://` callback URL, or the reason when the
         * user cancelled or Microsoft returned an error.
         */
        fun liveCode(callback: String): Result<String> {
            val query = runCatching { URI(callback).rawQuery }.getOrNull() ?: ""
            val items = query.split("&").filter { it.isNotEmpty() }.associate { part ->
                val k = part.substringBefore("=")
                val v = URLDecoder.decode(part.substringAfter("=", ""), "UTF-8")
                k to v
            }
            items["code"]?.takeIf { it.isNotEmpty() }?.let { return Result.success(it) }
            val error = items["error"] ?: "no code"
            if (error.startsWith("access_denied")) return Result.failure(AuthException.Declined)
            return Result.failure(AuthException.Microsoft(items["error_description"] ?: error))
        }

        private fun tokens(j: JsonResponse): MicrosoftTokens? {
            val a = j.string("access_token") ?: return null
            val r = j.string("refresh_token") ?: return null
            return MicrosoftTokens(a, r)
        }

        /** XSTS XErr codes (as listed by ZalithLauncher2 / prismarine-auth). */
        fun xstsReason(xerr: String?): String? = when (xerr) {
            "2148916227" -> "This Xbox account is banned."
            "2148916229" -> "This account is restricted; a parent must allow online play."
            "2148916233" -> "This Microsoft account has no Xbox profile. Sign in once at xbox.com to create one."
            "2148916234" -> "This account has not accepted the Xbox terms. Sign in once at xbox.com."
            "2148916235" -> "Xbox Live is not available in this account's region."
            "2148916236", "2148916237" -> "This account needs adult verification at xbox.com."
            "2148916238" -> "This is a child account; it must be added to a Microsoft family by an adult."
            else -> null
        }
    }
}

/** A signed-in Microsoft account. Holds secrets: store it only in encrypted storage. */
data class MicrosoftAccount(
    val username: String,
    val uuid: String,
    val xuid: String,
    val minecraftAccessToken: String,
    val minecraftTokenExpiry: Instant,
    val microsoftRefreshToken: String,
    /** Which client ID signed in (the refresh must use the same one); null = Live. */
    val clientId: String? = null
) {
    val identity: LaunchIdentity
        get() = LaunchIdentity(playerName = username, uuid = uuid, accessToken = minecraftAccessToken, userType = "msa", xuid = xuid)

    override fun toString(): String = "MicrosoftAccount(username=$username, uuid=$uuid)" // never print tokens
}

sealed class AuthException(message: String) : Exception(message) {
    data object NoClientId : AuthException("This build has no Microsoft app (client) ID, so Microsoft sign-in is unavailable.")
    data object Declined : AuthException("Sign-in was declined.")
    data object Expired : AuthException("The sign-in code expired. Try again.")
    data object SignInAgain : AuthException("Your Microsoft sign-in has expired. Sign in again.")
    data object AppNotApproved : AuthException("Minecraft services rejected this app's client ID (HTTP 403). New Azure app IDs must be approved by Mojang first.")
    data object RateLimited : AuthException("Too many sign-in attempts. Wait a few minutes and try again.")
    data object DoesNotOwnMinecraft : AuthException("This Microsoft account does not own Minecraft: Java Edition.")
    data object NoProfile : AuthException("This account owns Minecraft but has no Java profile yet. Create one at minecraft.net.")
    data class Microsoft(val detail: String) : AuthException("Microsoft sign-in failed: $detail")
    data class Xbox(val detail: String) : AuthException("Xbox Live sign-in failed: $detail")
    data class Minecraft(val detail: String) : AuthException("Minecraft sign-in failed: $detail")
    data class BadResponse(val what: String) : AuthException("Unexpected response from $what.")
}
