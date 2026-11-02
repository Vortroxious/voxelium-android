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

package com.movtery.zalithlauncher.game.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.movtery.zalithlauncher.utils.logging.Logger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "AccountSecrets"

/**
 * Voxelium: account secrets (Minecraft access token, Microsoft refresh token, third-party login
 * password) are stored encrypted with an AES-GCM key kept in the Android Keystore, as Voxelium for iOS
 * keeps them in the Keychain. The key never leaves the device, so a database restored from a backup
 * on another phone can't be read: those accounts just sign in again.
 */
object AccountSecrets {
    private const val KEY_ALIAS = "voxelium_account_secrets"
    private const val PREFIX = "vx1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Values that aren't secrets ("no token" placeholders) are stored as they are. */
    private fun isPlaceholder(value: String?) = value == null || value.isEmpty() || value == "0" || value == "None"

    fun isSealed(value: String?): Boolean = value?.startsWith(PREFIX) == true

    /** True when [account] (as read from the database) still has a secret in plain text. */
    fun hasPlainSecrets(account: Account): Boolean =
        listOf(account.accessToken, account.refreshToken, account.otherPassword)
            .any { !isPlaceholder(it) && !isSealed(it) }

    /** The account as it is written to the database: secrets encrypted. */
    fun sealed(account: Account): Account = account.copy(
        accessToken = seal(account.accessToken)!!,
        refreshToken = seal(account.refreshToken)!!,
        otherPassword = seal(account.otherPassword)
    )

    /**
     * The account as read from the database, with secrets decrypted. Plain-text values (written
     * before encryption) are returned as they are. A value that can't be decrypted is dropped, so
     * the account has to sign in again.
     */
    fun opened(account: Account): Account {
        var lost = false
        fun open(value: String?, fallback: String?): String? {
            if (!isSealed(value)) return value
            return runCatching { decrypt(value!!.removePrefix(PREFIX)) }.getOrElse { e ->
                lost = true
                Logger.warning(TAG, "Could not decrypt a secret of ${account.username}; it must sign in again", e)
                fallback
            }
        }
        val result = account.copy(
            accessToken = open(account.accessToken, "0")!!,
            refreshToken = open(account.refreshToken, "0")!!,
            otherPassword = open(account.otherPassword, "")
        )
        if (lost) result.expiresAt = 0L
        return result
    }

    private fun seal(value: String?): String? =
        if (isPlaceholder(value) || isSealed(value)) value else PREFIX + encrypt(value!!)

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).toString(Charsets.UTF_8)
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
