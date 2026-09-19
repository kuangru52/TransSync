package com.kuangru52.transsync

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TorrentListActivity : AppCompatActivity() {

    private val viewModel: TorrentListViewModel by viewModels()

    private var rpcUrl: String = ""
    private var user: String = ""
    private var pass: String = ""

    private var selectedFileUriForAdd by mutableStateOf<Uri?>(null)
    private var initialUrlForAdd by mutableStateOf<String?>(null)
    private var showAddTorrentState by mutableStateOf(value = false)

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            showAddTorrentDialog(initialFileUri = it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val activeServer = ServerManager.getActiveServer(this)
        rpcUrl = intent.getStringExtra("rpcUrl") ?: activeServer?.rpcUrl ?: prefs.getString("rpcUrl", "") ?: ""
        user = intent.getStringExtra("user") ?: activeServer?.user ?: prefs.getString("user", "") ?: ""
        pass = intent.getStringExtra("pass") ?: activeServer?.pass ?: prefs.getString("pass", "") ?: ""

        setContent {
            MaterialTheme {
                TorrentListScreen(
                    viewModel = viewModel,
                    rpcUrl = rpcUrl,
                    user = user,
                    pass = pass,
                    externalShowAddTorrentDialog = showAddTorrentState,
                    externalInitialUrl = initialUrlForAdd,
                    externalInitialFileUri = selectedFileUriForAdd,
                    onCloseExternalAddTorrentDialog = {
                        showAddTorrentState = false
                        initialUrlForAdd = null
                        selectedFileUriForAdd = null
                    },
                    onTorrentClick = { torrent ->
                        val intent = Intent(this, TorrentDetailActivity::class.java).apply {
                            putExtra("rpcUrl", rpcUrl)
                            putExtra("user", user)
                            putExtra("pass", pass)
                            putExtra("torrent_id", torrent.id)
                            putExtra("torrent_name", torrent.name)
                        }
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.stay_still)
                    },
                    onAddClick = {
                        showAddTorrentDialog()
                    },
                    onPickFile = {
                        filePickerLauncher.launch("*/*")
                    },
                )
            }
        }

        handleIntent(intent)
        startPeriodicRefresh()
    }

    override fun onResume() {
        super.onResume()
        val activeServer = ServerManager.getActiveServer(this)
        if (activeServer != null) {
            rpcUrl = activeServer.rpcUrl
            user = activeServer.user
            pass = activeServer.pass
            viewModel.refreshTorrents(rpcUrl, user, pass)
        }
    }

    private fun startPeriodicRefresh() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    val activeServer = ServerManager.getActiveServer(this@TorrentListActivity)
                    if (activeServer != null) {
                        rpcUrl = activeServer.rpcUrl
                        user = activeServer.user
                        pass = activeServer.pass
                    }
                    viewModel.refreshTorrents(rpcUrl, user, pass)
                    delay(5000.milliseconds)
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleExternalUri(uri: Uri) {
        val scheme = uri.scheme
        val isMagnetOrHttp = (scheme == "magnet") || (scheme == "http") || (scheme == "https")
        if (isMagnetOrHttp) {
            showAddTorrentDialog(initialUrl = uri.toString())
        } else if ((scheme == "file") || (scheme == "content")) {
            val fileName = uri.path?.lowercase()
            val isTorrentFile = (fileName?.endsWith(".torrent") == true) || (contentResolver.getType(uri) == "application/x-bittorrent")
            if (isTorrentFile) {
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

    private fun showAddTorrentDialog(initialUrl: String? = null, initialFileUri: Uri? = null) {
        initialUrl?.let { initialUrlForAdd = it }
        initialFileUri?.let { selectedFileUriForAdd = it }
        showAddTorrentState = true
    }
}
