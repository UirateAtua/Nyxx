package com.ladybug.nyxx

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun encrypt(plainText: String, secret: String): String {
        val key = generate128BitKey(secret)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(
            plainText.toByteArray(Charsets.UTF_8)
        )

        val combined = iv + encryptedBytes

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String, secret: String): String {
        val key = generate128BitKey(secret)

        val combined = Base64.decode(cipherText, Base64.NO_WRAP)

        if (combined.size < 17) {
            throw IllegalArgumentException("Invalid encrypted data")
        }

        val iv = combined.copyOfRange(0, 16)
        val encryptedBytes = combined.copyOfRange(16, combined.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

        val decryptedBytes = cipher.doFinal(encryptedBytes)

        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun generate128BitKey(secret: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(secret.toByteArray())

        val keyBytes = hash.copyOf(16)

        return SecretKeySpec(keyBytes, "AES")
    }
}