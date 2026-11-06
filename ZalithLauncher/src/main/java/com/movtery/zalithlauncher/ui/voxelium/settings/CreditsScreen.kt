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

package com.movtery.zalithlauncher.ui.voxelium.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.plugin.PluginLoader
import com.movtery.zalithlauncher.library.libraryData
import com.movtery.zalithlauncher.path.URL_COMMUNITY
import com.movtery.zalithlauncher.path.URL_MCMOD
import com.movtery.zalithlauncher.path.URL_PROJECT
import com.movtery.zalithlauncher.path.URL_WEBLATE
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.ui.voxelium.components.SectionLabel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

private class Credit(val title: String, val text: String?, val link: String?, val licence: Int? = null, val licenceName: String? = null)

/**
 * Voxelium: credits and licences. Everything Zalith Launcher 2's About screen credits (its
 * author, the projects it builds on, its libraries and their licences, loaded plugins) stays shown
 * here, as Zalith Launcher 2's GPLv3 §7 terms ask.
 */
@Composable
fun VoxeliumCreditsScreen(
    backScreenViewModel: ScreenBackStackViewModel,
    onBack: () -> Unit,
    openLink: (String) -> Unit,
    openLicence: (Int) -> Unit
) {
    BaseScreen(screenKey = NormalNavKey.VoxeliumCredits, currentKey = backScreenViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        val name = BuildKeys.LAUNCHER_SHORT_NAME
        val basedOn = listOf(
            Credit("Zalith Launcher 2", stringResource(R.string.voxelium_ui_credits_zalith), URL_PROJECT),
            Credit("MovTery", stringResource(R.string.about_launcher_author_movtery_text, "Zalith Launcher 2"), "https://github.com/MovTery")
        )
        val thanks = listOf(
            Credit("PojavLauncher", stringResource(R.string.about_acknowledgements_pojav_text, name), "https://github.com/PojavLauncherTeam/PojavLauncher", R.raw.lgpl_3_license, "LGPL-3.0"),
            Credit("Fold Craft Launcher", stringResource(R.string.about_acknowledgements_fcl_text, name), "https://github.com/FCL-Team/FoldCraftLauncher", R.raw.fcl_license, "GPL-3.0"),
            Credit("Hello Minecraft! Launcher", stringResource(R.string.about_acknowledgements_hmcl_text, name), "https://github.com/HMCL-dev/HMCL", R.raw.hmcl_license, "GPL-3.0"),
            Credit("Plain Craft Launcher 2", stringResource(R.string.about_acknowledgements_pcl_text, name), "https://github.com/Meloong-Git/PCL"),
            Credit("bangbang93", stringResource(R.string.about_acknowledgements_bangbang93_text, name), "https://ifdian.net/a/bangbang93"),
            Credit("mcmod-info-mirror", stringResource(R.string.about_acknowledgements_mcim_text, name), "https://www.mcimirror.top/sponsor"),
            Credit(stringResource(R.string.about_acknowledgements_mcmod), stringResource(R.string.about_acknowledgements_mcmod_text, name), URL_MCMOD),
            Credit(stringResource(R.string.about_acknowledgements_github_community), stringResource(R.string.about_acknowledgements_github_community_text), URL_COMMUNITY),
            Credit(stringResource(R.string.about_acknowledgements_weblate_community), stringResource(R.string.about_acknowledgements_weblate_community_text), URL_WEBLATE)
        )
        val libraries = libraryData.map { Credit(it.name, it.copyrightInfo, it.webUrl, it.license.raw, it.license.name) }
        val plugins = PluginLoader.allPlugins.map { Credit(it.appName, "${it.packageName} · ${it.appVersion}", null) }

        Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            PageHeader(title = stringResource(R.string.voxelium_ui_set_licences), onBack = onBack)
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { SectionLabel(stringResource(R.string.voxelium_ui_set_based_on)) }
                items(basedOn) { CreditRow(it, openLink, openLicence) }
                item { SectionLabel(stringResource(R.string.about_acknowledgements_title), modifier = Modifier.padding(top = 10.dp)) }
                items(thanks) { CreditRow(it, openLink, openLicence) }
                item { SectionLabel(stringResource(R.string.about_library_title), modifier = Modifier.padding(top = 10.dp)) }
                items(libraries) { CreditRow(it, openLink, openLicence) }
                if (plugins.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.about_plugin_title), modifier = Modifier.padding(top = 10.dp)) }
                    items(plugins) { CreditRow(it, openLink, openLicence) }
                }
                item { Text(" ", modifier = Modifier.padding(bottom = 8.dp)) }
            }
        }
    }
}

@Composable
private fun CreditRow(c: Credit, openLink: (String) -> Unit, openLicence: (Int) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall)
                c.text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                c.licenceName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
            if (c.licence != null) TextButton(onClick = { openLicence(c.licence) }) { Text(stringResource(R.string.voxelium_ui_credits_licence)) }
            if (c.link != null) TextButton(onClick = { openLink(c.link) }) { Text(stringResource(R.string.voxelium_ui_set_open)) }
        }
    }
}
