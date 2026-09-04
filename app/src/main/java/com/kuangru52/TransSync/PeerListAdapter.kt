package com.kuangru52.transsync

import com.kuangru52.transsync.R
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kuangru52.transsync.databinding.ItemPeerBinding
import java.util.*

class PeerListAdapter : ListAdapter<Peer, PeerListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPeerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemPeerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(peer: Peer) {
            binding.tvPeerAddress.text = peer.address
            binding.tvPeerClient.text = peer.clientName
            binding.tvPeerFlags.text = peer.flagStr
            binding.tvPeerProgress.text = binding.root.context.getString(R.string.peer_progress, "${(peer.progress * 100).toInt()}%")
            binding.tvPeerDownSpeed.text = "↓ ${FormatUtils.formatSpeed(peer.rateToClient.toDouble())}"
            binding.tvPeerUpSpeed.text = "↑ ${FormatUtils.formatSpeed(peer.rateToPeer.toDouble())}"

            // Fetch and set country emoji
            binding.tvPeerFlag.visibility = android.view.View.GONE
            GeoIpService.getCountryEmoji(peer.address) { emoji ->
                if (emoji != null) {
                    binding.tvPeerFlag.text = emoji
                    binding.tvPeerFlag.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Peer>() {
        override fun areItemsTheSame(oldItem: Peer, newItem: Peer) = oldItem.address == newItem.address
        override fun areContentsTheSame(oldItem: Peer, newItem: Peer) = oldItem == newItem
    }
}
