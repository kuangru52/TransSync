package com.kuangru52.TransSync

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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class FeedbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val scrollView = findViewById<View>(R.id.scrollView)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
            insets
        }

        val tvGithubLink = findViewById<TextView>(R.id.tvGithubLink)
        tvGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kuangru52/TransSync"))
            startActivity(intent)
        }

        val tvTelegramLink = findViewById<TextView>(R.id.tvTelegramLink)
        val telegramFullText = "本应用更新请加群组：\nhttps://t.me/+FFEviJJq9GkyOWFl"
        val telegramSpannable = SpannableString(telegramFullText)
        val urlStart = telegramFullText.indexOf("https://")
        if (urlStart != -1) {
            telegramSpannable.setSpan(
                android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.state_blue)),
                urlStart,
                telegramFullText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvTelegramLink.text = telegramSpannable
        tvTelegramLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+FFEviJJq9GkyOWFl"))
            startActivity(intent)
        }

        setupTrackerInfo()

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.12"
        }
        tvVersion.text = "v$versionName"
    }

    private fun setupTrackerInfo() {
        val tvTrackerInfo = findViewById<TextView>(R.id.tvTrackerInfo)
        val email = "kuangru52@163.com"
        val fullText = "由于 tracker 标签采用白名单制，我已经尽可能地添加我所拥有的站点。如果您希望增加其他站点，请以 www.baidu.com=baidu 这种形式投稿到我的邮箱 $email。为保证站点安全，切不可在 GitHub 上提交 issues。反馈按钮会在 tracker 标签完善后去掉。"
        
        val spannable = SpannableString(fullText)
        val startIndex = fullText.indexOf(email)
        val endIndex = startIndex + email.length

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("email", email)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@FeedbackActivity, "邮箱地址已复制到剪切板", Toast.LENGTH_SHORT).show()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@FeedbackActivity, R.color.state_blue)
                ds.isUnderlineText = false // 不显示下划线
                ds.isFakeBoldText = true   // 加粗
            }
        }

        spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        tvTrackerInfo.text = spannable
        tvTrackerInfo.movementMethod = LinkMovementMethod.getInstance() // 必须设置才能点击
    }
}
