package com.kuangru52.transsync

import android.net.Uri
import android.util.Base64
import android.widget.EditText
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
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
                val service = TransmissionClient.getService(rpcUrl, user, pass)
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
                topMargin = (context.resources.displayMetrics.density * 24f).toInt()
            }
        }

        val btnDelete = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = context.getString(R.string.btn_confirm)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.state_red))
            setTextColor(Color.WHITE)
            stateListAnimator = null
            elevation = 0f
            cornerRadius = (context.resources.displayMetrics.density * 16f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (context.resources.displayMetrics.density * 32f).toInt()
            )
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
            val service = TransmissionClient.getService(rpcUrl, user, pass)
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

    fun showAddTorrentDialog(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        initialUrl: String? = null,
        initialFileUri: Uri? = null,
        allTorrents: List<Torrent>? = null,
        onPickFile: (() -> Unit)? = null,
        onSuccess: () -> Unit,
        onDismiss: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_torrent, null)
        val tilUrl = view.findViewById<TextInputLayout>(R.id.tilTorrentUrl)
        val etUrl = view.findViewById<TextInputEditText>(R.id.etTorrentUrl)
        val etDir = view.findViewById<AutoCompleteTextView>(R.id.etDownloadDir)
        val etHr = view.findViewById<TextInputEditText>(R.id.etHrLabel)
        val llHrQuickButtons = view.findViewById<LinearLayout>(R.id.llHrQuickButtons)
        val btnAdd = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddTorrent)
        val tvFree = view.findViewById<TextView>(R.id.tvFreeSpace)

        tilUrl.setEndIconOnClickListener {
            onPickFile?.invoke()
        }

        if (initialUrl != null) {
            etUrl.setText(initialUrl)
        }

        if (initialFileUri != null) {
            etUrl.setText(initialFileUri.lastPathSegment ?: "Local file selected")
        }

        setupHrSlidingInput(llHrQuickButtons, etHr)

        val tilHr = view.findViewById<TextInputLayout>(R.id.tilHrLabel)
        etHr.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tilHr.prefixText = if (s.isNullOrEmpty()) "-- " else null
            }
        })

        val allDirs = DownloadDirManager.getAllDirs(context, allTorrents)
        DownloadDirManager.setupAdapter(etDir, allDirs)

        fun updateFreeSpace(path: String) {
            if (path.isEmpty()) {
                tvFree.visibility = View.GONE
                return
            }
            val service = TransmissionClient.getService(rpcUrl, user, pass)
            service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to path))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                        tvFree.text = context.getString(R.string.free_space_label, FormatUtils.formatSize(size))
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

        val dialog = AlertDialog.Builder(context, R.style.FabDialogTheme)
            .setView(view)
            .create()

        btnAdd.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val downloadDir = etDir.text.toString().trim()
            val selectedHr = etHr.text.toString().trim()
            // 修复解析逻辑：提取数字部分，处理带单位的情况（如 "3 天"）
            val days = selectedHr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            val hrLabel = if (days > 0) "HR:${days * 24}" else null

            btnAdd.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                btnAdd.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()

            dialog.dismiss()

            if (downloadDir.isNotEmpty()) {
                DownloadDirManager.saveDirToHistory(context, downloadDir)
            }

            val service = TransmissionClient.getService(rpcUrl, user, pass)

            if (initialFileUri != null) {
                val inputStream = context.contentResolver.openInputStream(initialFileUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val args = mutableMapOf<String, Any>("metainfo" to base64)
                    if (downloadDir.isNotEmpty()) args["download-dir"] = downloadDir
                    hrLabel?.let { args["labels"] = listOf(it) }

                    service.rpc(rpcUrl, null, RpcRequest("torrent-add", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                        override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                            if (response.isSuccessful) {
                                onSuccess()
                                Toast.makeText(context, R.string.msg_torrent_added_success, Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                            Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            } else if (url.isNotEmpty()) {
                val args = mutableMapOf<String, Any>("filename" to url)
                if (downloadDir.isNotEmpty()) args["download-dir"] = downloadDir
                hrLabel?.let { args["labels"] = listOf(it) }

                service.rpc(rpcUrl, null, RpcRequest("torrent-add", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        if (response.isSuccessful) {
                            onSuccess()
                            Toast.makeText(context, R.string.msg_torrent_added_success, Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                        Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }
        dialog.show()
    }

    fun showSetHrDialog(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentIds: List<Int>,
        allTorrents: List<Torrent>?,
        onSuccess: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_torrent, null)
        val etHr = view.findViewById<TextInputEditText>(R.id.etHrLabel)
        val llHrQuickButtons = view.findViewById<LinearLayout>(R.id.llHrQuickButtons)
        val btnAction = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddTorrent)

        view.findViewById<View>(R.id.tilTorrentUrl)?.visibility = View.GONE
        view.findViewById<View>(R.id.tilDownloadDir)?.visibility = View.GONE
        view.findViewById<View>(R.id.tvFreeSpace)?.visibility = View.GONE

        btnAction?.text = context.getString(R.string.btn_confirm)

        setupHrSlidingInput(llHrQuickButtons, etHr)

        val tilHr = view.findViewById<TextInputLayout>(R.id.tilHrLabel)
        etHr.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tilHr.prefixText = if (s.isNullOrEmpty()) "-- " else null
            }
        })

        if (torrentIds.size == 1) {
            val targetId = torrentIds.first()
            val torrent = allTorrents?.find { it.id == targetId }
            val currentHr = torrent?.labels?.find { it.startsWith("HR:") }
            if (currentHr != null) {
                val hrs = currentHr.substringAfter("HR:").toDoubleOrNull() ?: 0.0
                if (hrs > 0) {
                    val d = hrs / 24.0
                    val initialText = if (d == d.toInt().toDouble()) d.toInt().toString() else d.toString()
                    etHr.setText(initialText)
                }
            }
        }

        val dialog = AlertDialog.Builder(context, R.style.FabDialogTheme)
            .setView(view)
            .create()

        btnAction?.setOnClickListener {
            val selectedHr = etHr.text.toString().trim()
            // 改进解析逻辑：提取数字部分，处理带单位的情况（如 "3 天"）
            val days = selectedHr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            val hours = days * 24
            
            val service = TransmissionClient.getService(rpcUrl, user, pass)
            
            // 统一使用 Double 字符串格式如 HR:24.0，确保精度和一致性
            val hrLabel = if (hours > 0) "HR:$hours" else null 

            // 构造请求参数
            val args = mutableMapOf<String, Any>("ids" to torrentIds)
            args["labels"] = if (hrLabel != null) listOf(hrLabel) else emptyList()

            service.rpc(rpcUrl, null, RpcRequest("torrent-set", args))
                .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        val body = response.body()
                        if (response.isSuccessful && body?.result == "success") {
                            Toast.makeText(context, R.string.msg_hr_set_success, Toast.LENGTH_SHORT).show()
                            onSuccess()
                            dialog.dismiss()
                        } else {
                            val msg = body?.result ?: response.code().toString()
                            Toast.makeText(context, context.getString(R.string.msg_update_failed_with_msg, msg), Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                        Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                    }
                })
        }
        dialog.show()
    }

    private fun setupHrSlidingInput(container: LinearLayout, editText: EditText) {
        val touchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        if (event.x >= child.left && event.x <= child.right) {
                            val value = (child as? TextView)?.text?.toString() ?: ""
                            if (value.isNotEmpty() && editText.text.toString() != value) {
                                editText.setText(value)
                            }
                            child.isPressed = true
                        } else {
                            child.isPressed = false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    for (i in 0 until container.childCount) {
                        container.getChildAt(i).isPressed = false
                    }
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    for (i in 0 until container.childCount) {
                        container.getChildAt(i).isPressed = false
                    }
                    true
                }
                else -> false
            }
        }

        container.setOnTouchListener(touchListener)
        container.setOnClickListener { } // Ensure performClick has a target

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                val offsetEvent = MotionEvent.obtain(ev)
                offsetEvent.offsetLocation(child.left.toFloat(), child.top.toFloat())
                val handled = touchListener.onTouch(container, offsetEvent)
                offsetEvent.recycle()
                handled
            }
            child.setOnClickListener { }
        }
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
            val service = TransmissionClient.getService(rpcUrl, user, pass)
            service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to path))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                        tvFree.text = context.getString(R.string.free_space_label, FormatUtils.formatSize(size))
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
                val service = TransmissionClient.getService(rpcUrl, user, pass)
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
}
