package com.shadowlink.vpn.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shadowlink.vpn.R
import com.shadowlink.vpn.databinding.ActivityLoginBinding
import com.shadowlink.vpn.network.ApiClient
import com.shadowlink.vpn.utils.PrefsManager
import com.shadowlink.vpn.utils.SecurityUtils
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Permettre de fermer sans se connecter
        supportActionBar?.apply {
            title = "Connexion"
            setDisplayHomeAsUpEnabled(true)
        }

        setupUI()
    }

    private fun setupUI() {
        if (PrefsManager.savedUsername.isNotEmpty())
            binding.etUsername.setText(PrefsManager.savedUsername)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (username.isEmpty()) { binding.etUsername.error = getString(R.string.error_username_required); return@setOnClickListener }
            if (password.isEmpty()) { binding.etPassword.error = getString(R.string.error_password_required); return@setOnClickListener }
            performLogin(username, password)
        }

        // Bouton "Continuer sans compte"
        binding.tvSkipLogin.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.tvContactAdmin.setOnClickListener {
            val intent = Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("${PrefsManager.panelUrl}/contact")
            }
            startActivity(intent)
        }
    }

    private fun performLogin(username: String, password: String) {
        setLoading(true)
        val hwid = SecurityUtils.getHWID(this)

        lifecycleScope.launch {
            val result = ApiClient.login(username, password, hwid)
            result.fold(
                onSuccess = { response ->
                    if (response.success && response.token != null && response.user != null) {
                        PrefsManager.authToken    = response.token
                        PrefsManager.userInfo     = response.user
                        PrefsManager.savedUsername = username
                        // Retourner RESULT_OK → ProfilesFragment ou HomeFragment vont se rafraîchir
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        setLoading(false)
                        showError(response.message ?: getString(R.string.error_login_failed))
                    }
                },
                onFailure = { error ->
                    setLoading(false)
                    showError(error.message ?: getString(R.string.error_network))
                }
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.text = if (loading) "" else getString(R.string.login_button)
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(Activity.RESULT_CANCELED)
        finish()
        return true
    }
}
