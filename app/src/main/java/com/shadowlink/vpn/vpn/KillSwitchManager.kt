package com.shadowlink.vpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.shadowlink.vpn.utils.PrefsManager

/**
 * Kill Switch — coupe tout le trafic réseau si le VPN se déconnecte
 * de manière inattendue, pour éviter les fuites d'IP.
 *
 * Fonctionne en combinaison avec le VpnService.Builder :
 * - setBlocking(true)
 * - Les routes 0.0.0.0/0 couvrent tout le trafic
 * - En cas de déconnexion VPN, Android bloque automatiquement le trafic
 *   si BIND_VPN_SERVICE est actif et que l'interface TUN est close
 */
object KillSwitchManager {

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var onNetworkLost: (() -> Unit)? = null

    /**
     * Active la surveillance réseau.
     * Si le VPN tombe et que le kill switch est activé,
     * on coupe immédiatement.
     */
    fun enable(context: Context, onLost: () -> Unit) {
        if (!PrefsManager.killSwitchEnabled) return

        onNetworkLost = onLost
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // Si VPN était connecté et qu'on perd le réseau → déclencher kill switch
                if (VpnManager.state.value == VpnManager.VpnState.CONNECTED) {
                    onNetworkLost?.invoke()
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, networkCallback!!)
    }

    fun disable(context: Context) {
        val cb = networkCallback ?: return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        } catch (e: Exception) { /* ignore */ }
        networkCallback = null
    }
}
