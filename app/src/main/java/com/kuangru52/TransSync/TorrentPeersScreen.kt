package com.kuangru52.transsync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 100% 纯 Compose 版本的 TorrentPeersScreen 节点界面：
 * - 下拉手势支持 Material3 旋转下拉刷新动画 Indicator
 * - 空数据或有数据状态下均能流畅捕捉下拉手势，触发 Transmission torrent-reannounce 重新汇报
 * - 包含 IP 地址、GeoIP 国旗 Emoji、客户端名称、Flags 标志、进度与上下行速度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentPeersScreen(
    peers: List<Peer>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = if (isDark) Color(0xFF212D3B) else Color.White,
                color = if (isDark) Color(0xFF00B0FF) else Color(0xFF1D88E3),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        if (peers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无连接节点 (下拉重新汇报)",
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 56.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = peers,
                    key = { it.address },
                ) { peer ->
                    PeerCard(peer = peer, isDark = isDark)
                }
            }
        }
    }
}

@Composable
fun PeerCard(
    peer: Peer,
    isDark: Boolean,
) {
    val cardBgColor = if (isDark) Color(0x99141D26) else Color(0xA6FFFFFF)
    val cardBorderColor = if (isDark) Color(0x3BFFFFFF) else Color(0x55E0E0E0)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val secondaryTextColor = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)

    var countryEmoji by remember(peer.address) { mutableStateOf<String?>(null) }

    LaunchedEffect(peer.address) {
        GeoIpService.getCountryEmoji(peer.address) { emoji ->
            countryEmoji = emoji
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // 1. 顶行：国旗 + IP 地址 + 尾部 Flags
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!countryEmoji.isNullOrEmpty()) {
                        Text(
                            text = countryEmoji!!,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        text = peer.address,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                    )
                }

                if (peer.flagStr.isNotEmpty()) {
                    Text(
                        text = peer.flagStr,
                        fontSize = 11.sp,
                        color = secondaryTextColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 2. 中行：客户端名称
            Text(
                text = peer.clientName.ifEmpty { "未知客户端" },
                fontSize = 12.sp,
                color = secondaryTextColor,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. 底行：进度 + 下载/上传速度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "进度: ${(peer.progress * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.weight(1f),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "↓ ${FormatUtils.formatSpeed(peer.rateToClient.toDouble())}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryTextColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "↑ ${FormatUtils.formatSpeed(peer.rateToPeer.toDouble())}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryTextColor,
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun TorrentPeersScreenPreview() {
    MaterialTheme {
        TorrentPeersScreen(
            peers = listOf(
                Peer(
                address = "127.0.0.1:51413",
                clientName = "Transmission 4.0.0",
                flagStr = "T I U",
                progress = 1.0,
                rateToClient = 102400,
                rateToPeer = 51200,
            ),
            ),
            isRefreshing = false,
            onRefresh = {},
        )
    }
}
