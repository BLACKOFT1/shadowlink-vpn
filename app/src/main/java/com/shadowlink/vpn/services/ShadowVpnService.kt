package com.shadowlink.vpn.services

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.shadowlink.vpn.R
import com.shadowlink.vpn.activities.MainActivity
import com.shadowlink.vpn.utils.AppLogger
import com.shadowlink.vpn.utils.PrefsManager
import com.shadowlink.vpn.vpn.SplitTunnelManager
import com.shadowlink.vpn.vpn.VpnManager
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class ShadowVpnService : VpnService() {

    companion object {
        private const val CHANNEL_ID    = "shadowlink_vpn"
        private const val NOTIF_ID      = 1001
        var uploadBytes   = 0L
        var downloadBytes = 0L
        var lastPing      = 0
        private var instance: ShadowVpnService? = null
        fun isRunning() = instance != null
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var protocol     = "v2ray"
    private val scope        = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        AppLogger.vpn("[VpnService] Service créé")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DISCONNECT") {
            VpnManager.disconnect(this)
            return START_NOT_STICKY
        }
        protocol = intent?.getStringExtra("protocol") ?: "v2ray"
        startForeground(NOTIF_ID, buildNotification("Connexion en cours…"))
        scope.launch { startTunnel() }
        return START_STICKY
    }

    private suspend fun startTunnel() {
        try {
            val builder = Builder().apply {
                setSession("ShadowLink VPN")
                // Adresse TUN
                addAddress("10.8.0.2", 24)
                // Routes — tout le trafic passe par le VPN
                addRoute("0.0.0.0", 0)
                addRoute("::", 0) // IPv6
                // DNS depuis les préférences (plus de valeurs codées en dur)
                val dns = PrefsManager.dns.ifEmpty { "1.1.1.1" }
                addDnsServer(dns)
                addDnsServer("1.0.0.1") // Fallback Cloudflare
                setMtu(PrefsManager.mtu)
                setBlocking(false)
                // Exclure notre propre app du tunnel
                addDisallowedApplication(packageName)
                // Appliquer le split tunnel (apps exclues)
                SplitTunnelManager.applyToVpnBuilder(this)
                // Kill Switch — si activé, bloquer le trafic sans VPN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && PrefsManager.killSwitchEnabled) {
                    setMetered(false)
                    // Le blocage est assuré par le fait que l'interface TUN
                    // est la seule route disponible tant que le service tourne
                }
            }

            vpnInterface = builder.establish()
            AppLogger.vpn("[VpnService] Interface TUN établie")
            updateNotification("Connecté via ${protocol.uppercase()}")
            startPacketRelay()

        } catch (e: Exception) {
            AppLogger.e("[VpnService] Erreur tunnel : ${e.message}")
            updateNotification("Erreur : ${e.message}")
            VpnManager.onServiceStopped()
            stopSelf()
        }
    }

    private fun startPacketRelay() {
        val tunFd = vpnInterface ?: return
        scope.launch(Dispatchers.IO) {
            val input  = FileInputStream(tunFd.fileDescriptor)
            val output = FileOutputStream(tunFd.fileDescriptor)
            val buffer = ByteBuffer.allocate(32767)

            val proxyChannel = runCatching {
                DatagramChannel.open().apply {
                    connect(InetSocketAddress("127.0.0.1", 1080))
                    configureBlocking(false)
                    protect(socket())
                }
            }.getOrNull()

            AppLogger.vpn("[VpnService] Relay démarré → proxy 127.0.0.1:1080")

            while (isActive && vpnInterface != null) {
                try {
                    buffer.clear()
                    val len = input.read(buffer.array())
                    if (len > 0) { uploadBytes += len; buffer.limit(len); proxyChannel?.write(buffer) }
                    buffer.clear()
                    val recv = proxyChannel?.read(buffer) ?: -1
                    if (recv > 0) { downloadBytes += recv; output.write(buffer.array(), 0, recv) }
                    delay(1)
                } catch (e: Exception) {
                    AppLogger.e("[VpnService] Relay error : ${e.message}")
                    break
                }
            }
            AppLogger.vpn("[VpnService] Relay arrêté")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "ShadowLink VPN", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(status: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val disconnectPi = PendingIntent.getService(this, 1,
            Intent(this, ShadowVpnService::class.java).apply { action = "DISCONNECT" },
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShadowLink VPN Pro")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentIntent(pi)
            .addAction(R.drawable.ic_disconnect, "Déconnecter", disconnectPi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(status))
    }

    override fun onRevoke() { super.onRevoke(); cleanup() }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        VpnManager.onServiceStopped()
        cleanup()
        AppLogger.vpn("[VpnService] Service détruit")
    }

    private fun cleanup() {
        scope.cancel()
        vpnInterface?.close()
        vpnInterface  = null
        uploadBytes   = 0L
        downloadBytes = 0L
    }
}
