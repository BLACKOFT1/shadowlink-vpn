package com.shadowlink.vpn.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.shadowlink.vpn.utils.AntiSniffGuard

/**
 * Écran de blocage affiché lorsque des applications de sniffing réseau
 * sont détectées sur l'appareil. L'utilisateur DOIT les désinstaller
 * pour pouvoir continuer à utiliser ShadowLink VPN Pro.
 */
class AntiSniffActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val detectedApps = intent.getStringArrayListExtra("detected_apps") ?: arrayListOf()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0A0E1A.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 80, 56, 56)
        }

        // Icône d'alerte
        val icon = TextView(this).apply {
            text = "🛡️"
            textSize = 56f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
        }

        val title = TextView(this).apply {
            text = "Sécurité compromise détectée"
            textSize = 19f
            setTextColor(0xFFFF5252.toInt())
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
        }

        val subtitle = TextView(this).apply {
            text = "Des applications capables d'intercepter et d'analyser votre trafic réseau ont été détectées. " +
                   "Pour votre sécurité et celle du service, vous devez les désinstaller avant de continuer."
            textSize = 13f
            setTextColor(0xFF8B9CC8.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 28 }
        }

        val listLabel = TextView(this).apply {
            text = "APPLICATIONS DÉTECTÉES"
            textSize = 11f
            setTextColor(0xFF3B8AFF.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        detectedApps.forEach { appName ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(0xFF141C35.toInt())
                setPadding(28, 24, 28, 24)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
            }
            val warnIcon = TextView(this).apply { text = "⚠️ "; textSize = 14f }
            val name = TextView(this).apply {
                text = appName
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            row.addView(warnIcon)
            row.addView(name)
            listContainer.addView(row)
        }

        val btnRecheck = Button(this).apply {
            text = "✅ J'AI TOUT DÉSINSTALLÉ — VÉRIFIER"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1E6FE8.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 140).apply { topMargin = 28; bottomMargin = 12 }
            setOnClickListener { recheck() }
        }

        val btnSettings = Button(this).apply {
            text = "OUVRIR LES PARAMÈTRES D'APPLICATIONS"
            setTextColor(0xFF3B8AFF.toInt())
            setBackgroundColor(0xFF141C35.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 130)
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS))
            }
        }

        root.addView(icon)
        root.addView(title)
        root.addView(subtitle)
        root.addView(listLabel)
        root.addView(listContainer)
        root.addView(btnRecheck)
        root.addView(btnSettings)
        scroll.addView(root)
        setContentView(scroll)

        // Empêcher le retour arrière — l'utilisateur ne peut pas contourner cet écran
    }

    private fun recheck() {
        val result = AntiSniffGuard.scan(this)
        if (result.isSafe) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            Toast.makeText(
                this,
                "Encore ${result.detectedApps.size} application(s) détectée(s). Désinstallez-les toutes.",
                Toast.LENGTH_LONG
            ).show()
            recreate()
        }
    }

    override fun onBackPressed() {
        // Bloqué intentionnellement — l'utilisateur doit résoudre le problème
        moveTaskToBack(true)
    }
}
