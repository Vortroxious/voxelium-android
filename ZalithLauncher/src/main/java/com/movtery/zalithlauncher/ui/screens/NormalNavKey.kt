/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.zalithlauncher.ui.screens

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.assets.platform.Platform
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformClasses
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.ui.screens.content.FirstLoginMenu
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 普通的屏幕
 */
sealed interface NormalNavKey : TitledNavKey {
    @Contextual override val title: AndroidStringText?
        get() = null

    /** 账号管理屏幕 */
    @Serializable data class AccountManager(
        val loginMenu: FirstLoginMenu = FirstLoginMenu.NONE
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_account_list)
    }
    /** Web屏幕 */
    @Serializable data class WebScreen(val url: String) : NormalNavKey
    /** Voxelium: Play, the home screen (installed versions) */
    @Serializable data object Voxelium: NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.voxelium_ui_play)
    }
    /** Voxelium: Install (Minecraft versions, loaders, modpacks) */
    @Serializable data object VoxeliumInstall: NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.voxelium_ui_install)
    }
    /** Voxelium: one installed version (mods, packs, shaders, worlds, settings) */
    @Serializable data class VoxeliumVersion(val id: String): NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(id)
    }
    /** Voxelium: credits and licences */
    @Serializable data object VoxeliumCredits: NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.voxelium_ui_set_licences)
    }
    /** Voxelium: the touch controls editor */
    @Serializable data object VoxeliumControls: NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.voxelium_ui_controls)
    }
    /** Voxelium: Settings (game, graphics, Java, gamepad, launcher, about) */
    @Serializable data object VoxeliumSettings: NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.voxelium_ui_settings)
    }

    /** 查看日志屏幕 */
    @Serializable data class LogView(
        val logPath: String
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.versions_overview_log)
    }

    /** 协议展示屏幕 */
    @Serializable data class License(
        val raw: Int
    ): NormalNavKey
}