package com.shadowlink.vpn.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shadowlink.vpn.models.UserInfo
import com.shadowlink.vpn.models.VpnProfile

/**
 * Remplacement de PrefsManager utilisant EncryptedSharedPreferences.
 * Toutes les données sensibles (token, profils, credentials) sont
 * chiffrées sur le disque avec AES-256-GCM + AES-256-SIV.
 */
object PrefsManager {

    private const val PREFS_NAME       = "shadowlink_secure_prefs"
    private const val KEY_AUTH_TOKEN   = "auth_token"
    private const val KEY_USER_INFO    = "user_info"
    private const val KEY_SAVED_USER   = "saved_username"
    private const val KEY_LOCAL_PROFS  = "local_profiles"
    private const val KEY_SERVER_PROFS = "server_profiles"
    private const val KEY_LAST_PROF_ID = "last_profile_id"
    private const val KEY_REWARD_EXP   = "reward_expires"
    private const val KEY_REWARDS_TODAY_COUNT = "rewards_today_count"
    private const val KEY_REWARDS_TODAY_DATE  = "rewards_today_date"
    private const val KEY_PANEL_URL    = "panel_url"
    private const val KEY_LANGUAGE     = "language"
    private const val KEY_THEME_MODE   = "theme_mode"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_APP_VER_CODE = "app_version_code"
    private const val KEY_DNS          = "dns"
    private const val KEY_MTU          = "mtu"
    private const val KEY_HOTSPOT      = "hotspot_enabled"
    private const val KEY_XRAY_ENGINE  = "xray_engine"
    private const val KEY_MUX_TYPE     = "mux_type"
    private const val KEY_MUX_CONC     = "mux_concurrency"
    private const val KEY_MUX_PADDING  = "mux_padding"
    private const val KEY_TCP_FAST     = "tcp_fast_open"
    private const val KEY_MPATH_TCP    = "multipath_tcp"
    private const val KEY_KILL_SWITCH  = "kill_switch"
    private const val KEY_SPLIT_TUNNEL = "split_tunnel_apps"
    private const val KEY_CERT_PINS    = "cert_pins"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── AUTH ──────────────────────────────────────────────

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(v) = prefs.edit().putString(KEY_AUTH_TOKEN, v).apply()

    var userInfo: UserInfo?
        get() {
            val j = prefs.getString(KEY_USER_INFO, null) ?: return null
            return runCatching { gson.fromJson(j, UserInfo::class.java) }.getOrNull()
        }
        set(v) {
            val j = if (v != null) gson.toJson(v) else null
            prefs.edit().putString(KEY_USER_INFO, j).apply()
        }

    var savedUsername: String
        get() = prefs.getString(KEY_SAVED_USER, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SAVED_USER, v).apply()

    val isLoggedIn: Boolean get() = authToken != null && userInfo != null

    fun logout() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_INFO)
            .remove(KEY_SERVER_PROFS)
            .apply()
    }

    // ── PANEL ─────────────────────────────────────────────

    var panelUrl: String
        get() = prefs.getString(KEY_PANEL_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PANEL_URL, v.trimEnd('/')).apply()

    // ── PROFILES ──────────────────────────────────────────

    var localProfiles: List<VpnProfile>
        get() {
            val j    = prefs.getString(KEY_LOCAL_PROFS, "[]") ?: "[]"
            val type = object : TypeToken<List<VpnProfile>>() {}.type
            return runCatching { gson.fromJson<List<VpnProfile>>(j, type) }.getOrDefault(emptyList())
        }
        set(v) = prefs.edit().putString(KEY_LOCAL_PROFS, gson.toJson(v)).apply()

    var serverProfiles: List<VpnProfile>
        get() {
            val j    = prefs.getString(KEY_SERVER_PROFS, "[]") ?: "[]"
            val type = object : TypeToken<List<VpnProfile>>() {}.type
            return runCatching { gson.fromJson<List<VpnProfile>>(j, type) }.getOrDefault(emptyList())
        }
        set(v) = prefs.edit().putString(KEY_SERVER_PROFS, gson.toJson(v)).apply()

    val allProfiles: List<VpnProfile> get() = serverProfiles + localProfiles

    var lastUsedProfileId: Int
        get() = prefs.getInt(KEY_LAST_PROF_ID, -1)
        set(v) = prefs.edit().putInt(KEY_LAST_PROF_ID, v).apply()

    fun addLocalProfile(profile: VpnProfile) {
        val list  = localProfiles.toMutableList()
        val newId = (list.maxOfOrNull { it.id } ?: 0) + 1
        list.add(profile.copy(id = newId, isLocal = true))
        localProfiles = list
    }

    fun removeLocalProfile(id: Int) {
        localProfiles = localProfiles.filter { it.id != id }
    }

    // ── REWARD ────────────────────────────────────────────

    var rewardExpiresAt: Long
        get() = prefs.getLong(KEY_REWARD_EXP, 0L)
        set(v) = prefs.edit().putLong(KEY_REWARD_EXP, v).apply()

    // ⚠️ Utilise NtpTimeValidator.now() (heure serveur corrigée) au lieu de
    // System.currentTimeMillis() pour empêcher la fraude par modification d'horloge.
    val hasActiveReward: Boolean get() = NtpTimeValidator.now() < rewardExpiresAt

    fun grantReward(minutes: Int) {
        val now     = NtpTimeValidator.now()
        val current = if (hasActiveReward) rewardExpiresAt else now
        rewardExpiresAt = current + (minutes * 60 * 1000L)
    }

    val rewardMinutesLeft: Int
        get() = if (!hasActiveReward) 0
                else ((rewardExpiresAt - NtpTimeValidator.now()) / 60000).toInt()

    // ─── Compteur quotidien de pubs (anti-fraude horaire via NTP) ──

    private fun todayKey(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(NtpTimeValidator.now()))
    }

    fun rewardsUsedToday(): Int {
        val storedDate = prefs.getString(KEY_REWARDS_TODAY_DATE, "") ?: ""
        return if (storedDate == todayKey()) prefs.getInt(KEY_REWARDS_TODAY_COUNT, 0) else 0
    }

    fun incrementRewardsUsedToday() {
        val today = todayKey()
        val storedDate = prefs.getString(KEY_REWARDS_TODAY_DATE, "") ?: ""
        val current = if (storedDate == today) prefs.getInt(KEY_REWARDS_TODAY_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_REWARDS_TODAY_DATE, today)
            .putInt(KEY_REWARDS_TODAY_COUNT, current + 1)
            .apply()
    }

    // ── CONNEXION ─────────────────────────────────────────

    var dns: String
        get() = prefs.getString(KEY_DNS, "1.1.1.1") ?: "1.1.1.1"
        set(v) = prefs.edit().putString(KEY_DNS, v).apply()

    var mtu: Int
        get() = prefs.getInt(KEY_MTU, 1500)
        set(v) = prefs.edit().putInt(KEY_MTU, v).apply()

    var hotspotEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOTSPOT, false)
        set(v) = prefs.edit().putBoolean(KEY_HOTSPOT, v).apply()

    // ── KILL SWITCH ───────────────────────────────────────

    var killSwitchEnabled: Boolean
        get() = prefs.getBoolean(KEY_KILL_SWITCH, false)
        set(v) = prefs.edit().putBoolean(KEY_KILL_SWITCH, v).apply()

    // ── SPLIT TUNNEL ──────────────────────────────────────

    /** Liste des package names exclus du VPN (split tunnel) */
    var splitTunnelApps: Set<String>
        get() {
            val j    = prefs.getString(KEY_SPLIT_TUNNEL, "[]") ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            return runCatching { gson.fromJson<List<String>>(j, type).toSet() }.getOrDefault(emptySet())
        }
        set(v) = prefs.edit().putString(KEY_SPLIT_TUNNEL, gson.toJson(v.toList())).apply()

    // ── XRAY ENGINE ───────────────────────────────────────

    var xrayEngine: Int
        get() = prefs.getInt(KEY_XRAY_ENGINE, 0)
        set(v) = prefs.edit().putInt(KEY_XRAY_ENGINE, v).apply()

    val xrayEngineLabel: String
        get() = when (xrayEngine) {
            0    -> "Xray-Core Subprocess (Stable)"
            1    -> "Xray-Core Subprocess (Beta)"
            2    -> "Libxray (Intégré)"
            else -> "Xray-Core Subprocess (Stable)"
        }

    // ── MUX ───────────────────────────────────────────────

    var muxType: Int
        get() = prefs.getInt(KEY_MUX_TYPE, 0)
        set(v) = prefs.edit().putInt(KEY_MUX_TYPE, v).apply()

    val muxTypeLabel: String
        get() = when (muxType) { 0->"h2mux (HTTP/2)"; 1->"smux"; 2->"yamux"; else->"Désactivé" }

    var muxConcurrency: Int
        get() = prefs.getInt(KEY_MUX_CONC, 8)
        set(v) = prefs.edit().putInt(KEY_MUX_CONC, v).apply()

    var muxPadding: Boolean
        get() = prefs.getBoolean(KEY_MUX_PADDING, false)
        set(v) = prefs.edit().putBoolean(KEY_MUX_PADDING, v).apply()

    // ── TCP ───────────────────────────────────────────────

    var tcpFastOpen: Boolean
        get() = prefs.getBoolean(KEY_TCP_FAST, true)
        set(v) = prefs.edit().putBoolean(KEY_TCP_FAST, v).apply()

    var multipathTcp: Boolean
        get() = prefs.getBoolean(KEY_MPATH_TCP, false)
        set(v) = prefs.edit().putBoolean(KEY_MPATH_TCP, v).apply()

    // ── DIVERS ────────────────────────────────────────────

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "fr") ?: "fr"
        set(v) = prefs.edit().putString(KEY_LANGUAGE, v).apply()

    // 0 = Sombre (defaut), 1 = Clair, 2 = Systeme
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 0)
        set(v) = prefs.edit().putInt(KEY_THEME_MODE, v).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, v).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(v) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, v).apply()

    var lastKnownVersionCode: Int
        get() = prefs.getInt(KEY_APP_VER_CODE, 0)
        set(v) = prefs.edit().putInt(KEY_APP_VER_CODE, v).apply()
}
