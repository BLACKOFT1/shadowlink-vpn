package com.shadowlink.vpn.vpn

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.shadowlink.vpn.models.ConnectionStats
import com.shadowlink.vpn.models.VpnProfile
import com.shadowlink.vpn.models.VpnProtocol
import com.shadowlink.vpn.services.ShadowVpnService
import com.shadowlink.vpn.services.SshTunnelService
import com.shadowlink.vpn.utils.AppLogger
import com.shadowlink.vpn.utils.PrefsManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

object VpnManager {

    enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

    private val _state        = MutableLiveData(VpnState.DISCONNECTED)
    val state: LiveData<VpnState> = _state

    private val _stats        = MutableLiveData(ConnectionStats())
    val stats: LiveData<ConnectionStats> = _stats

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var currentProfile: VpnProfile? = null
    private var statsJob:       Job?         = null
    private val isConnected   = AtomicBoolean(false)
    private var connectionStartTime = 0L
    private var reconnectAttempts   = 0

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun connect(context: Context, profile: VpnProfile) {
        if (_state.value == VpnState.CONNECTING || _state.value == VpnState.CONNECTED) return

        currentProfile = profile
        _state.value   = VpnState.CONNECTING
        _errorMessage.value = null
        reconnectAttempts   = 0

        AppLogger.vpn("[VPN] Connexion → ${profile.name} (${profile.protocol})")

        // Activer le Kill Switch si configuré
        KillSwitchManager.enable(context) {
            AppLogger.w("[KillSwitch] Réseau perdu — trafic bloqué")
            onServiceStopped()
        }

        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    connectWithProtocol(context, profile)
                }
                if (success) {
                    _state.value       = VpnState.CONNECTED
                    isConnected.set(true)
                    connectionStartTime = System.currentTimeMillis()
                    AppLogger.vpn("[VPN] ✓ Connecté via ${profile.protocol}")
                    startStatsUpdater()
                    // Annuler toute reconnexion en attente
                    AutoReconnectManager.cancel(context)
                } else {
                    _state.value = VpnState.ERROR
                    _errorMessage.value = "Connexion échouée. Vérifiez le profil."
                    AppLogger.e("[VPN] Connexion échouée")
                    scheduleReconnectIfNeeded(context)
                }
            } catch (e: Exception) {
                _state.value = VpnState.ERROR
                _errorMessage.value = e.message ?: "Erreur inconnue"
                AppLogger.e("[VPN] Exception : ${e.message}")
                scheduleReconnectIfNeeded(context)
            }
        }
    }

    fun disconnect(context: Context) {
        AppLogger.vpn("[VPN] Déconnexion demandée")
        _state.value = VpnState.DISCONNECTING
        statsJob?.cancel()
        isConnected.set(false)
        KillSwitchManager.disable(context)
        AutoReconnectManager.cancel(context)

        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { context.stopService(Intent(context, SshTunnelService::class.java)) }
                runCatching { context.stopService(Intent(context, ShadowVpnService::class.java)) }
            }
            _state.value  = VpnState.DISCONNECTED
            _stats.value  = ConnectionStats()
            currentProfile = null
            AppLogger.vpn("[VPN] Déconnecté")
        }
    }

    private suspend fun connectWithProtocol(context: Context, profile: VpnProfile): Boolean {
        val protocol = runCatching { VpnProtocol.valueOf(profile.protocol) }.getOrNull()
            ?: return false.also { AppLogger.e("[VPN] Protocole inconnu : ${profile.protocol}") }

        return when (protocol) {
            VpnProtocol.SSH_DIRECT,
            VpnProtocol.SSH_PROXY,
            VpnProtocol.SSH_SSL,
            VpnProtocol.SSH_SSL_PAYLOAD -> connectSSH(context, profile)
            VpnProtocol.V2RAY_VMESS,
            VpnProtocol.V2RAY_VLESS,
            VpnProtocol.V2RAY_TROJAN   -> connectV2Ray(context, profile)
            VpnProtocol.SLOWDNS         -> connectSlowDNS(context, profile)
            VpnProtocol.UDP_HYSTERIA    -> connectHysteria(context, profile)
            VpnProtocol.SHADOWSOCKS     -> connectShadowsocks(context, profile)
            VpnProtocol.WIREGUARD       -> connectWireGuard(context, profile)
        }
    }

    private suspend fun connectSSH(context: Context, profile: VpnProfile): Boolean {
        AppLogger.ssh("[SSH] Connexion vers ●●●:${profile.port}")
        val intent = Intent(context, SshTunnelService::class.java).apply {
            putExtra("profile", profile)
        }
        context.startService(intent)
        repeat(30) {
            delay(500)
            if (SshTunnelService.isConnected) { AppLogger.ssh("[SSH] ✓ Tunnel établi"); return true }
            if (SshTunnelService.lastError != null) { AppLogger.e("[SSH] ${SshTunnelService.lastError}"); return false }
        }
        return SshTunnelService.isConnected
    }

    private suspend fun connectV2Ray(context: Context, profile: VpnProfile): Boolean {
        AppLogger.vpn("[V2Ray] Démarrage ${profile.protocol}")
        val config = V2RayConfigBuilder.build(profile)
        context.startService(Intent(context, ShadowVpnService::class.java).apply {
            putExtra("config", config); putExtra("protocol", "v2ray")
        })
        delay(3000)
        return true
    }

    private suspend fun connectSlowDNS(context: Context, profile: VpnProfile): Boolean {
        AppLogger.vpn("[SlowDNS] Démarrage")
        context.startService(Intent(context, ShadowVpnService::class.java).apply {
            putExtra("profile", profile); putExtra("protocol", "slowdns")
        })
        delay(3000)
        return true
    }

    private suspend fun connectHysteria(context: Context, profile: VpnProfile): Boolean {
        AppLogger.vpn("[Hysteria] Démarrage UDP")
        context.startService(Intent(context, ShadowVpnService::class.java).apply {
            putExtra("profile", profile); putExtra("protocol", "hysteria")
        })
        delay(2000)
        return true
    }

    private suspend fun connectShadowsocks(context: Context, profile: VpnProfile): Boolean {
        AppLogger.vpn("[SS] Démarrage Shadowsocks")
        context.startService(Intent(context, ShadowVpnService::class.java).apply {
            putExtra("profile", profile); putExtra("protocol", "shadowsocks")
        })
        delay(2000)
        return true
    }

    private suspend fun connectWireGuard(context: Context, profile: VpnProfile): Boolean {
        AppLogger.vpn("[WG] Démarrage WireGuard")
        context.startService(Intent(context, ShadowVpnService::class.java).apply {
            putExtra("profile", profile); putExtra("protocol", "wireguard")
        })
        delay(2000)
        return true
    }

    private fun startStatsUpdater() {
        statsJob = scope.launch {
            while (isConnected.get()) {
                val duration = (System.currentTimeMillis() - connectionStartTime) / 1000
                _stats.value = ConnectionStats(
                    uploadBytes   = ShadowVpnService.uploadBytes,
                    downloadBytes = ShadowVpnService.downloadBytes,
                    durationSeconds = duration,
                    ping          = ShadowVpnService.lastPing,
                    isConnected   = true
                )
                // Log stats toutes les 60 secondes
                if (duration % 60 == 0L) {
                    AppLogger.stats(
                        "[STATS] ↓ ${ShadowVpnService.downloadBytes/1048576}MB " +
                        "↑ ${ShadowVpnService.uploadBytes/1048576}MB " +
                        "Ping:${ShadowVpnService.lastPing}ms"
                    )
                }
                delay(1000)
            }
        }
    }

    private fun scheduleReconnectIfNeeded(context: Context) {
        if (PrefsManager.autoConnect && reconnectAttempts < 5) {
            reconnectAttempts++
            AutoReconnectManager.scheduleReconnect(context, reconnectAttempts)
        }
    }

    fun onServiceStopped() {
        if (_state.value == VpnState.CONNECTED) {
            isConnected.set(false)
            statsJob?.cancel()
            _state.postValue(VpnState.DISCONNECTED)
            _stats.postValue(ConnectionStats())
            AppLogger.w("[VPN] Service arrêté de manière inattendue")
        }
    }

    fun getCurrentProfile(): VpnProfile? = currentProfile
}
