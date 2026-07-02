package com.shadowlink.vpn.utils

import androidx.appcompat.app.AppCompatDelegate

/**
 * Gestionnaire de thème — bascule entre mode sombre (par défaut)
 * et mode clair, persisté dans les préférences chiffrées.
 */
object ThemeManager {

    const val MODE_DARK   = 0
    const val MODE_LIGHT  = 1
    const val MODE_SYSTEM = 2

    fun applyTheme(mode: Int) {
        val nightMode = when (mode) {
            MODE_DARK   -> AppCompatDelegate.MODE_NIGHT_YES
            MODE_LIGHT  -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else        -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        PrefsManager.themeMode = mode
    }

    fun currentMode(): Int = PrefsManager.themeMode

    fun applyStoredTheme() {
        applyTheme(PrefsManager.themeMode)
    }

    fun modeLabel(mode: Int): String = when (mode) {
        MODE_DARK   -> "Sombre"
        MODE_LIGHT  -> "Clair"
        MODE_SYSTEM -> "Système"
        else        -> "Sombre"
    }
}
