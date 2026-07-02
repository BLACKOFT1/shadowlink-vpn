package com.shadowlink.vpn.utils

import android.content.Context
import com.shadowlink.vpn.network.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress

/**
 * Validateur de temps anti-fraude.
 *
 * Problème résolu : un utilisateur pourrait reculer l'horloge de son
 * téléphone pour faire croire que son temps VPN gratuit (reward ads)
 * n'a pas encore expiré, et ainsi obtenir un accès illimité.
 *
 * Solution : on ne fait JAMAIS confiance à System.currentTimeMillis()
 * pour calculer les expirations. On utilise :
 *  1. L'heure du serveur panel (source de vérité principale)
 *  2. Un serveur NTP public en secours si le panel est injoignable
 *  3. On calcule un "offset" entre l'heure système et l'heure réelle,
 *     puis on l'applique à tous les calculs de temps restant.
 */
object NtpTimeValidator {

    // Pool de serveurs NTP publics (secours si panel injoignable)
    private val NTP_POOLS = listOf(
        "pool.ntp.org",
        "time.google.com",
        "time.cloudflare.com"
    )

    private var serverTimeOffsetMs: Long = 0L  // serverTime - localTime
    private var lastSyncAt: Long = 0L
    private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L  // re-sync toutes les 15 min
    private const val MAX_DRIFT_MS = 5 * 60 * 1000L       // tolérance 5 min

    var isClockTampered = false
        private set

    /**
     * Synchronise l'heure avec le serveur panel, avec repli NTP.
     * À appeler au démarrage et périodiquement.
     */
    suspend fun syncServerTime(): Boolean = withContext(Dispatchers.IO) {
        // 1. Essayer via l'API du panel
        val panelSynced = runCatching { syncFromPanel() }.getOrDefault(false)
        if (panelSynced) return@withContext true

        // 2. Repli sur NTP public
        return@withContext runCatching { syncFromNtp() }.getOrDefault(false)
    }

    private fun syncFromPanel(): Boolean {
        val url = PrefsManager.panelUrl
        if (url.isEmpty()) return false

        val client = PinnedHttpClient.build()
        val request = Request.Builder().url("$url/api/app/time").get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return false

        val body = response.body?.string() ?: return false
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: return false
        val serverTimestamp = data.optLong("serverTimestamp", 0) * 1000L
        if (serverTimestamp <= 0) return false

        applyOffset(serverTimestamp)
        return true
    }

    private fun syncFromNtp(): Boolean {
        // Implémentation simplifiée — vérifie juste la résolvabilité DNS
        // d'un pool NTP et utilise l'heure réseau via une requête HTTPS
        // vers un service de temps fiable comme repli ultime.
        for (host in NTP_POOLS) {
            val resolved = runCatching { InetAddress.getByName(host) }.getOrNull()
            if (resolved != null) {
                // Utiliser un endpoint HTTPS de temps comme repli pratique
                val ok = runCatching {
                    val client = okhttp3.OkHttpClient()
                    val req = Request.Builder().url("https://worldtimeapi.org/api/timezone/Etc/UTC").build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string() ?: "")
                        val unixTime = json.optLong("unixtime", 0) * 1000L
                        if (unixTime > 0) { applyOffset(unixTime); true } else false
                    } else false
                }.getOrDefault(false)
                if (ok) return true
            }
        }
        return false
    }

    private fun applyOffset(serverTimeMs: Long) {
        val localTimeMs = System.currentTimeMillis()
        val newOffset = serverTimeMs - localTimeMs

        // Détecter une manipulation grossière de l'horloge locale
        if (lastSyncAt > 0) {
            val previousDrift = kotlin.math.abs(newOffset - serverTimeOffsetMs)
            if (previousDrift > MAX_DRIFT_MS) {
                isClockTampered = true
                AppLogger.w("[NTP] Dérive d'horloge suspecte détectée : ${previousDrift/1000}s")
            }
        }

        serverTimeOffsetMs = newOffset
        lastSyncAt = System.currentTimeMillis()
        AppLogger.i("[NTP] Heure synchronisée — offset: ${newOffset}ms")
    }

    /**
     * Retourne l'heure "réelle" (corrigée), à utiliser PARTOUT à la place
     * de System.currentTimeMillis() pour les calculs de reward/expiration.
     */
    fun now(): Long {
        // Re-sync automatique si trop vieux
        if (System.currentTimeMillis() - lastSyncAt > SYNC_INTERVAL_MS) {
            // Sync asynchrone en arrière-plan — ne bloque pas l'appel
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { syncServerTime() }
            }
        }
        return System.currentTimeMillis() + serverTimeOffsetMs
    }

    /** Indique si on a au moins une synchronisation valide */
    fun hasValidSync(): Boolean = lastSyncAt > 0
}
