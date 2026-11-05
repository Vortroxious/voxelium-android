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

package com.movtery.zalithlauncher.game.download.assets.platform

import com.movtery.zalithlauncher.game.download.assets.utils.ModTranslations
import com.movtery.zalithlauncher.ui.AndroidStringText

// Voxelium: moved here from ZalithLauncher2's Download screens (removed), which the search and
// mod update code used them from.

/**
 * 资源搜索结果页面信息
 * @param pageNumber 第几页
 * @param pageIndex 页面索引
 * @param totalPage 总页数
 * @param isLastPage 是否为最后一页
 * @param data 搜索结果缓存
 */
data class AssetsPage(
    val pageNumber: Int,
    val pageIndex: Int,
    val totalPage: Int,
    val isLastPage: Boolean,
    val data: List<Pair<PlatformSearchData, ModTranslations.McMod?>>
)

sealed interface SearchAssetsState {
    data object Searching: SearchAssetsState
    data class Success(val page: AssetsPage): SearchAssetsState
    data class Error(val message: AndroidStringText): SearchAssetsState
}

sealed interface DownloadAssetsState<T> {
    class Getting<T> : DownloadAssetsState<T>
    data class Success<T>(val result: T) : DownloadAssetsState<T>
    data class Error<T>(val message: AndroidStringText) : DownloadAssetsState<T>
}

suspend fun <E: PlatformVersion> List<E>.initAllGeneric(
    currentProjectId: String,
    also: suspend (E) -> Unit = {}
): List<E> {
    return mapNotNull { version ->
        if (!version.initFile(currentProjectId)) return@mapNotNull null
        version.also {
            also(it)
        }
    }.sortedByDescending {
        //排序：最新的版本在前
        it.platformDatePublished()
    }
}

/**
 * 初始化全部版本数据，并筛选出成功初始化的所有版本
 */
suspend fun List<PlatformVersion>.initAll(
    currentProjectId: String,
    also: suspend (PlatformVersion) -> Unit = {}
): List<PlatformVersion> {
    return initAllGeneric<PlatformVersion>(currentProjectId, also)
}
