package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.RunLogEntity
import com.example.myapplication.ui.theme.TerminalError
import com.example.myapplication.ui.theme.TerminalSuccess
import com.example.myapplication.ui.theme.TerminalWarn
import com.example.myapplication.ui.theme.TerminalInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalLogBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val db = remember { AppDatabase.getDatabase(context) }
    val logs by db.runLogDao().getAllRecent()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedLog by remember { mutableStateOf<RunLogEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            // ── 标题栏 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "全部执行日志",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "最近 ${logs.size} 条记录 · 按时间倒序",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "暂无执行记录",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                        Text(
                            "执行任何脚本后，日志将汇总显示在这里",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        GlobalLogCard(
                            log = log,
                            onClick = { selectedLog = log }
                        )
                    }
                }
            }
        }
    }

    // 点击后打开详情
    selectedLog?.let { log ->
        GlobalLogDetailBottomSheet(
            log = log,
            onDismiss = { selectedLog = null }
        )
    }
}

@Composable
private fun GlobalLogCard(
    log: RunLogEntity,
    onClick: () -> Unit
) {
    val timeLabel = remember(log.startTime) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.startTime))
    }
    val durationLabel = when {
        log.durationMs <= 0L -> "时长未知"
        log.durationMs < 1000L -> "${log.durationMs}ms"
        else -> "${log.durationMs / 1000}s"
    }
    val isSuccess = log.exitCode == 0
    val isUnknown = log.exitCode == -1
    val statusColor = when {
        isSuccess -> TerminalSuccess
        isUnknown -> TerminalWarn
        else -> TerminalError
    }
    val statusLabel = when (log.exitCode) {
        0 -> "成功"
        -1 -> "未知"
        else -> "失败 (${log.exitCode})"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态图标
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )

            // 主内容
            Column(modifier = Modifier.weight(1f)) {
                // 脚本名（最显眼）
                Text(
                    text = log.scriptName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = durationLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // 脚本类型角标
            val ext = log.scriptName.substringAfterLast('.', "").lowercase()
            val typeLabel = when (ext) {
                "py" -> "PY"
                "sh" -> "SH"
                "js", "ts" -> "JS"
                else -> "•••"
            }
            val typeBgColor = when (ext) {
                "py" -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                "sh" -> Color(0xFF22C55E).copy(alpha = 0.15f)
                "js", "ts" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            }
            val typeFgColor = when (ext) {
                "py" -> Color(0xFF3B82F6)
                "sh" -> Color(0xFF22C55E)
                "js", "ts" -> Color(0xFFF59E0B)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(color = typeBgColor, shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = typeLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = typeFgColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalLogDetailBottomSheet(
    log: RunLogEntity,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val timeLabel = remember(log.startTime) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.startTime))
    }
    val durationLabel = when {
        log.durationMs <= 0L -> "时长未知"
        log.durationMs < 1000L -> "${log.durationMs}ms"
        else -> String.format("%.2fs", log.durationMs / 1000.0)
    }
    val isSuccess = log.exitCode == 0
    val isUnknown = log.exitCode == -1
    val statusColor = when {
        isSuccess -> TerminalSuccess
        isUnknown -> TerminalWarn
        else -> TerminalError
    }
    val statusLabel = when (log.exitCode) {
        0 -> "成功"
        -1 -> "未知"
        else -> "失败"
    }
    val logLines = remember(log.logText) {
        if (log.logText.isBlank()) emptyList() else log.logText.split("\n")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            // ── 标题栏 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Terminal,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = log.scriptName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 元数据摘要 ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GlobalMetaStat(label = "状态", value = statusLabel, valueColor = statusColor)
                    VerticalDivider(
                        modifier = Modifier.height(32.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    GlobalMetaStat(
                        label = "退出码",
                        value = if (log.exitCode == -1) "N/A" else log.exitCode.toString(),
                        valueColor = statusColor
                    )
                    VerticalDivider(
                        modifier = Modifier.height(32.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    GlobalMetaStat(label = "耗时", value = durationLabel)
                    VerticalDivider(
                        modifier = Modifier.height(32.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    GlobalMetaStat(label = "行数", value = "${logLines.size} 行")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "终端输出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (logLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "本次执行无终端输出",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(logLines) { line ->
                            val lineColor = when {
                                line.contains("error", ignoreCase = true) ||
                                line.contains("failed", ignoreCase = true) ||
                                line.contains("exception", ignoreCase = true) -> TerminalError
                                line.contains("warn", ignoreCase = true) -> TerminalWarn
                                line.contains("success", ignoreCase = true) ||
                                line.contains("installed", ignoreCase = true) ||
                                line.contains("done", ignoreCase = true) -> TerminalSuccess
                                line.startsWith("[") -> TerminalInfo
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            }
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                color = lineColor
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalMetaStat(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
