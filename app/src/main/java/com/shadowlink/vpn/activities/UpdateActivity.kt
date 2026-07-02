package com.shadowlink.vpn.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shadowlink.vpn.R
import com.shadowlink.vpn.databinding.ActivityUpdateBinding
import com.shadowlink.vpn.models.AppUpdate
import com.shadowlink.vpn.utils.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UpdateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.update_available)
            setDisplayHomeAsUpEnabled(true)
        }

        val apkUrl = intent.getStringExtra("apk_url") ?: return
        val version = intent.getStringExtra("version") ?: ""
        val changelog = intent.getStringExtra("changelog") ?: ""

        binding.tvVersion.text = "Version $version"
        binding.tvChangelog.text = changelog

        binding.btnUpdate.setOnClickListener {
            startDownload(apkUrl, version)
        }

        binding.btnLater.setOnClickListener { finish() }
    }

    private fun startDownload(apkUrl: String, version: String) {
        binding.btnUpdate.isEnabled = false
        binding.btnLater.isEnabled = false
        binding.progressDownload.visibility = View.VISIBLE
        binding.tvDownloadStatus.visibility = View.VISIBLE
        binding.tvDownloadStatus.text = getString(R.string.downloading)

        val update = AppUpdate(
            latestVersion = version,
            versionCode = 0,
            apkUrl = apkUrl,
            changelog = ""
        )

        UpdateManager.downloadAndInstall(this, update)

        // Suivre la progression
        lifecycleScope.launch {
            while (true) {
                val progress = UpdateManager.getDownloadProgress(this@UpdateActivity)
                binding.progressDownload.progress = progress
                binding.tvDownloadStatus.text = "$progress%"
                if (progress >= 100) break
                delay(500)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
