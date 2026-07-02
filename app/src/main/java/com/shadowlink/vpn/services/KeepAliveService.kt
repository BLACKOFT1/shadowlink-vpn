package com.shadowlink.vpn.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.shadowlink.vpn.vpn.VpnManager
import kotlinx.coroutines.*

class KeepAliveService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            while (true) {
                delay(30_000) // Vérification toutes les 30 secondes
                checkAndRestore()
            }
        }
        return START_STICKY
    }

    private fun checkAndRestore() {
        val state = VpnManager.state.value
        // Si on était connecté mais le service s'est arrêté → signaler
        if (state == VpnManager.VpnState.DISCONNECTED) {
            VpnManager.onServiceStopped()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
