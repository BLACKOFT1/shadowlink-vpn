package com.shadowlink.vpn.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.shadowlink.vpn.R
import com.shadowlink.vpn.databinding.ActivityVpnFileBinding
import com.shadowlink.vpn.models.VpnProfile

class VpnFileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVpnFileBinding

    // Catégories et leurs modes
    private val categories = arrayOf("SSH", "Xray (V2Ray)", "Shadowsocks", "WireGuard", "UDP Custom", "SlowDNS", "Client Proxy Simple")

    private val modesByCategory = mapOf(
        "SSH"                 to arrayOf("SSH Direct", "SSH Proxy", "SSH SSL", "SSH SSL + Payload"),
        "Xray (V2Ray)"        to arrayOf("VMess", "VLESS", "Trojan", "VLESS + Reality"),
        "Shadowsocks"         to arrayOf("Shadowsocks Standard", "Shadowsocks 2022"),
        "WireGuard"           to arrayOf("WireGuard Standard"),
        "UDP Custom"          to arrayOf("UDP Hysteria", "UDP Hysteria 2", "UDP Generic"),
        "SlowDNS"             to arrayOf("SlowDNS"),
        "Client Proxy Simple" to arrayOf("HTTP Proxy", "SOCKS5 Proxy")
    )

    private var selectedCategory = "SSH"
    private var selectedMode = "SSH Direct"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVpnFileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.add_profile)
            setDisplayHomeAsUpEnabled(true)
        }

        setupCategorySpinner()
        setupButtons()
        showFieldsForMode(selectedMode)

        // Édition d'un profil existant
        intent.getParcelableExtra<VpnProfile>("edit_profile")?.let { populateForm(it) }
    }

    // ── Spinners ─────────────────────────────────────────────────

    private fun setupCategorySpinner() {
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = catAdapter

        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedCategory = categories[pos]
                updateModeSpinner(selectedCategory)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateModeSpinner(category: String) {
        val modes = modesByCategory[category] ?: arrayOf()
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMode.adapter = modeAdapter

        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedMode = modes[pos]
                showFieldsForMode(selectedMode)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Afficher les champs selon le mode ────────────────────────

    private fun showFieldsForMode(mode: String) {
        // Tout cacher d'abord
        binding.layoutSshFields.visibility    = View.GONE
        binding.layoutProxyFields.visibility  = View.GONE
        binding.layoutSslFields.visibility    = View.GONE
        binding.layoutPayloadField.visibility = View.GONE
        binding.layoutV2rayFields.visibility  = View.GONE
        binding.layoutRealityFields.visibility = View.GONE
        binding.layoutSsFields.visibility     = View.GONE
        binding.layoutWgFields.visibility     = View.GONE
        binding.layoutUdpFields.visibility    = View.GONE
        binding.layoutSlowdnsFields.visibility = View.GONE
        binding.layoutProxySimple.visibility  = View.GONE

        when (mode) {
            "SSH Direct"         -> binding.layoutSshFields.visibility = View.VISIBLE
            "SSH Proxy"          -> { binding.layoutSshFields.visibility = View.VISIBLE; binding.layoutProxyFields.visibility = View.VISIBLE }
            "SSH SSL"            -> { binding.layoutSshFields.visibility = View.VISIBLE; binding.layoutSslFields.visibility = View.VISIBLE }
            "SSH SSL + Payload"  -> { binding.layoutSshFields.visibility = View.VISIBLE; binding.layoutSslFields.visibility = View.VISIBLE; binding.layoutPayloadField.visibility = View.VISIBLE }
            "VMess", "VLESS", "Trojan" -> binding.layoutV2rayFields.visibility = View.VISIBLE
            "VLESS + Reality"    -> { binding.layoutV2rayFields.visibility = View.VISIBLE; binding.layoutRealityFields.visibility = View.VISIBLE }
            "Shadowsocks Standard", "Shadowsocks 2022" -> binding.layoutSsFields.visibility = View.VISIBLE
            "WireGuard Standard" -> binding.layoutWgFields.visibility = View.VISIBLE
            "UDP Hysteria", "UDP Hysteria 2", "UDP Generic" -> binding.layoutUdpFields.visibility = View.VISIBLE
            "SlowDNS"            -> binding.layoutSlowdnsFields.visibility = View.VISIBLE
            "HTTP Proxy", "SOCKS5 Proxy" -> binding.layoutProxySimple.visibility = View.VISIBLE
        }
    }

    // ── Boutons ──────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.btnCancel.setOnClickListener { finish() }

        binding.btnImportLink.setOnClickListener {
            val link = binding.etShareLink.text.toString().trim()
            if (link.isEmpty()) { Toast.makeText(this, getString(R.string.enter_link), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            Toast.makeText(this, "Lien importé — voir Profils", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Sauvegarder ──────────────────────────────────────────────

    private fun saveProfile(): Boolean {
        val name = binding.etProfileName.text.toString().trim()
        val host = binding.etHost.text.toString().trim()
        val port = binding.etPort.text.toString().toIntOrNull()

        if (name.isEmpty()) { binding.etProfileName.error = getString(R.string.field_required); return false }
        if (host.isEmpty()) { binding.etHost.error = getString(R.string.field_required); return false }
        if (port == null)   { binding.etPort.error = getString(R.string.invalid_port); return false }

        // Mapper catégorie + mode → protocole interne
        val protocol = mapModeToProtocol(selectedCategory, selectedMode)

        val profile = VpnProfile(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            country = binding.etCountry.text.toString().trim(),
            sshUser = binding.etSshUser.text?.toString()?.trim() ?: "",
            sshPass = binding.etSshPass.text?.toString() ?: "",
            sshProxyHost = binding.etProxyHost.text?.toString()?.trim() ?: "",
            sshProxyPort = binding.etProxyPort.text?.toString()?.toIntOrNull() ?: 0,
            sshSslPort = binding.etSslPort.text?.toString()?.toIntOrNull() ?: 443,
            payload = binding.etPayload.text?.toString()?.trim() ?: "",
            slowdnsNs = binding.etSlowdnsNs.text?.toString()?.trim() ?: "",
            slowdnsKey = binding.etSlowdnsKey.text?.toString()?.trim() ?: "",
            udpObfs = binding.etUdpObfs.text?.toString()?.trim() ?: "",
            udpAuth = binding.etUdpAuth.text?.toString()?.trim() ?: "",
            v2rayUuid = binding.etV2rayUuid.text?.toString()?.trim() ?: "",
            v2rayNetwork = binding.spinnerV2rayNetwork.selectedItem?.toString() ?: "tcp",
            v2rayTls = binding.switchV2rayTls.isChecked,
            v2rayPath = binding.etV2rayPath.text?.toString()?.trim() ?: "",
            v2raySni = binding.etV2raySni.text?.toString()?.trim() ?: "",
            ssMethod = binding.spinnerSsMethod.selectedItem?.toString() ?: "aes-256-gcm",
            ssPassword = binding.etSsPassword.text?.toString() ?: "",
            isLocal = true
        )

        setResult(Activity.RESULT_OK, Intent().apply { putExtra("profile", profile) })
        finish()
        return true
    }

    private fun mapModeToProtocol(category: String, mode: String): String = when {
        mode == "SSH Direct"          -> "SSH_DIRECT"
        mode == "SSH Proxy"           -> "SSH_PROXY"
        mode == "SSH SSL"             -> "SSH_SSL"
        mode == "SSH SSL + Payload"   -> "SSH_SSL_PAYLOAD"
        mode == "VMess"               -> "V2RAY_VMESS"
        mode == "VLESS"               -> "V2RAY_VLESS"
        mode.contains("VLESS + Reality") -> "V2RAY_VLESS"
        mode == "Trojan"              -> "V2RAY_TROJAN"
        mode.contains("Shadowsocks") -> "SHADOWSOCKS"
        mode == "WireGuard Standard"  -> "WIREGUARD"
        mode.contains("Hysteria")     -> "UDP_HYSTERIA"
        mode == "UDP Generic"         -> "UDP_HYSTERIA"
        mode == "SlowDNS"             -> "SLOWDNS"
        mode == "HTTP Proxy"          -> "SSH_PROXY"
        mode == "SOCKS5 Proxy"        -> "SSH_PROXY"
        else -> "SSH_DIRECT"
    }

    // ── Pré-remplir si édition ───────────────────────────────────

    private fun populateForm(p: VpnProfile) {
        binding.etProfileName.setText(p.name)
        binding.etHost.setText(p.host)
        binding.etPort.setText(p.port.toString())
        binding.etCountry.setText(p.country)
        binding.etSshUser.setText(p.sshUser)
        binding.etSshPass.setText(p.sshPass)
        binding.etV2rayUuid.setText(p.v2rayUuid)
        binding.etV2raySni.setText(p.v2raySni)
        binding.etV2rayPath.setText(p.v2rayPath)
        binding.switchV2rayTls.isChecked = p.v2rayTls
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
