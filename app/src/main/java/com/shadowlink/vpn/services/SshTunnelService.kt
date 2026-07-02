package com.shadowlink.vpn.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.shadowlink.vpn.models.VpnProfile
import com.shadowlink.vpn.models.VpnProtocol
import com.shadowlink.vpn.vpn.VpnManager
import kotlinx.coroutines.*

class SshTunnelService : Service() {

    companion object {
        var isConnected = false
        var lastError: String? = null
        private const val LOCAL_SOCKS_PORT = 1080
        private const val LOCAL_HTTP_PORT = 8080
    }

    private var sshSession: Session? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastError = null
        isConnected = false

        val profile = intent?.getParcelableExtra<VpnProfile>("profile")
        if (profile == null) {
            lastError = "Profil invalide"
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            connectSSH(profile)
        }

        return START_STICKY
    }

    private suspend fun connectSSH(profile: VpnProfile) {
        try {
            val jsch = JSch()

            // Clé privée si disponible
            if (profile.sshPrivateKey.isNotEmpty()) {
                val keyBytes = profile.sshPrivateKey.toByteArray(Charsets.UTF_8)
                jsch.addIdentity("key", keyBytes, null, null)
            }

            val protocol = runCatching { VpnProtocol.valueOf(profile.protocol) }.getOrNull()

            val session = when (protocol) {
                VpnProtocol.SSH_PROXY -> {
                    // SSH via proxy HTTP
                    val jschSession = jsch.getSession(profile.sshUser, profile.host, profile.port)
                    jschSession.setPassword(profile.sshPass)
                    jschSession.setConfig("StrictHostKeyChecking", "no")
                    jschSession.setConfig("PreferredAuthentications", "password,publickey")
                    if (profile.sshProxyHost.isNotEmpty()) {
                        jschSession.setProxy(
                            com.jcraft.jsch.ProxyHTTP(profile.sshProxyHost, profile.sshProxyPort)
                        )
                    }
                    jschSession
                }
                VpnProtocol.SSH_SSL -> {
                    // SSH encapsulé dans SSL (stunnel-like)
                    val jschSession = jsch.getSession(profile.sshUser, "127.0.0.1", profile.sshSslPort)
                    jschSession.setPassword(profile.sshPass)
                    jschSession.setConfig("StrictHostKeyChecking", "no")
                    jschSession
                }
                else -> {
                    // SSH Direct
                    val jschSession = jsch.getSession(profile.sshUser, profile.host, profile.port)
                    jschSession.setPassword(profile.sshPass)
                    jschSession.setConfig("StrictHostKeyChecking", "no")
                    jschSession.setConfig("PreferredAuthentications", "password,publickey")
                    jschSession
                }
            }

            session.connect(10000)

            // SOCKS5 dynamic forwarding
            session.setPortForwardingL(LOCAL_SOCKS_PORT, "127.0.0.1", LOCAL_SOCKS_PORT)

            sshSession = session
            isConnected = true

            // Démarrer le VPN TUN pour router le trafic
            // Le VpnService va lire le port SOCKS local

            // Garder la connexion vivante
            keepAlive()

        } catch (e: Exception) {
            lastError = e.message
            isConnected = false
            VpnManager.onServiceStopped()
        }
    }

    private suspend fun keepAlive() {
        while (isConnected && sshSession?.isConnected == true) {
            delay(30000)
            try {
                sshSession?.sendKeepAliveMsg()
            } catch (e: Exception) {
                isConnected = false
                lastError = "Connexion perdue"
                VpnManager.onServiceStopped()
                break
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        isConnected = false
        try {
            sshSession?.disconnect()
        } catch (e: Exception) { /* ignore */ }
    }
}
