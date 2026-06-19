package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(
    // 📌 1. 声明接收来自外层的系统内边距（包含顶栏和底栏的高度）
    contentPadding: PaddingValues = PaddingValues(), 
    modifier: Modifier = Modifier
) {
    LazyColumn(
        // 📌 2. 这里的外壳 modifier 保持纯净，只负责填满全屏
        modifier = modifier.fillMaxSize(),
        
        // 📌 3. 核心魔法：将系统边距与我们自定义的 16.dp 完美的累加在一起
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==== 1. 后台守护状态 ====
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 绿色的状态点（模拟呼吸灯）
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF22C55E), RoundedCornerShape(50))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("面板守护服务", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("已连续运行 24 小时", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    IconButton(onClick = { /* 重启服务 */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // ==== 2. 数据战报网格 (2x2) ====
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusCard(title = "总脚本数", value = "12", modifier = Modifier.weight(1f), iconColor = MaterialTheme.colorScheme.primary)
                    StatusCard(title = "当前运行", value = "2", modifier = Modifier.weight(1f), iconColor = MaterialTheme.colorScheme.tertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusCard(title = "今日触发", value = "148", modifier = Modifier.weight(1f), iconColor = Color(0xFF22C55E))
                    // 失败数：如果有失败，把文字颜色设为 Material 3 的 error 红色
                    StatusCard(title = "执行失败", value = "1", modifier = Modifier.weight(1f), iconColor = MaterialTheme.colorScheme.error, isError = true)
                }
            }
        }

        // ==== 3. 系统资源监控 ====
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("系统资源", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    
                    // 内存进度条
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("RAM 内存占用", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            Text("65% (2.4G / 4G)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { 0.65f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }

                    // 存储进度条
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("沙盒存储空间", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            Text("12% (1.2G / 10G)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { 0.12f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF22C55E))
                    }
                }
            }
        }

        // ==== 4. 最近终端动态流 ====
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // 故意用极深黑，模拟终端感
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("最近执行动态", fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 模拟日志行，采用等宽字体 FontFamily.Monospace
                    val logStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    Text("[21:40:08] telegram_bot.py -> 通知发送成功 ✅", color = Color(0xFF4ADE80), style = logStyle)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("[21:55:12] daily_check.js -> 正在跑 npm run...", color = Color(0xFF38BDF8), style = logStyle)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("[22:00:00] sign_in.py -> 登录失败: 凭证过期 ❌", color = Color(0xFFF87171), style = logStyle)
                }
            }
        }
    }
    
}

// 抽离出来的网格小卡片组件
@Composable
fun StatusCard(title: String, value: String, modifier: Modifier = Modifier, iconColor: Color, isError: Boolean = false) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (isError && value != "0") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
