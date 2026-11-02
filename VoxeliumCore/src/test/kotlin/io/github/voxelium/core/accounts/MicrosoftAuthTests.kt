/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/MicrosoftAuthTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.accounts

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Canned HTTP responses per URL, in order; records every request. */
class MockHttp : HttpClient {
    private val lock = Any()
    private val queues = mutableMapOf<String, ArrayDeque<Pair<Int, String>>>()
    private val log = mutableListOf<HttpRequest>()

    val requests: List<HttpRequest> get() = synchronized(lock) { log.toList() }

    /** [body] is JSON text. */
    fun on(url: String, status: Int, body: String) = synchronized(lock) {
        queues.getOrPut(url) { ArrayDeque() }.addLast(status to body)
    }

    override suspend fun send(request: HttpRequest): JsonResponse = synchronized(lock) {
        log.add(request)
        var key = request.url
        // Exact URL first, else the longest registered prefix (for URLs with query strings).
        if (queues[key] == null) queues.keys.filter { key.startsWith(it) }.maxByOrNull { it.length }?.let { key = it }
        val q = queues[key]
        if (q.isNullOrEmpty()) throw IOException("bad URL ${request.url}")
        val (status, body) = if (q.size > 1) q.removeFirst() else q.first()
        JsonResponse(status, body.toByteArray())
    }

    fun bodies(url: String): List<String> = synchronized(lock) { log.filter { it.url == url }.map { it.bodyText } }
}

class MicrosoftAuthTests {
    companion object {
        const val MS = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
        const val XBL = "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox"
        const val STORE = "https://api.minecraftservices.com/entitlements/mcstore"
        const val PROFILE = "https://api.minecraftservices.com/minecraft/profile"
        const val LIVE_TOKEN = "https://login.live.com/oauth20_token.srf"
    }

    private fun auth(http: MockHttp, sleeps: MutableList<Double> = mutableListOf()) =
        MicrosoftAuth("test-client", http) { synchronized(sleeps) { sleeps.add(it) } }

    private fun happyChain(http: MockHttp) {
        http.on(XBL, 200, """{"Token":"xbl-token","DisplayClaims":{"xui":[{"uhs":"user-hash"}]}}""")
        http.on(XSTS, 200, """{"Token":"xsts-token"}""")
        http.on(LOGIN, 200, """{"access_token":"mc-token","expires_in":86400}""")
        http.on(STORE, 200, """{"items":[{"name":"game_minecraft"}]}""")
        http.on(PROFILE, 200, """{"id":"0123456789abcdef0123456789ABCDEF","name":"Steve"}""")
    }

    private inline fun <reified T : Throwable> assertThrowsSuspend(expected: T? = null, crossinline block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw e
            if (expected != null) assertEquals(expected, e)
        }
    }

    @Test
    fun deviceCodeFlowSignsIn() = runBlocking {
        val http = MockHttp()
        val sleeps = mutableListOf<Double>()
        http.on("$MS/devicecode", 200, """{"user_code":"ABCD-EFGH","device_code":"dev","verification_uri":"https://www.microsoft.com/link","expires_in":900,"interval":5,"message":"Go to microsoft.com/link"}""")
        http.on("$MS/token", 400, """{"error":"authorization_pending"}""")
        http.on("$MS/token", 400, """{"error":"slow_down"}""")
        http.on("$MS/token", 200, """{"access_token":"ms-access","refresh_token":"ms-refresh"}""")
        happyChain(http)
        val a = auth(http, sleeps)

        val code = a.requestDeviceCode()
        assertEquals("ABCD-EFGH", code.userCode)
        assertEquals("https://www.microsoft.com/link", code.verificationUrl)
        val ms = a.pollForTokens(code)
        assertEquals(MicrosoftAuth.MicrosoftTokens("ms-access", "ms-refresh"), ms)
        assertEquals(listOf(5.0, 5.0, 10.0), sleeps) // slow_down adds 5 s

        val account = a.minecraftAccount(ms, Instant.ofEpochSecond(0))
        assertEquals("Steve", account.username)
        assertEquals("0123456789abcdef0123456789abcdef", account.uuid)
        assertEquals("mc-token", account.minecraftAccessToken)
        assertEquals("ms-refresh", account.microsoftRefreshToken)
        assertEquals(Instant.ofEpochSecond(86400), account.minecraftTokenExpiry)
        assertEquals("msa", account.identity.userType)
        assertEquals("user-hash", account.identity.xuid)

        assertEquals("client_id=test-client&scope=XboxLive.signin%20offline_access", http.bodies("$MS/devicecode").first())
        assertTrue(http.bodies("$MS/token").first().contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
        assertTrue(http.bodies(XBL).first().contains(""""RpsTicket":"d=ms-access""""))
        assertEquals("""{"identityToken":"XBL3.0 x=user-hash;xsts-token"}""", http.bodies(LOGIN).first())
        val bearer = http.requests.first { it.url == PROFILE }.headers["Authorization"]
        assertEquals("Bearer mc-token", bearer)
    }

    @Test
    fun liveWebSignIn() = runBlocking {
        assertTrue(
            MicrosoftAuth.LIVE_AUTHORIZE_URL.startsWith(
                "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&response_type=code&scope=service::user.auth.xboxlive.com::MBI_SSL"
            )
        )
        assertEquals("M.C1_abc", MicrosoftAuth.liveCode("ms-xal-00000000402b5328://auth/?code=M.C1_abc&lc=1033").getOrThrow())
        assertEquals(
            AuthException.Declined,
            MicrosoftAuth.liveCode("ms-xal-00000000402b5328://auth/?error=access_denied&error_description=x").exceptionOrNull()
        )

        val http = MockHttp()
        http.on(LIVE_TOKEN, 200, """{"access_token":"live-access","refresh_token":"live-refresh"}""")
        happyChain(http)
        val a = MicrosoftAuth.live(http)
        val ms = a.redeemLiveCode("M.C1_abc")
        val account = a.minecraftAccount(ms)
        assertEquals("Steve", account.username)
        assertEquals(MicrosoftAuth.LIVE_CLIENT_ID, account.clientId)
        val body = http.bodies(LIVE_TOKEN).first()
        assertTrue(body.contains("client_id=00000000402b5328") && body.contains("code=M.C1_abc") && body.contains("grant_type=authorization_code"))
        // Live tokens go to Xbox Live without the "d=" prefix.
        assertTrue(http.bodies(XBL).first().contains(""""RpsTicket":"live-access""""))

        a.refresh("live-refresh")
        assertTrue(http.bodies(LIVE_TOKEN).last().contains("grant_type=refresh_token"))
    }

    @Test
    fun declinedCode() {
        val http = MockHttp()
        http.on("$MS/token", 400, """{"error":"authorization_declined"}""")
        val code = MicrosoftAuth.DeviceCode("A", "d", "https://x", expiresIn = 60, interval = 1, message = "")
        assertThrowsSuspend<AuthException>(AuthException.Declined) { auth(http).pollForTokens(code) }
    }

    @Test
    fun xstsErrorsAreExplained() {
        val http = MockHttp()
        http.on(XBL, 200, """{"Token":"xbl-token","DisplayClaims":{"xui":[{"uhs":"h"}]}}""")
        http.on(XSTS, 401, """{"XErr":2148916233,"Message":""}""")
        assertThrowsSuspend<AuthException>(AuthException.Xbox(MicrosoftAuth.xstsReason("2148916233")!!)) {
            auth(http).minecraftAccount(MicrosoftAuth.MicrosoftTokens("a", "r"))
        }
    }

    @Test
    fun unapprovedAppAndNoOwnership() {
        val only403 = MockHttp()
        only403.on(XBL, 200, """{"Token":"t","DisplayClaims":{"xui":[{"uhs":"h"}]}}""")
        only403.on(XSTS, 200, """{"Token":"x"}""")
        only403.on(LOGIN, 403, "{}")
        assertThrowsSuspend<AuthException>(AuthException.AppNotApproved) {
            auth(only403).minecraftAccount(MicrosoftAuth.MicrosoftTokens("a", "r"))
        }

        val noGame = MockHttp()
        noGame.on(XBL, 200, """{"Token":"t","DisplayClaims":{"xui":[{"uhs":"h"}]}}""")
        noGame.on(XSTS, 200, """{"Token":"x"}""")
        noGame.on(LOGIN, 200, """{"access_token":"mc","expires_in":86400}""")
        noGame.on(STORE, 200, """{"items":[]}""")
        assertThrowsSuspend<AuthException>(AuthException.DoesNotOwnMinecraft) {
            auth(noGame).minecraftAccount(MicrosoftAuth.MicrosoftTokens("a", "r"))
        }
    }

    @Test
    fun refreshOnlyWhenTheTokenIsNearExpiry() = runBlocking {
        val now = Instant.ofEpochSecond(1_000_000)
        val fresh = MicrosoftAccount(
            username = "Steve", uuid = "u", xuid = "h", minecraftAccessToken = "old",
            minecraftTokenExpiry = now.plusSeconds(7200), microsoftRefreshToken = "r1"
        )
        val idle = MockHttp()
        assertEquals(fresh, auth(idle).validAccount(fresh, now))
        assertTrue(idle.requests.isEmpty())

        // The early refresh (while the app is open) asks for a new token sooner.
        val early = MockHttp()
        early.on("$MS/token", 200, """{"access_token":"ms2","refresh_token":"r2"}""")
        happyChain(early)
        assertEquals("r2", auth(early).validAccount(fresh, now, marginSeconds = 12 * 3600).microsoftRefreshToken)

        val http = MockHttp()
        http.on("$MS/token", 200, """{"access_token":"ms2","refresh_token":"r2"}""")
        happyChain(http)
        val stale = fresh.copy(minecraftTokenExpiry = now.plusSeconds(60))
        val renewed = auth(http).validAccount(stale, now)
        assertEquals("mc-token", renewed.minecraftAccessToken)
        assertEquals("r2", renewed.microsoftRefreshToken)
        assertTrue(http.bodies("$MS/token").first().contains("grant_type=refresh_token"))

        val gone = MockHttp()
        gone.on("$MS/token", 400, """{"error":"invalid_grant"}""")
        assertThrowsSuspend<AuthException>(AuthException.SignInAgain) { auth(gone).validAccount(stale, now) }
    }

    /** From PlannerTests.swift: vanilla's offline UUID for "Notch". */
    @Test
    fun offlineUuidMatchesVanilla() {
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", OfflineIdentity.uuid("Notch").toString())
        assertTrue(OfflineIdentity.isValidName("Steve_1"))
        assertTrue(!OfflineIdentity.isValidName("ab") && !OfflineIdentity.isValidName("Stéve") && !OfflineIdentity.isValidName("a".repeat(17)))
        assertEquals("legacy", OfflineIdentity.identity("Steve").userType)
    }

    @Suppress("unused")
    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
}
