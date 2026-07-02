package com.shadowlink.vpn.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Certificate Pinning — empêche les attaques MITM.
 *
 * COMMENT OBTENIR LES PINS DE TON SERVEUR :
 * 1. Ouvre ton panel dans Chrome → icône cadenas → Certificat
 * 2. Exporte le certificat en .pem
 * 3. Lance : openssl x509 -in cert.pem -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
 * 4. Remplace les pins ci-dessous par les tiens
 *
 * Pour l'instant les pins sont vides → aucun pinning actif en développement.
 * À ACTIVER AVANT LA PUBLICATION.
 */
object PinnedHttpClient {

    // ⚠️ REMPLACE PAR LES VRAIS PINS DE TON CERTIFICAT AVANT PUBLICATION
    private val PANEL_PINS = listOf(
        // "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",  // Pin principal
        // "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",  // Pin de secours
    )

    fun build(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(buildAuthInterceptor())

        // Activer le certificate pinning uniquement si des pins sont configurés
        if (PANEL_PINS.isNotEmpty()) {
            val panelHost = extractHost(com.shadowlink.vpn.utils.PrefsManager.panelUrl)
            if (panelHost.isNotEmpty()) {
                val pinnersBuilder = CertificatePinner.Builder()
                for (pin in PANEL_PINS) {
                    pinnersBuilder.add(panelHost, pin)
                }
                val pinner = pinnersBuilder.build()
                builder.certificatePinner(pinner)
            }
        }

        // Logging en debug seulement — jamais en release
        if (com.shadowlink.vpn.BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    private fun buildAuthInterceptor() = okhttp3.Interceptor { chain ->
        val token  = com.shadowlink.vpn.utils.PrefsManager.authToken
        val hwid   = runCatching {
            val ctx = com.shadowlink.vpn.ShadowLinkApp.instance
            com.shadowlink.vpn.utils.SecurityUtils.getHWID(ctx)
        }.getOrDefault("")

        val request = chain.request().newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
            header("X-HWID",        hwid)
            header("X-App-Version", com.shadowlink.vpn.BuildConfig.VERSION_NAME)
            header("Content-Type",  "application/json")
        }.build()

        chain.proceed(request)
    }

    private fun extractHost(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (e: Exception) { "" }
    }
}
