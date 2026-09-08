package com.kuangru52.transsync

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MaskFilterSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.kuangru52.transsync.databinding.ActivityTorrentListBinding
import com.kuangru52.transsync.databinding.NavHeaderBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TorrentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTorrentListBinding
    private lateinit var headerBinding: NavHeaderBinding
    private lateinit var adapter: TorrentListAdapter
    private lateinit var searchManager: SearchManager
    private lateinit var trackerManager: TrackerManager
    private lateinit var swipeGestureDetector: GestureDetector
    
    private val viewModel: TorrentListViewModel by viewModels()
    
    private var lastBottomBarClickTime = 0L
    private var rpcUrl: String = ""
    private var isUrlVisible: Boolean = false
    private var user: String = ""
    private var pass: String = ""
    
    private var currentFilter: String = "All"
    private var lastBackTime = 0L
    private var isDrawerMoving = false 

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            showAddTorrentDialog(initialFileUri = it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        binding = ActivityTorrentListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        headerBinding = NavHeaderBinding.bind(binding.navigationView.getHeaderView(0))

        searchManager = SearchManager(this, binding, viewModel)
        trackerManager = TrackerManager(this, binding, headerBinding, viewModel)

        rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        user = intent.getStringExtra("user") ?: ""
        pass = intent.getStringExtra("pass") ?: ""

        setupObservers()
        setupUI()
        handleIntent(intent)
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    viewModel.refreshTorrents(rpcUrl, user, pass)
                    delay(5000)
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.torrents.observe(this) { torrents ->
            adapter.submitList(torrents)
        }

        viewModel.totalDownloadSpeed.observe(this) { speed ->
            binding.tvTotalDownloadSpeed.text = speed
        }

        viewModel.totalUploadSpeed.observe(this) { speed ->
            binding.tvTotalUploadSpeed.text = speed
        }

        viewModel.totalSize.observe(this) { size ->
            updateTitleWithTotalSize(currentFilter, FormatUtils.formatSize(size))
        }

        viewModel.drawerData.observe(this) { data ->
            data?.let { updateDrawerCounts(it) }
        }

        viewModel.trackerData.observe(this) { data ->
            data?.let { 
                trackerManager.updateTrackerChips(it, isDrawerMoving)
            }
        }

        viewModel.revealedTrackerNames.observe(this) { revealed ->
            adapter.revealedTrackerNames = revealed
            viewModel.trackerData.value?.let { trackerManager.updateTrackerChips(it, isDrawerMoving) }
        }

        viewModel.isTrackerBlurEnabled.observe(this) { enabled ->
            val isBlur = enabled ?: false
            adapter.isTrackerBlurEnabled = isBlur
            viewModel.trackerData.value?.let { trackerManager.updateTrackerChips(it, isDrawerMoving) }
        }

        viewModel.freeSpace.observe(this) { space ->
            binding.tvFreeSpace.text = getString(R.string.free_space_label, space)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.altSpeedEnabled.observe(this) { enabled ->
            binding.ivTurtle.setImageResource(if (enabled) R.drawable.ic_turtle else R.drawable.ic_turtle_outline)
            binding.ivTurtle.imageTintList = if (enabled) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.state_yellow)) else null
        }
    }

    private fun setupUI() {
        val filterPrefs = getSharedPreferences("filter_prefs", MODE_PRIVATE)
        currentFilter = filterPrefs.getString("last_filter", "All") ?: "All"
        viewModel.setFilter(currentFilter)

        binding.tvTitle.text = getFilterTitle(currentFilter)

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            v.setPadding(0, 0, 0, 0)
            binding.appBarLayout.updatePadding(top = systemBars.top)
            
            val bottomPadding = maxOf(imeInsets.bottom, systemBars.bottom)
            binding.bottomContainer.updatePadding(bottom = bottomPadding)
            
            val imeHeight = (imeInsets.bottom - systemBars.bottom).coerceAtLeast(0)
            binding.fabAdd.translationY = -imeHeight.toFloat()
            
            insets
        }

        binding.drawerLayout.setStatusBarBackground(null)
        
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "1.20"
        }
        headerBinding.tvAppName.text = getString(R.string.app_name_version, versionName)
        
        updateServerInfoText()
        headerBinding.tvServerInfo.setOnClickListener {
            isUrlVisible = !isUrlVisible
            updateServerInfoText()
        }
        
        trackerManager.setupTrackerPrivacy()

        binding.bottomBar.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBottomBarClickTime < 300) {
                binding.rvTorrents.smoothScrollToPosition(0)
                lastBottomBarClickTime = 0L
            } else {
                lastBottomBarClickTime = currentTime
            }
        }

        binding.rvTorrents.layoutManager = LinearLayoutManager(this)
        (binding.rvTorrents.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        
        adapter = TorrentListAdapter(
            onStatusClick = { torrent ->
                val currentTorrent = adapter.currentList.find { it.id == torrent.id } ?: torrent
                val effectiveIsPaused = currentTorrent.status == 0
                val method = if (effectiveIsPaused) "torrent-start" else "torrent-stop"
                
                // Optimistic update
                val newList = adapter.currentList.map { 
                    if (it.id == torrent.id) it.copy(status = if (effectiveIsPaused) 4 else 0) else it 
                }
                adapter.submitList(newList)
                
                viewModel.performBatchAction(rpcUrl, user, pass, method, listOf(torrent.id))
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
        binding.rvTorrents.adapter = adapter

        swipeGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY) * 2 && Math.abs(diffX) > 150 && Math.abs(velocityX) > 150) {
                    if (diffX > 0 && !binding.drawerLayout.isDrawerOpen(GravityCompat.START) && !adapter.isSelectionMode) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return false
            }
        })

        binding.rvTorrents.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    binding.fabAdd.animate()?.alpha(1.0f)?.setDuration(200)?.start()
                    binding.fabAdd.imageAlpha = 255
                } else {
                    binding.fabAdd.animate()?.alpha(0.4f)?.setDuration(200)?.start()
                    binding.fabAdd.imageAlpha = 0
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshTorrents(rpcUrl, user, pass) }

        binding.tvFeedback.setOnClickListener {
            val intent = Intent(this, FeedbackActivity::class.java)
            startActivity(intent)
        }

        setupDrawer()
        
        val menuItemId = when (currentFilter) {
            "Downloading" -> R.id.nav_downloading
            "Seeding" -> R.id.nav_seeding
            "Paused" -> R.id.nav_paused
            "Active" -> R.id.nav_active
            "Inactive" -> R.id.nav_inactive
            else -> if (currentFilter.startsWith("tracker:")) -1 else R.id.nav_all
        }
        if (menuItemId != -1) {
            binding.navigationView.setCheckedItem(menuItemId)
        }
        
        headerBinding.ivLogo.setOnClickListener {
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val newMode = if (isNight) {
                AppCompatDelegate.MODE_NIGHT_NO
            } else {
                AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(newMode)
            getSharedPreferences("theme_prefs", MODE_PRIVATE).edit { putInt("theme_mode", newMode) }
        }

        updateServerInfoText()
        
        headerBinding.tvServerInfo.setOnClickListener {
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

        binding.nsvTrackers.setOnScrollChangeListener { v: androidx.core.widget.NestedScrollView, _, scrollY, _, _ ->
            binding.vTrackerDividerTop.alpha = if (scrollY <= 0) 1f else 0f
            val canScrollDown = v.getChildAt(0).measuredHeight > scrollY + v.measuredHeight
            binding.vTrackerDividerBottom.alpha = if (!canScrollDown) 1f else 0f
        }

        searchManager.setupSearch()
        setupSelectionActions()

        binding.ivSelectAll.setOnClickListener { 
            val allIds = adapter.currentList.map { it.id }
            adapter.selectAll()
            updateSelectionUI(allIds.size)
        }
        binding.ivDeleteSelected.setOnClickListener { confirmDeleteSelected() }
        binding.ivCloseSelection.setOnClickListener { exitSelectionMode() }
        
        binding.ivMoreActions.setOnClickListener { v ->
            showMoreActionsMenu(v)
        }
        
        binding.fabAdd.setOnClickListener {
            showAddTorrentDialog()
        }
    }

    private fun showMoreActionsMenu(v: View) {
        val popupContext = androidx.appcompat.view.ContextThemeWrapper(this, R.style.AppPopupTheme)
        val popup = androidx.appcompat.widget.PopupMenu(popupContext, v)
        
        binding.vDimOverlay.visibility = View.VISIBLE
        binding.vDimOverlay.animate()?.alpha(1f)?.setDuration(200)?.start()

        popup.setOnDismissListener {
            binding.vDimOverlay.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
                binding.vDimOverlay.visibility = View.GONE
            }?.start()
        }

        val selectedCount = adapter.getSelectedIds().size
        
        popup.menu.add(0, 2, 0, getString(R.string.menu_pause))
        popup.menu.add(0, 1, 0, getString(R.string.menu_start))
        
        val renameItem = popup.menu.add(0, 3, 0, getString(R.string.menu_rename))
        renameItem.isEnabled = selectedCount == 1
        if (!renameItem.isEnabled) {
            val spanString = SpannableString(renameItem.title.toString())
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
                6 -> viewModel.reannounceTorrents(rpcUrl, user, pass, adapter.getSelectedIds()) {
                    exitSelectionMode()
                    refreshTorrents()
                }
            }
            true
        }
        popup.show()
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        
        if (uri != null) {
            handleExternalUri(uri)
            // 清理已处理的 Intent 数据，防止旋屏后重复触发
            intent.data = null
            intent.removeExtra(Intent.EXTRA_STREAM)
        } else if (sharedText != null) {
            handleSharedText(sharedText)
            intent.removeExtra(Intent.EXTRA_TEXT)
        }
    }

    private fun refreshTorrents() {
        viewModel.refreshTorrents(rpcUrl, user, pass)
    }

    private fun updateServerInfoText() {
        val cleanUrl = rpcUrl.removeSuffix("/transmission/rpc")
        if (isUrlVisible) {
            headerBinding.tvServerInfo.text = cleanUrl
            headerBinding.tvServerInfo.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            val spannable = SpannableString(cleanUrl)
            val startIndex = when {
                cleanUrl.startsWith("https://") -> 8
                cleanUrl.startsWith("http://") -> 7
                else -> 0
            }
            if (startIndex < cleanUrl.length) {
                headerBinding.tvServerInfo.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                spannable.setSpan(
                    MaskFilterSpan(BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)),
                    startIndex,
                    cleanUrl.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            headerBinding.tvServerInfo.text = spannable
        }
    }

    private fun getFilterTitle(filter: String): String {
        return when (filter) {
            "Downloading" -> getString(R.string.nav_downloading)
            "Seeding" -> getString(R.string.nav_seeding)
            "Paused" -> getString(R.string.nav_paused)
            "Active" -> getString(R.string.nav_active)
            "Inactive" -> getString(R.string.nav_inactive)
            "Error" -> getString(R.string.nav_error)
            else -> if (filter.startsWith("tracker:")) filter.substringAfter("tracker:") else getString(R.string.nav_all)
        }
    }

    private fun updateTitleWithTotalSize(filter: String, size: String) {
        val titleText = getFilterTitle(filter)
        val fullText = "$titleText ($size)"
        val spannable = SpannableString(fullText)
        val startIndex = fullText.indexOf("(")
        if (startIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary)), startIndex, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(AbsoluteSizeSpan(14, true), startIndex, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvTitle.text = spannable
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            val newFilter = when (menuItem.itemId) {
                R.id.nav_all -> "All"
                R.id.nav_downloading -> "Downloading"
                R.id.nav_seeding -> "Seeding"
                R.id.nav_paused -> "Paused"
                R.id.nav_active -> "Active"
                R.id.nav_inactive -> "Inactive"
                R.id.nav_error -> "Error"
                else -> "All"
            }
            
            currentFilter = newFilter
            viewModel.setFilter(newFilter)
            
            getSharedPreferences("filter_prefs", MODE_PRIVATE).edit {
                putString("last_filter", newFilter)
            }
            
            binding.tvTitle.text = menuItem.title.toString().substringBefore(" (")
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            
            if (searchManager.isSearchVisible()) {
                searchManager.closeSearch()
            }
            
            true
        }

        binding.ivMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                isDrawerMoving = true
            }
            override fun onDrawerOpened(drawerView: View) {
                isDrawerMoving = false
                viewModel.trackerData.value?.let { trackerManager.updateTrackerChips(it, false) }
            }
            override fun onDrawerClosed(drawerView: View) {
                isDrawerMoving = false
            }
            override fun onDrawerStateChanged(newState: Int) {
                if (newState == DrawerLayout.STATE_IDLE) {
                    isDrawerMoving = false
                    viewModel.trackerData.value?.let { trackerManager.updateTrackerChips(it, false) }
                }
            }
        })
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (searchManager.isSearchVisible()) {
                    searchManager.closeSearch()
                } else if (adapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackTime > 2000) {
                        Toast.makeText(this@TorrentListActivity, R.string.msg_exit_press_again, Toast.LENGTH_SHORT).show()
                        lastBackTime = currentTime
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun setupSelectionActions() {
        binding.ivTurtle.setOnClickListener {
            viewModel.toggleAltSpeedLimits(rpcUrl, user, pass)
        }
    }

    private fun updateSelectionUI(count: Int) {
        if (count > 0) {
            binding.layoutSelectionBar.isVisible = true
            binding.tvSelectionCount.text = getString(R.string.selected_count, count)
        } else {
            binding.layoutSelectionBar.isVisible = false
        }
    }

    private fun exitSelectionMode() {
        adapter.isSelectionMode = false
        adapter.clearSelection()
        updateSelectionUI(0)
        
        if (searchManager.isSearchVisible() && searchManager.getSearchQuery().isNotEmpty()) {
            viewModel.setSearchQuery(searchManager.getSearchQuery())
        } else {
            viewModel.setFilter(currentFilter)
        }
    }

    private fun confirmDeleteSelected() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) return

        val hasActiveHr = adapter.currentList.filter { selectedIds.contains(it.id) }.any { torrent ->
            val hrLabel = torrent.labels?.find { it.startsWith("HR:") }
            if (hrLabel != null) {
                val hours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
                if (hours > 0) {
                    if (torrent.percentDone < 1.0) {
                        true 
                    } else {
                        val doneDateMs = torrent.doneDate * 1000L
                        val requiredMs = (hours * 3600 * 1000L).toLong()
                        val elapsedMs = System.currentTimeMillis() - doneDateMs
                        elapsedMs < requiredMs 
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
            deleteDataByDefault = !hasActiveHr,
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
        val menu = binding.navigationView.menu

        fun updateItem(itemId: Int, nameRes: Int, key: String) {
            val newItem = menu.findItem(itemId) ?: menu.add(0, itemId, Menu.NONE, nameRes).apply {
                isCheckable = true
            }
            val itemData = data[key] ?: DrawerItemData(0, 0L)
            
            newItem.title = "${getString(nameRes)} (${itemData.count})"
            
            val actionView = newItem.actionView as? TextView ?: TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER_VERTICAL
                val color = if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    ContextCompat.getColor(this@TorrentListActivity, R.color.text_white_53)
                } else {
                    ContextCompat.getColor(this@TorrentListActivity, R.color.state_gray_alt)
                }
                setTextColor(color)
                textSize = 12f
                newItem.actionView = this
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
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
        val urlRegex = """(https?|magnet):\S+""".toRegex(RegexOption.IGNORE_CASE)
        val match = urlRegex.find(trimmed)
        val url = match?.value ?: trimmed
        
        showAddTorrentDialog(initialUrl = url)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::swipeGestureDetector.isInitialized) {
            swipeGestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        searchManager.onDestroy()
        trackerManager.onDestroy()
        super.onDestroy()
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
