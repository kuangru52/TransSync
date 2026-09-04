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
            binding.tvPeerDownSpeed.text = "↓ ${formatSpeed(peer.rateToClient.toLong())}"
            binding.tvPeerUpSpeed.text = "↑ ${formatSpeed(peer.rateToPeer.toLong())}"

            // Fetch and set country emoji
            binding.tvPeerFlag.visibility = android.view.View.GONE
            GeoIpService.getCountryEmoji(peer.address) { emoji ->
                if (emoji != null) {
                    binding.tvPeerFlag.text = emoji
                    binding.tvPeerFlag.visibility = android.view.View.VISIBLE
                }
            }
        }

        private fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0) return "0 KB/s"
            val kbs = bytesPerSec / 1024.0
            return if (kbs < 1024) {
                String.format(Locale.US, "%.1f KB/s", kbs)
            } else {
                String.format(Locale.US, "%.1f MB/s", kbs / 1024.0)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Peer>() {
        override fun areItemsTheSame(oldItem: Peer, newItem: Peer) = oldItem.address == newItem.address
        override fun areContentsTheSame(oldItem: Peer, newItem: Peer) = oldItem == newItem
    }
}
