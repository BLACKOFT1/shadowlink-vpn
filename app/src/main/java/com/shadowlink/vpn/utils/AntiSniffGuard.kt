package com.shadowlink.vpn.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Anti-Sniffing Guard — empêche le lancement de l'app si des outils
 * de capture/analyse réseau sont installés (style HTTP Injector).
 *
 * Détecte : PCAPdroid, Packet Capture, HTTP Toolkit, Wireshark mobile,
 * Charles Proxy, Fiddler, mitmproxy apps, et autres sniffers connus.
 */
object AntiSniffGuard {

    /** Packages connus d'outils de capture/sniff réseau */
    private val BLOCKED_PACKAGES = listOf(
        "app.greyshirts.sslcapture",
        "com.emanuelef.remote_capture",       // PCAPdroid
        "com.minhui.networkcapture",          // Packet Capture
        "app.intra",
        "com.guardsquare.dexguard.tester",
        "com.httptoolkit.android",            // HTTP Toolkit
        "com.wireshark.android",
        "de.measite.minimaxi",
        "com.minhui.packetcapture",
        "com.lipisoft.sslcapture",
        "com.android.proxy",
        "org.proxydroid",
        "com.github.megatronking.netbare",
        "com.charlesproxy.android",
        "io.github.zhongjihua.packetcapture",
        "com.evolly.app.networktools",        // peut inclure sniffers
        "com.minhui.android.fingdevice",
        "com.crackerapps.android.spcheck",
        "ru.maximoff.apptracker",
        "com.tracesoft.qustodio",
        "net.typeblog.shelter"                // contournement éventuel
    )

    /** Signatures de paquets (préfixes) susceptibles d'être des sniffers */
    private val SUSPICIOUS_PREFIXES = listOf(
        "com.network.sniffer",
        "com.packet.capture",
        "com.mitm",
        "com.proxydroid"
    )

    data class ScanResult(
        val isSafe: Boolean,
        val detectedApps: List<String>
    )

    /**
     * Scanne les apps installées à la recherche d'outils de sniffing.
     * À appeler au démarrage de l'app (Splash) et avant chaque connexion VPN.
     */
    fun scan(context: Context): ScanResult {
        val pm = context.packageManager
        val detected = mutableListOf<String>()

        val installedPackages = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        for (app in installedPackages) {
            val pkg = app.packageName.lowercase()

            // Correspondance exacte
            if (pkg in BLOCKED_PACKAGES) {
                detected.add(getAppLabel(pm, app.packageName))
                continue
            }

            // Correspondance par préfixe suspect
            if (SUSPICIOUS_PREFIXES.any { pkg.startsWith(it) }) {
                detected.add(getAppLabel(pm, app.packageName))
            }
        }

        return ScanResult(isSafe = detected.isEmpty(), detectedApps = detected)
    }

    /**
     * Vérifie si un VPN tiers ou un proxy système est déjà actif,
     * ce qui pourrait indiquer une interception du trafic.
     */
    fun isForeignVpnOrProxyActive(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            // Si un VPN externe (pas le nôtre) est actif
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                !ShadowVpnServiceRunning(context)
        } catch (e: Exception) { false }
    }

    private fun ShadowVpnServiceRunning(context: Context): Boolean {
        return com.shadowlink.vpn.services.ShadowVpnService.isRunning()
    }

    private fun getAppLabel(pm: PackageManager, packageName: String): String {
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) { packageName }
    }
}
