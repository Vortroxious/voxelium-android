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

    /** 设置嵌套子屏幕 */
    sealed interface Settings : NormalNavKey {
        /** 渲染器设置屏幕 */
        @Serializable data object Renderer : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_renderer)
        }
        /** 游戏设置屏幕 */
        @Serializable data object Game : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_game)
        }
        /** 手柄设置屏幕 */
        @Serializable data object Gamepad : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_gamepad)
        }
        /** 启动器设置屏幕 */
        @Serializable data object Launcher : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_launcher)
        }
        /** Java管理屏幕 */
        @Serializable data object JavaManager : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_java_manage)
        }
        /** 关于屏幕 */
        @Serializable data object AboutInfo : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_info_about)
        }
    }

    /** 版本详细设置嵌套子屏幕 */
    sealed interface Versions : NormalNavKey {
        /** 版本概览屏幕 */
        @Serializable data object OverView : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_settings_overview)
        }
        /** 版本配置屏幕 */
        @Serializable data object Config : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_settings_config)
        }
        /** 修改版本屏幕 */
        @Serializable data object ModifyVersion : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_modify_version)
        }
        /** 选择要修改的 Minecraft 版本屏幕 */
        @Serializable data object SelectGameVersion : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_modify_select_mc)
        }
        /** 模组管理屏幕 */
        @Serializable data object ModsManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.mods_manage)
        }
        /** 存档管理屏幕 */
        @Serializable data object SavesManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.saves_manage)
        }
        /** 资源包管理屏幕 */
        @Serializable data object ResourcePackManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.resource_pack_manage)
        }
        /** 光影包管理屏幕 */
        @Serializable data object ShadersManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.shader_pack_manage)
        }
    }


    /** 下载游戏嵌套子屏幕 */
    sealed interface DownloadGame : NormalNavKey {
        /** 选择游戏版本屏幕 */
        @Serializable data object SelectGameVersion : Versions
        /** 选择附加内容屏幕 */
        @Serializable data class Addons(val gameVersion: String) : Versions
    }

    /** 搜索整合包屏幕 */
    @Serializable data object SearchModPack : NormalNavKey
    /** 搜索模组屏幕 */
    @Serializable data object SearchMod : NormalNavKey
    /** 搜索资源包屏幕 */
    @Serializable data object SearchResourcePack : NormalNavKey
    /** 搜索存档屏幕 */
    @Serializable data object SearchSaves : NormalNavKey
    /** 搜索光影包屏幕 */
    @Serializable data object SearchShaders : NormalNavKey
    /** 搜索ID屏幕 */
    @Serializable data object SearchId : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.download_category_by_id)
    }
    /** 收藏屏幕 */
    @Serializable data object Favorites : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.download_category_favorites)
    }

    /** 下载资源屏幕 */
    @Serializable data class DownloadAssets(
        val platform: Platform,
        val projectId: String,
        val classes: PlatformClasses,
        val iconUrl: String? = null
    ) : NormalNavKey

    /** 协议展示屏幕 */
    @Serializable data class License(
        val raw: Int
    ): NormalNavKey
}