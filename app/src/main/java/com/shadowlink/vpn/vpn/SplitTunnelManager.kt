package com.shadowlink.vpn.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.shadowlink.vpn.utils.PrefsManager

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val isExcluded: Boolean
)

/**
 * Split Tunnel — permet d'exclure certaines apps du tunnel VPN.
 * Ces apps utiliseront la connexion Internet directe (sans VPN).
 * Utile pour les apps bancaires, streaming géolocalisé, etc.
 */
object SplitTunnelManager {

    /**
     * Retourne la liste de toutes les apps installées
     * avec leur statut d'exclusion.
     */
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm          = context.packageManager
        val excluded    = PrefsManager.splitTunnelApps
        val packages    = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return packages
            .filter { it.packageName != context.packageName } // exclure notre app
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    appName     = pm.getApplicationLabel(info).toString(),
                    isSystem    = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isExcluded  = info.packageName in excluded
                )
            }
            .sortedWith(compareBy({ it.isSystem }, { it.appName }))
    }

    /**
     * Ajoute une app à la liste d'exclusion (bypass VPN).
     */
    fun excludeApp(packageName: String) {
        val current = PrefsManager.splitTunnelApps.toMutableSet()
        current.add(packageName)
        PrefsManager.splitTunnelApps = current
    }

    /**
     * Retire une app de la liste d'exclusion (inclure dans VPN).
     */
    fun includeApp(packageName: String) {
        val current = PrefsManager.splitTunnelApps.toMutableSet()
        current.remove(packageName)
        PrefsManager.splitTunnelApps = current
    }

    fun isExcluded(packageName: String): Boolean =
        packageName in PrefsManager.splitTunnelApps

    fun clearAll() {
        PrefsManager.splitTunnelApps = emptySet()
    }

    /**
     * Applique le split tunnel au builder VPN.
     * Les apps exclues passent par la connexion directe.
     */
    fun applyToVpnBuilder(builder: android.net.VpnService.Builder) {
        PrefsManager.splitTunnelApps.forEach { pkg ->
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                // App désinstallée — ignorer
            }
        }
    }
}
