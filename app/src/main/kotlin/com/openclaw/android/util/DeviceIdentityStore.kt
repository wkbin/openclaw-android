package com.openclaw.android.util

import android.content.Context
import android.util.Base64
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 设备身份信息。私钥不再明文存储，而是经 AES-GCM 加密后保存为 `privateKeyEncryptedBase64`。
 * 密文格式为 Base64(IV(12B) + ciphertext)，加解密密钥保存在 AndroidKeyStore 中。
 * 若 KeyStore 密钥丢失，将重新生成设备身份。
 */
data class DeviceIdentity(
    val deviceId: String,
    val publicKeyRawBase64: String,
    val privateKeyEncryptedBase64: String,
)

object DeviceIdentityStore {
    private const val KEYSTORE_ALIAS = "openclaw_device_identity_aes"

    @Synchronized
    fun loadOrCreate(context: Context): DeviceIdentity {
        val file = identityFile(context)
        file.parentFile?.mkdirs()
        if (file.exists()) {
            runCatching {
                val json = JSONObject(file.readText())
                val encrypted = json.optString("privateKeyEncryptedBase64")
                if (encrypted.isNotBlank()) {
                    val existing = DeviceIdentity(
                        deviceId = json.getString("deviceId"),
                        publicKeyRawBase64 = json.getString("publicKeyRawBase64"),
                        privateKeyEncryptedBase64 = encrypted,
                    )
                    // 校验能否用当前 KeyStore 密钥解密；KeyStore 密钥丢失时解密失败，需重新生成
                    if (existing.deviceId.isNotBlank() && canDecrypt(existing)) {
                        return existing
                    }
                } else {
                    // 兼容旧版本：读取明文 privateKeyPkcs8Base64，迁移为加密存储
                    val legacyPlain = json.optString("privateKeyPkcs8Base64")
                    if (legacyPlain.isNotBlank()) {
                        val migrated = DeviceIdentity(
                            deviceId = json.getString("deviceId"),
                            publicKeyRawBase64 = json.getString("publicKeyRawBase64"),
                            privateKeyEncryptedBase64 = KeystoreCrypto.encrypt(
                                KEYSTORE_ALIAS,
                                Base64.decode(legacyPlain, Base64.DEFAULT),
                            ),
                        )
                        writeIdentity(file, migrated)
                        return migrated
                    }
                }
            }
        }
        val fresh = generate()
        val identity = DeviceIdentity(
            deviceId = fresh.deviceId,
            publicKeyRawBase64 = fresh.publicKeyRawBase64,
            privateKeyEncryptedBase64 = KeystoreCrypto.encrypt(KEYSTORE_ALIAS, fresh.pkcs8),
        )
        writeIdentity(file, identity)
        return identity
    }

    fun signPayload(
        payload: String,
        identity: DeviceIdentity,
    ): String? {
        return runCatching {
            val privateKeyBytes = KeystoreCrypto.decrypt(KEYSTORE_ALIAS, identity.privateKeyEncryptedBase64)
            val pkInfo = PrivateKeyInfo.getInstance(privateKeyBytes)
            val parsed = pkInfo.parsePrivateKey()
            val rawPrivate = DEROctetString.getInstance(parsed).octets
            val privateKey = Ed25519PrivateKeyParameters(rawPrivate, 0)
            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            signer.update(payloadBytes, 0, payloadBytes.size)
            base64UrlEncode(signer.generateSignature())
        }.getOrNull()
    }

    fun publicKeyBase64Url(identity: DeviceIdentity): String? {
        return runCatching {
            val raw = Base64.decode(identity.publicKeyRawBase64, Base64.DEFAULT)
            base64UrlEncode(raw)
        }.getOrNull()
    }

    private fun canDecrypt(identity: DeviceIdentity): Boolean {
        return runCatching {
            KeystoreCrypto.decrypt(KEYSTORE_ALIAS, identity.privateKeyEncryptedBase64)
        }.isSuccess
    }

    private fun writeIdentity(file: File, identity: DeviceIdentity) {
        val json = JSONObject()
            .put("deviceId", identity.deviceId)
            .put("publicKeyRawBase64", identity.publicKeyRawBase64)
            .put("privateKeyEncryptedBase64", identity.privateKeyEncryptedBase64)
        file.writeText(json.toString())
    }

    private data class GeneratedIdentity(
        val deviceId: String,
        val publicKeyRawBase64: String,
        val pkcs8: ByteArray,
    )

    private fun generate(): GeneratedIdentity {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val pubKey = keyPair.public as Ed25519PublicKeyParameters
        val privKey = keyPair.private as Ed25519PrivateKeyParameters
        val rawPublic = pubKey.encoded
        val deviceId = sha256Hex(rawPublic)
        val pkcs8 = PrivateKeyInfoFactory.createPrivateKeyInfo(privKey).encoded
        return GeneratedIdentity(
            deviceId = deviceId,
            publicKeyRawBase64 = Base64.encodeToString(rawPublic, Base64.NO_WRAP),
            pkcs8 = pkcs8,
        )
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }

    private fun identityFile(context: Context): File =
        File(context.filesDir, "openclaw/identity/device.json")
}
