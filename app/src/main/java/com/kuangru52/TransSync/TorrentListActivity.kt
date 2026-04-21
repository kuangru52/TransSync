package com.kuangru52.TransSync

import android.content.res.ColorStateList
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MaskFilterSpan
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.util.Base64
import android.net.Uri
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.content.edit
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TorrentListActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rvTorrents: RecyclerView
    private lateinit var adapter: TorrentListAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvTotalDownloadSpeed: TextView
    private lateinit var tvTotalUploadSpeed: TextView
    private lateinit var ivTurtle: ImageView
    private lateinit var bottomBar: View
    
    private var lastBottomBarClickTime = 0L
    private var appNameClickCount = 0
    private var isTrackerBlurEnabled = false
    private val revealedTrackerNames = mutableSetOf<String>()
    private var rpcUrl: String = ""
    private var isUrlVisible: Boolean = false
    private var user: String = ""
    private var pass: String = ""
    
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshTorrents()
            handler.postDelayed(this, 3000)
        }
    }

    private var allTorrents: List<Map<String, Any>> = emptyList()
    private var pendingFilteredList: List<Torrent>? = null // 新增：滚动时缓存待刷新的数据
    private val activeTorrentsLastSeen = mutableMapOf<Int, Long>() // id to timestamp
    private val GRACE_PERIOD_MS = 30000L // 30 seconds grace period
    private var currentFilter: String = "All"
    private var lastBackTime = 0L
    private var isDrawerMoving = false // 新增：标记侧边栏是否正在移动

    private var selectedFileUri: Uri? = null
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedFileUri = it
            // 如果在对话框中，需要更新显示
            lastAddDialog?.findViewById<TextInputEditText>(R.id.etTorrentUrl)?.setText(it.lastPathSegment ?: "Local file selected")
        }
    }
    private var lastAddDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_torrent_list)

        // 读取上次保存的过滤器
        val filterPrefs = getSharedPreferences("filter_prefs", MODE_PRIVATE)
        currentFilter = filterPrefs.getString("last_filter", "All") ?: "All"

        // 设置顶栏标题
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = when(currentFilter) {
            "Downloading" -> getString(R.string.nav_downloading)
            "Seeding" -> getString(R.string.nav_seeding)
            "Paused" -> getString(R.string.nav_paused)
            "Active" -> getString(R.string.nav_active)
            "Inactive" -> getString(R.string.nav_inactive)
            else -> if (currentFilter.startsWith("tracker:")) currentFilter.substringAfter("tracker:") else getString(R.string.nav_all)
        }

        rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        user = intent.getStringExtra("user") ?: ""
        pass = intent.getStringExtra("pass") ?: ""

        drawerLayout = findViewById(R.id.drawerLayout)
        drawerLayout.setStatusBarBackground(null) // 移除状态栏遮罩
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val headerView = navigationView.getHeaderView(0)
        val tvAppName = headerView.findViewById<TextView>(R.id.tvAppName)
        
        // 设置应用名 + 版本号
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }
        tvAppName.text = "TransSync v$versionName"
        tvAppName.setOnClickListener {
            appNameClickCount++
            if (appNameClickCount >= 5) {
                // 切换隐私模式状态
                isTrackerBlurEnabled = !isTrackerBlurEnabled
                revealedTrackerNames.clear() // 切换模式时清空已解锁列表
                updateTrackerChips(allTorrents) 
                
                val msg = if (isTrackerBlurEnabled) "Privacy mode enabled" else "Privacy mode disabled"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                appNameClickCount = 0
            }
        }
        rvTorrents = findViewById(R.id.rvTorrents)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        tvTotalDownloadSpeed = findViewById(R.id.tvTotalDownloadSpeed)
        tvTotalUploadSpeed = findViewById(R.id.tvTotalUploadSpeed)
        ivTurtle = findViewById(R.id.ivTurtle)
        bottomBar = findViewById(R.id.bottomBar)

        bottomBar.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBottomBarClickTime < 300) {
                rvTorrents.smoothScrollToPosition(0)
                lastBottomBarClickTime = 0L
            } else {
                lastBottomBarClickTime = currentTime
            }
        }

        rvTorrents.layoutManager = LinearLayoutManager(this)
        rvTorrents.itemAnimator = null // 彻底禁用动画，消除刷新时的位移抖动
        adapter = TorrentListAdapter(
            onStatusClick = { torrent ->
                // 强制刷新当前点击项的状态判定，避免因为列表刷新延迟导致的逻辑错误
                val currentTorrent = adapter.currentList.find { it.id == torrent.id } ?: torrent
                val effectiveIsPaused = currentTorrent.status == 0
                
                val method = if (effectiveIsPaused) "torrent-start" else "torrent-stop"
                val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
                
                android.util.Log.d("TorrentList", "Action on ID: ${torrent.id}, Status: ${currentTorrent.status}, Method: $method")
                
                service.rpc(rpcUrl, null, RpcRequest(method, mapOf("ids" to listOf(torrent.id)))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        if (response.isSuccessful) {
                            // 立即更新本地状态，给予即时反馈
                            val newList = adapter.currentList.map { 
                                if (it.id == torrent.id) it.copy(status = if (effectiveIsPaused) 4 else 0) else it 
                            }
                            adapter.submitList(newList)
                            
                            handler.postDelayed({ refreshTorrents() }, 500)
                        } else {
                            val errorMsg = response.errorBody()?.string() ?: ""
                            android.util.Log.e("TorrentList", "RPC Error: $errorMsg")
                            Toast.makeText(this@TorrentListActivity, "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                        Toast.makeText(this@TorrentListActivity, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                    }
                })
            },
            onTorrentClick = { torrent ->
                val selectedIds = adapter.getSelectedIds()
                if (adapter.isSelectionMode && selectedIds.isNotEmpty()) {
                    adapter.toggleSelection(torrent.id)
                    val newSelectedIds = adapter.getSelectedIds()
                    updateSelectionUI(newSelectedIds.size)
                    if (newSelectedIds.isEmpty()) {
                        adapter.isSelectionMode = false
                    }
                } else {
                    // 安全重置：如果模式开启但没选东西，强制关闭模式进入详情页
                    if (adapter.isSelectionMode) {
                        adapter.isSelectionMode = false
                        adapter.clearSelection()
                        updateSelectionUI(0)
                    }
                    val intent = Intent(this, TorrentDetailActivity::class.java).apply {
                        putExtra("rpcUrl", rpcUrl)
                        putExtra("user", user)
                        putExtra("pass", pass)
                        putExtra("torrent_id", torrent.id)
                        putExtra("torrent_name", torrent.name)
                    }
                    startActivity(intent)
                }
            },
            onTorrentLongClick = { torrent ->
                if (!adapter.isSelectionMode) {
                    adapter.isSelectionMode = true
                    adapter.toggleSelection(torrent.id)
                    updateSelectionUI(1)
                }
            },
            onSelectionChange = { torrent, _ ->
                adapter.toggleSelection(torrent.id)
                val selectedIds = adapter.getSelectedIds()
                updateSelectionUI(selectedIds.size)
                
                if (selectedIds.isEmpty()) {
                    adapter.isSelectionMode = false
                }
            }
        )
        rvTorrents.adapter = adapter
        
        // 核心修复：彻底禁用 Item 内容变更时的渐变动画（闪烁的罪魁祸首）
        (rvTorrents.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false

        // 新增：滚动监听，解决停止滚动时的闪烁/跳动问题
        rvTorrents.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // 当滚动彻底停止后，检查是否有待更新的数据
                    pendingFilteredList?.let {
                        adapter.submitList(it)
                        pendingFilteredList = null
                    }
                }
            }
        })

        swipeRefresh.setOnRefreshListener { refreshTorrents() }

        findViewById<View>(R.id.tvFeedback).setOnClickListener {
            val intent = Intent(this, FeedbackActivity::class.java)
            startActivity(intent)
        }

        setupDrawer(navigationView)
        
        // 根据恢复的过滤器设置菜单选中
        val menuItemId = when (currentFilter) {
            "Downloading" -> R.id.nav_downloading
            "Seeding" -> R.id.nav_seeding
            "Paused" -> R.id.nav_paused
            "Active" -> R.id.nav_active
            "Inactive" -> R.id.nav_inactive
            else -> if (currentFilter.startsWith("tracker:")) -1 else R.id.nav_all
        }
        if (menuItemId != -1) {
            navigationView.setCheckedItem(menuItemId)
        }
        
        // 设置图标点击切换主题
        val ivLogo = headerView.findViewById<ImageView>(R.id.ivLogo)
        
        ivLogo.setOnClickListener {
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val newMode = if (isNight) {
                AppCompatDelegate.MODE_NIGHT_NO
            } else {
                AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(newMode)
            
            // 保存用户手动选择的主题
                getSharedPreferences("theme_prefs", MODE_PRIVATE).edit { putInt("theme_mode", newMode) }
            }

        val layoutSearch = findViewById<View>(R.id.layoutSearch)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val ivCloseSearch = findViewById<ImageView>(R.id.ivCloseSearch)

        findViewById<ImageView>(R.id.ivSearch).setOnClickListener {
            if (!layoutSearch.isVisible) {
                layoutSearch.isVisible = true
                layoutSearch.translationX = layoutSearch.width.toFloat()
                layoutSearch.animate()
                    .translationX(0f)
                    .setDuration(300)
                    .withEndAction {
                        etSearch.requestFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                    .start()
            }
        }

        updateServerInfoText()
        
        headerView.findViewById<TextView>(R.id.tvServerInfo).setOnClickListener {
            if (!isUrlVisible) {
                isUrlVisible = true
                updateServerInfoText()
            } else {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("isEditing", true)
                }
                startActivity(intent)
            }
        }

        val nsvTrackers = findViewById<androidx.core.widget.NestedScrollView>(R.id.nsvTrackers)
        val vDividerTop = findViewById<View>(R.id.vTrackerDividerTop)
        val vDividerBottom = findViewById<View>(R.id.vTrackerDividerBottom)

        nsvTrackers.setOnScrollChangeListener { v: androidx.core.widget.NestedScrollView, _, scrollY, _, _ ->
            vDividerTop.alpha = if (scrollY <= 0) 1f else 0f
            val canScrollDown = v.getChildAt(0).measuredHeight > scrollY + v.measuredHeight
            vDividerBottom.alpha = if (!canScrollDown) 1f else 0f
        }

        setupSearch()
        setupSelectionActions()
        
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).setOnClickListener { fab ->
            fab.animate().rotation(135f).setDuration(300).withEndAction {
                showAddTorrentDialog {
                    fab.animate().rotation(0f).setDuration(300).start()
                }
            }.start()
        }

        findViewById<View>(R.id.ivMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                isDrawerMoving = true
            }
            override fun onDrawerOpened(drawerView: View) {
                isDrawerMoving = false
                updateTrackerChips(allTorrents)
            }
            override fun onDrawerClosed(drawerView: View) {
                isDrawerMoving = false
            }
            override fun onDrawerStateChanged(newState: Int) {
                isDrawerMoving = newState != DrawerLayout.STATE_IDLE
            }
        })

        ivTurtle.setOnClickListener {
            toggleAltSpeedLimits()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (adapter.isSelectionMode) {
                    exitSelectionMode()
                } else if (findViewById<View>(R.id.layoutSearch).isVisible) {
                    findViewById<ImageView>(R.id.ivCloseSearch).performClick()
                } else {
                    if (System.currentTimeMillis() - lastBackTime < 2000) {
                        finish()
                    } else {
                        lastBackTime = System.currentTimeMillis()
                        Toast.makeText(this@TorrentListActivity, R.string.msg_exit_press_again, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun updateServerInfoText() {
        val fullUrl = rpcUrl.substringBefore("/transmission/rpc")
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val tvServerInfo = navigationView.getHeaderView(0).findViewById<TextView>(R.id.tvServerInfo)
        
        if (isUrlVisible) {
            tvServerInfo.text = fullUrl
            tvServerInfo.paint.maskFilter = null
        } else {
            val spannable = SpannableString(fullUrl)
            val prefix = "https://"
            val start = if (fullUrl.startsWith(prefix)) prefix.length else 0
            if (start < fullUrl.length) {
                spannable.setSpan(
                    MaskFilterSpan(BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)),
                    start,
                    fullUrl.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            tvServerInfo.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            tvServerInfo.text = spannable
        }
        tvServerInfo.invalidate()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }


    private var startX = 0f
    private var startY = 0f
    private var isSwipeCandidate = false

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            return super.dispatchTouchEvent(ev)
        }

        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        val screenWidth = resources.displayMetrics.widthPixels

        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isSwipeCandidate = startX < screenWidth * 0.5f
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isSwipeCandidate) {
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    if (dx > touchSlop && dx > kotlin.math.abs(dy) * 1.5) {
                        drawerLayout.openDrawer(GravityCompat.START)
                        isSwipeCandidate = false
                        // 发送 CANCEL 事件给子 View，防止触发点击
                        val cancelEvent = android.view.MotionEvent.obtain(ev)
                        cancelEvent.action = android.view.MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                        return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                isSwipeCandidate = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private var isUpdatingFromThread = false

    private fun refreshTorrents() {
        if (isUpdatingFromThread) return
        
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        val fields = listOf(
            "id", "name", "status", "sizeWhenDone", "leftUntilDone", "percentDone", 
            "rateDownload", "rateUpload", "eta", "errorString", "queuePosition",
            "totalSize", "downloadedEver", "uploadedEver", "uploadRatio", "trackers",
            "addedDate", "downloadDir", "recheckProgress"
        )
        val request = RpcRequest("torrent-get", mapOf("fields" to fields))

        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (isFinishing || isDestroyed) return
                swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    @Suppress("UNCHECKED_CAST")
                    val torrents = response.body()?.arguments?.get("torrents") as? List<Map<String, Any>> ?: emptyList()
                    allTorrents = torrents
                    
                    filterAndDisplay(currentFilter)

                    updateFreeSpace()
                    checkAltSpeedStatus()
                }
            }

            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                if (isFinishing || isDestroyed) return
                swipeRefresh.isRefreshing = false
                Toast.makeText(this@TorrentListActivity, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun filterAndDisplay(filter: String) {
        if (isUpdatingFromThread) return
        isUpdatingFromThread = true

        currentFilter = filter
        getSharedPreferences("filter_prefs", MODE_PRIVATE).edit { putString("last_filter", filter) }

        val selectedIds = adapter.getSelectedIds()
        val currentTime = System.currentTimeMillis()

        Thread {
            try {
                var totalDlSpeed = 0.0
                var totalUlSpeed = 0.0
                
                // 数据深拷贝，防止在后台线程遍历时被外部修改导致 ConcurrentModificationException
                val torrentsSnapshot = ArrayList(allTorrents)
                
                torrentsSnapshot.forEach { t ->
                    val id = (t["id"] as? Number)?.toInt() ?: -1
                    val dlSpeed = (t["rateDownload"] as? Number)?.toDouble() ?: 0.0
                    val ulSpeed = (t["rateUpload"] as? Number)?.toDouble() ?: 0.0
                    
                    totalDlSpeed += dlSpeed
                    totalUlSpeed += ulSpeed
                    
                    if (dlSpeed > 0 || ulSpeed > 0) {
                        activeTorrentsLastSeen[id] = currentTime
                    }
                }

                val filteredMap = allTorrents.filter { t ->
                    val id = (t["id"] as? Number)?.toInt() ?: -1
                    if (id in selectedIds) return@filter true

                    if (filter.startsWith("tracker:")) {
                        val trackerName = filter.substringAfter("tracker:")
                        @Suppress("UNCHECKED_CAST")
                        val trackers = t["trackers"] as? List<Map<String, Any>>
                        val firstTrackerUrl = trackers?.firstOrNull()?.get("announce") as? String
                        if (firstTrackerUrl != null) {
                            TrackerUtils.getTrackerNameFromUrl(firstTrackerUrl) == trackerName
                        } else {
                            false
                        }
                    } else {
                        when (filter) {
                            "Downloading" -> (t["status"] as? Number)?.toInt() == 4
                            "Seeding" -> (t["status"] as? Number)?.toInt() == 6
                            "Paused" -> (t["status"] as? Number)?.toInt() == 0
                            "Active" -> {
                                val status = (t["status"] as? Number)?.toInt()
                                val dlSpeed = (t["rateDownload"] as? Number)?.toDouble() ?: 0.0
                                val ulSpeed = (t["rateUpload"] as? Number)?.toDouble() ?: 0.0
                                val lastSeen = activeTorrentsLastSeen[id] ?: 0L
                                (status == 1 || status == 2) || (dlSpeed > 0 || ulSpeed > 0) || (currentTime - lastSeen < GRACE_PERIOD_MS)
                            }
                            "Inactive" -> {
                                val status = (t["status"] as? Number)?.toInt()
                                val dlSpeed = (t["rateDownload"] as? Number)?.toDouble() ?: 0.0
                                val ulSpeed = (t["rateUpload"] as? Number)?.toDouble() ?: 0.0
                                val lastSeen = activeTorrentsLastSeen[id] ?: 0L
                                (status != 1 && status != 2) && (dlSpeed <= 0 && ulSpeed <= 0) && (currentTime - lastSeen >= GRACE_PERIOD_MS)
                            }
                            else -> true
                        }
                    }
                }
                
                val torrentList = filteredMap.map { t ->
                    val id = (t["id"] as? Number)?.toInt() ?: 0
                    val status = (t["status"] as? Number)?.toInt() ?: 0
                    val percentDone = (t["percentDone"] as? Number)?.toDouble() ?: 0.0
                    val recheckProgress = (t["recheckProgress"] as? Number)?.toDouble() ?: 0.0
                    val totalSize = (t["totalSize"] as? Number)?.toLong() ?: 0L
                    val downloadedEver = (t["downloadedEver"] as? Number)?.toLong() ?: 0L
                    val uploadedEver = (t["uploadedEver"] as? Number)?.toLong() ?: 0L
                    val rateDownload = (t["rateDownload"] as? Number)?.toLong() ?: 0L
                    val rateUpload = (t["rateUpload"] as? Number)?.toLong() ?: 0L
                    val uploadRatio = (t["uploadRatio"] as? Number)?.toDouble() ?: 0.0
                    val error = (t["error"] as? Number)?.toInt() ?: 0
                    val errorString = (t["errorString"] as? String) ?: ""

                    // 预计算颜色与进度
                    val color = when {
                        error != 0 || (errorString.isNotEmpty() && !errorString.contains("none", ignoreCase = true)) -> ContextCompat.getColor(this@TorrentListActivity, R.color.state_red)
                        status == 1 || status == 2 -> Color.parseColor("#FFF9A825") // 校验中：黄色
                        status == 0 -> ContextCompat.getColor(this@TorrentListActivity, R.color.state_gray) // 暂停
                        percentDone >= 1.0 -> ContextCompat.getColor(this@TorrentListActivity, R.color.state_green) // 完成：绿色
                        else -> ContextCompat.getColor(this@TorrentListActivity, R.color.state_blue) // 下载中：蓝色
                    }

                    val progress = if (status == 1 || status == 2) (recheckProgress * 1000).toInt() else (percentDone * 1000).toInt()
                    val statusText = if (status == 1 || status == 2) "校验中 (${String.format(Locale.US, "%.1f%%", recheckProgress * 100)})" else ""

                    // 预计算字符串
                    val sizeStr = formatSize(totalSize)
                    val displaySize = if (percentDone >= 1.0) sizeStr else "${formatSize(downloadedEver)} / $sizeStr"
                    val displayStats = "${formatSize(uploadedEver)} (分享率 ${String.format(Locale.US, "%.2f", uploadRatio)})"

                    Torrent(
                        id = id,
                        name = (t["name"] as? String) ?: "",
                        status = status,
                        percentDone = percentDone,
                        recheckProgress = recheckProgress,
                        totalSize = totalSize,
                        sizeWhenDone = (t["sizeWhenDone"] as? Number)?.toLong() ?: 0L,
                        leftUntilDone = (t["leftUntilDone"] as? Number)?.toLong() ?: 0L,
                        rateDownload = rateDownload,
                        rateUpload = rateUpload,
                        downloadedEver = downloadedEver,
                        uploadedEver = uploadedEver,
                        uploadRatio = uploadRatio,
                        error = error,
                        errorString = errorString,
                        addedDate = (t["addedDate"] as? Number)?.toLong() ?: 0L,
                        doneDate = (t["doneDate"] as? Number)?.toLong() ?: 0L,
                        activityDate = (t["activityDate"] as? Number)?.toLong() ?: 0L,
                        eta = (t["eta"] as? Number)?.toLong() ?: -1L,
                        trackers = (t["trackers"] as? List<Map<String, Any>>)?.map { m ->
                            Tracker(announce = (m["announce"] as? String) ?: "")
                        },
                        displaySize = displaySize,
                        displayStatusText = statusText,
                        displayDownloadSpeed = "${formatSpeed(rateDownload.toDouble())} ↓",
                        displayUploadSpeed = "${formatSpeed(rateUpload.toDouble())} ↑",
                        displayStats = displayStats,
                        displayProgress = progress,
                        displayColor = color
                    )
                }
                val sortedList = torrentList.sortedByDescending { it.addedDate }
                val totalSizeValue = filteredMap.sumOf { (it["totalSize"] as? Number)?.toDouble()?.toLong() ?: 0L }

                // 返回主线程更新 UI
                runOnUiThread {
                    isUpdatingFromThread = false
                    
                    // 如果正在搜索，优先显示搜索结果
                    if (findViewById<View>(R.id.layoutSearch).visibility == View.VISIBLE) {
                        val query = findViewById<EditText>(R.id.etSearch).text.toString().lowercase()
                        if (query.isNotEmpty()) {
                            performSearch(query)
                            return@runOnUiThread
                        }
                    }

                    // 只有在空闲时才提交重大结构性更新，但对于普通数值变化，直接提交
                    if (rvTorrents.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        adapter.submitList(sortedList)
                        pendingFilteredList = null
                    } else {
                        // 滚动时，我们依然更新 adapter 中的数据，但跳过那些会导致 Layout 剧烈变化的逻辑
                        // ListAdapter 的 DiffUtil 已经在子线程跑了，所以这里直接 submit 其实是安全的
                        adapter.submitList(sortedList)
                    }

                    updateTitleWithTotalSize(filter, totalSizeValue)
                    tvTotalDownloadSpeed.text = formatSpeed(totalDlSpeed)
                    tvTotalUploadSpeed.text = formatSpeed(totalUlSpeed)
                    
                    updateDrawerCounts(allTorrents)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { isUpdatingFromThread = false }
            }
        }.start()
    }

    private fun updateTitleWithTotalSize(filter: String, totalSize: Long) {
        val formattedSize = formatSize(totalSize)
        val baseTitle = if (filter.startsWith("tracker:")) {
            filter.substringAfter("tracker:")
        } else {
            val titleRes = when (filter) {
                "Downloading" -> R.string.nav_downloading
                "Seeding" -> R.string.nav_seeding
                "Paused" -> R.string.nav_paused
                "Active" -> R.string.nav_active
                "Inactive" -> R.string.nav_inactive
                else -> R.string.nav_all
            }
            getString(titleRes)
        }
        
        val fullText = "$baseTitle  $formattedSize"
        val spannable = SpannableString(fullText)
        val start = baseTitle.length
        
        spannable.setSpan(AbsoluteSizeSpan(12, true), start, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan("#B3FFFFFF".toColorInt()), start, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        findViewById<TextView>(R.id.tvTitle).text = spannable
    }


    private fun updateStats(torrents: List<Map<String, Any>>) {
        var dlSpeed = 0.0
        var ulSpeed = 0.0
        for (t in torrents) {
            dlSpeed += (t["rateDownload"] as? Double) ?: 0.0
            ulSpeed += (t["rateUpload"] as? Double) ?: 0.0
        }
        tvTotalDownloadSpeed.text = formatSpeed(dlSpeed)
        tvTotalUploadSpeed.text = formatSpeed(ulSpeed)
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kbs = bytesPerSec / 1024.0
        return if (kbs < 1024) {
            String.format(Locale.getDefault(), "%.1f KB/s", kbs)
        } else {
            String.format(Locale.getDefault(), "%.1f MB/s", kbs / 1024.0)
        }
    }

    private fun setupDrawer(navigationView: com.google.android.material.navigation.NavigationView) {
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_all -> filterAndDisplay("All")
                R.id.nav_downloading -> filterAndDisplay("Downloading")
                R.id.nav_seeding -> filterAndDisplay("Seeding")
                R.id.nav_paused -> filterAndDisplay("Paused")
                R.id.nav_active -> filterAndDisplay("Active")
                R.id.nav_inactive -> filterAndDisplay("Inactive")
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun setupSearch() {
        val layoutSearch = findViewById<View>(R.id.layoutSearch)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val ivSearch = findViewById<ImageView>(R.id.ivSearch)
        val ivCloseSearch = findViewById<ImageView>(R.id.ivCloseSearch)

        ivSearch.setOnClickListener {
            layoutSearch.visibility = View.VISIBLE
            layoutSearch.animate()
                .translationX(0f)
                .setDuration(300)
                .withEndAction {
                    etSearch.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                .start()
        }

        ivCloseSearch.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
            
            layoutSearch.animate()
                .translationX(layoutSearch.width.toFloat())
                .setDuration(300)
                .withEndAction {
                    layoutSearch.visibility = View.GONE
                    etSearch.text.clear()
                    filterAndDisplay(currentFilter)
                }
                .start()
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().lowercase()
                performSearch(query)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
                true
            } else {
                false
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        val selectedIds = adapter.getSelectedIds()
        val filtered = allTorrents.filter { t ->
            val id = (t["id"] as? Number)?.toInt() ?: -1
            (t["name"] as String).lowercase().contains(query) || id in selectedIds
        }
        val gson = Gson()
        val json = gson.toJson(filtered)
        val torrentList: List<Torrent> = gson.fromJson(json, object : TypeToken<List<Torrent>>() {}.type)
        val sortedList = torrentList.sortedByDescending { it.addedDate }

        // 优化策略：如果正在滚动，暂存数据不提交，直到滚动停止
        if (rvTorrents.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            pendingFilteredList = sortedList
        } else {
            // 关键修复：使用 ListAdapter 的时候，直接比较 currentList 和 sortedList
            // 如果列表内容没有实质变化（基于我们定义的 areContentsTheSame），submitList 内部会处理
            // 但为了彻底消除闪烁，我们在这里做一次前置检查
            adapter.submitList(sortedList)
            pendingFilteredList = null
        }

        val totalSize = filtered.sumOf { (it["totalSize"] as? Double)?.toLong() ?: 0L }
        val formattedSize = formatSize(totalSize)
        val baseTitle = getString(R.string.search_results)
        val fullText = "$baseTitle  $formattedSize"
        val spannable = SpannableString(fullText)
        val start = baseTitle.length
        
        spannable.setSpan(AbsoluteSizeSpan(12, true), start, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan("#B3FFFFFF".toColorInt()), start, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        findViewById<TextView>(R.id.tvTitle).text = spannable
    }

    private fun updateFreeSpace() {
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        // 获取默认下载目录
        service.rpc(rpcUrl, null, RpcRequest("session-get", emptyMap())).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    val downloadDir = response.body()?.arguments?.get("download-dir") as? String
                    if (downloadDir != null) {
                        // 获取指定目录的可用空间
                        service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to downloadDir))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                                if (response.isSuccessful) {
                                    val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                                    findViewById<TextView>(R.id.tvFreeSpace).text = getString(R.string.free_space_label, formatSize(size))
                                }
                            }
                            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
                        })
                    }
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    private fun setupSelectionActions() {
        findViewById<View>(R.id.ivSelectAll).setOnClickListener { 
            val allIds = adapter.currentList.map { it.id }
            adapter.selectAll()
            updateSelectionUI(allIds.size)
        }
        findViewById<View>(R.id.ivDeleteSelected).setOnClickListener { confirmDeleteSelected() }
        findViewById<View>(R.id.ivCloseSelection).setOnClickListener { exitSelectionMode() }
        
        val vDimOverlay = findViewById<View>(R.id.vDimOverlay)

        findViewById<View>(R.id.ivMoreActions).setOnClickListener { v ->
            val popupContext = androidx.appcompat.view.ContextThemeWrapper(this, R.style.AppPopupTheme)
            val popup = androidx.appcompat.widget.PopupMenu(popupContext, v)
            
            // 显示遮罩
            vDimOverlay.visibility = View.VISIBLE
            vDimOverlay.animate().alpha(1f).setDuration(200).start()

            // 菜单关闭时取消遮罩
            popup.setOnDismissListener {
                vDimOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                    vDimOverlay.visibility = View.GONE
                }.start()
            }

            val selectedCount = adapter.getSelectedIds().size
            
            popup.menu.add(0, 2, 0, getString(R.string.menu_pause))
            popup.menu.add(0, 1, 0, getString(R.string.menu_start))
            
            val renameItem = popup.menu.add(0, 3, 0, getString(R.string.menu_rename))
            renameItem.isEnabled = selectedCount == 1
            if (!renameItem.isEnabled) {
                val spanString = SpannableString(renameItem.title)
                spanString.setSpan(ForegroundColorSpan(Color.GRAY), 0, spanString.length, 0)
                renameItem.title = spanString
            }

            popup.menu.add(0, 4, 0, getString(R.string.menu_set_location))
            popup.menu.add(0, 5, 0, getString(R.string.menu_verify))
            popup.menu.add(0, 6, 0, getString(R.string.menu_reannounce))
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> startSelected()
                    2 -> stopSelected()
                    3 -> renameSelected()
                    4 -> setLocationSelected()
                    5 -> performBatchAction("torrent-verify", mapOf("ids" to adapter.getSelectedIds()))
                    6 -> performBatchAction("torrent-reannounce", mapOf("ids" to adapter.getSelectedIds()))
                }
                true
            }
            popup.show()
        }
    }

    private fun updateSelectionUI(count: Int) {
        val selectionBar = findViewById<View>(R.id.layoutSelectionBar)
        val tvCount = findViewById<TextView>(R.id.tvSelectionCount)
        if (count > 0) {
            selectionBar.isVisible = true
            tvCount.text = getString(R.string.selected_count, count)
        } else {
            selectionBar.isVisible = false
        }
    }

    private fun exitSelectionMode() {
        adapter.isSelectionMode = false
        adapter.clearSelection()
        updateSelectionUI(0)
        // 退出选择模式时重新应用当前筛选，防止之前因为选中而被强制显示的不可见项目消失
        val etSearch = findViewById<EditText>(R.id.etSearch)
        if (findViewById<View>(R.id.layoutSearch).isVisible && etSearch.text.isNotEmpty()) {
            performSearch(etSearch.text.toString().lowercase())
        } else {
            filterAndDisplay(currentFilter)
        }
    }

    private fun confirmDeleteSelected() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val tvMsg = TextView(this).apply {
            text = getString(R.string.delete_confirm_msg, ids.size)
            setTextColor(ContextCompat.getColor(this@TorrentListActivity, R.color.text_primary))
            textSize = 16f
        }
        container.addView(tvMsg)

        val cbDeleteData = android.widget.CheckBox(this).apply {
            text = getString(R.string.cb_delete_data)
            setTextColor(ContextCompat.getColor(this@TorrentListActivity, R.color.text_secondary))
            setPadding(0, 20, 0, 20)
            isChecked = true
        }
        container.addView(cbDeleteData)

        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (resources.displayMetrics.density * 12f).toInt()
            }
        }

        val btnDelete = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = getString(R.string.btn_delete)
            backgroundTintList = ColorStateList.valueOf("#E53935".toColorInt())
            setTextColor(Color.WHITE)
            textSize = 12f
            stateListAnimator = null
            insetTop = 0
            insetBottom = 0
            cornerRadius = (resources.displayMetrics.density * 20f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (resources.displayMetrics.density * 40f).toInt()
            )
            minimumWidth = (resources.displayMetrics.density * 80f).toInt()
        }
        btnContainer.addView(btnDelete)
        container.addView(btnContainer)

        val dialog = AlertDialog.Builder(this, R.style.AppDialogTheme)
            .setView(container)
            .create()

        btnDelete.setOnClickListener {
            performBatchAction("torrent-remove", mapOf("ids" to ids, "delete-local-data" to cbDeleteData.isChecked)) 
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun performBatchAction(method: String, arguments: Map<String, Any>) {
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest(method, arguments)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    exitSelectionMode()
                    refreshTorrents()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    private fun startSelected() = performBatchAction("torrent-start", mapOf("ids" to adapter.getSelectedIds()))
    private fun stopSelected() = performBatchAction("torrent-stop", mapOf("ids" to adapter.getSelectedIds()))
    private fun renameSelected() {
        val ids = adapter.getSelectedIds()
        if (ids.size != 1) {
            Toast.makeText(this@TorrentListActivity, R.string.msg_select_only_one, Toast.LENGTH_SHORT).show()
            return
        }
        val id = ids.first()
        val torrent = adapter.currentList.find { it.id == id } ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val til = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.hint_new_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerFamily(com.google.android.material.shape.CornerFamily.ROUNDED)
            val radius = resources.displayMetrics.density * 28f
            setBoxCornerRadii(radius, radius, radius, radius)
            boxStrokeColor = ContextCompat.getColor(this@TorrentListActivity, R.color.colorAccent)
            hintTextColor = ColorStateList.valueOf(ContextCompat.getColor(this@TorrentListActivity, R.color.colorAccent))
            defaultHintTextColor = ColorStateList.valueOf(ContextCompat.getColor(this@TorrentListActivity, R.color.text_secondary))
        }

        val btnRename = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = getString(R.string.btn_confirm)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@TorrentListActivity, R.color.white))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@TorrentListActivity, R.color.colorAccent))
            rippleColor = ColorStateList.valueOf(Color.parseColor("#20FFFFFF"))
            stateListAnimator = null
            elevation = 2f
            isEnabled = false
            alpha = 0.5f
            cornerRadius = (resources.displayMetrics.density * 20f).toInt()
            insetTop = 0
            insetBottom = 0
            setPadding((resources.displayMetrics.density * 16f).toInt(), 0, (resources.displayMetrics.density * 16f).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (resources.displayMetrics.density * 40f).toInt()).apply {
                gravity = android.view.Gravity.END
                topMargin = (resources.displayMetrics.density * 12f).toInt()
            }
            minimumWidth = (resources.displayMetrics.density * 80f).toInt()
        }

        val input = TextInputEditText(til.context).apply {
            setText(torrent.name)
            setTextColor(ContextCompat.getColor(this@TorrentListActivity, R.color.text_primary))
            setPadding((resources.displayMetrics.density * 24f).toInt(), paddingTop, (resources.displayMetrics.density * 24f).toInt(), paddingBottom)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newName = s.toString().trim()
                    val changed = newName.isNotEmpty() && newName != torrent.name
                    btnRename.isEnabled = changed
                    btnRename.alpha = if (changed) 1.0f else 0.5f
                }
            })
        }
        til.addView(input)
        container.addView(til)
        container.addView(btnRename)

        val dialog = AlertDialog.Builder(this, R.style.AppDialogTheme)
            .setView(container)
            .create()

        btnRename.setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != torrent.name) {
                val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
                service.rpc(rpcUrl, null, RpcRequest("torrent-rename-path", mapOf("ids" to listOf(id), "path" to torrent.name, "name" to newName)))
                    .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                        override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                            if (response.isSuccessful) {
                                exitSelectionMode()
                                refreshTorrents()
                                dialog.dismiss()
                            }
                        }
                        override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
                    })
            }
        }
        dialog.show()
    }

    private fun setLocationSelected() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return

        val view = layoutInflater.inflate(R.layout.dialog_add_torrent, null)
        
        // 隐藏不必要的控件
        val etDir = view.findViewById<AutoCompleteTextView>(R.id.etDownloadDir)
        val tvFree = view.findViewById<TextView>(R.id.tvFreeSpace)
        val cbMove = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbMoveData)
        val btnAction = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddTorrent)
        
        // 隐藏不需要的 URL 输入框
        view.findViewById<View>(R.id.tilTorrentUrl)?.visibility = View.GONE
        
        cbMove?.visibility = View.VISIBLE
        cbMove?.isChecked = true
        btnAction?.text = getString(R.string.btn_confirm)

        // 历史目录加载
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val historyDirs = prefs.getStringSet("history_dirs", mutableSetOf())?.toList() ?: emptyList()
        val currentDirs = allTorrents.mapNotNull { it["downloadDir"] as? String }.distinct()
        val allDirs = (historyDirs + currentDirs).distinct().sorted()
        etDir?.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, allDirs))

        fun updateFreeSpace(path: String) {
            if (path.isEmpty()) {
                tvFree?.isVisible = false
                return
            }
            val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
            service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to path))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                        tvFree?.text = getString(R.string.free_space_label, formatSize(size))
                        tvFree?.isVisible = true
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
        }

        etDir?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateFreeSpace(s.toString().trim())
            }
        })

        val dialog = AlertDialog.Builder(this, R.style.AppDialogTheme)
            .setView(view)
            .create()

        btnAction?.setOnClickListener {
            val newLocation = etDir?.text.toString().trim()
            if (newLocation.isNotEmpty()) {
                performBatchAction("torrent-set-location", mapOf(
                    "ids" to ids,
                    "location" to newLocation,
                    "move" to (cbMove?.isChecked ?: false)
                ))
                // 保存到历史记录
                val newHistory = (historyDirs + newLocation).toSet()
                prefs.edit { putStringSet("history_dirs", newHistory) }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun toggleAltSpeedLimits() {
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest("session-get", emptyMap())).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    val current = response.body()?.arguments?.get("alt-speed-enabled") as? Boolean ?: false
                    service.rpc(rpcUrl, null, RpcRequest("session-set", mapOf("alt-speed-enabled" to !current))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                        override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                            if (response.isSuccessful) checkAltSpeedStatus()
                        }
                        override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
                    })
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    private fun checkAltSpeedStatus() {
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest("session-get", emptyMap())).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (isFinishing || isDestroyed) return
                if (response.isSuccessful) {
                    val enabled = response.body()?.arguments?.get("alt-speed-enabled") as? Boolean ?: false
                    ivTurtle.setImageResource(if (enabled) R.drawable.ic_turtle else R.drawable.ic_turtle_outline)
                    ivTurtle.imageTintList = if (enabled) ColorStateList.valueOf("#FFF9A825".toColorInt()) else null
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    private fun updateDrawerCounts(torrents: List<Map<String, Any>>) {
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val menu = navigationView.menu

        fun updateItem(itemId: Int, nameRes: Int, list: List<Map<String, Any>>) {
            val item = menu.findItem(itemId) ?: return
            val count = list.size
            // 优化：避免在 UI 线程使用 sumOf 进行大数据遍历，如果数据量大考虑在后台算好传入
            val size = list.sumOf { (it["totalSize"] as? Number)?.toLong() ?: 0L }
            
            item.title = "${getString(nameRes)} ($count)"
            
            // 使用 ActionView 实现右侧显示详情
            val actionView = item.actionView as? TextView ?: TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER_VERTICAL
                val color = if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    "#88FFFFFF".toColorInt()
                } else {
                    "#FF636E72".toColorInt() // 浅色模式下使用次要文字颜色，确保对比度
                }
                setTextColor(color)
                textSize = 12f
                item.actionView = this
            }
            actionView.text = getString(R.string.count_bracket, formatSize(size))
        }

        updateItem(R.id.nav_all, R.string.nav_all, torrents)
        updateItem(R.id.nav_downloading, R.string.nav_downloading, torrents.filter { (it["status"] as? Number)?.toInt() == 4 })
        updateItem(R.id.nav_seeding, R.string.nav_seeding, torrents.filter { (it["status"] as? Number)?.toInt() == 6 })
        updateItem(R.id.nav_paused, R.string.nav_paused, torrents.filter { (it["status"] as? Number)?.toInt() == 0 })
        updateItem(R.id.nav_active, R.string.nav_active, torrents.filter { 
            ((it["rateDownload"] as? Number)?.toDouble() ?: 0.0) > 0 || ((it["rateUpload"] as? Number)?.toDouble() ?: 0.0) > 0 
        })
        updateItem(R.id.nav_inactive, R.string.nav_inactive, torrents.filter { 
            ((it["rateDownload"] as? Number)?.toDouble() ?: 0.0) <= 0 && ((it["rateUpload"] as? Number)?.toDouble() ?: 0.0) <= 0 
        })

        updateTrackerChips(torrents)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    private var lastTrackerData: Map<String, Int>? = null
    private var lastBlurState: Boolean = false

    private fun updateTrackerChips(torrents: List<Map<String, Any>>) {
        val cgTrackers = findViewById<ChipGroup>(R.id.cgTrackers) ?: return
        
        // 如果侧边栏正在滑动，禁止更新布局，防止卡顿
        if (isDrawerMoving) return

        val trackerMap = mutableMapOf<String, Int>()

        for (t in torrents) {
            @Suppress("UNCHECKED_CAST")
            val trackers = t["trackers"] as? List<Map<String, Any>>
            val firstTrackerUrl = trackers?.firstOrNull()?.get("announce") as? String
            if (firstTrackerUrl != null) {
                val name = TrackerUtils.getTrackerNameFromUrl(firstTrackerUrl)
                if (name != null) {
                    trackerMap[name] = (trackerMap[name] ?: 0) + 1
                }
            }
        }

        // 状态判定：数据变了 或者 隐私模式开关变了，才允许进入刷新逻辑
        val stateChanged = trackerMap != lastTrackerData || isTrackerBlurEnabled != lastBlurState
        if (!stateChanged) return
        
        lastTrackerData = trackerMap
        lastBlurState = isTrackerBlurEnabled

        val sortedEntries = trackerMap.entries.sortedByDescending { it.value }
        
        // 1. 标记并移除不再需要的 Chip
        val newNames = sortedEntries.map { it.key }.toSet()
        val chipsToRemove = mutableListOf<View>()
        for (i in 0 until cgTrackers.childCount) {
            val child = cgTrackers.getChildAt(i)
            if (child.tag !in newNames) chipsToRemove.add(child)
        }
        chipsToRemove.forEach { cgTrackers.removeView(it) }

        // 2. 更新现有 Chip 或添加新 Chip
        sortedEntries.forEach { entry ->
            val trackerName = entry.key
            val displayCount = entry.value
            val isRevealed = revealedTrackerNames.contains(trackerName)
            
            // 查找现有 Chip
            // 查找现有 Chip
            var existingChip: com.google.android.material.chip.Chip? = null
            for (i in 0 until cgTrackers.childCount) {
                val child = cgTrackers.getChildAt(i) as? com.google.android.material.chip.Chip
                if (child?.tag == trackerName) {
                    existingChip = child
                    break
                }
            }

            val chip = existingChip ?: com.google.android.material.chip.Chip(this).apply {
                tag = trackerName
            }

            chip.apply {
                val shouldBlur = isTrackerBlurEnabled && !isRevealed
                val isCurrentlyBlur = paint.maskFilter != null
                val targetText = getString(R.string.tracker_count_format, trackerName, displayCount)

                // 核心修复：仅在状态真正改变时操作，避免全量重建导致的闪烁
                if (shouldBlur) {
                    if (!isCurrentlyBlur || text != targetText) {
                        text = targetText
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        post {
                            paint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
                            invalidate()
                        }
                    }
                } else {
                    if (isCurrentlyBlur || text != targetText) {
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        paint.maskFilter = null
                        text = targetText
                    }
                }

                isClickable = true
                isFocusable = true
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                
                val isNight = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                
                // 统一背景色：使用种子列表同款配色
                val bgColor = ContextCompat.getColor(this@TorrentListActivity, R.color.bg_tag)
                chipBackgroundColor = ColorStateList.valueOf(bgColor)
                
                // 统一边框：使用种子列表同款边框色
                val strokeColor = ContextCompat.getColor(this@TorrentListActivity, R.color.stroke_tag)
                chipStrokeColor = ColorStateList.valueOf(strokeColor)
                chipStrokeWidth = resources.displayMetrics.density * 1.0f
                
                val textColor = ContextCompat.getColor(this@TorrentListActivity, R.color.text_secondary)
                setTextColor(textColor)
                textSize = 12f
                includeFontPadding = false
                
                // 彻底恢复到最初的大尺寸：侧边栏 Chip 应该是大气的操作按钮样式
                setPadding(0, 0, 0, 0)
                minHeight = 0
                minimumHeight = 0
                chipMinHeight = resources.displayMetrics.density * 28f
                minimumWidth = 0 
                
                // 解决深色模式下隐私切换闪烁：彻底禁用高度、Overlay和状态动画
                elevation = 0f
                stateListAnimator = null
                
                isChipIconVisible = false
                chipIcon = null
                iconStartPadding = 0f
                iconEndPadding = 0f
                
                chipStartPadding = resources.displayMetrics.density * 8f
                chipEndPadding = resources.displayMetrics.density * 8f
                textStartPadding = 0f
                textEndPadding = 0f

                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(resources.displayMetrics.density * 16f)
                    .build()

                setOnClickListener {
                    if (isTrackerBlurEnabled && !revealedTrackerNames.contains(trackerName)) {
                        revealedTrackerNames.add(trackerName)
                        lastBlurState = !isTrackerBlurEnabled // 诱导下次强制刷新
                        updateTrackerChips(allTorrents)
                    } else {
                        filterAndDisplay("tracker:$trackerName")
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                }
            }
            if (existingChip == null) {
                cgTrackers.addView(chip)
            }
        }
    }

    private fun showAddTorrentDialog(onDismiss: (() -> Unit)? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_add_torrent, null)
        val tilUrl = view.findViewById<TextInputLayout>(R.id.tilTorrentUrl)
        val etUrl = view.findViewById<TextInputEditText>(R.id.etTorrentUrl)
        val etDir = view.findViewById<AutoCompleteTextView>(R.id.etDownloadDir)
        val btnAdd = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddTorrent)
        val tvFree = view.findViewById<TextView>(R.id.tvFreeSpace)

        // 记录历史目录
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val historyDirs = prefs.getStringSet("history_dirs", mutableSetOf())?.toMutableList() ?: mutableListOf()
        
        // 从当前种子列表提取目录
        val currentDirs = allTorrents.mapNotNull { it["downloadDir"] as? String }.distinct()
        val allDirs = (historyDirs + currentDirs).distinct().sorted()
        
        val dirAdapter = ArrayAdapter(this, R.layout.item_dropdown_compact, allDirs)
        etDir.setAdapter(dirAdapter)
        etDir.setOnTouchListener { v, _ ->
            etDir.showDropDown()
            v.performClick()
            false
        }

        fun updateFreeSpaceForPath(path: String) {
            if (path.isEmpty()) {
                tvFree.isVisible = false
                return
            }
            val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
            service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to path))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                        tvFree.text = getString(R.string.free_space_label, formatSize(size))
                        tvFree.isVisible = true
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
        }

        etDir.setOnItemClickListener { parent, _, position, _ ->
            val selectedPath = parent.getItemAtPosition(position) as String
            updateFreeSpaceForPath(selectedPath)
        }

        etDir.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateFreeSpaceForPath(s.toString().trim())
            }
        })

        val dialog = AlertDialog.Builder(this, R.style.AppDialogTheme)
            .setView(view)
            .create()
        
        lastAddDialog = dialog
        selectedFileUri = null

        tilUrl.setEndIconOnClickListener {
            filePickerLauncher.launch("application/x-bittorrent")
        }

        btnAdd.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val downloadDir = etDir.text.toString().trim()
            
            // 按钮动效反馈
            btnAdd.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                btnAdd.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()

            // 立即关闭弹窗
            dialog.dismiss()

            // 历史目录逻辑
            if (downloadDir.isNotEmpty()) {
                val newHistory = historyDirs.toMutableSet()
                newHistory.add(downloadDir)
                prefs.edit { putStringSet("history_dirs", newHistory) }
            }

            val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
            
            if (selectedFileUri != null) {
                // 添加种子文件
                val inputStream = contentResolver.openInputStream(selectedFileUri!!)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val args = mutableMapOf<String, Any>("metainfo" to base64)
                    if (downloadDir.isNotEmpty()) args["download-dir"] = downloadDir
                    
                    service.rpc(rpcUrl, null, RpcRequest("torrent-add", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                        override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                            if (response.isSuccessful) {
                                refreshTorrents()
                                Toast.makeText(this@TorrentListActivity, R.string.msg_torrent_added_success, Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                            Toast.makeText(this@TorrentListActivity, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            } else if (url.isNotEmpty()) {
                // 添加链接
                val args = mutableMapOf<String, Any>("filename" to url)
                if (downloadDir.isNotEmpty()) args["download-dir"] = downloadDir
                
                service.rpc(rpcUrl, null, RpcRequest("torrent-add", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        if (response.isSuccessful) {
                            refreshTorrents()
                            Toast.makeText(this@TorrentListActivity, R.string.msg_torrent_added_success, Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                        Toast.makeText(this@TorrentListActivity, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }
        dialog.show()
    }
}


