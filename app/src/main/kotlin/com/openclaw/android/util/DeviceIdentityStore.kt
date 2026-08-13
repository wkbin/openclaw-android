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

data class DeviceIdentity(
    val deviceId: String,
    val publicKeyRawBase64: String,
    val privateKeyPkcs8Base64: String,
)

object DeviceIdentityStore {
    @Synchronized
    fun loadOrCreate(context: Context): DeviceIdentity {
        val file = identityFile(context)
        file.parentFile?.mkdirs()
        if (file.exists()) {
            runCatching {
                val json = JSONObject(file.readText())
                val existing = DeviceIdentity(
                    deviceId = json.getString("deviceId"),
                    publicKeyRawBase64 = json.getString("publicKeyRawBase64"),
                    privateKeyPkcs8Base64 = json.getString("privateKeyPkcs8Base64"),
                )
                if (existing.deviceId.isNotBlank()) return existing
            }
        }
        val fresh = generate()
        val json = JSONObject()
            .put("deviceId", fresh.deviceId)
            .put("publicKeyRawBase64", fresh.publicKeyRawBase64)
            .put("privateKeyPkcs8Base64", fresh.privateKeyPkcs8Base64)
        file.writeText(json.toString())
        return fresh
    }

    fun signPayload(
        payload: String,
        identity: DeviceIdentity,
    ): String? {
        return runCatching {
            val privateKeyBytes = Base64.decode(identity.privateKeyPkcs8Base64, Base64.DEFAULT)
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

    private fun generate(): DeviceIdentity {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val pubKey = keyPair.public as Ed25519PublicKeyParameters
        val privKey = keyPair.private as Ed25519PrivateKeyParameters
        val rawPublic = pubKey.encoded
        val deviceId = sha256Hex(rawPublic)
        val pkcs8 = PrivateKeyInfoFactory.createPrivateKeyInfo(privKey).encoded
        return DeviceIdentity(
            deviceId = deviceId,
            publicKeyRawBase64 = Base64.encodeToString(rawPublic, Base64.NO_WRAP),
            privateKeyPkcs8Base64 = Base64.encodeToString(pkcs8, Base64.NO_WRAP),
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

