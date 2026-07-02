package com.shadowlink.vpn.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shadowlink.vpn.R
import com.shadowlink.vpn.network.ApiClient
import com.shadowlink.vpn.utils.AntiSniffGuard
import com.shadowlink.vpn.utils.NtpTimeValidator
import com.shadowlink.vpn.utils.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            delay(1200)

            // ── 1. Vérification anti-sniffing (bloquant) ──
            val sniffResult = AntiSniffGuard.scan(this@SplashActivity)
            if (!sniffResult.isSafe) {
                val intent = Intent(this@SplashActivity, AntiSniffActivity::class.java).apply {
                    putStringArrayListExtra("detected_apps", ArrayList(sniffResult.detectedApps))
                }
                startActivity(intent)
                finish()
                return@launch
            }

            // ── 2. Synchronisation horaire NTP (anti-fraude rewards) ──
            NtpTimeValidator.syncServerTime()

            delay(600)

            // ── 3. Session check silencieux ──
            checkSession()
        }
    }

    private suspend fun checkSession() {
        if (PrefsManager.isLoggedIn) {
            val result = ApiClient.getUserInfo()
            if (result.isSuccess) {
                PrefsManager.userInfo = result.getOrNull()
            } else {
                PrefsManager.logout()
            }
        }
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
