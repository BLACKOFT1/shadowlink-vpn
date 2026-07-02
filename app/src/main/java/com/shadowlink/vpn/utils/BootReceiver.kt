package com.shadowlink.vpn.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shadowlink.vpn.utils.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PrefsManager.autoConnect && PrefsManager.isLoggedIn) {
                val profiles = PrefsManager.allProfiles
                if (profiles.isNotEmpty()) {
                    // L'auto-connect via VPN nécessite une interaction utilisateur pour la permission VPN
                    // donc on affiche juste une notification pour inviter l'utilisateur
                }
            }
        }
    }
}
