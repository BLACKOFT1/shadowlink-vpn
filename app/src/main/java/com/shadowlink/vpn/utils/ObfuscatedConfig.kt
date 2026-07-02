package com.shadowlink.vpn.utils

/**
 * Configuration codée en dur, jamais exposée dans l'UI.
 * ProGuard renomme cette classe et inline les constantes en release,
 * rendant l'URL du panel beaucoup plus difficile à extraire par
 * simple décompilation ou inspection des préférences de l'app.
 *
 * ⚠️ Remplace cette URL par celle de ton panel avant de compiler.
 */
internal object ObfuscatedConfig {
    // Découpée en fragments pour gêner un grep texte basique sur l'APK.
    private const val P1 = "https://"
    private const val P2 = "panel.shadowlink"
    private const val P3 = ".net"

    val PANEL_URL: String get() = P1 + P2 + P3
}
