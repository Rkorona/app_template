// app_template/app/src/main/java/com/example/myapplication/ui/screens/DashboardScreen.kt

package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// =====================================================================================
// 数据模型 —— 之前是写死的字符串，现在拆成状态，方便接 ViewModel / 真实数据源
// =====================================================================================

enum class LogStatus { SUCCESS, RUNNING, FAILED }

data class LogEntry(
    val time: String,
    val scriptName: String,
    val message: String,
    val status: LogStatus
)

data class NextRun(val time: String, val scriptName: String)

data class DashboardUiState(
    val serviceRunning: Boolean = true,
    val uptimeLabel: String = "已连续运行 24 小时",
    val totalScripts: Int = 12,
    val runningNow: Int = 2,
    val triggeredToday: Int = 148,
    val failedCount: Int = 1,
    val nextRun: NextRun = NextRun("02:00", "daily_check.js"),
    val ramUsedGb: Float = 2.4f,
    val ramTotalGb: Float = 4f,
    val storageUsedGb: Float = 1.2f,
    val storageTotalGb: Float = 10f,
    val recentLogs: List<LogEntry> = listOf(
        LogEntry("21:40:08", "telegram_bot.py", "通知发送成功", LogStatus.SUCCESS),
        LogEntry("21:55:12", "daily_check.js", "正在执行 npm run...", LogStatus.RUNNING),
        LogEntry("22:00:00", "sign_in.py", "登录失败：凭证过期", LogStatus.FAILED)
    )
)

// =====================================================================================
// 主屏幕
// =====================================================================================

@Composable
fun DashboardScreen(
    state: DashboardUiState = DashboardUiState(),
    contentPadding: PaddingValues = PaddingValues(),
    onRestartService: () -> Unit = {},
    onViewServiceLogs: () -> Unit = {},
    onFailuresClick: () -> Unit = {},
    onStatClick: (String) -> Unit = {},
    onViewAllLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 入场时做一次轻量的编排动效（淡入 + 上浮），而不是各卡片各自闪现
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "service") {
            AnimatedSection(visible, 0) {
                ServiceHealthCard(state, onRestartService, onViewServiceLogs)
            }
        }

        // 失败数从 KPI 网格里拎出来，做成可点击的警示条 —— 0 失败时直接不渲染，
        // “没有横幅”本身就是一种状态信息，不需要用 0 去填格子
        if (state.failedCount > 0) {
            item(key = "failures") {
                AnimatedSection(visible, 60) {
                    FailureBanner(state.failedCount, onFailuresClick)
                }
            }
        }

        item(key = "stats") {
            AnimatedSection(visible, 120) {
                StatGrid(state, onStatClick)
            }
        }

        item(key = "resources") {
            AnimatedSection(visible, 180) {
                ResourceCard(state)
            }
        }

        item(key = "logs") {
            AnimatedSection(visible, 240) {
                TerminalLogCard(state.recentLogs, onViewAllLogs)
            }
        }
    }
}

@Composable
private fun AnimatedSection(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350, delayMillis)) +
            slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = tween(350, delayMillis)
            )
    ) {
        content()
    }
}

// =====================================================================================
// 1. 守护服务状态卡
// =====================================================================================

@Composable
private fun ServiceHealthCard(
    state: DashboardUiState,
    onRestart: () -> Unit,
    onViewLogs: () -> Unit
) {
    val statusColor = if (state.serviceRunning) Color(0xFF22C55E) else MaterialTheme.colorScheme.error

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 呼吸灯：服务运行中才有脉动透明度，停止时是静态红点
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            statusColor.copy(alpha = if (state.serviceRunning) pulseAlpha else 0.2f),
                            RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(10.dp).background(statusColor, RoundedCornerShape(50)))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "面板守护服务",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (state.serviceRunning) state.uptimeLabel else "服务已停止，点击重启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                // 之前“最近执行动态”没有入口可以看全部日志，这里补一个直达按钮
                IconButton(onClick = onViewLogs) {
                    Icon(Icons.Default.History, contentDescription = "查看日志")
                }
                FilledIconButton(
                    onClick = onRestart,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重启服务")
                }
            }
        }
    }
}

// =====================================================================================
// 2. 失败警示条（仅在 failedCount > 0 时出现）
// =====================================================================================

@Composable
private fun FailureBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "$count 个脚本执行失败，点击查看详情",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

// =====================================================================================
// 3. KPI 网格 —— 颜色全部换成主题 tonal token，不再是写死的十六进制
// =====================================================================================

@Composable
private fun StatGrid(state: DashboardUiState, onStatClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(
                title = "总脚本数",
                value = state.totalScripts.toString(),
                icon = Icons.Default.Code,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
                onClick = { onStatClick("total") }
            )
            StatCard(
                title = "当前运行",
                value = state.runningNow.toString(),
                icon = Icons.Default.PlayArrow,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
                onClick = { onStatClick("running") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(
                title = "今日触发",
                value = state.triggeredToday.toString(),
                icon = Icons.Default.Speed,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
                onClick = { onStatClick("triggered") }
            )
            // 原来第四格是“执行失败”，现在挪到顶部警示条；这里换成青龙类面板里
            // 真正有用、但原设计缺失的信息——下一次定时任务什么时候跑、跑哪个脚本
            StatCard(
                title = "下次执行",
                value = state.nextRun.time,
                caption = state.nextRun.scriptName,
                icon = Icons.Default.Schedule,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                onClick = { onStatClick("nextRun") }
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    caption: String? = null,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.expressiveClickable(onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
            )
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// =====================================================================================
// 4. 系统资源 —— 进度条按用量阈值变色，而不是固定蓝/绿
// =====================================================================================

@Composable
private fun ResourceCard(state: DashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "系统资源",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ResourceRow("RAM 内存占用", state.ramUsedGb, state.ramTotalGb)
            ResourceRow("沙盒存储空间", state.storageUsedGb, state.storageTotalGb)
        }
    }
}

@Composable
private fun ResourceRow(label: String, usedGb: Float, totalGb: Float) {
    val pct = (usedGb / totalGb).coerceIn(0f, 1f)
    val barColor = when {
        pct >= 0.85f -> MaterialTheme.colorScheme.error
        pct >= 0.6f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val animatedProgress by animateFloatAsState(
        targetValue = pct,
        animationSpec = tween(700, easing = FastOutSlowInEasing)
    )

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(pct * 100).roundToInt()}% (${usedGb.trimZero()}G / ${totalGb.trimZero()}G)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

private fun Float.trimZero(): String =
    if (this == this.toInt().toFloat()) this.toInt().toString() else this.toString()

// =====================================================================================
// 5. 终端日志卡 —— 这是整个仪表盘里唯一刻意保留的“非主题色”区域，
//    深色背景 + 荧光等宽字体，呼应“脚本面板/终端”这个产品本质
// =====================================================================================

@Composable
private fun TerminalLogCard(logs: List<LogEntry>, onViewAll: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近执行动态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onViewAll) {
                    Text("查看全部", color = Color(0xFF38BDF8), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            logs.forEachIndexed { index, entry ->
                LogLine(entry)
                if (index != logs.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val (icon, color) = when (entry.status) {
        LogStatus.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF4ADE80)
        LogStatus.RUNNING -> Icons.Default.PlayArrow to Color(0xFF38BDF8)
        LogStatus.FAILED -> Icons.Default.Cancel to Color(0xFFF87171)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "[${entry.time}] ${entry.scriptName} -> ${entry.message}",
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )
    }
}

// =====================================================================================
// 触感反馈：点击时轻微缩放 + 弹性回弹，比单纯的水波纹更贴近 M3 Expressive 的“可触摸”质感
// =====================================================================================

private fun Modifier.expressiveClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

// =====================================================================================
// 预览
// =====================================================================================

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    MaterialTheme {
        DashboardScreen()
    }
}

@Preview(showBackground = true, name = "无失败 / 服务停止")
@Composable
private fun DashboardScreenEdgeCasesPreview() {
    MaterialTheme {
        DashboardScreen(
            state = DashboardUiState(
                serviceRunning = false,
                failedCount = 0
            )
        )
    }
}
