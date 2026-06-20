package com.example.myapplication.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.myapplication.ui.components.TerminalConsoleBottomSheet
import com.example.myapplication.utils.CronTranslator

// ─────────────────────────────────────────────────────────────
// 任务启停状态
// ─────────────────────────────────────────────────────────────
enum class CronTaskStatus {
    Enabled,
    Disabled,
}

// ─────────────────────────────────────────────────────────────
// 脚本类型 → 与「脚本管理」页保持同一套配色 / 标签，
// 避免同一个脚本在两个页面里看起来像两套体系
// ─────────────────────────────────────────────────────────────
enum class ScriptType(val label: String, val color: Color) {
    PYTHON("Python", Color(0xFF38BDF8)),
    SHELL("Shell", Color(0xFF22C55E)),
    NODE("Node.js", Color(0xFFA855F7)),
    OTHER("脚本", Color(0xFF94A3B8));

    companion object {
        fun fromFileName(fileName: String): ScriptType = when {
            fileName.endsWith(".py", ignoreCase = true) -> PYTHON
            fileName.endsWith(".sh", ignoreCase = true) -> SHELL
            fileName.endsWith(".js", ignoreCase = true) ||
                fileName.endsWith(".ts", ignoreCase = true) -> NODE
            else -> OTHER
        }
    }
}

data class ScheduledTask(
    val id: String,
    val name: String,
    val targetScript: String,
    val cronExpression: String,
    val nextRunTime: String,
    val lastRunResult: String,
    val themeColor: Color? = null,       // null → 按脚本类型自动取色
    val isSuccess: Boolean = true,
    val isRunning: Boolean = false,      // 当前是否在执行（用于呼吸动效）
    val initialStatus: CronTaskStatus = CronTaskStatus.Enabled
) {
    val scriptType: ScriptType get() = ScriptType.fromFileName(targetScript)
    val resolvedColor: Color get() = themeColor ?: scriptType.color
}

// 图标容器使用非对称圆角（squircle 感的「软方块」），
// 和资料卡那套视觉语言保持一致，而不是普通的等角圆角矩形
private val taskIconBlobShape = RoundedCornerShape(
    topStart = 16.dp, topEnd = 10.dp, bottomEnd = 16.dp, bottomStart = 10.dp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val tasksList = remember {
        mutableStateListOf(
            ScheduledTask(
                id = "1", name = "全网币价监控", targetScript = "crypto_monitor.py",
                cronExpression = "*/5 * * * *", nextRunTime = "预计 5分钟后",
                lastRunResult = "上次耗时 1.2s", themeColor = Color(0xFF38BDF8), isRunning = true
            ),
            ScheduledTask(
                id = "2", name = "数据库每日冷备", targetScript = "backup_db.sh",
                cronExpression = "0 2 * * *", nextRunTime = "明天凌晨 02:00",
                lastRunResult = "备份成功 · 45MB", themeColor = Color(0xFF22C55E)
            ),
            ScheduledTask(
                id = "3", name = "GitHub 绿墙自动打卡", targetScript = "auto_commit.sh",
                cronExpression = "0 23 * * *", nextRunTime = "今天 23:00",
                lastRunResult = "打卡完成", themeColor = Color(0xFFEAB308)
            ),
            ScheduledTask(
                id = "4", name = "日志残渣自动清理", targetScript = "clean_logs.sh",
                cronExpression = "0 0 * * 0", nextRunTime = "本周日凌晨 00:00",
                lastRunResult = "执行失败 · 权限不足", isSuccess = false
            )
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    var onlyFailed by remember { mutableStateOf(false) }
    val filters = listOf("全部", "Python", "Shell", "Node.js")

    var isCreatingNew by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<ScheduledTask?>(null) }
    var activeTerminalTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var pendingDelete by remember { mutableStateOf<ScheduledTask?>(null) }

    val failedCount = tasksList.count { !it.isSuccess }
    // 失败任务清零后自动退出「仅看失败」筛选，避免筛选条件悬空
    LaunchedEffect(failedCount) {
        if (failedCount == 0) onlyFailed = false
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isCreatingNew = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(50) // 完整的胶囊形，呼应仪表盘 / 底部导航的圆润语言
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建定时", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            // 搜索栏：胶囊形 + 可清除
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        "搜索定时调度任务...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            // 横向滚动的脚本类型筛选 — 完整胶囊形，和卡片上的类型徽标统一形状语言
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                filter,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            // 失败任务提示 banner，呼应仪表盘页「1 个脚本执行失败」的视觉语言，
            // 点击可一键筛选出所有失败任务，而不必去翻整张列表找
            if (failedCount > 0) {
                FailureBanner(
                    count = failedCount,
                    active = onlyFailed,
                    onClick = { onlyFailed = !onlyFailed }
                )
            }

            val filteredTasks = tasksList.filter { task ->
                val matchesSearch = task.name.contains(searchQuery, ignoreCase = true) ||
                    task.targetScript.contains(searchQuery, ignoreCase = true)
                val matchesType = selectedFilter == "全部" || task.scriptType.label == selectedFilter
                val matchesFailure = !onlyFailed || !task.isSuccess
                matchesSearch && matchesType && matchesFailure
            }

            if (filteredTasks.isEmpty()) {
                EmptyTasksState(modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        CronTaskCard(
                            task = task,
                            onStatusToggle = { enabled ->
                                val idx = tasksList.indexOfFirst { it.id == task.id }
                                if (idx >= 0) {
                                    tasksList[idx] = tasksList[idx].copy(
                                        initialStatus = if (enabled) CronTaskStatus.Enabled else CronTaskStatus.Disabled
                                    )
                                }
                            },
                            onExecuteNow = { activeTerminalTask = task },
                            onViewLog = { activeTerminalTask = task },
                            onEdit = { editorTarget = task },
                            onDelete = { pendingDelete = task }
                        )
                    }
                }
            }
        }
    }

    // ─── 新建 / 编辑 共用同一张底部表单 ───
    if (isCreatingNew || editorTarget != null) {
        TaskEditorSheet(
            existing = editorTarget,
            availableScripts = tasksList.map { it.targetScript }.distinct(),
            onDismiss = {
                isCreatingNew = false
                editorTarget = null
            },
            onSave = { saved ->
                val idx = tasksList.indexOfFirst { it.id == saved.id }
                if (idx >= 0) {
                    tasksList[idx] = saved
                } else {
                    tasksList.add(saved)
                }
                isCreatingNew = false
                editorTarget = null
            }
        )
    }

    // ─── 删除二次确认，防止误触丢任务 ───
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除「${task.name}」？") },
            text = { Text("该定时任务将被永久移除，绑定的脚本文件本身不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    tasksList.removeAll { it.id == task.id }
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    activeTerminalTask?.let { task ->
        TerminalConsoleBottomSheet(
            taskName = task.name,
            scriptName = task.targetScript,
            onDismiss = { activeTerminalTask = null }
        )
    }
}

@Composable
private fun FailureBanner(count: Int, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = if (active) 1f else 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (active) "正在筛选 $count 个执行失败的任务" else "$count 个任务执行失败，点击仅看失败",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (active) "✕" else "›",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun EmptyTasksState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "没有匹配的定时任务",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "试试切换筛选条件，或新建一个调度任务",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CronTaskCard(
    task: ScheduledTask,
    onStatusToggle: (Boolean) -> Unit,
    onExecuteNow: () -> Unit,
    onViewLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isEnabled = task.initialStatus == CronTaskStatus.Enabled
    val accent = task.resolvedColor
    val cardAlpha = if (isEnabled) 1f else 0.55f
    var menuExpanded by remember { mutableStateOf(false) }

    // 一个颜色同时承担「启停 / 成败 / 运行中」三件事，作为贯穿整卡的状态色，
    // 用在左侧色条和图标角标上，形成「融合式」的状态提示，而不是分散成好几处文字
    val statusColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        !task.isSuccess -> MaterialTheme.colorScheme.error
        task.isRunning -> Color(0xFF22C55E)
        else -> accent
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 左侧状态色条：贴着卡片圆角边缘，一眼扫过去就能分辨健康状态
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor.copy(alpha = if (isEnabled) 1f else 0.35f))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        // 执行中的呼吸光圈，呼应仪表盘「当前运行」的动态感
                        if (task.isRunning && isEnabled) {
                            val pulse by rememberInfiniteTransition().animateFloat(
                                initialValue = 0.35f,
                                targetValue = 0.9f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(accent.copy(alpha = pulse * 0.22f), taskIconBlobShape)
                            )
                        }

                        // 非对称圆角的图标「软方块」，而不是普通正方形圆角
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    color = if (isEnabled) accent.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    shape = taskIconBlobShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (isEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 融合在图标角上的状态角标（成功/失败/禁用）
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .size(14.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                                .padding(2.5.dp)
                                .background(statusColor, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = cardAlpha)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 类型徽标，和「脚本管理」页的 Python / Shell / Node.js 标签同源
                            Box(
                                modifier = Modifier
                                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    task.scriptType.label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = task.targetScript,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isEnabled) 0.7f else 0.4f
                                )
                            )
                        }
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onStatusToggle,
                        modifier = Modifier.scale(0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 人话优先：下次执行时间放大写在前面，cron 语法降级成小字注脚，
                // 不用每个人都先在脑子里翻译 "*/5 * * * *" 才知道什么时候跑
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = task.nextRunTime,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = task.cronExpression,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (!task.isSuccess && isEnabled) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = task.lastRunResult,
                            fontSize = 11.sp,
                            color = if (!task.isSuccess && isEnabled) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onExecuteNow,
                            enabled = isEnabled,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "立即执行",
                                tint = if (isEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("编辑") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { menuExpanded = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text("查看执行日志") },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = { menuExpanded = false; onViewLog() }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = { menuExpanded = false; onDelete() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 新建 / 编辑共用的底部表单。existing == null 时为新建，
// 否则按传入任务预填，保存时原地替换。
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    existing: ScheduledTask?,
    availableScripts: List<String>,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var cron by remember { mutableStateOf(existing?.cronExpression ?: "*/10 * * * *") }
    var scriptDropdownExpanded by remember { mutableStateOf(false) }
    val scripts = availableScripts.ifEmpty { listOf("script.py") }
    var selectedScript by remember { mutableStateOf(existing?.targetScript ?: scripts.first()) }

    val cronValid = remember(cron) { cron.trim().split(Regex("\\s+")).size == 5 }
    val cronHint = remember(cron, cronValid) {
        if (!cronValid) {
            "Cron 表达式需要 5 个字段（分 时 日 月 周）"
        } else {
            runCatching { CronTranslator.translate(cron) }.getOrDefault("无法解析该表达式，请检查语法")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existing == null) "配置新自动化调度" else "编辑调度任务",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("任务代号 / 名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenuBox(
                expanded = scriptDropdownExpanded,
                onExpandedChange = { scriptDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedScript,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("绑定目标脚本") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scriptDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = scriptDropdownExpanded,
                    onDismissRequest = { scriptDropdownExpanded = false }
                ) {
                    scripts.forEach { script ->
                        DropdownMenuItem(
                            text = { Text(script, fontFamily = FontFamily.Monospace) },
                            onClick = {
                                selectedScript = script
                                scriptDropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            OutlinedTextField(
                value = cron,
                onValueChange = { cron = it },
                label = { Text("Cron 表达式 (分 时 日 月 周)") },
                singleLine = true,
                isError = !cronValid,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(12.dp)
            )

            // 人话翻译机：表达式不合法时切换成错误提示色，而不是悄悄显示空内容
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (cronValid) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = cronHint,
                    fontSize = 12.sp,
                    color = if (cronValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Button(
                onClick = {
                    val saved = existing?.copy(
                        name = name,
                        targetScript = selectedScript,
                        cronExpression = cron
                    ) ?: ScheduledTask(
                        id = "task_${System.currentTimeMillis()}",
                        name = name,
                        targetScript = selectedScript,
                        cronExpression = cron,
                        nextRunTime = "计算中...",
                        lastRunResult = "从未执行"
                    )
                    onSave(saved)
                },
                enabled = name.isNotBlank() && cronValid,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (existing == null) "挂载上线" else "保存修改", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
