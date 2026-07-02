package com.shadowlink.vpn.fragments

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.shadowlink.vpn.R
import com.shadowlink.vpn.activities.LoginActivity
import com.shadowlink.vpn.activities.VpnFileActivity
import com.shadowlink.vpn.adapters.ProfilesAdapter
import com.shadowlink.vpn.databinding.FragmentProfilesBinding
import com.shadowlink.vpn.models.VpnProfile
import com.shadowlink.vpn.network.ApiClient
import com.shadowlink.vpn.utils.PrefsManager
import com.shadowlink.vpn.utils.VpnFileManager
import kotlinx.coroutines.launch

class ProfilesFragment : Fragment() {

    private var _binding: FragmentProfilesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProfilesAdapter

    // ── Launchers ────────────────────────────────────────────────

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importFromFile(it) } }

    private val addProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val profile = result.data?.getParcelableExtra<VpnProfile>("profile")
            if (profile != null) {
                PrefsManager.addLocalProfile(profile)
                refreshList()
                Toast.makeText(context, getString(R.string.profile_added), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Après login, tenter sync auto
        if (PrefsManager.isLoggedIn) syncWithServer()
        refreshList()
        updateBannerVisibility()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupButtons()
        refreshList()
        updateBannerVisibility()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        updateBannerVisibility()
    }

    // ── Bannière invité ───────────────────────────────────────────

    /**
     * Si l'utilisateur n'est pas connecté, afficher un bandeau
     * "Connectez-vous pour accéder aux serveurs premium"
     */
    private fun updateBannerVisibility() {
        if (PrefsManager.isLoggedIn) {
            binding.bannerGuest.visibility = View.GONE
        } else {
            binding.bannerGuest.visibility = View.VISIBLE
            binding.btnLoginBanner.setOnClickListener {
                loginLauncher.launch(Intent(requireContext(), LoginActivity::class.java))
            }
        }
    }

    // ── Setup RecyclerView ───────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ProfilesAdapter(
            onSelect = { profile ->
                PrefsManager.lastUsedProfileId = profile.id
                Toast.makeText(context, getString(R.string.profile_selected, profile.name), Toast.LENGTH_SHORT).show()
                (activity as? com.shadowlink.vpn.activities.MainActivity)?.navigateTo("home")
            },
            onDelete = { profile ->
                if (profile.isLocal) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Supprimer ce profil ?")
                        .setMessage(profile.name)
                        .setPositiveButton("SUPPRIMER") { _, _ ->
                            PrefsManager.removeLocalProfile(profile.id)
                            refreshList()
                        }
                        .setNegativeButton("ANNULER", null)
                        .show()
                } else {
                    Toast.makeText(context, getString(R.string.cannot_delete_server_profile), Toast.LENGTH_SHORT).show()
                }
            },
            onShare = { profile ->
                val link = VpnFileManager.generateShareLink(profile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_profile)))
            }
        )
        binding.recyclerProfiles.adapter = adapter
    }

    // ── Setup boutons ─────────────────────────────────────────────

    private fun setupButtons() {
        // ↓ Sync (seulement si connecté)
        binding.btnSync.setOnClickListener {
            if (!PrefsManager.isLoggedIn) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Connexion requise")
                    .setMessage("Connectez-vous à votre compte pour synchroniser les profils premium.")
                    .setPositiveButton("SE CONNECTER") { _, _ ->
                        loginLauncher.launch(Intent(requireContext(), LoginActivity::class.java))
                    }
                    .setNegativeButton("ANNULER", null)
                    .show()
            } else {
                syncWithServer()
            }
        }

        // ↓ Import → dialog 3 options (disponible même en invité)
        binding.btnImport.setOnClickListener { showImportDialog() }

        // ↑ Export (disponible même en invité)
        binding.btnExport.setOnClickListener {
            if (PrefsManager.localProfiles.isEmpty()) {
                Toast.makeText(context, getString(R.string.no_local_profiles), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exportProfiles()
        }

        // FAB + → dialog ajout (disponible même en invité)
        binding.fabAdd.setOnClickListener { showAddProfileDialog() }
    }

    // ── Dialogs ───────────────────────────────────────────────────

    /** Dialog principal : Compte Auto OU Manuel */
    private fun showAddProfileDialog() {
        val options = mutableListOf<String>()
        options.add("⚙️  Configuration Personnelle (Manuelle)")

        // Compte auto seulement si connecté
        if (PrefsManager.isLoggedIn) {
            options.add(0, "🔗  Compte ShadowLink (Auto — Sync)")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Ajouter un profil")
            .setItems(options.toTypedArray()) { _, which ->
                if (PrefsManager.isLoggedIn && which == 0) {
                    // Sync auto depuis le panel
                    syncWithServer()
                } else {
                    // Formulaire manuel — disponible pour tous
                    addProfileLauncher.launch(
                        Intent(requireContext(), VpnFileActivity::class.java)
                    )
                }
            }
            .setNegativeButton("ANNULER", null)
            .show()
    }

    /** Dialog import : Fichier / Presse-papiers / QR Code */
    private fun showImportDialog() {
        val options = arrayOf(
            "📄  Fichier (.slvpn / .fpv)",
            "📋  Presse-papiers",
            "📷  Scanner un Code QR"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Importer une configuration")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importFileLauncher.launch(arrayOf("*/*"))
                    1 -> importFromClipboard()
                    2 -> importFromQR()
                }
            }
            .setNegativeButton("ANNULER", null)
            .show()
    }

    // ── Import ────────────────────────────────────────────────────

    private fun importFromClipboard() {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(context, "Presse-papiers vide", Toast.LENGTH_SHORT).show()
            return
        }
        importFromText(text)
    }

    private fun importFromText(text: String) {
        try {
            val tempFile = java.io.File(requireContext().cacheDir, "clipboard_import.txt")
            tempFile.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                tempFile
            )
            val result = VpnFileManager.importFromFile(requireContext(), uri)
            result.fold(
                onSuccess = { profiles ->
                    if (profiles.isNotEmpty()) {
                        profiles.forEach { PrefsManager.addLocalProfile(it) }
                        refreshList()
                        Toast.makeText(context,
                            getString(R.string.import_success, profiles.size),
                            Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Format non reconnu dans le presse-papiers", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur import : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromQR() {
        // Nécessite ZXing — on informe l'utilisateur
        Toast.makeText(context,
            "Scanner QR : ajouter 'com.journeyapps:zxing-android-embedded:4.3.0' dans build.gradle",
            Toast.LENGTH_LONG).show()
    }

    private fun importFromFile(uri: android.net.Uri) {
        val result = VpnFileManager.importFromFile(requireContext(), uri)
        result.fold(
            onSuccess = { profiles ->
                profiles.forEach { PrefsManager.addLocalProfile(it) }
                refreshList()
                Toast.makeText(context,
                    getString(R.string.import_success, profiles.size),
                    Toast.LENGTH_SHORT).show()
            },
            onFailure = { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
        )
    }

    // ── Export ────────────────────────────────────────────────────

    private fun exportProfiles() {
        val lines = PrefsManager.localProfiles.joinToString("\n") {
            VpnFileManager.generateShareLink(it)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lines)
            putExtra(Intent.EXTRA_SUBJECT, "ShadowLink VPN Profiles")
        }
        startActivity(Intent.createChooser(intent, "Exporter les profils"))
    }

    // ── Sync serveur ──────────────────────────────────────────────

    private fun syncWithServer() {
        binding.btnSync.isEnabled = false
        binding.progressSync.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = ApiClient.getProfiles()
            result.fold(
                onSuccess = { profiles ->
                    PrefsManager.serverProfiles = profiles
                    refreshList()
                    Toast.makeText(context,
                        getString(R.string.sync_success, profiles.size),
                        Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
                }
            )
            binding.btnSync.isEnabled = true
            binding.progressSync.visibility = View.GONE
        }
    }

    // ── Liste ─────────────────────────────────────────────────────

    private fun refreshList() {
        val all = PrefsManager.allProfiles
        adapter.submitList(all)
        binding.layoutEmpty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerProfiles.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
