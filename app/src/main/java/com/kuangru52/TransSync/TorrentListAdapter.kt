package com.kuangru52.transsync

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.graphics.BlurMaskFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.MaskFilterSpan
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kuangru52.transsync.databinding.ItemTorrentBinding
import java.util.Locale

class TorrentListAdapter(
    private val onTorrentClick: (Torrent) -> Unit,
    private val onStatusClick: (Torrent) -> Unit,
    private val onTorrentLongClick: (Torrent) -> Unit,
    private val onSelectionChange: (Torrent, Boolean) -> Unit
) : ListAdapter<Torrent, TorrentListAdapter.TorrentViewHolder>(TorrentDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    private val selectedIds = mutableSetOf<Int>()
    var isSelectionMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                val oldSelected = selectedIds.toList()
                selectedIds.clear()
                oldSelected.forEach { id ->
                    val pos = currentList.indexOfFirst { it.id == id }
                    if (pos != -1) notifyItemChanged(pos, "selection")
                }
            } else {
                notifyItemRangeChanged(0, itemCount, "selection")
            }
        }

    var isTrackerBlurEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, "tracker_blur")
        }

    var revealedTrackerNames: Set<String> = emptySet()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount, "tracker_blur")
        }

    fun getSelectedIds(): List<Int> = selectedIds.toList()

    fun toggleSelection(id: Int) {
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            notifyItemChanged(index, "selection")
        }
    }

    fun selectAll() {
        val oldIds = selectedIds.toSet()
        selectedIds.addAll(currentList.map { it.id })
        currentList.forEachIndexed { index, torrent ->
            if (!oldIds.contains(torrent.id)) {
                notifyItemChanged(index, "selection")
            }
        }
    }

    fun clearSelection() {
        val oldSelected = selectedIds.toList()
        selectedIds.clear()
        oldSelected.forEach { id ->
            val pos = currentList.indexOfFirst { it.id == id }
            if (pos != -1) notifyItemChanged(pos, "selection")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentViewHolder {
        val binding = ItemTorrentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TorrentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TorrentViewHolder, position: Int) {
        val torrent = getItem(position)
        holder.bind(torrent, selectedIds.contains(torrent.id))
    }

    override fun onBindViewHolder(holder: TorrentViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.partialUpdate(getItem(position), payloads)
        }
    }

    class TorrentDiffCallback : DiffUtil.ItemCallback<Torrent>() {
        override fun areItemsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            // 如果是 H&R 种子，强制返回 false 以确保每次列表刷新都更新倒计时
            if (newItem.labels?.any { it.startsWith("HR:") } == true) return false

            val oldLabels = oldItem.labels
            val newLabels = newItem.labels
            val labelsEqual = if (oldLabels == null) {
                newLabels == null
            } else if (newLabels == null) {
                false
            } else {
                oldLabels.size == newLabels.size && oldLabels.containsAll(newLabels)
            }

            return oldItem.name == newItem.name &&
                    oldItem.status == newItem.status &&
                    oldItem.displayProgress == newItem.displayProgress &&
                    oldItem.displayColor == newItem.displayColor &&
                    oldItem.displaySize == newItem.displaySize &&
                    oldItem.displayDownloadSpeed == newItem.displayDownloadSpeed &&
                    oldItem.displayUploadSpeed == newItem.displayUploadSpeed &&
                    oldItem.displayStats == newItem.displayStats &&
                    oldItem.error == newItem.error &&
                    oldItem.errorString == newItem.errorString &&
                    labelsEqual &&
                    oldItem.doneDate == newItem.doneDate
        }

        override fun getChangePayload(oldItem: Torrent, newItem: Torrent): Any? {
            val payloads = mutableSetOf<String>()
            if (oldItem.displayDownloadSpeed != newItem.displayDownloadSpeed || oldItem.displayUploadSpeed != newItem.displayUploadSpeed) {
                payloads.add("speed")
            }
            if (oldItem.displayProgress != newItem.displayProgress || oldItem.displayColor != newItem.displayColor) {
                payloads.add("progress")
            }
            if (oldItem.status != newItem.status) {
                payloads.add("status")
            }
            if (oldItem.error != newItem.error || oldItem.errorString != newItem.errorString) {
                payloads.add("error")
            }
            if (oldItem.displaySize != newItem.displaySize) {
                payloads.add("size")
            }
            if (oldItem.displayStats != newItem.displayStats) {
                payloads.add("stats")
            }
            
            // 只要包含 HR 标签就强制进行刷新更新，确保倒计时在每次列表刷新都更新
            val hasHr = newItem.labels?.any { it.startsWith("HR:") } == true
            if (hasHr || oldItem.labels != newItem.labels ||
                oldItem.doneDate != newItem.doneDate || oldItem.addedDate != newItem.addedDate) {
                payloads.add("hr")
            }

            return if (payloads.isEmpty()) null else payloads
        }
    }

    inner class TorrentViewHolder(private val binding: ItemTorrentBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(torrent: Torrent, isSelected: Boolean) {
            val context = itemView.context
            itemView.isSelected = isSelected
            itemView.isActivated = isSelected
            binding.tvName.text = torrent.name
            
            val color = getDisplayColor(context, torrent)
            binding.progressFill.backgroundTintList = ColorStateList.valueOf(color)
            binding.tvSizeInfo.text = torrent.displaySize

            updateProgressPercent(torrent)

            updateTrackerTag(torrent.trackerName)

            if (torrent.error != 0 && torrent.errorString.isNotEmpty()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = torrent.errorString
            } else {
                binding.tvError.visibility = View.GONE
            }

            binding.tvStatsLeft.text = torrent.displayStats
            binding.tvDownloadSpeed.text = torrent.displayDownloadSpeed
            binding.tvUploadSpeed.text = torrent.displayUploadSpeed

            updateStatusIcon(torrent)
            updateHrTag(torrent)

            binding.btnStatus.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onStatusClick(getItem(pos))
                }
            }
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val current = getItem(pos)
                    if (isSelectionMode) onSelectionChange(current, !itemView.isSelected) else onTorrentClick(current)
                }
            }
            itemView.setOnLongClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTorrentLongClick(getItem(pos))
                }
                true
            }
        }

        fun partialUpdate(torrent: Torrent, payloads: List<Any>) {
            val context = itemView.context
            val payloadSet = mutableSetOf<String>()
            payloads.forEach {
                if (it is Set<*>) @Suppress("UNCHECKED_CAST") payloadSet.addAll(it as Set<String>)
                else if (it is String) payloadSet.add(it)
            }
            
            if (payloadSet.contains("selection")) {
                val isSelected = selectedIds.contains(torrent.id)
                itemView.isSelected = isSelected
                itemView.isActivated = isSelected
            }
            if (payloadSet.contains("speed")) {
                binding.tvDownloadSpeed.text = torrent.displayDownloadSpeed
                binding.tvUploadSpeed.text = torrent.displayUploadSpeed
            }
            if (payloadSet.contains("progress")) {
                val color = getDisplayColor(context, torrent)
                binding.progressFill.backgroundTintList = ColorStateList.valueOf(color)
                updateProgressPercent(torrent)
                if (torrent.displayStatusText.isNotEmpty()) {
                    binding.tvVerificationStatus.visibility = View.VISIBLE
                    binding.tvVerificationStatus.text = torrent.displayStatusText
                } else {
                    binding.tvVerificationStatus.visibility = View.GONE
                }
            }
            if (payloadSet.contains("size")) {
                binding.tvSizeInfo.text = torrent.displaySize
            }
            if (payloadSet.contains("stats")) {
                binding.tvStatsLeft.text = torrent.displayStats
            }
            if (payloadSet.contains("status")) {
                updateStatusIcon(torrent)
            }
            if (payloadSet.contains("hr")) {
                updateHrTag(torrent)
            }
            if (payloadSet.contains("error")) {
                if (torrent.error != 0) {
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = torrent.errorString
                } else {
                    binding.tvError.visibility = View.GONE
                }
            }
            if (payloadSet.contains("tracker_blur")) {
                updateTrackerTag(torrent.trackerName)
            }
        }

        private fun updateTrackerTag(trackerName: String) {
            if (trackerName.isNotEmpty()) {
                binding.tvTrackerName.visibility = View.VISIBLE
                val shouldBlur = isTrackerBlurEnabled && !revealedTrackerNames.contains(trackerName)
                
                if (shouldBlur) {
                    binding.tvTrackerName.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    val spannable = SpannableString(trackerName)
                    spannable.setSpan(
                        MaskFilterSpan(BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)),
                        0,
                        trackerName.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    binding.tvTrackerName.text = spannable
                } else {
                    binding.tvTrackerName.setLayerType(View.LAYER_TYPE_NONE, null)
                    binding.tvTrackerName.text = trackerName
                }
            } else {
                binding.tvTrackerName.visibility = View.GONE
            }
        }

        private fun getDisplayColor(context: android.content.Context, torrent: Torrent): Int {
            return when {
                torrent.error != 0 || (torrent.errorString.isNotEmpty() && !torrent.errorString.contains("none", ignoreCase = true)) -> 
                    ContextCompat.getColor(context, R.color.state_red)
                torrent.status == 1 || torrent.status == 2 -> 
                    ContextCompat.getColor(context, R.color.state_yellow)
                torrent.status == 0 -> 
                    ContextCompat.getColor(context, R.color.state_gray)
                torrent.percentDone >= 1.0 -> 
                    ContextCompat.getColor(context, R.color.state_green)
                else -> 
                    ContextCompat.getColor(context, R.color.state_blue)
            }
        }

        private fun updateStatusIcon(torrent: Torrent) {
            val context = itemView.context
            binding.ivStatusIcon.setImageResource(if (torrent.status == 0) R.drawable.ic_play else R.drawable.ic_pause)
            val color = if (torrent.status == 0) ContextCompat.getColor(context, R.color.state_gray) else ContextCompat.getColor(context, R.color.button_color)
            val tint = ColorStateList.valueOf(color)
            binding.ivStatusIcon.imageTintList = tint
            binding.btnStatus.backgroundTintList = ColorStateList.valueOf(color).withAlpha(30)
        }

        private fun updateHrTag(torrent: Torrent) {
            val hrLabel = torrent.labels?.find { it.startsWith("HR:") }
            if (hrLabel == null) {
                binding.tvHrTag.visibility = View.GONE
                return
            }

            val hours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
            if (hours <= 0) {
                binding.tvHrTag.visibility = View.GONE
                return
            }

            binding.tvHrTag.visibility = View.VISIBLE
            
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val px8 = (8 * density).toInt()
            val px18 = (18 * density).toInt()
            
            // 设置为默认胶囊样式
            binding.tvHrTag.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            binding.tvHrTag.setPadding(px8, 0, px8, 0)
            binding.tvHrTag.setBackgroundResource(R.drawable.bg_tag_capsule)
            binding.tvHrTag.backgroundTintList = null
            val params = binding.tvHrTag.layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            binding.tvHrTag.layoutParams = params
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(binding.tvHrTag, null)

            // 如果未下载完毕，显示 "H&R"
            if (torrent.percentDone < 1.0) {
                binding.tvHrTag.text = context.getString(R.string.hr_tag)
                binding.tvHrTag.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                return
            }

            // 下载完成后，计算剩余时间
            val doneDate = torrent.doneDate * 1000L // 转换为毫秒
            val currentTime = System.currentTimeMillis()
            val totalRequiredMs = (hours * 3600 * 1000L).toLong()
            val elapsedMs = currentTime - doneDate
            val remainingMs = totalRequiredMs - elapsedMs
            val bufferMs = 30 * 60 * 1000L // 30分钟缓冲

            if (remainingMs <= -bufferMs) {
                // 已达到核销时间上限 - 直接显示图标，隐藏文字，节省空间
                binding.tvHrTag.text = ""
                binding.tvHrTag.setPadding(0, 0, 0, 0)
                params.width = px18
                binding.tvHrTag.layoutParams = params
                
                // 彻底移除背景胶囊，只显示图标本身颜色
                binding.tvHrTag.background = null
                
                val doneDrawable = ContextCompat.getDrawable(context, R.drawable.ic_done)
                doneDrawable?.setBounds(0, 0, px18, px18)
                
                binding.tvHrTag.setCompoundDrawables(doneDrawable, null, null, null)
                // 再次确认没有 Tint 影响，使用图标原始色调（蓝圆白勾对号）
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(binding.tvHrTag, null)
            } else if (remainingMs > 0) {
                // 正常的数值倒计时
                binding.tvHrTag.backgroundTintList = null 
                val totalMins = remainingMs / (60 * 1000L)
                val hrs = totalMins / 60
                val mins = totalMins % 60
                binding.tvHrTag.text = context.getString(R.string.hr_list_countdown_format, hrs, mins)
                binding.tvHrTag.setTextColor(ContextCompat.getColor(context, R.color.state_blue))
            } else {
                // 等待核销（30分钟缓冲区内）
                binding.tvHrTag.backgroundTintList = null 
                val remainingBufferMs = bufferMs + remainingMs
                val totalSecs = remainingBufferMs.coerceAtLeast(0L) / 1000L
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                binding.tvHrTag.text = String.format(Locale.US, "%02d:%02d", mins, secs)
                binding.tvHrTag.setTextColor(ContextCompat.getColor(context, R.color.state_blue))
            }
        }

        private fun updateProgressPercent(torrent: Torrent) {
            val params = binding.tvProgressPercent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            
            if (torrent.displayProgress >= 1000) {
                binding.tvProgressPercent.visibility = View.GONE
                params.horizontalBias = 1.0f
                binding.tvProgressPercent.layoutParams = params
                return
            }
            
            binding.tvProgressPercent.visibility = View.VISIBLE
            val progress = torrent.displayProgress / 10f
            val percentText = String.format(Locale.US, "%.1f", progress)
            binding.tvProgressPercent.text = percentText
            
            // 计算百分比文字的位置，使其通过约束跟随 progressFill
            params.horizontalBias = torrent.displayProgress / 1000f
            binding.tvProgressPercent.layoutParams = params
        }
    }
}
