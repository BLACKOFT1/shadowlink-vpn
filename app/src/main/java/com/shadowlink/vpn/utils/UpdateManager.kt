package com.shadowlink.vpn.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.shadowlink.vpn.BuildConfig
import com.shadowlink.vpn.models.AppUpdate
import com.shadowlink.vpn.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UpdateManager {

    private var downloadId = -1L

    /**
     * Vérifie s'il y a une mise à jour disponible
     */
    suspend fun checkForUpdate(): AppUpdate? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val result = ApiClient.checkUpdate(BuildConfig.VERSION_CODE)
                val update = result.getOrNull()
                if (update != null && update.versionCode > BuildConfig.VERSION_CODE) {
                    update
                } else null
            }.getOrNull()
        }
    }

    /**
     * Télécharge et installe le nouvel APK
     */
    fun downloadAndInstall(context: Context, update: AppUpdate, onProgress: (Int) -> Unit = {}) {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl)).apply {
            setTitle("ShadowLink VPN Pro - Mise à jour ${update.latestVersion}")
            setDescription("Téléchargement en cours...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "shadowlink_update_${update.versionCode}.apk"
            )
            setMimeType("application/vnd.android.package-archive")
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        // Observer la progression
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    installApk(context, update)
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    private fun installApk(context: Context, update: AppUpdate) {
        val apkFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "shadowlink_update_${update.versionCode}.apk"
        )

        if (!apkFile.exists()) return

        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }

    fun getDownloadProgress(context: Context): Int {
        if (downloadId == -1L) return 0
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)
        return if (cursor.moveToFirst()) {
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            cursor.close()
            if (total > 0) ((downloaded * 100) / total).toInt() else 0
        } else {
            cursor.close()
            0
        }
    }
}
