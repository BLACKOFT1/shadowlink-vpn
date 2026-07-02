package com.shadowlink.vpn.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ─── USER / AUTH ────────────────────────────────────────────────

data class LoginRequest(
    val username: String,
    val password: String,
    val hwid: String
)

data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserInfo? = null,
    val message: String? = null
)

data class UserInfo(
    val id: Int,
    val username: String,
    val plan: String,           // "free" | "premium"
    val expiresAt: String,      // ISO date
    val dataLimitMb: Long,      // -1 = unlimited
    val dataUsedMb: Long,
    val active: Boolean,
    val freeMinutesLeft: Int = 0  // pour les Rewarded Ads
)

// ─── VPN PROFILE ────────────────────────────────────────────────

enum class VpnProtocol {
    SSH_DIRECT,
    SSH_PROXY,
    SSH_SSL,
    SSH_SSL_PAYLOAD,
    SLOWDNS,
    UDP_HYSTERIA,
    V2RAY_VMESS,
    V2RAY_VLESS,
    V2RAY_TROJAN,
    SHADOWSOCKS,
    WIREGUARD
}

@Parcelize
data class VpnProfile(
    val id: Int = 0,
    val name: String,
    val protocol: String,       // VpnProtocol.name
    val host: String,
    val port: Int,
    val country: String = "",
    val flagCode: String = "",  // "fr", "us", etc.
    val isPremium: Boolean = false,
    // SSH fields
    val sshUser: String = "",
    val sshPass: String = "",
    val sshPrivateKey: String = "",
    val sshProxyHost: String = "",
    val sshProxyPort: Int = 0,
    val sshSslPort: Int = 443,
    val payload: String = "",
    // SlowDNS fields
    val slowdnsNs: String = "",
    val slowdnsKey: String = "",
    // UDP/Hysteria fields
    val udpObfs: String = "",
    val udpAuth: String = "",
    // V2Ray fields
    val v2rayUuid: String = "",
    val v2rayNetwork: String = "tcp",
    val v2rayTls: Boolean = false,
    val v2rayPath: String = "",
    val v2raySni: String = "",
    val v2rayAlterId: Int = 0,
    // Shadowsocks
    val ssMethod: String = "aes-256-gcm",
    val ssPassword: String = "",
    // Meta
    val createdAt: String = "",
    val isLocal: Boolean = false   // profil créé localement
) : Parcelable

// ─── SERVER STATS ───────────────────────────────────────────────

data class ConnectionStats(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val durationSeconds: Long = 0,
    val ping: Int = 0,
    val isConnected: Boolean = false
)

// ─── API GENERIC ─────────────────────────────────────────────────

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val code: Int = 200
)

// ─── APP UPDATE ──────────────────────────────────────────────────

data class AppUpdate(
    val latestVersion: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean = false
)

// ─── VPN FILE (import/export) ────────────────────────────────────

data class VpnFileConfig(
    val version: Int = 1,
    val appName: String = "ShadowLink VPN Pro",
    val profiles: List<VpnProfile>
)

// ─── AD REWARD ───────────────────────────────────────────────────

data class RewardSession(
    val minutesGranted: Int = 60,
    val expiresAt: Long = 0L  // System.currentTimeMillis() + duration
)
