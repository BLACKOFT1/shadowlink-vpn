package com.shadowlink.vpn.vpn

import android.content.Context
import androidx.work.*
import com.shadowlink.vpn.utils.AppLogger
import com.shadowlink.vpn.utils.PrefsManager
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Reconnexion automatique via WorkManager.
 * Si le VPN se déconnecte de manière inattendue et que
 * autoConnect est activé, une tentative de reconnexion
 * est planifiée toutes les 30 secondes (max 5 tentatives).
 */
class AutoReconnectWorker(
    context: Context,
    params:  WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val attempt = inputData.getInt("attempt", 1)
        AppLogger.vpn("[AutoReconnect] Tentative $attempt/5…")

        if (!PrefsManager.autoConnect) {
            AppLogger.vpn("[AutoReconnect] Désactivé — abandon")
            return Result.success()
        }

        if (VpnManager.state.value == VpnManager.VpnState.CONNECTED) {
            AppLogger.vpn("[AutoReconnect] Déjà connecté")
            return Result.success()
        }

        val profiles = PrefsManager.allProfiles
        if (profiles.isEmpty()) {
            AppLogger.vpn("[AutoReconnect] Aucun profil disponible")
            return Result.failure()
        }

        val lastId  = PrefsManager.lastUsedProfileId
        val profile = profiles.find { it.id == lastId } ?: profiles.first()

        delay(3000) // Délai avant tentative

        // La connexion nécessite le contexte d'une Activity pour la permission VPN.
        // On se contente ici de notifier — l'utilisateur verra une notification.
        AutoReconnectManager.scheduleNotification(applicationContext, profile.name, attempt)

        return if (attempt < 5) Result.retry() else Result.failure()
    }
}

object AutoReconnectManager {

    private const val WORK_TAG = "shadowlink_auto_reconnect"

    fun scheduleReconnect(context: Context, attempt: Int = 1) {
        if (!PrefsManager.autoConnect) return

        val data = workDataOf("attempt" to attempt)

        val request = OneTimeWorkRequestBuilder<AutoReconnectWorker>()
            .setInputData(data)
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_TAG, ExistingWorkPolicy.REPLACE, request)

        AppLogger.vpn("[AutoReconnect] Reconnexion planifiée dans 30s (tentative $attempt)")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        AppLogger.vpn("[AutoReconnect] Annulé")
    }

    fun scheduleNotification(context: Context, profileName: String, attempt: Int) {
        // Notification pour informer l'utilisateur de la reconnexion
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "reconnect", "Reconnexion VPN",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notifManager.createNotificationChannel(channel)
        }

        val notif = androidx.core.app.NotificationCompat.Builder(context, "reconnect")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ShadowLink VPN — Reconnexion")
            .setContentText("Tentative $attempt/5 → $profileName")
            .setAutoCancel(true)
            .build()

        notifManager.notify(2001, notif)
    }
}
