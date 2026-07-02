package com.shadowlink.vpn.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.shadowlink.vpn.BuildConfig
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Système de logs centralisé.
 * - En DEBUG : logs dans logcat + mémoire
 * - En RELEASE : logs en mémoire seulement (jamais dans logcat)
 * - Les IPs et credentials sont automatiquement masqués
 * - Observable via LiveData pour le fragment Logs
 */
object AppLogger {

    private const val TAG      = "ShadowLink"
    private const val MAX_LOGS = 500

    enum class Level { DEBUG, INFO, WARN, ERROR, VPN, SSH, STATS }

    data class LogEntry(
        val timestamp: String,
        val level: Level,
        val message: String
    )

    private val _logs = MutableLiveData<List<LogEntry>>(emptyList())
    val logs: LiveData<List<LogEntry>> = _logs

    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val fmt    = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun d(msg: String) = log(Level.DEBUG, msg)
    fun i(msg: String) = log(Level.INFO,  msg)
    fun w(msg: String) = log(Level.WARN,  msg)
    fun e(msg: String) = log(Level.ERROR, msg)
    fun vpn(msg: String) = log(Level.VPN, msg)
    fun ssh(msg: String) = log(Level.SSH, msg)
    fun stats(msg: String) = log(Level.STATS, msg)

    private fun log(level: Level, rawMessage: String) {
        val safeMsg = sanitize(rawMessage)
        val entry   = LogEntry(fmt.format(Date()), level, safeMsg)

        // Logcat seulement en debug
        if (BuildConfig.DEBUG) {
            when (level) {
                Level.ERROR -> Log.e(TAG, safeMsg)
                Level.WARN  -> Log.w(TAG, safeMsg)
                else        -> Log.d(TAG, safeMsg)
            }
        }

        // Buffer circulaire
        buffer.addLast(entry)
        while (buffer.size > MAX_LOGS) buffer.pollFirst()

        // Notifier le fragment Logs
        _logs.postValue(buffer.toList())
    }

    /**
     * Masque les données sensibles dans les messages de log.
     * IPs, passwords, UUIDs, tokens sont remplacés.
     */
    private fun sanitize(msg: String): String {
        var s = msg
        // IPs publiques → masquer les 2 derniers octets
        s = s.replace(Regex("(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}")) { m ->
            val parts = m.value.split(".")
            if (isPrivateIp(m.value)) m.value  // garder les IPs privées lisibles
            else "${parts[0]}.${parts[1]}.●●●.●●●"
        }
        // UUIDs
        s = s.replace(
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
            "●●●●●●●●-UUID-MASQUÉ"
        )
        // Tokens JWT / Bearer (64+ chars hex)
        s = s.replace(Regex("[0-9a-fA-F]{40,}"), "●●●TOKEN●●●")
        // Passwords dans les URLs
        s = s.replace(Regex("password=[^&\\s]+"), "password=●●●●●●●●")
        return s
    }

    private fun isPrivateIp(ip: String): Boolean {
        return ip.startsWith("10.") ||
               ip.startsWith("192.168.") ||
               ip.startsWith("172.") ||
               ip.startsWith("127.")
    }

    fun clearLogs() {
        buffer.clear()
        _logs.postValue(emptyList())
    }

    fun getLogsAsText(): String =
        buffer.joinToString("\n") { "[${it.timestamp}][${it.level}] ${it.message}" }
}
