package com.scripthub.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// =====================================================================================
// 数据模型
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
    val totalScripts: Int = 0,
    val enabledTaskCount: Int = 0,
    val triggeredToday: Int = 0,
    val failedCount: Int = 0,
    val nextRun: NextRun = NextRun("--:--", "暂无调度任务"),
    val recentLogs: List<LogEntry> = emptyList()
)

// =====================================================================================
// 主屏幕
// =====================================================================================

@Composable
fun DashboardScreen(
    state: DashboardUiState = DashboardUiState(),
    contentPadding: PaddingValues = PaddingValues(),
    onFailuresClick: () -> Unit = {},
    onStatClick: (String) -> Unit = {},
    onViewAllLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start  = 16.dp,
            end    = 16.dp,
            top    = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            AnimatedSection(visible, 0) {
                HeroCard(state, onViewAllLogs)
            }
        }

        item(key = "info_row") {
            AnimatedSection(visible, 80) {
                InfoRow(state, onStatClick)
            }
        }

        if (state.failedCount > 0) {
            item(key = "failures") {
                AnimatedSection(visible, 130) {
                    FailureBanner(state.failedCount, onFailuresClick)
                }
            }
        }

        item(key = "logs") {
            AnimatedSection(visible, 180) {
                RecentActivityCard(state.recentLogs, onViewAllLogs)
            }
        }
    }
}

@Composable
private fun AnimatedSection(visible: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(400, delayMs)) +
                  slideInVertically(initialOffsetY = { it / 8 }, animationSpec = tween(400, delayMs))
    ) { content() }
}

// =====================================================================================
// 1. Hero 卡 —— 今日活动摘要
// =====================================================================================

@Composable
private fun HeroCard(state: DashboardUiState, onViewAllLogs: () -> Unit) {
    val today = remember { SimpleDateFormat("M月d日 · E", Locale.CHINESE).format(Date()) }
    val c     = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(containerColor = c.primaryContainer),
        shape  = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Surface(
                    color = c.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text     = today,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style    = MaterialTheme.typography.labelMedium,
                        color    = c.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick        = onViewAllLogs,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        "执行日志",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = c.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint     = c.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text  = "${state.triggeredToday}",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = c.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = "次\n任务触发",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = c.onPrimaryContainer.copy(alpha = 0.65f),
                    modifier   = Modifier.padding(bottom = 5.dp),
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip(
                    label = "${state.totalScripts} 个脚本",
                    icon  = Icons.Default.Code,
                    tint  = c.primary,
                    bg    = c.primary.copy(alpha = 0.12f)
                )
                HeroChip(
                    label = "${state.enabledTaskCount} 个计划任务",
                    icon  = Icons.Default.Schedule,
                    tint  = c.primary,
                    bg    = c.primary.copy(alpha = 0.12f)
                )
            }
        }
    }
}

@Composable
private fun HeroChip(label: String, icon: ImageVector, tint: Color, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier             = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

// =====================================================================================
// 2. 信息双列 —— 下次执行 + 状态摘要
// =====================================================================================

@Composable
private fun InfoRow(state: DashboardUiState, onStatClick: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier              = Modifier.fillMaxWidth()
    ) {
        NextRunCard(state.nextRun, Modifier.weight(1.05f)) { onStatClick("nextRun") }
        StatusCard(state, Modifier.weight(1f)) { onStatClick("total") }
    }
}

@Composable
private fun NextRunCard(nextRun: NextRun, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c        = MaterialTheme.colorScheme
    val hasTask  = nextRun.scriptName != "暂无调度任务"

    Card(
        colors   = CardDefaults.cardColors(
            containerColor = if (hasTask) c.tertiaryContainer else c.surfaceContainer
        ),
        shape    = RoundedCornerShape(28.dp),
        modifier = modifier.expressiveClickable(onClick)
    ) {
        Column(Modifier.padding(18.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier        = Modifier
                        .size(34.dp)
                        .background(
                            if (hasTask) c.onTertiaryContainer.copy(alpha = 0.12f)
                            else c.onSurface.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint     = if (hasTask) c.onTertiaryContainer else c.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "下次执行",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = if (hasTask) c.onTertiaryContainer.copy(alpha = 0.7f) else c.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text  = nextRun.time,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = if (hasTask) c.onTertiaryContainer else c.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text     = if (hasTask) nextRun.scriptName else "无调度任务",
                style    = MaterialTheme.typography.bodySmall,
                color    = if (hasTask) c.onTertiaryContainer.copy(alpha = 0.65f) else c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusCard(state: DashboardUiState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c         = MaterialTheme.colorScheme
    val hasFailed = state.failedCount > 0

    Card(
        colors   = CardDefaults.cardColors(
            containerColor = if (hasFailed) c.errorContainer else c.secondaryContainer
        ),
        shape    = RoundedCornerShape(28.dp),
        modifier = modifier.expressiveClickable(onClick)
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier        = Modifier
                        .size(34.dp)
                        .background(
                            if (hasFailed) c.onErrorContainer.copy(alpha = 0.12f)
                            else c.onSecondaryContainer.copy(alpha = 0.12f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (hasFailed) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint     = if (hasFailed) c.onErrorContainer else c.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hasFailed) "执行异常" else "运行状态",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = if (hasFailed) c.onErrorContainer.copy(alpha = 0.7f) else c.onSecondaryContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text  = if (hasFailed) "${state.failedCount}" else "正常",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = if (hasFailed) c.onErrorContainer else c.onSecondaryContainer
                )
                Text(
                    text  = if (hasFailed) "个失败任务" else "无异常",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasFailed) c.onErrorContainer.copy(alpha = 0.65f) else c.onSecondaryContainer.copy(alpha = 0.65f)
                )
            }
        }
    }
}

// =====================================================================================
// 3. 失败警示条（全宽，仅 failedCount > 0 时出现）
// =====================================================================================

@Composable
private fun FailureBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().expressiveClickable(onClick),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor   = MaterialTheme.colorScheme.onErrorContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "$count 个脚本执行失败，点击查看详情",
                style    = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

// =====================================================================================
// 4. 最近活动卡
// =====================================================================================

@Composable
private fun RecentActivityCard(logs: List<LogEntry>, onViewAll: () -> Unit) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = LogCardBg),
        shape    = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier             = Modifier.weight(1f)
                ) {
                    Surface(
                        color = TerminalInfo.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint     = TerminalInfo,
                            modifier = Modifier.padding(6.dp).size(14.dp)
                        )
                    }
                    Text(
                        "最近执行动态",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = LogCardHeaderText
                    )
                }
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick        = onViewAll,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("全部日志", color = TerminalInfo, style = MaterialTheme.typography.labelSmall)
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint     = TerminalInfo
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (logs.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint     = LogCardMutedText,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "暂无执行记录",
                            color    = LogCardMutedText,
                            style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    logs.forEachIndexed { index, entry ->
                        ActivityLogRow(entry)
                        if (index != logs.lastIndex) {
                            HorizontalDivider(
                                color     = LogCardMutedText.copy(alpha = 0.15f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogRow(entry: LogEntry) {
    val (icon, color) = when (entry.status) {
        LogStatus.SUCCESS -> Icons.Default.CheckCircle to TerminalSuccess
        LogStatus.RUNNING -> Icons.Default.PlayArrow   to TerminalInfo
        LogStatus.FAILED  -> Icons.Default.Cancel      to TerminalError
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint     = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    entry.scriptName,
                    color    = color,
                    style    = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.time,
                    color  = LogCardMutedText,
                    style  = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1
                )
            }
            Text(
                entry.message,
                color  = color.copy(alpha = 0.6f),
                style  = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1
            )
        }
    }
}

// =====================================================================================
// 弹性点击效果
// =====================================================================================

private fun Modifier.expressiveClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label         = "scale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(28.dp))
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
