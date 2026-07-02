package com.shadowlink.vpn.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shadowlink.vpn.R
import com.shadowlink.vpn.vpn.AppInfo
import com.shadowlink.vpn.vpn.SplitTunnelManager
import kotlinx.coroutines.*

class SplitTunnelActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var searchView: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCount: TextView

    private var allApps:      List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout simple programmatique
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0E1A.toInt())
        }

        tvCount = TextView(this).apply {
            textSize  = 12f
            setTextColor(0xFF8B9CC8.toInt())
            setPadding(40, 20, 40, 8)
        }

        searchView = EditText(this).apply {
            hint      = "Rechercher une application…"
            setHintTextColor(0xFF4A5678.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF141C35.toInt())
            setPadding(40, 24, 40, 24)
            textSize  = 14f
        }

        progressBar = ProgressBar(this).apply {
            visibility   = View.VISIBLE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFF1E6FE8.toInt())
        }

        listView = ListView(this).apply {
            divider          = null
            dividerHeight    = 0
            setBackgroundColor(0xFF0A0E1A.toInt())
        }

        val btnClearAll = Button(this).apply {
            text      = "TOUT INCLURE (réinitialiser)"
            textSize  = 12f
            setTextColor(0xFFFF5252.toInt())
            setBackgroundColor(0xFF141C35.toInt())
            setPadding(40, 20, 40, 20)
            setOnClickListener {
                SplitTunnelManager.clearAll()
                loadApps()
            }
        }

        root.addView(searchView)
        root.addView(tvCount)
        root.addView(progressBar)
        root.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(btnClearAll)
        setContentView(root)

        supportActionBar?.apply {
            title = "Split Tunnel — Applications"
            setDisplayHomeAsUpEnabled(true)
        }

        setupSearch()
        loadApps()
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        listView.visibility    = View.GONE

        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) {
                SplitTunnelManager.getInstalledApps(this@SplitTunnelActivity)
            }
            filteredApps = allApps
            updateList()
            progressBar.visibility = View.GONE
            listView.visibility    = View.VISIBLE
        }
    }

    private fun setupSearch() {
        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase()
                filteredApps = if (q.isEmpty()) allApps
                               else allApps.filter { it.appName.lowercase().contains(q) || it.packageName.contains(q) }
                updateList()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateList() {
        val excludedCount = allApps.count { it.isExcluded }
        tvCount.text = "${allApps.size} applications — $excludedCount exclue(s) du VPN"

        val adapter = object : ArrayAdapter<AppInfo>(this, 0, filteredApps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val app = getItem(position)!!
                val row = convertView ?: LinearLayout(context).apply {
                    orientation  = LinearLayout.HORIZONTAL
                    setPadding(40, 20, 40, 20)
                    gravity      = android.view.Gravity.CENTER_VERTICAL
                }
                (row as LinearLayout).removeAllViews()

                val info = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                val tvName = TextView(context).apply {
                    text      = app.appName
                    textSize  = 13f
                    setTextColor(0xFFE8EDF5.toInt())
                }
                val tvPkg = TextView(context).apply {
                    text      = app.packageName
                    textSize  = 10f
                    setTextColor(0xFF4A5678.toInt())
                }
                if (app.isSystem) {
                    val tvSys = TextView(context).apply {
                        text      = "SYSTÈME"
                        textSize  = 9f
                        setTextColor(0xFF4A5678.toInt())
                    }
                    info.addView(tvSys)
                }
                info.addView(tvName)
                info.addView(tvPkg)

                val cb = CheckBox(context).apply {
                    isChecked        = app.isExcluded
                    buttonTintList   = android.content.res.ColorStateList.valueOf(0xFF1E6FE8.toInt())
                    text             = if (app.isExcluded) "Exclu (direct)" else "Via VPN"
                    setTextColor(if (app.isExcluded) 0xFFFF5252.toInt() else 0xFF3CF281.toInt())
                    textSize         = 10f
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) SplitTunnelManager.excludeApp(app.packageName)
                        else         SplitTunnelManager.includeApp(app.packageName)
                        // Rafraîchir le texte
                        text           = if (checked) "Exclu (direct)" else "Via VPN"
                        setTextColor(if (checked) 0xFFFF5252.toInt() else 0xFF3CF281.toInt())
                        val excl = PrefsManager.splitTunnelApps.size
                        tvCount.text = "${allApps.size} applications — $excl exclue(s) du VPN"
                    }
                }

                row.addView(info)
                row.addView(cb)
                row.setBackgroundColor(if (position % 2 == 0) 0xFF0F1629.toInt() else 0xFF0A0E1A.toInt())
                return row
            }
        }
        listView.adapter = adapter
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
