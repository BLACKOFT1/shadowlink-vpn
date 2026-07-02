package com.shadowlink.vpn.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.shadowlink.vpn.models.VpnFileConfig
import com.shadowlink.vpn.models.VpnProfile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object VpnFileManager {

    private val gson = Gson()

    // Extension propriétaire
    const val EXTENSION = ".slvpn"

    /**
     * Exporte une liste de profils dans un fichier .slvpn
     */
    fun exportProfiles(
        context: Context,
        profiles: List<VpnProfile>,
        uri: Uri
    ): Result<Boolean> {
        return try {
            val fileConfig = VpnFileConfig(profiles = profiles)
            val json = gson.toJson(fileConfig)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(json)
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(Exception("Export échoué : ${e.message}"))
        }
    }

    /**
     * Importe des profils depuis un fichier .slvpn
     */
    fun importFromFile(context: Context, uri: Uri): Result<List<VpnProfile>> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: return Result.failure(Exception("Fichier vide"))

            // Essayer format .slvpn (JSON natif)
            val nativeResult = tryParseNative(content)
            if (nativeResult != null) return Result.success(nativeResult)

            // Essayer format V2Ray share link (vmess://, vless://, trojan://)
            val linkResult = tryParseShareLinks(content)
            if (linkResult.isNotEmpty()) return Result.success(linkResult)

            // Essayer format Shadowsocks (ss://)
            val ssResult = tryParseShadowsocks(content)
            if (ssResult.isNotEmpty()) return Result.success(ssResult)

            Result.failure(Exception("Format de fichier non reconnu"))
        } catch (e: Exception) {
            Result.failure(Exception("Import échoué : ${e.message}"))
        }
    }

    private fun tryParseNative(content: String): List<VpnProfile>? {
        return try {
            val config = gson.fromJson(content, VpnFileConfig::class.java)
            if (config?.profiles?.isNotEmpty() == true) config.profiles else null
        } catch (e: Exception) { null }
    }

    private fun tryParseShareLinks(content: String): List<VpnProfile> {
        val profiles = mutableListOf<VpnProfile>()
        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("vmess://") -> {
                    parseVmessLink(trimmed)?.let { profiles.add(it) }
                }
                trimmed.startsWith("vless://") -> {
                    parseVlessLink(trimmed)?.let { profiles.add(it) }
                }
                trimmed.startsWith("trojan://") -> {
                    parseTrojanLink(trimmed)?.let { profiles.add(it) }
                }
            }
        }
        return profiles
    }

    private fun parseVmessLink(link: String): VpnProfile? {
        return try {
            val encoded = link.removePrefix("vmess://")
            val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
            val obj = gson.fromJson(decoded, Map::class.java)
            VpnProfile(
                name = obj["ps"] as? String ?: "VMess",
                protocol = "V2RAY_VMESS",
                host = obj["add"] as? String ?: return null,
                port = (obj["port"] as? String)?.toIntOrNull() ?: 443,
                v2rayUuid = obj["id"] as? String ?: "",
                v2rayAlterId = (obj["aid"] as? Double)?.toInt() ?: 0,
                v2rayNetwork = obj["net"] as? String ?: "tcp",
                v2rayPath = obj["path"] as? String ?: "",
                v2raySni = obj["host"] as? String ?: "",
                v2rayTls = obj["tls"] as? String == "tls"
            )
        } catch (e: Exception) { null }
    }

    private fun parseVlessLink(link: String): VpnProfile? {
        return try {
            val uri = android.net.Uri.parse(link)
            VpnProfile(
                name = uri.fragment ?: "VLESS",
                protocol = "V2RAY_VLESS",
                host = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
                v2rayUuid = uri.userInfo ?: "",
                v2rayNetwork = uri.getQueryParameter("type") ?: "tcp",
                v2rayPath = uri.getQueryParameter("path") ?: "",
                v2raySni = uri.getQueryParameter("sni") ?: "",
                v2rayTls = uri.getQueryParameter("security") == "tls"
            )
        } catch (e: Exception) { null }
    }

    private fun parseTrojanLink(link: String): VpnProfile? {
        return try {
            val uri = android.net.Uri.parse(link)
            VpnProfile(
                name = uri.fragment ?: "Trojan",
                protocol = "V2RAY_TROJAN",
                host = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
                v2rayUuid = uri.userInfo ?: "",
                v2raySni = uri.getQueryParameter("sni") ?: "",
                v2rayTls = true
            )
        } catch (e: Exception) { null }
    }

    private fun tryParseShadowsocks(content: String): List<VpnProfile> {
        val profiles = mutableListOf<VpnProfile>()
        content.lines().forEach { line ->
            if (line.trim().startsWith("ss://")) {
                try {
                    val uri = android.net.Uri.parse(line.trim())
                    val userInfo = String(
                        android.util.Base64.decode(uri.userInfo ?: "", android.util.Base64.DEFAULT)
                    )
                    val (method, password) = userInfo.split(":", limit = 2)
                    profiles.add(VpnProfile(
                        name = uri.fragment ?: "Shadowsocks",
                        protocol = "SHADOWSOCKS",
                        host = uri.host ?: return@forEach,
                        port = uri.port.takeIf { it > 0 } ?: 8388,
                        ssMethod = method,
                        ssPassword = password
                    ))
                } catch (e: Exception) { /* skip */ }
            }
        }
        return profiles
    }

    /**
     * Génère un lien de partage pour un profil
     */
    fun generateShareLink(profile: VpnProfile): String {
        return when (profile.protocol) {
            "V2RAY_VMESS" -> {
                val obj = mapOf(
                    "v" to "2", "ps" to profile.name, "add" to profile.host,
                    "port" to profile.port.toString(), "id" to profile.v2rayUuid,
                    "aid" to profile.v2rayAlterId.toString(), "net" to profile.v2rayNetwork,
                    "path" to profile.v2rayPath, "host" to profile.v2raySni,
                    "tls" to if (profile.v2rayTls) "tls" else ""
                )
                val encoded = android.util.Base64.encodeToString(
                    gson.toJson(obj).toByteArray(), android.util.Base64.NO_WRAP
                )
                "vmess://$encoded"
            }
            "V2RAY_VLESS" -> {
                "vless://${profile.v2rayUuid}@${profile.host}:${profile.port}" +
                "?type=${profile.v2rayNetwork}&security=${if (profile.v2rayTls) "tls" else "none"}" +
                "&sni=${profile.v2raySni}&path=${profile.v2rayPath}#${profile.name}"
            }
            "V2RAY_TROJAN" -> {
                "trojan://${profile.v2rayUuid}@${profile.host}:${profile.port}" +
                "?sni=${profile.v2raySni}#${profile.name}"
            }
            "SHADOWSOCKS" -> {
                val userInfo = android.util.Base64.encodeToString(
                    "${profile.ssMethod}:${profile.ssPassword}".toByteArray(),
                    android.util.Base64.NO_WRAP
                )
                "ss://$userInfo@${profile.host}:${profile.port}#${profile.name}"
            }
            else -> gson.toJson(profile)
        }
    }
}
