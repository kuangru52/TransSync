package com.kuangru52.transsync

import com.kuangru52.transsync.R
import android.content.pm.ActivityInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class FeedbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val scrollView = findViewById<View>(R.id.scrollView)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
            insets
        }

        val tvGithubLink = findViewById<TextView>(R.id.tvGithubLink)
        tvGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, getString(R.string.github_url).toUri())
            startActivity(intent)
        }

        val tvTelegramLink = findViewById<TextView>(R.id.tvTelegramLink)
        val telegramUrl = getString(R.string.telegram_url)
        val telegramFullText = getString(R.string.telegram_text, telegramUrl)
        val telegramSpannable = SpannableString(telegramFullText)
        val urlStart = telegramFullText.indexOf(telegramUrl)
        if (urlStart != -1) {
            telegramSpannable.setSpan(
                android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.state_blue)),
                urlStart,
                urlStart + telegramUrl.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvTelegramLink.text = telegramSpannable
        tvTelegramLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, telegramUrl.toUri())
            startActivity(intent)
        }

        setupTrackerInfo()

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val versionName = try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName
        } catch (e: Exception) {
            "1.20"
        }
        tvVersion.text = getString(R.string.version_format, versionName)
    }

    private fun setupTrackerInfo() {
        val tvTrackerInfo = findViewById<TextView>(R.id.tvTrackerInfo)
        val email = getString(R.string.email_address)
        val fullText = getString(R.string.feedback_tracker_info, email)
        
        val spannable = SpannableString(fullText)
        val startIndex = fullText.indexOf(email)
        val endIndex = startIndex + email.length

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("email", email)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@FeedbackActivity, R.string.feedback_email_copied, Toast.LENGTH_SHORT).show()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@FeedbackActivity, R.color.state_blue)
                ds.isUnderlineText = false // 不显示下划线
                ds.isFakeBoldText = true   // 加粗
            }
        }

        if (startIndex != -1) {
            spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        tvTrackerInfo.text = spannable
        tvTrackerInfo.movementMethod = LinkMovementMethod.getInstance() // 必须设置才能点击
    }
}
