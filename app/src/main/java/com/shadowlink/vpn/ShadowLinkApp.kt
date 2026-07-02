package com.shadowlink.vpn

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.google.android.gms.ads.MobileAds
import com.shadowlink.vpn.utils.PrefsManager
import com.shadowlink.vpn.utils.SecurityUtils
import org.conscrypt.Conscrypt
import java.security.Security

class ShadowLinkApp : Application() {

    companion object {
        lateinit var instance: ShadowLinkApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install Conscrypt for modern TLS support
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        // Initialize AdMob
        MobileAds.initialize(this)

        // Initialize PrefsManager
        PrefsManager.init(this)

        // Apply stored theme (dark/light/system)
        com.shadowlink.vpn.utils.ThemeManager.applyStoredTheme()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
