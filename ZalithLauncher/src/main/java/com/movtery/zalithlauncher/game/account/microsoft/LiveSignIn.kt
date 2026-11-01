/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.account.microsoft

import android.net.Uri
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Voxelium: Live web sign-in, as PojavLauncher / Amethyst and Voxelium for iOS do it.
 *
 * The client ID `00000000402b5328` is a Microsoft account (Live) client, so it only works with
 * login.live.com, not with the Microsoft identity platform's device code flow upstream uses.
 * The sign-in page runs in the in-app web screen; when login.live.com sends the browser back with
 * `?code=…` (to `ms-xal-<client id>://auth/`, or to [LIVE_REDIRECT_URL]), the web screen hands the URL
 * to [LiveSignIn.handleRedirect].
 */
const val LIVE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
const val LIVE_REDIRECT_URL = "$LIVE_AUTH_URL/oauth20_desktop.srf"

private val LIVE_CALLBACK_PREFIX: String
    get() = "ms-xal-${BuildKeys.OAUTH_CLIENT_ID}:"

val LIVE_AUTHORIZE_URL: String
    get() = Uri.parse("$LIVE_AUTH_URL/oauth20_authorize.srf").buildUpon()
        .appendQueryParameter("client_id", BuildKeys.OAUTH_CLIENT_ID)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("scope", LIVE_SCOPE)
        .appendQueryParameter("redirect_url", LIVE_REDIRECT_URL)
        .build()
        .toString()

object LiveSignIn {
    private const val TAG = "LiveSignIn"

    @Volatile
    private var pending: CompletableDeferred<String>? = null

    /** Starts waiting for a redirect; the returned value completes with the authorization code. */
    fun begin(): CompletableDeferred<String> {
        pending?.cancel()
        return CompletableDeferred<String>().also { pending = it }
    }

    fun end() {
        pending = null
    }

    /**
     * Called by the web screen for every page it is about to load.
     * @return true if [url] is the sign-in redirect and was consumed
     */
    fun handleRedirect(url: String?): Boolean {
        if (url == null) return false
        if (!url.startsWith(LIVE_CALLBACK_PREFIX) && !url.startsWith(LIVE_REDIRECT_URL)) return false
        val deferred = pending ?: return false
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        when {
            !code.isNullOrEmpty() -> {
                Logger.info(TAG, "Authorization code received")
                deferred.complete(code)
            }
            uri.getQueryParameter("error")?.startsWith("access_denied") == true -> {
                Logger.info(TAG, "The user declined the sign-in")
                deferred.cancel(CancellationException("Sign-in declined"))
            }
            else -> {
                val reason = uri.getQueryParameter("error_description")
                    ?: uri.getQueryParameter("error")
                    ?: "no code"
                Logger.warning(TAG, "Sign-in page returned an error: $reason")
                deferred.completeExceptionally(Exception(reason))
            }
        }
        return true
    }
}
