package com.shadowlink.vpn.fragments

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.shadowlink.vpn.R
import com.shadowlink.vpn.activities.LoginActivity
import com.shadowlink.vpn.databinding.FragmentHomeBinding
import com.shadowlink.vpn.models.VpnProfile
import com.shadowlink.vpn.utils.AdManager
import com.shadowlink.vpn.utils.FormatUtils
import com.shadowlink.vpn.utils.PrefsManager
import com.shadowlink.vpn.vpn.VpnManager

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var pendingProfile: VpnProfile? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingProfile?.let { startConnection(it) }
        } else {
            Toast.makeText(context, getString(R.string.vpn_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Après login réussi, rafraîchir l'UI
        updateAccountCard()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeVpnState()
        updateAccountCard()
        // La bannière publicitaire fixe est gérée par MainActivity (visible sur tous les onglets)
    }

    override fun onResume() {
        super.onResume()
        // Rafraîchir la carte compte à chaque retour sur Home
        updateAccountCard()
        updateSelectedServer()
    }

    // ── Setup ─────────────────────────────────────────────────────

    private fun setupUI() {
        // Bouton Connect / Disconnect
        binding.btnConnect.setOnClickListener {
            when (VpnManager.state.value) {
                VpnManager.VpnState.CONNECTED      -> disconnect()
                VpnManager.VpnState.DISCONNECTED,
                VpnManager.VpnState.ERROR          -> connectOrPrompt()
                else                               -> { /* en cours */ }
            }
        }

        // Card serveur → aller aux profils
        binding.cardServer.setOnClickListener {
            (activity as? com.shadowlink.vpn.activities.MainActivity)?.navigateTo("profiles")
        }

        // Bouton Pub → 1h gratuite (2 pubs de 30s, max 3x/jour)
        binding.btnWatchAd.setOnClickListener { showRewardedAd() }
        updateAdButtonLabel()

        // Bouton Se connecter / Mon compte
        binding.btnAccountAction.setOnClickListener {
            if (PrefsManager.isLoggedIn) {
                // Déjà connecté → voir les détails du compte
                showAccountDetails()
            } else {
                // Pas connecté → ouvrir le login
                loginLauncher.launch(Intent(requireContext(), LoginActivity::class.java))
            }
        }
    }

    // ── Carte compte ──────────────────────────────────────────────

    private fun updateAdButtonLabel() {
        val used = com.shadowlink.vpn.utils.PrefsManager.rewardsUsedToday()
        val left = 3 - used
        binding.btnWatchAd.text = if (left > 0)
            "▶  Regarder 2 pubs (30s) → 1h gratuite  ($left/3 aujourd'hui)"
        else
            "Limite quotidienne atteinte (3/3)"
        binding.btnWatchAd.isEnabled = left > 0
    }

    private fun updateAccountCard() {
        if (PrefsManager.isLoggedIn) {
            val user = PrefsManager.userInfo!!
            // Mode connecté
            binding.tvAccountStatus.text = user.username
            binding.tvAccountSub.text = "${user.plan.uppercase()} — expire ${user.expiresAt}"
            binding.btnAccountAction.text = getString(R.string.my_account)
            binding.tvPlan.text = user.plan.uppercase()
            binding.tvPlan.visibility = View.VISIBLE

            // Reward time
            if (PrefsManager.hasActiveReward) {
                binding.tvRewardTime.visibility = View.VISIBLE
                binding.tvRewardTime.text = getString(R.string.free_time_left, PrefsManager.rewardMinutesLeft)
            } else {
                binding.tvRewardTime.visibility = View.GONE
            }

            // Quota
            if (user.dataLimitMb > 0) {
                val pct = ((user.dataUsedMb * 100) / user.dataLimitMb).toInt()
                binding.progressData.visibility = View.VISIBLE
                binding.progressData.progress = pct
                binding.tvDataUsage.visibility = View.VISIBLE
                binding.tvDataUsage.text = getString(
                    R.string.data_usage,
                    FormatUtils.formatMb(user.dataUsedMb),
                    FormatUtils.formatMb(user.dataLimitMb)
                )
            }
        } else {
            // Mode invité
            binding.tvAccountStatus.text = getString(R.string.guest_mode)
            binding.tvAccountSub.text = getString(R.string.guest_mode_sub)
            binding.btnAccountAction.text = getString(R.string.login_to_premium)
            binding.tvPlan.visibility = View.GONE
            binding.progressData.visibility = View.GONE
            binding.tvDataUsage.visibility = View.GONE
            binding.tvRewardTime.visibility = View.GONE
        }
    }

    private fun updateSelectedServer() {
        val lastId = PrefsManager.lastUsedProfileId
        val profile = PrefsManager.allProfiles.find { it.id == lastId }
        if (profile != null) {
            binding.tvServerName.text = profile.name
            binding.tvServerProtocol.text = profile.protocol.replace("_", " ")
            binding.tvServerCountry.text = profile.country.ifEmpty { "—" }
        } else {
            binding.tvServerName.text = getString(R.string.select_server)
            binding.tvServerProtocol.text = "—"
            binding.tvServerCountry.text = "—"
        }
    }

    // ── Connexion VPN ─────────────────────────────────────────────

    private fun connectOrPrompt() {
        val profiles = PrefsManager.allProfiles
        if (profiles.isEmpty()) {
            Toast.makeText(context, getString(R.string.no_profiles), Toast.LENGTH_SHORT).show()
            (activity as? com.shadowlink.vpn.activities.MainActivity)?.navigateTo("profiles")
            return
        }

        // Vérification expiration seulement si connecté à un compte
        if (PrefsManager.isLoggedIn) {
            val user = PrefsManager.userInfo
            if (user?.active != true) {
                Toast.makeText(context, getString(R.string.account_inactive), Toast.LENGTH_SHORT).show()
                return
            }
        }

        val lastId = PrefsManager.lastUsedProfileId
        val profile = profiles.find { it.id == lastId } ?: profiles.first()
        requestVpnPermission(profile)
    }

    private fun requestVpnPermission(profile: VpnProfile) {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            pendingProfile = profile
            vpnPermissionLauncher.launch(intent)
        } else {
            startConnection(profile)
        }
    }

    private fun startConnection(profile: VpnProfile) {
        context?.let { ctx ->
            VpnManager.connect(ctx, profile)
            PrefsManager.lastUsedProfileId = profile.id
            updateSelectedServer()
        }
    }

    private fun disconnect() {
        context?.let { VpnManager.disconnect(it) }
    }

    // ── Observer état VPN ─────────────────────────────────────────

    private fun observeVpnState() {
        VpnManager.state.observe(viewLifecycleOwner) { state ->
            updateConnectionUI(state)
        }
        VpnManager.stats.observe(viewLifecycleOwner) { stats ->
            if (stats.isConnected) {
                binding.tvUpload.text   = FormatUtils.formatBytes(stats.uploadBytes)
                binding.tvDownload.text = FormatUtils.formatBytes(stats.downloadBytes)
                binding.tvDuration.text = FormatUtils.formatDuration(stats.durationSeconds)
                binding.tvPing.text     = if (stats.ping > 0) "${stats.ping}ms" else "--"
            }
        }
        VpnManager.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateConnectionUI(state: VpnManager.VpnState) {
        when (state) {
            VpnManager.VpnState.DISCONNECTED -> {
                binding.btnConnect.text = getString(R.string.connect)
                binding.btnConnect.setBackgroundResource(R.drawable.bg_button_connect)
                binding.tvStatus.text = getString(R.string.status_disconnected)
                binding.tvStatus.setTextColor(resources.getColor(R.color.status_disconnected, null))
                binding.statsCard.visibility = View.GONE
                binding.btnWatchAd.visibility = View.VISIBLE
            }
            VpnManager.VpnState.CONNECTING -> {
                binding.btnConnect.text = getString(R.string.connecting)
                binding.btnConnect.setBackgroundResource(R.drawable.bg_button_connecting)
                binding.tvStatus.text = getString(R.string.status_connecting)
                binding.tvStatus.setTextColor(resources.getColor(R.color.status_connecting, null))
            }
            VpnManager.VpnState.CONNECTED -> {
                binding.btnConnect.text = getString(R.string.disconnect)
                binding.btnConnect.setBackgroundResource(R.drawable.bg_button_disconnect)
                binding.tvStatus.text = getString(R.string.status_connected)
                binding.tvStatus.setTextColor(resources.getColor(R.color.status_connected, null))
                binding.statsCard.visibility = View.VISIBLE
                binding.btnWatchAd.visibility = View.GONE
            }
            VpnManager.VpnState.DISCONNECTING -> {
                binding.btnConnect.text = getString(R.string.disconnecting)
                binding.tvStatus.text = getString(R.string.status_disconnecting)
            }
            VpnManager.VpnState.ERROR -> {
                binding.btnConnect.text = getString(R.string.connect)
                binding.btnConnect.setBackgroundResource(R.drawable.bg_button_connect)
                binding.tvStatus.text = getString(R.string.status_error)
                binding.tvStatus.setTextColor(resources.getColor(R.color.status_error, null))
                binding.statsCard.visibility = View.GONE
            }
        }
    }

    // ── Compte details dialog ─────────────────────────────────────

    private fun showAccountDetails() {
        val user = PrefsManager.userInfo ?: return
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(user.username)
            .setMessage(
                "Plan : ${user.plan.uppercase()}\n" +
                "Expiration : ${user.expiresAt}\n" +
                "Connexions : 1 / 2\n" +
                if (user.dataLimitMb > 0)
                    "Données : ${FormatUtils.formatMb(user.dataUsedMb)} / ${FormatUtils.formatMb(user.dataLimitMb)}"
                else "Données : Illimitées"
            )
            .setNegativeButton("FERMER", null)
            .setNeutralButton("DÉCONNEXION") { _, _ ->
                PrefsManager.logout()
                updateAccountCard()
                Toast.makeText(context, "Déconnecté du compte", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ── Rewarded Ad ───────────────────────────────────────────────

    private fun showRewardedAd() {
        val activity = activity ?: return

        if (!AdManager.canWatchAdsToday()) {
            Toast.makeText(context, "Limite de 3 pubs/jour atteinte. Revenez demain.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Regardez 2 publicités de 30s pour 1h gratuite…", Toast.LENGTH_SHORT).show()

        AdManager.startRewardSequence(
            activity = activity,
            onAdShown = { current, total ->
                Toast.makeText(context, "Publicité $current sur $total…", Toast.LENGTH_SHORT).show()
            },
            onSequenceComplete = {
                // Les 2 pubs ont été regardées en entier -> accorder 60 min (heure serveur)
                PrefsManager.grantReward(60)
                Toast.makeText(context, getString(R.string.reward_granted, 60), Toast.LENGTH_LONG).show()
                updateAccountCard()
                val profiles = PrefsManager.allProfiles
                if (profiles.isNotEmpty() && VpnManager.state.value == VpnManager.VpnState.DISCONNECTED) {
                    requestVpnPermission(profiles.first())
                }
            },
            onFailed = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
