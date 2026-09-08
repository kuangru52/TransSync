package com.kuangru52.transsync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class TorrentFileAdapter : RecyclerView.Adapter<TorrentFileAdapter.ViewHolder>() {

    private var allNodes = mutableListOf<FileNode>() // 所有的原始树节点（逻辑结构）
    private var visibleNodes = mutableListOf<FileNode>() // 当前显示的节点（打平后的列表）

    data class FileNode(
        val name: String,
        val isFolder: Boolean,
        val level: Int,
        var isExpanded: Boolean = false,
        val length: Long = 0,
        val bytesCompleted: Long = 0,
        val children: MutableList<FileNode> = mutableListOf()
    )

    fun setFiles(files: List<TorrentFile>) {
        val expandedPaths = getExpandedPaths(allNodes)
        val rootNodes = buildTree(files, expandedPaths)
        allNodes = rootNodes.toMutableList()
        updateVisibleNodes()
    }

    private fun getExpandedPaths(nodes: List<FileNode>, parentPath: String = ""): Set<String> {
        val paths = mutableSetOf<String>()
        for (node in nodes) {
            val currentPath = if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
            if (node.isFolder) {
                if (node.isExpanded) {
                    paths.add(currentPath)
                }
                paths.addAll(getExpandedPaths(node.children, currentPath))
            }
        }
        return paths
    }

    private fun buildTree(files: List<TorrentFile>, expandedPaths: Set<String> = emptySet()): List<FileNode> {
        val root = FileNode("", true, -1)
        for (file in files) {
            val parts = file.name.split("/")
            var currentNode = root
            var path = ""
            for (i in parts.indices) {
                val part = parts[i]
                path = if (path.isEmpty()) part else "$path/$part"
                val isLast = i == parts.size - 1
                var child = currentNode.children.find { it.name == part }
                if (child == null) {
                    child = FileNode(
                        name = part,
                        isFolder = !isLast,
                        level = i,
                        isExpanded = !isLast && expandedPaths.contains(path),
                        length = if (isLast) file.length else 0,
                        bytesCompleted = if (isLast) file.bytesCompleted else 0
                    )
                    currentNode.children.add(child)
                }
                currentNode = child
            }
        }
        return root.children
    }

    private fun updateVisibleNodes() {
        visibleNodes.clear()
        fun addNode(node: FileNode) {
            visibleNodes.add(node)
            if (node.isFolder && node.isExpanded) {
                node.children.sortBy { !it.isFolder }
                node.children.forEach { addNode(it) }
            }
        }
        allNodes.sortBy { !it.isFolder }
        allNodes.forEach { addNode(it) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_torrent_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = visibleNodes[position]
        holder.bind(node)
        holder.itemView.setOnClickListener {
            if (node.isFolder) {
                node.isExpanded = !node.isExpanded
                if (!node.isExpanded) {
                    collapseAllChildren(node)
                }
                updateVisibleNodes()
            }
        }
    }

    private fun collapseAllChildren(node: FileNode) {
        node.children.forEach { child ->
            if (child.isFolder) {
                child.isExpanded = false
                collapseAllChildren(child)
            }
        }
    }

    override fun getItemCount() = visibleNodes.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val vIndent: View = view.findViewById(R.id.vIndent)
        private val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        private val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        private val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        private val tvFileProgress: TextView = view.findViewById(R.id.tvFileProgress)

        fun bind(node: FileNode) {
            val params = vIndent.layoutParams
            params.width = node.level * 32
            vIndent.layoutParams = params

            tvFileName.text = node.name
            
            if (node.isFolder) {
                ivIcon.visibility = View.VISIBLE
                ivIcon.setImageResource(if (node.isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right)
                tvFileSize.visibility = View.GONE
                tvFileProgress.visibility = View.GONE
            } else {
                ivIcon.visibility = View.INVISIBLE
                tvFileSize.visibility = View.VISIBLE
                tvFileProgress.visibility = View.VISIBLE
                tvFileSize.text = FormatUtils.formatSize(node.length)
                val progress = if (node.length > 0) (node.bytesCompleted.toDouble() / node.length * 100) else 0.0
                tvFileProgress.text = String.format(Locale.US, "%.1f%%", progress)
            }
        }
    }
}
