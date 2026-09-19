package com.kuangru52.transsync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 1:1 绝对对齐截图卡片尺寸 (外边距 12dp, 圆角 16dp)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TorrentItemCard(
    torrent: Torrent,
    isSelected: Boolean,
    isTrackerBlurEnabled: Boolean,
    revealedTrackerNames: Set<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val isPaused = torrent.status == 0

    val statusColor = when {
        (torrent.error != 0) || ((torrent.errorString.isNotEmpty()) && (!torrent.errorString.contains("none", ignoreCase = true))) -> Color(0xFFF44336)
        ((torrent.status == 1) || (torrent.status == 2)) -> Color(0xFFF9A825)
        torrent.status == 0 -> Color(0xFF737373)
        torrent.percentDone >= 1.0 -> Color(0xFF43A047)
        else -> Color(0xFF2196F3)
    }

    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val secondaryTextColor = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
    val cardBgColor = if (isSelected) {
        if (isDark) Color(0xFF3F4348) else Color(0x1A2196F3)
    } else {
        if (isDark) Color(0xFF212D3B) else Color.White
    }
    val cardBorderColor = if (isDark) Color(0x1AFFFFFF) else Color(0xFFE0E0E0)

    val tagBgColor = if (isDark) Color(0xFF212D3B) else Color(0xFFF0F2F5)
    val tagStrokeColor = if (isDark) Color(0xFF34495E) else Color(0xFFE0E0E0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. 左侧状态圆形按钮
            val iconRes = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
            val iconTint = if (isPaused) Color(0xFF737373) else Color(0xFF888888)

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tagBgColor)
                    .border(1.dp, tagStrokeColor, CircleShape)
                    .clickable { onToggleStatus() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = if (isPaused) "开始" else "暂停",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. 右侧所有元素列
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // 1) 种子标题 (在点号 '.' 处自然换行，保证 x265, WEB-DL, BluRay 词汇完整不拆断)
                Text(
                    text = FormatUtils.formatTorrentTitle(torrent.name),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        lineBreak = LineBreak.Paragraph,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(0.dp))

                // 2) 体积与 Tracker 行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                ) {
                    Text(
                        text = torrent.displaySize,
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            color = secondaryTextColor,
                        ),
                    )

                    if (torrent.trackerName.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val shouldBlur = isTrackerBlurEnabled && !revealedTrackerNames.contains(torrent.trackerName)
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(tagBgColor)
                                .border(1.dp, tagStrokeColor, RoundedCornerShape(100.dp))
                                .then(if (shouldBlur) Modifier.blur(6.dp) else Modifier)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = torrent.trackerName,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    color = secondaryTextColor,
                                ),
                            )
                        }
                    }

                    HrTagCapsule(torrent = torrent, secondaryTextColor = secondaryTextColor)

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = torrent.displayDownloadSpeed,
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            color = secondaryTextColor,
                        ),
                    )
                }

                if ((torrent.error != 0) && (torrent.errorString.isNotEmpty())) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = torrent.errorString,
                        fontSize = 12.sp,
                        color = Color(0xFFF44336),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 3) 上传统计与上传速度行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                ) {
                    Text(
                        text = torrent.displayStats,
                        style = TextStyle(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            color = secondaryTextColor,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = torrent.displayUploadSpeed,
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            color = secondaryTextColor,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 4) 进度条与百分比胶囊
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val totalWidth = this.maxWidth
                    val fraction = (torrent.displayProgress / 1000f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)),
                    )

                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = fraction)
                                .height(2.dp)
                                .background(statusColor),
                        )
                    }

                    if (torrent.displayProgress < 1000) {
                        val pillWidth = 46.dp
                        val rawOffset = totalWidth * fraction
                        val startOffset = (rawOffset - (pillWidth / 2)).coerceIn(0.dp, (totalWidth - pillWidth).coerceAtLeast(0.dp))

                        Box(
                            modifier = Modifier.padding(start = startOffset),
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(tagBgColor)
                                    .border(1.dp, tagStrokeColor, RoundedCornerShape(100.dp))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", torrent.displayProgress / 10f),
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        lineHeight = 11.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        color = secondaryTextColor,
                                    ),
                                )
                            }
                        }
                    }
                }

                if (torrent.displayStatusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = torrent.displayStatusText,
                        fontSize = 11.sp,
                        color = secondaryTextColor,
                    )
                }
            }
        }
    }
}

@Composable
fun HrTagCapsule(
    torrent: Torrent,
    secondaryTextColor: Color,
) {
    val hrLabel = torrent.labels?.find { it.startsWith("HR:") } ?: return
    val hours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
    if (hours <= 0) return

    val isDark = isSystemInDarkTheme()
    val hrBgColor = if (isDark) Color(0xFF1E334D) else Color(0xFFE3F2FD)
    val hrStrokeColor = if (isDark) Color(0xFF294E6B) else Color(0xFFBBDEFB)
    val hrTextColor = if (isDark) Color(0xFF4FA3F7) else Color(0xFF1976D2)

    Spacer(modifier = Modifier.width(8.dp))

    if (torrent.percentDone < 1.0) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(hrBgColor)
                .border(1.dp, hrStrokeColor, RoundedCornerShape(100.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "H&R",
                style = TextStyle(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    color = secondaryTextColor,
                )
            )
        }
        return
    }

    val doneDateMs = torrent.doneDate * 1000L
    val currentTime = System.currentTimeMillis()
    val totalRequiredMs = (hours * 3600 * 1000L).toLong()
    val elapsedMs = currentTime - doneDateMs
    val remainingMs = totalRequiredMs - elapsedMs
    val bufferMs = 30 * 60 * 1000L

    if (remainingMs <= -bufferMs) {
        Icon(
            painter = painterResource(id = R.drawable.ic_done),
            contentDescription = "已核销",
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp)
        )
    } else if (remainingMs > 0) {
        val totalMins = remainingMs / (60 * 1000L)
        val hrs = totalMins / 60
        val mins = totalMins % 60
        val text = if (hrs >= 24) {
            val days = hrs / 24
            val h = hrs % 24
            "${days}d ${h}h"
        } else {
            "${hrs}h ${mins}m"
        }
        Box(
            modifier = Modifier
                .height(18.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(hrBgColor)
                .border(1.dp, hrStrokeColor, RoundedCornerShape(100.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    color = hrTextColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    } else {
        val remainingBufferMs = bufferMs + remainingMs
        val totalSecs = remainingBufferMs.coerceAtLeast(0L) / 1000L
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val text = String.format(Locale.US, "%02d:%02d", mins, secs)
        Box(
            modifier = Modifier
                .height(18.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(hrBgColor)
                .border(1.dp, hrStrokeColor, RoundedCornerShape(100.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    color = hrTextColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TorrentItemCardPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            TorrentItemCard(
                torrent = Torrent(
                    id = 1,
                    name = "Jumanji.The.Next.Level.2026.2160p.HQ.WEB-DL.mkv",
                    totalSize = 44238000000L,
                    percentDone = 0.605,
                    rateDownload = 1200000L,
                    rateUpload = 450000L,
                    status = 4,
                    displayProgress = 605,
                    displaySize = "26.7 GB / 44.2 GB",
                    displayDownloadSpeed = "1.2 MB/s ↓",
                    displayUploadSpeed = "450 KB/s ↑",
                    displayStats = "Uploaded: 160.0 GB (Ratio: 6.04)",
                    trackerName = "Google",
                ),
                isSelected = false,
                isTrackerBlurEnabled = false,
                revealedTrackerNames = emptySet(),
                onClick = {},
                onLongClick = {},
                onToggleStatus = {}
            )
        }
    }
}
