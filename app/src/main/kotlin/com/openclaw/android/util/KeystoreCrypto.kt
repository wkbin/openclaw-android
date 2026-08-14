package com.openclaw.android.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStore AES-GCM 加解密工具。
 * 密文格式：Base64(IV(12B) + ciphertext)，GCM tag 128bit，密钥保存在 AndroidKeyStore 中。
 */
object KeystoreCrypto {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    @Synchronized
    fun encrypt(alias: String, plain: ByteArray): String {
        val key = ensureKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    @Synchronized
    fun decrypt(alias: String, encryptedBase64: String): ByteArray {
        val key = getKey(alias) ?: error("AndroidKeyStore key not found: $alias")
        val raw = Base64.decode(encryptedBase64, Base64.DEFAULT)
        require(raw.size > GCM_IV_LENGTH + GCM_TAG_BITS / 8) { "invalid encrypted data" }
        val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(alias: String, plain: String): String =
        encrypt(alias, plain.toByteArray(Charsets.UTF_8))

    fun decryptString(alias: String, encryptedBase64: String): String =
        String(decrypt(alias, encryptedBase64), Charsets.UTF_8)

    @Synchronized
    fun ensureKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(alias, null) as SecretKey
    }

    fun getKey(alias: String): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.getKey(alias, null) as? SecretKey
        }.getOrNull()
    }
}
