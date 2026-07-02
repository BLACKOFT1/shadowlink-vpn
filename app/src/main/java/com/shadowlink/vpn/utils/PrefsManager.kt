package com.shadowlink.vpn.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.shadowlink.vpn.models.UserInfo
import com.shadowlink.vpn.models.VpnProfile

/**
 * Gestionnaire des préférences sécurisées avec chiffrement AES-256-GCM.
 * Toutes les données sensibles (token, profils, credentials) sont chiffrées sur le disque.
 */
object PrefsManager {

    private const val PREFS_NAME = "shadowlink_secure_prefs"
    
    // Keys
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_USER_INFO = "user_info"
    private const val KEY_SAVED_USER = "saved_username"
    private const val KEY_LOCAL_PROFS = "local_profiles"
    private const val KEY_SERVER_PROFS = "server_profiles"
    private const val KEY_LAST_PROF_ID = "last_profile_id"
    private const val KEY_PANEL_URL = "panel_url"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_DNS = "dns"
    private const val KEY_MTU = "mtu"
    private const val KEY_HOTSPOT = "hotspot_enabled"
    private const val KEY_XRAY_ENGINE = "xray_engine"
    private const val KEY_MUX_TYPE = "mux_type"
    private const val KEY_MUX_CONC = "mux_concurrency"
    private const val KEY_MUX_PADDING = "mux_padding"
    private const val KEY_TCP_FAST = "tcp_fast_open"
    private const val KEY_MPATH_TCP = "multipath_tcp"
    private const val KEY_KILL_SWITCH = "kill_switch"
    private const val KEY_SPLIT_TUNNEL = "split_tunnel_apps"

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

    val isLoggedIn: Boolean
        get() = authToken != null && authToken!!.isNotEmpty()

    // ── PROFILES ──────────────────────────────────────────

    var lastUsedProfileId: String
        get() = prefs.getString(KEY_LAST_PROF_ID, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_PROF_ID, v).apply()

    var localProfiles: Set<String>
        get() {
            val j = prefs.getString(KEY_LOCAL_PROFS, "[]") ?: "[]"
            return runCatching { gson.fromJson(j, Array<VpnProfile>::class.java).map { it.id }.toSet() }.getOrDefault(emptySet())
        }
        set(v) = prefs.edit().putString(KEY_LOCAL_PROFS, gson.toJson(v)).apply()

    var serverProfiles: Set<String>
        get() {
            val j = prefs.getString(KEY_SERVER_PROFS, "[]") ?: "[]"
            return runCatching { gson.fromJson(j, Array<VpnProfile>::class.java).map { it.id }.toSet() }.getOrDefault(emptySet())
        }
        set(v) = prefs.edit().putString(KEY_SERVER_PROFS, gson.toJson(v)).apply()

    val allProfiles: List<VpnProfile>
        get() = emptyList() // À implémenter selon la logique métier

    fun addLocalProfile(profile: VpnProfile) {
        // À implémenter
    }

    fun removeLocalProfile(id: String) {
        // À implémenter
    }

    // ── PANEL ────────────────────────────────────────────

    var panelUrl: String
        get() = prefs.getString(KEY_PANEL_URL, "https://panel.example.com") ?: "https://panel.example.com"
        set(v) = prefs.edit().putString(KEY_PANEL_URL, v).apply()

    // ── UI ───────────────────────────────────────────────

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "fr") ?: "fr"
        set(v) = prefs.edit().putString(KEY_LANGUAGE, v).apply()

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 0)
        set(v) = prefs.edit().putInt(KEY_THEME_MODE, v).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, v).apply()

    // ── VPN CONFIG ───────────────────────────────────────

    var dns: String
        get() = prefs.getString(KEY_DNS, "1.1.1.1") ?: "1.1.1.1"
        set(v) = prefs.edit().putString(KEY_DNS, v).apply()

    var mtu: Int
        get() = prefs.getInt(KEY_MTU, 1500)
        set(v) = prefs.edit().putInt(KEY_MTU, v).apply()

    var hotspotEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOTSPOT, false)
        set(v) = prefs.edit().putBoolean(KEY_HOTSPOT, v).apply()

    var xrayEngine: Int
        get() = prefs.getInt(KEY_XRAY_ENGINE, 0)
        set(v) = prefs.edit().putInt(KEY_XRAY_ENGINE, v).apply()

    var muxType: Int
        get() = prefs.getInt(KEY_MUX_TYPE, 0)
        set(v) = prefs.edit().putInt(KEY_MUX_TYPE, v).apply()

    var muxConcurrency: Int
        get() = prefs.getInt(KEY_MUX_CONC, 8)
        set(v) = prefs.edit().putInt(KEY_MUX_CONC, v).apply()

    var muxPadding: Boolean
        get() = prefs.getBoolean(KEY_MUX_PADDING, false)
        set(v) = prefs.edit().putBoolean(KEY_MUX_PADDING, v).apply()

    var tcpFastOpen: Boolean
        get() = prefs.getBoolean(KEY_TCP_FAST, false)
        set(v) = prefs.edit().putBoolean(KEY_TCP_FAST, v).apply()

    var multipathTcp: Boolean
        get() = prefs.getBoolean(KEY_MPATH_TCP, false)
        set(v) = prefs.edit().putBoolean(KEY_MPATH_TCP, v).apply()

    var killSwitchEnabled: Boolean
        get() = prefs.getBoolean(KEY_KILL_SWITCH, false)
        set(v) = prefs.edit().putBoolean(KEY_KILL_SWITCH, v).apply()

    var splitTunnelApps: Set<String>
        get() {
            val j = prefs.getString(KEY_SPLIT_TUNNEL, "[]") ?: "[]"
            return runCatching { gson.fromJson(j, Array<String>::class.java).toSet() }.getOrDefault(emptySet())
        }
        set(v) = prefs.edit().putString(KEY_SPLIT_TUNNEL, gson.toJson(v.toTypedArray())).apply()

    fun rewardsUsedToday(): Int = 0 // À implémenter
}
