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

import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountType
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.CredentialsExpiredException
import com.movtery.zalithlauncher.game.account.wardrobe.SkinModelType
import com.movtery.zalithlauncher.game.account.yggdrasil.findUsing
import com.movtery.zalithlauncher.game.account.yggdrasil.getPlayerProfile
import com.movtery.zalithlauncher.game.account.yggdrasil.getSkinModel
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.string.toUuidStr
import io.github.voxelium.core.accounts.AuthException
import io.github.voxelium.core.accounts.MicrosoftAuth
import io.github.voxelium.core.accounts.MicrosoftAuth.MicrosoftTokens
import io.github.voxelium.core.accounts.OkHttpJsonClient

private const val TAG = "VoxeliumMicrosoftAuth"

/**
 * Voxelium: Microsoft sign-in and refresh through VoxeliumCore's [MicrosoftAuth] (the same code as
 * Voxelium for iOS), filling in ZalithLauncher2's [Account] record.
 */
object VoxeliumMicrosoftAuth {
    val auth: MicrosoftAuth by lazy { MicrosoftAuth(BuildKeys.OAUTH_CLIENT_ID, OkHttpJsonClient()) }

    /**
     * Gets Microsoft tokens with [tokens] (redeeming a sign-in code, or refreshing), then runs
     * Xbox Live → XSTS → Minecraft and returns the account to save.
     */
    suspend fun signIn(
        tokens: suspend (MicrosoftAuth) -> MicrosoftTokens,
        statusUpdate: (AsyncStatus) -> Unit
    ): Account {
        try {
            statusUpdate(AsyncStatus.GETTING_ACCESS_TOKEN)
            val ms = tokens(auth)
            val mc = auth.minecraftAccount(ms) { step ->
                statusUpdate(
                    when (step) {
                        MicrosoftAuth.Step.XBOX_LIVE -> AsyncStatus.GETTING_XBL_TOKEN
                        MicrosoftAuth.Step.XSTS -> AsyncStatus.GETTING_XSTS_TOKEN
                        MicrosoftAuth.Step.MINECRAFT_LOGIN -> AsyncStatus.AUTHENTICATE_MINECRAFT
                        MicrosoftAuth.Step.OWNERSHIP -> AsyncStatus.VERIFY_GAME_OWNERSHIP
                        MicrosoftAuth.Step.PROFILE -> AsyncStatus.GETTING_PLAYER_PROFILE
                    }
                )
            }
            //避免同一个账号反复添加
            val account = AccountsManager.loadFromProfileID(mc.uuid, AccountType.MICROSOFT.tag) ?: Account()
            //the skin model is cosmetic: never fail a sign-in over it
            val skinModel = runCatching {
                getPlayerProfile(MINECRAFT_SERVICES_URL, mc.minecraftAccessToken).skins.findUsing()?.getSkinModel()
            }.onFailure { e ->
                Logger.warning(TAG, "Could not read the skin model", e)
            }.getOrNull()
            return account.apply {
                username = mc.username
                accessToken = mc.minecraftAccessToken
                expiresAt = mc.minecraftTokenExpiry.toEpochMilli()
                accountType = AccountType.MICROSOFT.tag
                clientToken = BuildKeys.LAUNCHER_NAME.toUuidStr().replace("-", "")
                profileId = mc.uuid
                refreshToken = mc.microsoftRefreshToken.ifEmpty { "None" }
                xUid = mc.xuid
                skinModelType = skinModel ?: SkinModelType.NONE
            }
        } catch (_: AuthException.SignInAgain) {
            //刷新令牌已被撤销或失效，需要重新登录
            throw CredentialsExpiredException()
        }
    }
}
