package com.shadowlink.vpn.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.shadowlink.vpn.R
import com.shadowlink.vpn.databinding.ActivityMainBinding
import com.shadowlink.vpn.fragments.*
import com.shadowlink.vpn.utils.AdManager
import com.shadowlink.vpn.utils.UpdateManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), BottomNavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment     = HomeFragment()
    private val profilesFragment = ProfilesFragment()
    private val logsFragment     = LogsFragment()
    private val settingsFragment = SettingsFragment()

    private var activeFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupBottomNav()
        setupFixedBanner()

        // Préchargement des pubs rewarded
        AdManager.loadRewardedAd(this)

        // Vérification mise à jour en arrière-plan
        lifecycleScope.launch { checkUpdate() }
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, homeFragment,     "home")
            add(R.id.fragment_container, profilesFragment, "profiles").hide(profilesFragment)
            add(R.id.fragment_container, logsFragment,     "logs").hide(logsFragment)
            add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
        }.commit()
    }

    private fun setupFixedBanner() {
        // Banniere FIXE - visible en permanence en bas de l'app, sur tous les onglets,
        // ne bouge jamais avec le scroll des fragments.
        val banner = AdManager.createBannerAd(this)
        binding.bannerContainer.removeAllViews()
        binding.bannerContainer.addView(banner)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val fragment = when (item.itemId) {
            R.id.nav_home     -> homeFragment
            R.id.nav_profiles -> profilesFragment
            R.id.nav_logs     -> logsFragment
            R.id.nav_settings -> settingsFragment
            else              -> return false
        }
        switchFragment(fragment)
        return true
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(fragment)
            .commit()
        activeFragment = fragment
    }

    fun navigateTo(tag: String) {
        when (tag) {
            "home" -> {
                binding.bottomNav.selectedItemId = R.id.nav_home
                switchFragment(homeFragment)
            }
            "profiles" -> {
                binding.bottomNav.selectedItemId = R.id.nav_profiles
                switchFragment(profilesFragment)
            }
            "logs" -> {
                binding.bottomNav.selectedItemId = R.id.nav_logs
                switchFragment(logsFragment)
            }
            "settings" -> {
                binding.bottomNav.selectedItemId = R.id.nav_settings
                switchFragment(settingsFragment)
            }
        }
    }

    private suspend fun checkUpdate() {
        val update = UpdateManager.checkForUpdate() ?: return
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_available))
                .setMessage("Version ${update.latestVersion}\n\n${update.changelog}")
                .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                    startActivity(Intent(this, UpdateActivity::class.java).apply {
                        putExtra("apk_url",   update.apkUrl)
                        putExtra("version",   update.latestVersion)
                        putExtra("changelog", update.changelog)
                    })
                }
                .apply {
                    if (!update.forceUpdate)
                        setNegativeButton(getString(R.string.later)) { d, _ -> d.dismiss() }
                    setCancelable(!update.forceUpdate)
                }
                .show()
        }
    }

    override fun onBackPressed() {
        if (activeFragment != homeFragment) {
            binding.bottomNav.selectedItemId = R.id.nav_home
            switchFragment(homeFragment)
        } else {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.exit_app))
                .setMessage(getString(R.string.exit_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> super.onBackPressed() }
                .setNegativeButton(getString(R.string.no))  { d, _ -> d.dismiss() }
                .show()
        }
    }
}
