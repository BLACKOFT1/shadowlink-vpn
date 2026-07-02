package com.shadowlink.vpn.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {

    /**
     * Génère un HWID unique basé sur les caractéristiques matérielles de l'appareil.
     * Ce HWID est utilisé pour l'anti-partage de compte.
     */
    fun getHWID(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val deviceInfo = buildString {
            append(androidId)
            append(Build.BOARD)
            append(Build.BRAND)
            append(Build.DEVICE)
            append(Build.HARDWARE)
            append(Build.MANUFACTURER)
            append(Build.MODEL)
            append(Build.PRODUCT)
        }

        return sha256(deviceInfo)
    }

    /**
     * Hash SHA-256
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Chiffrement AES-256-GCM
     */
    fun encryptAES(data: String, key: String): String {
        return try {
            val keyBytes = key.toByteArray(Charsets.UTF_8).copyOf(32)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            android.util.Base64.encodeToString(iv + encrypted, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            data
        }
    }

    /**
     * Déchiffrement AES-256-GCM
     */
    fun decryptAES(encryptedData: String, key: String): String {
        return try {
            val keyBytes = key.toByteArray(Charsets.UTF_8).copyOf(32)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val decoded = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP)
            val iv = decoded.copyOfRange(0, 12)
            val encrypted = decoded.copyOfRange(12, decoded.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Valide le token de session
     */
    fun isTokenValid(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        // Le serveur gère la validité réelle, ici on vérifie juste le format
        return token.length > 20
    }
}
