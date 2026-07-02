package com.shadowlink.vpn.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.shadowlink.vpn.R
import com.shadowlink.vpn.activities.LoginActivity
import com.shadowlink.vpn.activities.SplitTunnelActivity
import com.shadowlink.vpn.databinding.FragmentSettingsBinding
import com.shadowlink.vpn.network.ApiClient
import com.shadowlink.vpn.utils.AppLogger
import com.shadowlink.vpn.utils.PrefsManager
import com.shadowlink.vpn.utils.SecurityUtils
import com.shadowlink.vpn.utils.UpdateManager
import com.shadowlink.vpn.vpn.VpnManager
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val xrayEngines = arrayOf(
        "Xray-Core Subprocess (Stable)",
        "Xray-Core Subprocess (Beta)",
        "Libxray (Intégré)"
    )
    private val muxTypes = arrayOf("h2mux (HTTP/2)", "smux", "yamux", "Désactivé")
    private val themeModes = arrayOf("Sombre", "Clair", "Système")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.etDns.setText(PrefsManager.dns)
        binding.etMtu.setText(PrefsManager.mtu.toString())
        binding.switchHotspot.isChecked     = PrefsManager.hotspotEnabled
        binding.switchAutoConnect.isChecked = PrefsManager.autoConnect
        binding.switchKillSwitch.isChecked  = PrefsManager.killSwitchEnabled
        binding.btnXrayEngine.text          = PrefsManager.xrayEngineLabel
        binding.btnMuxType.text             = PrefsManager.muxTypeLabel
        binding.etMuxConcurrency.setText(PrefsManager.muxConcurrency.toString())
        binding.checkMuxPadding.isChecked   = PrefsManager.muxPadding
        binding.checkTcpFastOpen.isChecked  = PrefsManager.tcpFastOpen
        binding.checkMultipathTcp.isChecked = PrefsManager.multipathTcp
        binding.tvHwid.text                 = SecurityUtils.getHWID(requireContext())
        binding.tvAppVersion.text           = getString(R.string.app_version, getAppVersion())
        binding.spinnerLanguage.setSelection(if (PrefsManager.language == "fr") 0 else 1)
        binding.tvSplitTunnelCount.text     = "${PrefsManager.splitTunnelApps.size} app(s) exclue(s)"
        binding.btnThemeMode.text           = com.shadowlink.vpn.utils.ThemeManager.modeLabel(com.shadowlink.vpn.utils.ThemeManager.currentMode())
        updateAccountButton()
    }

    private fun setupListeners() {

        // DNS
        binding.btnSaveDns.setOnClickListener {
            val dns = binding.etDns.text.toString().trim()
            if (dns.isNotEmpty()) { PrefsManager.dns = dns; toast(R.string.saved) }
        }

        // MTU
        binding.btnSaveMtu.setOnClickListener {
            val mtu = binding.etMtu.text.toString().toIntOrNull()
            if (mtu != null && mtu in 576..9000) { PrefsManager.mtu = mtu; toast(R.string.saved) }
            else toast(R.string.invalid_mtu)
        }

        // Hotspot
        binding.switchHotspot.setOnCheckedChangeListener { _, v -> PrefsManager.hotspotEnabled = v }

        // Auto-connect
        binding.switchAutoConnect.setOnCheckedChangeListener { _, v -> PrefsManager.autoConnect = v }

        // Kill Switch
        binding.switchKillSwitch.setOnCheckedChangeListener { _, v ->
            PrefsManager.killSwitchEnabled = v
            if (v) {
                AlertDialog.Builder(requireContext())
                    .setTitle("⚠️ Kill Switch activé")
                    .setMessage(
                        "Si le VPN se déconnecte, TOUTE connexion Internet sera coupée " +
                        "jusqu'à ce que le VPN soit reconnecté ou désactivé.\n\n" +
                        "Cela protège votre vie privée mais peut couper l'accès si le VPN a un problème."
                    )
                    .setPositiveButton("Compris", null)
                    .setNegativeButton("Désactiver") { _, _ ->
                        PrefsManager.killSwitchEnabled = false
                        binding.switchKillSwitch.isChecked = false
                    }
                    .show()
            }
            AppLogger.i("[Settings] Kill Switch : $v")
        }

        // Theme Mode
        binding.btnThemeMode.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("APPARENCE")
                .setSingleChoiceItems(themeModes, com.shadowlink.vpn.utils.ThemeManager.currentMode()) { d, which ->
                    com.shadowlink.vpn.utils.ThemeManager.applyTheme(which)
                    binding.btnThemeMode.text = themeModes[which]
                    d.dismiss()
                    requireActivity().recreate()
                }
                .setNegativeButton("ANNULER", null)
                .show()
        }

        // Split Tunnel
        binding.btnSplitTunnel.setOnClickListener {
            startActivity(Intent(requireContext(), SplitTunnelActivity::class.java))
        }

        // Xray Engine
        binding.btnXrayEngine.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("MOTEUR XRAY")
                .setSingleChoiceItems(xrayEngines, PrefsManager.xrayEngine) { d, which ->
                    PrefsManager.xrayEngine = which
                    binding.btnXrayEngine.text = xrayEngines[which]
                    d.dismiss()
                }
                .setNegativeButton("ANNULER", null)
                .show()
        }

        // MUX Type
        binding.btnMuxType.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("TYPE MUX")
                .setSingleChoiceItems(muxTypes, PrefsManager.muxType) { d, which ->
                    PrefsManager.muxType = which
                    binding.btnMuxType.text = muxTypes[which]
                    d.dismiss()
                }
                .setNegativeButton("ANNULER", null)
                .show()
        }

        // MUX Concurrency
        binding.btnSaveMux.setOnClickListener {
            val c = binding.etMuxConcurrency.text.toString().toIntOrNull()
            if (c != null && c in 1..32) { PrefsManager.muxConcurrency = c; toast(R.string.saved) }
            else toast("Concurrence invalide (1–32)")
        }

        // MUX Padding
        binding.checkMuxPadding.setOnCheckedChangeListener { _, v -> PrefsManager.muxPadding = v }

        // TCP Fast Open
        binding.checkTcpFastOpen.setOnCheckedChangeListener { _, v -> PrefsManager.tcpFastOpen = v }

        // Multipath TCP
        binding.checkMultipathTcp.setOnCheckedChangeListener { _, v -> PrefsManager.multipathTcp = v }

        // HWID copier
        binding.btnCopyHwid.setOnClickListener {
            val hwid = SecurityUtils.getHWID(requireContext())
            val cb   = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("HWID", hwid))
            toast(R.string.hwid_copied)
        }

        // Langue
        binding.spinnerLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                val lang = if (pos == 0) "fr" else "en"
                if (lang != PrefsManager.language) { PrefsManager.language = lang; requireActivity().recreate() }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        // Vérifier mises à jour
        binding.btnCheckUpdate.setOnClickListener { checkUpdate() }

        // Login / Logout
        binding.btnLogout.setOnClickListener {
            if (PrefsManager.isLoggedIn) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.logout))
                    .setMessage(getString(R.string.logout_confirm))
                    .setPositiveButton(getString(R.string.yes)) { _, _ -> performLogout() }
                    .setNegativeButton(getString(R.string.no)) { d, _ -> d.dismiss() }
                    .show()
            } else {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
        }
    }

    private fun updateAccountButton() {
        if (PrefsManager.isLoggedIn) {
            val user = PrefsManager.userInfo
            binding.btnLogout.text = "DÉCONNEXION (${user?.username ?: ""})"
            binding.btnLogout.setBackgroundResource(R.drawable.bg_button_disconnect)
        } else {
            binding.btnLogout.text = "SE CONNECTER AU PANEL"
            binding.btnLogout.setBackgroundResource(R.drawable.bg_button_connect)
        }
    }

    private fun checkUpdate() {
        binding.btnCheckUpdate.isEnabled = false
        lifecycleScope.launch {
            val update = UpdateManager.checkForUpdate()
            if (update != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.update_available))
                    .setMessage("v${update.latestVersion}\n\n${update.changelog}")
                    .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                        UpdateManager.downloadAndInstall(requireContext(), update)
                    }
                    .setNegativeButton(getString(R.string.later)) { d, _ -> d.dismiss() }
                    .show()
            } else toast(R.string.already_latest)
            binding.btnCheckUpdate.isEnabled = true
        }
    }

    private fun performLogout() {
        if (VpnManager.state.value == VpnManager.VpnState.CONNECTED)
            context?.let { VpnManager.disconnect(it) }
        lifecycleScope.launch {
            ApiClient.logout()
            PrefsManager.logout()
            updateAccountButton()
            toast("Déconnecté du compte")
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tvSplitTunnelCount.text = "${PrefsManager.splitTunnelApps.size} app(s) exclue(s)"
        updateAccountButton()
    }

    private fun toast(resId: Int) = Toast.makeText(context, getString(resId), Toast.LENGTH_SHORT).show()
    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    private fun getAppVersion() = try {
        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
    } catch (e: Exception) { "1.0.0" }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
