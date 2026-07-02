package com.shadowlink.vpn.utils

import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private val themes = arrayOf("Système", "Clair", "Sombre")

    // 0 = System, 1 = Light, 2 = Dark
    fun currentMode(): Int {
        return PrefsManager.themeMode
    }

    fun modeLabel(mode: Int): String {
        return if (mode in themes.indices) themes[mode] else themes[0]
    }

    fun applyTheme(mode: Int) {
        PrefsManager.themeMode = mode
        when (mode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    fun applyStoredTheme() {
        val mode = PrefsManager.themeMode
        applyTheme(mode)
    }
}
