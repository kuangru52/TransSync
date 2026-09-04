package com.kuangru52.transsync

import android.content.pm.ActivityInfo
import android.annotation.SuppressLint
import com.kuangru52.transsync.R
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.MotionEvent
import android.graphics.Rect
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
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.chip.ChipGroup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TorrentListActivity : AppCompatActivity() {

    private var drawerLayout: View? = null
    private lateinit var rvTorrents: RecyclerView
    private lateinit var adapter: TorrentListAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvTotalDownloadSpeed: TextView
    private lateinit var tvTotalUploadSpeed: TextView
    private lateinit var ivTurtle: ImageView
    private lateinit var bottomBar: View
    
    private val viewModel: TorrentListViewModel by viewModels()
    
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
            viewModel.refreshTorrents(this@TorrentListActivity, rpcUrl, user, pass)
            handler.postDelayed(this, 5000)
        }
    }

    private var currentFilter: String = "All"
    private var lastBackTime = 0L
    private var isDrawerMoving = false 

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            showAddTorrentDialog(initialFileUri = it)
        }
    }
    private var lastAddDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_torrent_list)

        rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        user = intent.getStringExtra("user") ?: ""
        pass = intent.getStringExtra("pass") ?: ""

        viewModel.torrents.observe(this) { torrents ->
            adapter.submitList(torrents)
        }

        viewModel.totalDownloadSpeed.observe(this) { speed ->
            tvTotalDownloadSpeed.text = speed
        }

        viewModel.totalUploadSpeed.observe(this) { speed ->
            tvTotalUploadSpeed.text = speed
        }

        viewModel.totalSize.observe(this) { size ->
            updateTitleWithTotalSize(currentFilter, size)
        }

        viewModel.drawerData.observe(this) { data ->
            data?.let { updateDrawerCounts(it) }
        }

        viewModel.trackerData.observe(this) { data ->
            data?.let { updateTrackerChips(it) }
        }

        viewModel.freeSpace.observe(this) { space ->
            findViewById<TextView>(R.id.tvFreeSpace)?.text = getString(R.string.free_space_label, space)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            swipeRefresh.isRefreshing = isLoading
        }

        viewModel.altSpeedEnabled.observe(this) { enabled ->
            ivTurtle.setImageResource(if (enabled) R.drawable.ic_turtle else R.drawable.ic_turtle_outline)
            ivTurtle.imageTintList = if (enabled) ColorStateList.valueOf("#FFF9A825".toColorInt()) else null
        }

        val filterPrefs = getSharedPreferences("filter_prefs", MODE_PRIVATE)
        currentFilter = filterPrefs.getString("last_filter", "All") ?: "All"
        viewModel.setFilter(currentFilter)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = when(currentFilter) {
            "Downloading" -> getString(R.string.nav_downloading)
            "Seeding" -> getString(R.string.nav_seeding)
            "Paused" -> getString(R.string.nav_paused)
            "Active" -> getString(R.string.nav_active)
            "Inactive" -> getString(R.string.nav_inactive)
            else -> if (currentFilter.startsWith("tracker:")) currentFilter.substringAfter("tracker:") else getString(R.string.nav_all)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            v.setPadding(0, 0, 0, 0)
            findViewById<View>(R.id.toolbarContainer).updatePadding(top = systemBars.top)
            
            val bottomPadding = if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                imeInsets.bottom
            } else {
                systemBars.bottom
            }
            findViewById<View>(R.id.bottomContainer).setPadding(0, 0, 0, bottomPadding)
            
            insets
        }

        val externalUri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (externalUri != null) {
            handleExternalUri(externalUri)
            intent.data = null
            intent.removeExtra(Intent.EXTRA_STREAM)
        } else if (sharedText != null) {
            handleSharedText(sharedText)
            intent.removeExtra(Intent.EXTRA_TEXT)
        }

        drawerLayout = findViewById(R.id.drawerLayout)
        (drawerLayout as? DrawerLayout)?.setStatusBarBackground(null)
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val headerView = navigationView.getHeaderView(0)
        val tvAppName = headerView.findViewById<TextView>(R.id.tvAppName)
        
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "1.20"
        }
        tvAppName.text = getString(R.string.app_name_version, versionName)
        tvAppName.setOnClickListener {
            appNameClickCount++
            if (appNameClickCount >= 5) {
                isTrackerBlurEnabled = !isTrackerBlurEnabled
                revealedTrackerNames.clear()
                viewModel.trackerData.value?.let { updateTrackerChips(it) } 
                
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
        rvTorrents.itemAnimator = null 
        adapter = TorrentListAdapter(
            onStatusClick = { torrent ->
                val currentTorrent = adapter.currentList.find { it.id == torrent.id } ?: torrent
                val effectiveIsPaused = currentTorrent.status == 0
                
                val method = if (effectiveIsPaused) "torrent-start" else "torrent-stop"
                val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
                
                service.rpc(rpcUrl, null, RpcRequest(method, mapOf("ids" to listOf(torrent.id)))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        if (response.isSuccessful) {
                            val newList = adapter.currentList.map { 
                                if (it.id == torrent.id) it.copy(status = if (effectiveIsPaused) 4 else 0) else it 
                            }
                            adapter.submitList(newList)
                            handler.postDelayed({ refreshTorrents() }, 500)
                        } else {
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
        
        (rvTorrents.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false

        val fabAdd = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd)

        rvTorrents.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    fabAdd.animate().alpha(1.0f).setDuration(200).start()
                    fabAdd.setImageAlpha(255)
                } else {
                    // 滑动中（包括拖动和惯性滑动）
                    fabAdd.animate().alpha(0.4f).setDuration(200).start()
                    fabAdd.setImageAlpha(0)
                }
            }
        })

        swipeRefresh.setOnRefreshListener { viewModel.refreshTorrents(this, rpcUrl, user, pass) }

        findViewById<View>(R.id.tvFeedback).setOnClickListener {
            val intent = Intent(this, FeedbackActivity::class.java)
            startActivity(intent)
        }

        setupDrawer(navigationView)
        
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
        
        val ivLogo = headerView.findViewById<ImageView>(R.id.ivLogo)
        ivLogo.setOnClickListener {
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val newMode = if (isNight) {
                AppCompatDelegate.MODE_NIGHT_NO
            } else {
                AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(newMode)
            getSharedPreferences("theme_prefs", MODE_PRIVATE).edit { putInt("theme_mode", newMode) }
        }

        val layoutSearch = findViewById<View>(R.id.layoutSearch)
        val etSearch = findViewById<EditText>(R.id.etSearch)

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
        
        fabAdd.setOnClickListener { fab ->
            fab.animate().rotation(135f).setDuration(300).withEndAction {
                showAddTorrentDialog(onDismiss = {
                    fab.animate().rotation(0f).setDuration(300).start()
                })
            }.start()
        }

        findViewById<View>(R.id.ivMenu).setOnClickListener {
            (drawerLayout as? DrawerLayout)?.openDrawer(GravityCompat.START)
        }

        (drawerLayout as? DrawerLayout)?.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                isDrawerMoving = true
            }
            override fun onDrawerOpened(drawerView: View) {
                isDrawerMoving = false
                viewModel.trackerData.value?.let { updateTrackerChips(it) }
            }
            override fun onDrawerClosed(drawerView: View) {
                isDrawerMoving = false
            }
            override fun onDrawerStateChanged(newState: Int) {
                isDrawerMoving = newState != DrawerLayout.STATE_IDLE
            }
        })

        ivTurtle.setOnClickListener {
            viewModel.toggleAltSpeedLimits(rpcUrl, user, pass)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val dl = drawerLayout as? DrawerLayout
                if (dl != null && dl.isDrawerOpen(GravityCompat.START)) {
                    dl.closeDrawer(GravityCompat.START)
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
        val dl = drawerLayout as? DrawerLayout
        if (dl != null && dl.isDrawerOpen(GravityCompat.START)) {
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
                        (drawerLayout as? DrawerLayout)?.openDrawer(GravityCompat.START)
                        isSwipeCandidate = false
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

    private fun refreshTorrents() {
        viewModel.refreshTorrents(this, rpcUrl, user, pass)
    }

    private fun updateTitleWithTotalSize(filter: String, totalSize: Long) {
        val formattedSize = FormatUtils.formatSize(totalSize)
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

    private fun setupDrawer(navigationView: com.google.android.material.navigation.NavigationView) {
        navigationView.setNavigationItemSelectedListener { item ->
            val filter = when (item.itemId) {
                R.id.nav_all -> "All"
                R.id.nav_downloading -> "Downloading"
                R.id.nav_seeding -> "Seeding"
                R.id.nav_paused -> "Paused"
                R.id.nav_active -> "Active"
                R.id.nav_inactive -> "Inactive"
                R.id.nav_error -> "Error"
                else -> "All"
            }
            currentFilter = filter
            getSharedPreferences("filter_prefs", MODE_PRIVATE).edit { putString("last_filter", filter) }
            viewModel.setFilter(filter)

            (drawerLayout as? DrawerLayout)?.closeDrawers()
            true
        }
    }

    private fun setupSearch() {
        val layoutSearch = findViewById<View>(R.id.layoutSearch)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val ivSearch = findViewById<ImageView>(R.id.ivSearch)
        val ivCloseSearch = findViewById<ImageView>(R.id.ivCloseSearch)

        ivSearch.setOnClickListener {
            layoutSearch.translationX = layoutSearch.width.toFloat()
            layoutSearch.visibility = View.VISIBLE
            
            etSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

            layoutSearch.animate()
                .translationX(0f)
                .setDuration(300)
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
                    viewModel.setSearchQuery("")
                }
                .start()
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().lowercase()
                viewModel.setSearchQuery(query)
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
                viewModel.setSearchQuery(query)
            }
            override fun afterTextChanged(s: Editable?) {}
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
            
            vDimOverlay.visibility = View.VISIBLE
            vDimOverlay.animate().alpha(1f).setDuration(200).start()

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
            popup.menu.add(0, 7, 0, getString(R.string.menu_set_hr))
            popup.menu.add(0, 5, 0, getString(R.string.menu_verify))
            popup.menu.add(0, 6, 0, getString(R.string.menu_reannounce))
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> startSelected()
                    2 -> stopSelected()
                    3 -> renameSelected()
                    4 -> {
                        val ids = adapter.getSelectedIds()
                        if (ids.isNotEmpty()) {
                            val firstId = ids.first()
                            val torrent = adapter.currentList.find { it.id == firstId }
                            DialogUtils.showSetLocationDialog(
                                this, rpcUrl, user, pass, ids, torrent?.downloadDir, adapter.currentList
                            ) {
                                exitSelectionMode()
                                refreshTorrents()
                            }
                        }
                    }
                    7 -> {
                        DialogUtils.showSetHrDialog(
                            this, rpcUrl, user, pass, adapter.getSelectedIds(), viewModel.torrents.value
                        ) {
                            exitSelectionMode()
                            refreshTorrents()
                        }
                    }
                    5 -> viewModel.performBatchAction(rpcUrl, user, pass, "torrent-verify", adapter.getSelectedIds()) {
                        exitSelectionMode()
                        refreshTorrents()
                    }
                    6 -> viewModel.performBatchAction(rpcUrl, user, pass, "torrent-reannounce", adapter.getSelectedIds()) {
                        exitSelectionMode()
                        refreshTorrents()
                    }
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
        
        val etSearch = findViewById<EditText>(R.id.etSearch)
        if (findViewById<View>(R.id.layoutSearch).isVisible && etSearch.text.isNotEmpty()) {
            viewModel.setSearchQuery(etSearch.text.toString().lowercase())
        } else {
            viewModel.setFilter(currentFilter)
        }
    }

    private fun confirmDeleteSelected() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) return

        // 检查是否有任何选中的种子处于 H&R 未完成状态
        val hasActiveHr = adapter.currentList.filter { selectedIds.contains(it.id) }.any { torrent ->
            val hrLabel = torrent.labels?.find { it.startsWith("HR:") }
            if (hrLabel != null) {
                val hours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
                if (hours > 0) {
                    if (torrent.percentDone < 1.0) {
                        true // 未下载完成，H&R 肯定没结束
                    } else {
                        // 已下载完成，计算剩余考核时间
                        val doneDateMs = torrent.doneDate * 1000L
                        val requiredMs = (hours * 3600 * 1000L).toLong()
                        val elapsedMs = System.currentTimeMillis() - doneDateMs
                        elapsedMs < requiredMs // 如果已过时间小于要求时间，则 H&R 未结束
                    }
                } else false
            } else false
        }

        DialogUtils.showDeleteDialog(
            context = this,
            rpcUrl = rpcUrl,
            user = user,
            pass = pass,
            torrentIds = selectedIds,
            deleteDataByDefault = !hasActiveHr, // 如果有未完成的 H&R，则默认不删除数据
            onSuccess = {
                exitSelectionMode()
                refreshTorrents()
            }
        )
    }

    private fun startSelected() = viewModel.performBatchAction(rpcUrl, user, pass, "torrent-start", adapter.getSelectedIds()) {
        exitSelectionMode()
        refreshTorrents()
    }

    private fun stopSelected() = viewModel.performBatchAction(rpcUrl, user, pass, "torrent-stop", adapter.getSelectedIds()) {
        exitSelectionMode()
        refreshTorrents()
    }

    private fun renameSelected() {
        val ids = adapter.getSelectedIds()
        if (ids.size != 1) {
            Toast.makeText(this@TorrentListActivity, R.string.msg_select_only_one, Toast.LENGTH_SHORT).show()
            return
        }
        val id = ids.first()
        val torrent = adapter.currentList.find { it.id == id } ?: return

        DialogUtils.showRenameDialog(
            context = this,
            rpcUrl = rpcUrl,
            user = user,
            pass = pass,
            torrentId = id,
            currentName = torrent.name,
            onSuccess = {
                exitSelectionMode()
                refreshTorrents()
            }
        )
    }

    private fun updateDrawerCounts(data: Map<String, DrawerItemData>) {
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val menu = navigationView.menu

        fun updateItem(itemId: Int, nameRes: Int, key: String) {
            val item = menu.findItem(itemId) ?: return
            val itemData = data[key] ?: DrawerItemData(0, 0L)
            
            item.title = "${getString(nameRes)} (${itemData.count})"
            
            val actionView = item.actionView as? TextView ?: TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER_VERTICAL
                val color = if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    "#88FFFFFF".toColorInt()
                } else {
                    "#FF636E72".toColorInt() 
                }
                setTextColor(color)
                textSize = 12f
                item.actionView = this
            }
            actionView.text = getString(R.string.count_bracket, FormatUtils.formatSize(itemData.totalSize))
        }

        updateItem(R.id.nav_all, R.string.nav_all, "All")
        updateItem(R.id.nav_downloading, R.string.nav_downloading, "Downloading")
        updateItem(R.id.nav_seeding, R.string.nav_seeding, "Seeding")
        updateItem(R.id.nav_paused, R.string.nav_paused, "Paused")
        updateItem(R.id.nav_active, R.string.nav_active, "Active")
        updateItem(R.id.nav_inactive, R.string.nav_inactive, "Inactive")
        updateItem(R.id.nav_error, R.string.nav_error, "Error")
    }

    private var lastTrackerData: Map<String, Int>? = null
    private var lastBlurState: Boolean = false

    private fun updateTrackerChips(trackerMap: Map<String, Int>) {
        val cgTrackers = findViewById<ChipGroup>(R.id.cgTrackers) ?: return
        if (isDrawerMoving) return

        val stateChanged = trackerMap != lastTrackerData || isTrackerBlurEnabled != lastBlurState
        if (!stateChanged) return
        
        lastTrackerData = trackerMap
        lastBlurState = isTrackerBlurEnabled

        val sortedEntries = trackerMap.entries.sortedByDescending { it.value }
        val newNames = sortedEntries.map { it.key }.toSet()
        val chipsToRemove = mutableListOf<View>()
        for (i in 0 until cgTrackers.childCount) {
            val child = cgTrackers.getChildAt(i)
            if (child.tag !in newNames) chipsToRemove.add(child)
        }
        chipsToRemove.forEach { cgTrackers.removeView(it) }

        sortedEntries.forEach { entry ->
            val trackerName = entry.key
            val displayCount = entry.value
            val isRevealed = revealedTrackerNames.contains(trackerName)
            
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
                
                val bgColor = ContextCompat.getColor(this@TorrentListActivity, R.color.bg_tag)
                chipBackgroundColor = ColorStateList.valueOf(bgColor)
                
                val strokeColor = ContextCompat.getColor(this@TorrentListActivity, R.color.stroke_tag)
                chipStrokeColor = ColorStateList.valueOf(strokeColor)
                chipStrokeWidth = resources.displayMetrics.density * 1.0f
                
                val textColor = ContextCompat.getColor(this@TorrentListActivity, R.color.text_secondary)
                setTextColor(textColor)
                textSize = 12f
                includeFontPadding = false
                
                minHeight = 0
                minimumHeight = 0
                chipMinHeight = resources.displayMetrics.density * 28f
                minimumWidth = 0 
                
                elevation = 0f
                stateListAnimator = null
                
                isChipIconVisible = false
                chipIcon = null
                iconStartPadding = 0f
                iconEndPadding = 0f
                
                chipStartPadding = resources.displayMetrics.density * 10f
                chipEndPadding = resources.displayMetrics.density * 10f
                textStartPadding = 0f
                textEndPadding = 0f

                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(resources.displayMetrics.density * 16f)
                    .build()

                setOnClickListener {
                    if (isTrackerBlurEnabled && !revealedTrackerNames.contains(trackerName)) {
                        revealedTrackerNames.add(trackerName)
                        lastBlurState = !isTrackerBlurEnabled
                        viewModel.trackerData.value?.let { updateTrackerChips(it) }
                    } else {
                        viewModel.setFilter("tracker:$trackerName")
                        (drawerLayout as? DrawerLayout)?.closeDrawer(GravityCompat.START)
                    }
                }
            }
            if (existingChip == null) {
                cgTrackers.addView(chip)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        
        if (uri != null) {
            handleExternalUri(uri)
            intent.data = null
            intent.removeExtra(Intent.EXTRA_STREAM)
        } else if (sharedText != null) {
            handleSharedText(sharedText)
            intent.removeExtra(Intent.EXTRA_TEXT)
        }
    }

    private fun handleExternalUri(uri: Uri) {
        val scheme = uri.scheme
        if (scheme == "magnet" || scheme == "http" || scheme == "https") {
            showAddTorrentDialog(initialUrl = uri.toString())
        } else if (scheme == "file" || scheme == "content") {
            val fileName = uri.path?.lowercase()
            if (fileName?.endsWith(".torrent") == true || contentResolver.getType(uri) == "application/x-bittorrent") {
                showAddTorrentDialog(initialFileUri = uri)
            } else {
                showAddTorrentDialog(initialUrl = uri.toString())
            }
        }
    }

    private fun handleSharedText(text: String) {
        val trimmed = text.trim()
        val urlRegex = """(https?|magnet):[^\s]+""".toRegex(RegexOption.IGNORE_CASE)
        val match = urlRegex.find(trimmed)
        val url = match?.value ?: trimmed
        
        showAddTorrentDialog(initialUrl = url)
    }

    private fun showAddTorrentDialog(onDismiss: (() -> Unit)? = null, initialUrl: String? = null, initialFileUri: Uri? = null) {
        DialogUtils.showAddTorrentDialog(
            context = this,
            rpcUrl = rpcUrl,
            user = user,
            pass = pass,
            initialUrl = initialUrl,
            initialFileUri = initialFileUri,
            allTorrents = viewModel.torrents.value,
            onPickFile = { filePickerLauncher.launch("application/x-bittorrent") },
            onSuccess = { refreshTorrents() },
            onDismiss = onDismiss
        )
    }

}
