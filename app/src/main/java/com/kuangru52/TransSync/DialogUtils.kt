package com.kuangru52.transsync

import com.kuangru52.transsync.R
import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object DialogUtils {

    fun showRenameDialog(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentId: Int,
        currentName: String,
        onSuccess: () -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = context.getString(R.string.hint_new_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerFamily(com.google.android.material.shape.CornerFamily.ROUNDED)
            val radius = context.resources.displayMetrics.density * 28f
            setBoxCornerRadii(radius, radius, radius, radius)
            boxStrokeColor = ContextCompat.getColor(context, R.color.colorAccent)
            hintTextColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorAccent))
            defaultHintTextColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
        }

        val btnRename = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            id = View.generateViewId()
            text = context.getString(R.string.btn_confirm)
            textSize = 14f
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorAccent))
            setTextColor(Color.WHITE)
            stateListAnimator = null
            elevation = 0f
            isEnabled = false
            alpha = 0.5f
            cornerRadius = (context.resources.displayMetrics.density * 16f).toInt()
            setPadding((context.resources.displayMetrics.density * 16f).toInt(), 0, (context.resources.displayMetrics.density * 16f).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (context.resources.displayMetrics.density * 32f).toInt()).apply {
                gravity = Gravity.END
                topMargin = (context.resources.displayMetrics.density * 16f).toInt()
            }
            insetTop = 0
            insetBottom = 0
            minimumWidth = (context.resources.displayMetrics.density * 80f).toInt()
            textSize = 13f
        }

        val input = TextInputEditText(til.context).apply {
            setText(currentName)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding((context.resources.displayMetrics.density * 24f).toInt(), paddingTop, (context.resources.displayMetrics.density * 24f).toInt(), paddingBottom)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newName = s.toString().trim()
                    val changed = newName.isNotEmpty() && newName != currentName
                    btnRename.isEnabled = changed
                    btnRename.alpha = if (changed) 1.0f else 0.5f
                }
            })
        }
        til.addView(input)
        container.addView(til)
        container.addView(btnRename)

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(container)
            .create()

        btnRename.setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != currentName) {
                val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
                service.rpc(rpcUrl, null, RpcRequest("torrent-rename-path", mapOf("ids" to listOf(torrentId), "path" to currentName, "name" to newName)))
                    .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                        override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                            if (response.isSuccessful) {
                                onSuccess()
                                dialog.dismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                            Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }
        dialog.show()
    }

    fun showEditTrackersDialog(
        context: Context,
        currentUrls: String,
        onSave: (String) -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = context.getString(R.string.dialog_edit_tracker_title)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerFamily(com.google.android.material.shape.CornerFamily.ROUNDED)
            val radius = context.resources.displayMetrics.density * 28f
            setBoxCornerRadii(radius, radius, radius, radius)
            boxStrokeColor = ContextCompat.getColor(context, R.color.colorAccent)
            hintTextColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorAccent))
            defaultHintTextColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
        }

        val btnSave = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = context.getString(R.string.dialog_save)
            textSize = 14f
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorAccent))
            setTextColor(Color.WHITE)
            stateListAnimator = null
            elevation = 0f
            isEnabled = false
            alpha = 0.5f
            cornerRadius = (context.resources.displayMetrics.density * 16f).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (context.resources.displayMetrics.density * 32f).toInt()).apply {
                gravity = Gravity.END
                topMargin = (context.resources.displayMetrics.density * 16f).toInt()
            }
            insetTop = 0
            insetBottom = 0
            minimumWidth = (context.resources.displayMetrics.density * 80f).toInt()
            textSize = 13f
        }

        val input = TextInputEditText(til.context).apply {
            setText(currentUrls)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = Gravity.TOP
            minLines = 5
            setPadding((context.resources.displayMetrics.density * 24f).toInt(), (context.resources.displayMetrics.density * 16f).toInt(), (context.resources.displayMetrics.density * 24f).toInt(), (context.resources.displayMetrics.density * 16f).toInt())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newText = s.toString()
                    val changed = newText != currentUrls
                    btnSave.isEnabled = changed && newText.isNotBlank()
                    btnSave.alpha = if (btnSave.isEnabled) 1.0f else 0.5f
                }
            })
        }
        til.addView(input)
        container.addView(til)
        container.addView(btnSave)

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(container)
            .create()

        btnSave.setOnClickListener {
            onSave(input.text.toString())
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showDeleteDialog(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentIds: List<Int>,
        deleteDataByDefault: Boolean = true,
        onSuccess: () -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val tvMsg = TextView(context).apply {
            text = context.getString(R.string.delete_confirm_msg, torrentIds.size)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 16f
        }
        container.addView(tvMsg)

        val cbDeleteData = CheckBox(context).apply {
            text = context.getString(R.string.cb_delete_data)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 20, 0, 20)
            isChecked = deleteDataByDefault
        }
        container.addView(cbDeleteData)

        val btnContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (context.resources.displayMetrics.density * 12f).toInt()
            }
        }

        val btnDelete = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = context.getString(R.string.btn_confirm)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.state_red))
            setTextColor(Color.WHITE)
            textSize = 14f
            stateListAnimator = null
            elevation = 0f
            cornerRadius = (context.resources.displayMetrics.density * 16f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (context.resources.displayMetrics.density * 32f).toInt()
            ).apply {
                topMargin = (context.resources.displayMetrics.density * 16f).toInt()
            }
            insetTop = 0
            insetBottom = 0
            minimumWidth = (context.resources.displayMetrics.density * 80f).toInt()
            textSize = 13f
            setPadding((context.resources.displayMetrics.density * 16f).toInt(), 0, (context.resources.displayMetrics.density * 16f).toInt(), 0)
        }
        btnContainer.addView(btnDelete)
        container.addView(btnContainer)

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(container)
            .create()

        btnDelete.setOnClickListener {
            val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
            val args = mapOf("ids" to torrentIds, "delete-local-data" to cbDeleteData.isChecked)
            service.rpc(rpcUrl, null, RpcRequest("torrent-remove", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        onSuccess()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                    Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                }
            })
        }
        dialog.show()
    }

    fun showSetLocationDialog(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentIds: List<Int>,
        currentDir: String?,
        allTorrents: List<Torrent>?,
        onSuccess: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_set_location, null)
        val etDir = view.findViewById<AutoCompleteTextView>(R.id.etDownloadDir)
        val cbMove = view.findViewById<CheckBox>(R.id.cbMoveData)
        val tvFree = view.findViewById<TextView>(R.id.tvFreeSpace)
        val btnConfirm = view.findViewById<View>(R.id.btnConfirm)

        etDir.setText(currentDir ?: "")

        // 使用 DownloadDirManager 统一获取并绑定 Adapter，传入所有种子以嗅探目录
        val allDirs = DownloadDirManager.getAllDirs(context, allTorrents)
        DownloadDirManager.setupAdapter(etDir, allDirs)

        fun updateFreeSpace(path: String) {
            if (path.isEmpty()) {
                tvFree.visibility = View.GONE
                return
            }
            val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
            service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to path))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                        tvFree.text = context.getString(R.string.free_space_label, formatSize(size))
                        tvFree.visibility = View.VISIBLE
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
        }

        etDir.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateFreeSpace(s.toString().trim())
            }
        })

        if (!currentDir.isNullOrEmpty()) {
            updateFreeSpace(currentDir)
        }

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(view)
            .create()

        btnConfirm.setOnClickListener {
            val newLocation = etDir.text.toString().trim()
            if (newLocation.isNotEmpty()) {
                val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
                val args = mapOf(
                    "ids" to torrentIds,
                    "location" to newLocation,
                    "move" to cbMove.isChecked
                )
                val request = RpcRequest("torrent-set-location", args)
                service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        if (response.isSuccessful) {
                            Toast.makeText(context, R.string.msg_location_updated, Toast.LENGTH_SHORT).show()
                            onSuccess()
                            DownloadDirManager.saveDirToHistory(context, newLocation)
                            dialog.dismiss()
                        } else {
                            Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                        Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        dialog.show()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupDownloadDirAdapter(etDir: AutoCompleteTextView, dirs: List<String>) {
        DownloadDirManager.setupAdapter(etDir, dirs)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
