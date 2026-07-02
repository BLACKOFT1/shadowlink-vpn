package com.shadowlink.vpn.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shadowlink.vpn.R
import com.shadowlink.vpn.databinding.ItemProfileBinding
import com.shadowlink.vpn.models.VpnProfile
import com.shadowlink.vpn.utils.PrefsManager

class ProfilesAdapter(
    private val onSelect: (VpnProfile) -> Unit,
    private val onDelete: (VpnProfile) -> Unit,
    private val onShare: (VpnProfile) -> Unit
) : ListAdapter<VpnProfile, ProfilesAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<VpnProfile>() {
            override fun areItemsTheSame(old: VpnProfile, new: VpnProfile) = old.id == new.id
            override fun areContentsTheSame(old: VpnProfile, new: VpnProfile) = old == new
        }
    }

    inner class ViewHolder(private val binding: ItemProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: VpnProfile) {
            binding.tvProfileName.text = profile.name
            binding.tvProtocol.text = profile.protocol.replace("_", " ")
            binding.tvHost.text = "${profile.host}:${profile.port}"
            binding.tvCountry.text = profile.country.ifEmpty { "—" }

            // Badge premium / local
            if (profile.isPremium) {
                binding.badgePremium.visibility = View.VISIBLE
                binding.badgeLocal.visibility = View.GONE
            } else if (profile.isLocal) {
                binding.badgeLocal.visibility = View.VISIBLE
                binding.badgePremium.visibility = View.GONE
            } else {
                binding.badgePremium.visibility = View.GONE
                binding.badgeLocal.visibility = View.GONE
            }

            // Surligner le profil sélectionné
            val isSelected = profile.id == PrefsManager.lastUsedProfileId
            binding.root.setBackgroundResource(
                if (isSelected) R.drawable.bg_profile_selected else R.drawable.bg_profile_normal
            )
            binding.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Clics
            binding.root.setOnClickListener { onSelect(profile) }
            binding.btnDelete.setOnClickListener { onDelete(profile) }
            binding.btnShare.setOnClickListener { onShare(profile) }

            // Afficher le bouton delete seulement pour les profils locaux
            binding.btnDelete.visibility = if (profile.isLocal) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProfileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
