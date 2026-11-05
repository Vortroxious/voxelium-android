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

package com.movtery.zalithlauncher.ui.voxelium.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.isAuthServerAccount
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftLogging
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.SkinPreview3D
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.AccountFlows
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountSkinOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.LocalLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.LoginMenuOperation
import com.movtery.zalithlauncher.ui.screens.content.rememberAccountFlows
import com.movtery.zalithlauncher.ui.screens.content.startMicrosoftLogin
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.viewmodel.AccountManageIntent
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

/**
 * Voxelium: Accounts. Who you play as on the left with the ways to add an account; the chosen
 * account on the right with its skin, refresh, skin and cape changes and removal. The sign-in and
 * dialog flows are the launcher's existing account code (AccountManageViewModel).
 */
@Composable
fun VoxeliumAccountsScreen(
    key: NormalNavKey.AccountManager,
    backStackViewModel: ScreenBackStackViewModel,
    backToMainScreen: () -> Unit,
    openLink: (String) -> Unit,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    eventViewModel: EventViewModel
) {
    val (viewModel, actions) = rememberAccountFlows(key, backStackViewModel, backToMainScreen, openLink, submitError, eventViewModel)
    BaseScreen(screenKey = key, currentKey = backStackViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        val profile by viewModel.profileUiState.collectAsStateWithLifecycle()
        val current = profile.currentAccount

        Row(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PageHeader(title = stringResource(R.string.voxelium_ui_accounts))
                if (profile.accounts.isEmpty()) {
                    Text(stringResource(R.string.voxelium_ui_accounts_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(profile.accounts, key = { it.uniqueUUID }) { a ->
                        val selected = a.uniqueUUID == current?.uniqueUUID
                        Surface(
                            onClick = { AccountsManager.setCurrentAccount(a) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = { AccountsManager.setCurrentAccount(a) })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(a.username, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(typeLine(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { startMicrosoftLogin(actions) },
                    enabled = !isMicrosoftLogging(),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text(stringResource(R.string.voxelium_ui_accounts_microsoft)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { actions.onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.Edit)) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.voxelium_ui_accounts_offline), maxLines = 1) }
                    OutlinedButton(
                        onClick = { actions.onIntent(AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.Login)) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.voxelium_ui_accounts_other), maxLines = 1) }
                }
            }

            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (current == null) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.voxelium_ui_accounts_pick), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    AccountPanel(
                        account = current,
                        onRefresh = { actions.onIntent(AccountManageIntent.RefreshAccount(current)) },
                        onSkin = { actions.onIntent(AccountManageIntent.UpdateAccountSkinOp(AccountSkinOperation.ChangeSkin(current))) },
                        onRemove = { actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.Delete(current))) }
                    )
                }
            }
        }
    }
    AccountFlows(viewModel, actions)
}

@Composable
private fun AccountPanel(account: Account, onRefresh: () -> Unit, onSkin: () -> Unit, onRemove: () -> Unit) {
    val wardrobe by AccountsManager.refreshWardrobe.collectAsStateWithLifecycle()
    val skin = remember(account.uniqueUUID, wardrobe) { account.getSkinFile().takeIf { it.exists() } }
    val cape = remember(account.uniqueUUID, wardrobe) { account.getCapeFile().takeIf { it.exists() } }
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(modifier = Modifier.width(150.dp).fillMaxHeight(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SkinPreview3D(
                skinFile = skin,
                capeFile = cape,
                modelType = account.skinModelType,
                modifier = Modifier.fillMaxSize(),
                refreshKey = listOf(account.uniqueUUID, wardrobe, skin?.lastModified() ?: 0L, cape?.lastModified() ?: 0L)
            )
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(account.username, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    account.isMicrosoftAccount() -> stringResource(R.string.voxelium_ui_accounts_microsoft_hint)
                    account.isLocalAccount() -> stringResource(R.string.voxelium_ui_accounts_offline_hint)
                    else -> stringResource(R.string.voxelium_ui_accounts_other_hint, account.accountType ?: "")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!account.isLocalAccount()) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.voxelium_ui_accounts_refresh)) }
                }
                // Skins of third-party server accounts are managed on that server.
                if (!account.isAuthServerAccount()) {
                    OutlinedButton(onClick = onSkin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.voxelium_ui_accounts_skin)) }
                }
                OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.voxelium_ui_accounts_remove), color = MaterialTheme.colorScheme.error)
                }
            }
            if (account.isMicrosoftAccount()) {
                Text(stringResource(R.string.voxelium_ui_accounts_encrypted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun typeLine(a: Account): String = when {
    a.isMicrosoftAccount() -> stringResource(R.string.voxelium_account_microsoft)
    a.isLocalAccount() -> stringResource(R.string.voxelium_account_offline)
    else -> a.accountType ?: ""
}
