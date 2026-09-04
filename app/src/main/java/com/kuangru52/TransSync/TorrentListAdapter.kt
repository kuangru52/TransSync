package com.kuangru52.transsync

import com.kuangru52.transsync.R
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_torrent, parent, false)
        return TorrentViewHolder(view)
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
                    (oldItem.labels ?: emptyList<String>()) == (newItem.labels ?: emptyList<String>()) &&
                    oldItem.doneDate == newItem.doneDate &&
                    // ����� H&R ���ӣ�ǿ�Ʒ��� false ��ȷ��ÿ���б�ˢ�¶������¼��㵹��ʱ
                    newItem.labels?.any { it.startsWith("HR:") } != true
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
            
            // ֻҪ���� HR ��ǩ����ǿ�Ƽ���ˢ�¸��أ�ȷ������ʱ����ÿ���б�ˢ�¶�����
            val hasHr = newItem.labels?.any { it.startsWith("HR:") } == true
            if (hasHr || (oldItem.labels ?: emptyList<String>()) != (newItem.labels ?: emptyList<String>()) || oldItem.doneDate != newItem.doneDate) {
                payloads.add("hr")
            }

            return if (payloads.isEmpty()) null else payloads
        }
    }

    inner class TorrentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSizeInfo: TextView = itemView.findViewById(R.id.tvSizeInfo)
        private val progressFill: View = itemView.findViewById(R.id.progressFill)
        private val tvDownloadSpeed: TextView = itemView.findViewById(R.id.tvDownloadSpeed)
        private val tvUploadSpeed: TextView = itemView.findViewById(R.id.tvUploadSpeed)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)
        private val btnStatus: View = itemView.findViewById(R.id.btnStatus)
        private val tvTrackerName: TextView = itemView.findViewById(R.id.tvTrackerName)
        private val tvStatsLeft: TextView = itemView.findViewById(R.id.tvStatsLeft)
        private val tvVerificationStatus: TextView = itemView.findViewById(R.id.tvVerificationStatus)
        private val tvError: TextView = itemView.findViewById(R.id.tvError)
        private val tvProgressPercent: TextView = itemView.findViewById(R.id.tvProgressPercent)
        private val tvHrTag: TextView = itemView.findViewById(R.id.tvHrTag)

        fun bind(torrent: Torrent, isSelected: Boolean) {
            itemView.isSelected = isSelected
            itemView.isActivated = isSelected
            tvName.text = torrent.name
            
            progressFill.backgroundTintList = ColorStateList.valueOf(torrent.displayColor)
            tvSizeInfo.text = torrent.displaySize

            updateProgressPercent(torrent)

            val trackerDisplay = torrent.trackers?.firstOrNull()?.let { TrackerUtils.getTrackerNameFromUrl(it.announce) } ?: ""
            if (trackerDisplay.isNotEmpty()) {
                tvTrackerName.visibility = View.VISIBLE
                tvTrackerName.text = trackerDisplay
            } else {
                tvTrackerName.visibility = View.GONE
            }

            if (torrent.error != 0 && torrent.errorString.isNotEmpty()) {
                tvError.visibility = View.VISIBLE
                tvError.text = torrent.errorString
            } else {
                tvError.visibility = View.GONE
            }

            tvStatsLeft.text = torrent.displayStats
            tvDownloadSpeed.text = torrent.displayDownloadSpeed
            tvUploadSpeed.text = torrent.displayUploadSpeed

            updateStatusIcon(torrent)
            updateHrTag(torrent)

            btnStatus.setOnClickListener {
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
                tvDownloadSpeed.text = torrent.displayDownloadSpeed
                tvUploadSpeed.text = torrent.displayUploadSpeed
            }
            if (payloadSet.contains("progress")) {
                progressFill.backgroundTintList = ColorStateList.valueOf(torrent.displayColor)
                updateProgressPercent(torrent)
                if (torrent.displayStatusText.isNotEmpty()) {
                    tvVerificationStatus.visibility = View.VISIBLE
                    tvVerificationStatus.text = torrent.displayStatusText
                } else {
                    tvVerificationStatus.visibility = View.GONE
                }
            }
            if (payloadSet.contains("size")) {
                tvSizeInfo.text = torrent.displaySize
            }
            if (payloadSet.contains("stats")) {
                tvStatsLeft.text = torrent.displayStats
            }
            if (payloadSet.contains("status")) {
                updateStatusIcon(torrent)
            }
            if (payloadSet.contains("hr")) {
                updateHrTag(torrent)
            }
            if (payloadSet.contains("error")) {
                if (torrent.error != 0) {
                    tvError.visibility = View.VISIBLE
                    tvError.text = torrent.errorString
                } else {
                    tvError.visibility = View.GONE
                }
            }
        }

        private fun updateStatusIcon(torrent: Torrent) {
            val context = itemView.context
            ivStatusIcon.setImageResource(if (torrent.status == 0) R.drawable.ic_play else R.drawable.ic_pause)
            val color = if (torrent.status == 0) ContextCompat.getColor(context, R.color.state_gray) else ContextCompat.getColor(context, R.color.button_color)
            val tint = ColorStateList.valueOf(color)
            ivStatusIcon.imageTintList = tint
            btnStatus.backgroundTintList = ColorStateList.valueOf(color).withAlpha(30)
        }

        private fun updateHrTag(torrent: Torrent) {
            val hrLabel = torrent.labels?.find { it.startsWith("HR:") }
            if (hrLabel == null) {
                tvHrTag.visibility = View.GONE
                return
            }

            val hours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
            if (hours <= 0) {
                tvHrTag.visibility = View.GONE
                return
            }

            tvHrTag.visibility = View.VISIBLE
            
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val px8 = (8 * density).toInt()
            val px18 = (18 * density).toInt()
            
            // ����ΪĬ�Ͻ�����ʽ
            tvHrTag.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tvHrTag.setPadding(px8, 0, px8, 0)
            tvHrTag.setBackgroundResource(R.drawable.bg_tag_capsule)
            tvHrTag.backgroundTintList = null
            val params = tvHrTag.layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            tvHrTag.layoutParams = params
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(tvHrTag, null)

            // �����û�����꣬��ʾ "H&R"
            if (torrent.percentDone < 1.0) {
                tvHrTag.text = context.getString(R.string.hr_tag)
                tvHrTag.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                return
            }

            // �������ɣ�����ʣ��ʱ��
            val doneDate = torrent.doneDate * 1000L // ת��Ϊ����
            val currentTime = System.currentTimeMillis()
            val totalRequiredMs = (hours * 3600 * 1000L).toLong()
            val elapsedMs = currentTime - doneDate
            val remainingMs = totalRequiredMs - elapsedMs
            val bufferMs = 30 * 60 * 1000L // 30���ӻ���

            if (remainingMs <= -bufferMs) {
                // �Ѵ���ҳ��������� - ֱ����ʾͼ�꣬�������֣���������
                tvHrTag.text = ""
                tvHrTag.setPadding(0, 0, 0, 0)
                params.width = px18
                tvHrTag.layoutParams = params
                
                // �����Ƴ��������ң�ֻ��ʾͼ�걾����ɫ
                tvHrTag.background = null
                
                val doneDrawable = ContextCompat.getDrawable(context, R.drawable.ic_done)
                val iconSize = px18
                doneDrawable?.setBounds(0, 0, iconSize, iconSize)
                
                tvHrTag.setCompoundDrawables(doneDrawable, null, null, null)
                // �ٴ�ȷ��û�� Tint Ӱ�죬ʹ��ͼ��ԭʼɫ�ʣ���Բ�װ׶Ժţ�
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(tvHrTag, null)
            } else if (remainingMs > 0) {
                // �������ֵ���ʱ
                tvHrTag.backgroundTintList = null 
                val totalMins = remainingMs / (60 * 1000L)
                val hrs = totalMins / 60
                val mins = totalMins % 60
                tvHrTag.text = context.getString(R.string.hr_list_countdown_format, hrs, mins)
                tvHrTag.setTextColor(ContextCompat.getColor(context, R.color.state_blue))
            } else {
                // ��ȴʱ�䣨30���ӻ����ڣ�
                tvHrTag.backgroundTintList = null 
                val remainingBufferMs = bufferMs + remainingMs
                val totalSecs = Math.max(0, remainingBufferMs / 1000L)
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                tvHrTag.text = String.format(Locale.US, "%02d:%02d", mins, secs)
                tvHrTag.setTextColor(ContextCompat.getColor(context, R.color.state_blue))
            }
        }

        private fun updateProgressPercent(torrent: Torrent) {
            val params = tvProgressPercent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            
            if (torrent.displayProgress >= 1000) {
                tvProgressPercent.visibility = View.GONE
                params.horizontalBias = 1.0f
                tvProgressPercent.layoutParams = params
                return
            }
            
            tvProgressPercent.visibility = View.VISIBLE
            val progress = torrent.displayProgress / 10f
            val percentText = String.format(Locale.US, "%.1f", progress)
            tvProgressPercent.text = percentText
            
            // �������ֵ�λ�ã��Ӷ�ͨ��Լ������ progressFill
            params.horizontalBias = torrent.displayProgress / 1000f
            tvProgressPercent.layoutParams = params
        }
    }
}
