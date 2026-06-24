// app_template/app/src/main/java/com/example/myapplication/ui/screens/ScheduledTaskManagerScreen.kt

package com.scripthub.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scripthub.app.data.AppDatabase
import com.scripthub.app.data.ScheduledTaskEntity
import com.scripthub.app.ui.components.TerminalConsoleBottomSheet
import com.scripthub.app.ui.components.LogViewerBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.scripthub.app.utils.CronTranslator
import com.scripthub.app.utils.CronNextRunCalculator
import com.scripthub.app.utils.SchedulerPreference
import com.scripthub.app.utils.SchedulerPreference.SchedulerType
import com.scripthub.app.utils.scheduler.TaskSchedulerManager
import com.scripthub.app.ui.theme.TypeColorPython
import com.scripthub.app.ui.theme.TypeColorShell
import com.scripthub.app.ui.theme.TypeColorNode
import com.scripthub.app.ui.theme.TypeColorOther
import com.scripthub.app.ui.theme.StatusRunning

import androidx.compose.material3.pulltorefresh.PullToRefreshBox

// ─────────────────────────────────────────────────────────────
// 任务启停状态
// ─────────────────────────────────────────────────────────────
enum class CronTaskStatus { Enabled, Disabled }

enum class ScriptType(val label: String, val color: Color) {
    PYTHON("Python", TypeColorPython),
    SHELL("Shell", TypeColorShell),
    NODE("Node.js", TypeColorNode),
    OTHER("脚本", TypeColorOther);

    companion object {
        fun fromFileName(fileName: String): ScriptType = when {
            fileName.endsWith(".py", ignoreCase = true) -> PYTHON
            fileName.endsWith(".sh", ignoreCase = true) -> SHELL
            fileName.endsWith(".js", ignoreCase = true) || fileName.endsWith(".ts", ignoreCase = true) -> NODE
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
    val themeColor: Color? = null,
    val isSuccess: Boolean = true,
    val isRunning: Boolean = false,
    val initialStatus: CronTaskStatus = CronTaskStatus.Enabled
) {
    val scriptType: ScriptType get() = ScriptType.fromFileName(targetScript)
    val resolvedColor: Color get() = themeColor ?: scriptType.color
}

private val taskIconBlobShape = RoundedCornerShape(topStart = 16.dp, topEnd = 10.dp, bottomEnd = 16.dp, bottomStart = 10.dp)

// ─────────────────────────────────────────────────────────────
// Entity ↔ UI 模型转换
// ─────────────────────────────────────────────────────────────
private fun ScheduledTaskEntity.toUiModel() = ScheduledTask(
    id = id,
    name = name,
    targetScript = targetScript,
    cronExpression = cronExpression,
    nextRunTime = nextRunTime,
    lastRunResult = lastRunResult,
    isSuccess = isSuccess,
    isRunning = false, // App 重启后运行状态归零
    initialStatus = if (isEnabled) CronTaskStatus.Enabled else CronTaskStatus.Disabled
)

private fun ScheduledTask.toEntity() = ScheduledTaskEntity(
    id = id,
    name = name,
    targetScript = targetScript,
    cronExpression = cronExpression,
    nextRunTime = nextRunTime,
    lastRunResult = lastRunResult,
    isEnabled = initialStatus == CronTaskStatus.Enabled,
    isSuccess = isSuccess,
    isRunning = isRunning
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val taskDao = remember { db.scheduledTaskDao() }
    val scope = rememberCoroutineScope()

    // 定时任务列表：从 Room 实时订阅，持久化到数据库
    val dbTasks by taskDao.getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val tasksList = remember(dbTasks) { dbTasks.map { it.toUiModel() } }

    var isRefreshing by remember { mutableStateOf(false) }

    // 对数据库中仍显示"计算中..."的老任务补算下次执行时间，并每分钟刷新一次所有已启用任务的下次执行时间
    LaunchedEffect(dbTasks) {
        val stale = dbTasks.filter { it.nextRunTime == "计算中..." }
        if (stale.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                stale.forEach { task ->
                    val computed = CronNextRunCalculator.nextRunTime(task.cronExpression)
                    taskDao.update(task.copy(nextRunTime = computed))
                }
            }
        }
    }

    // 每分钟刷新一次所有已启用任务的"下次执行"时间，避免时间过期后仍显示旧值
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            scope.launch(Dispatchers.IO) {
                dbTasks.filter { it.isEnabled }.forEach { task ->
                    val computed = CronNextRunCalculator.nextRunTime(task.cronExpression)
                    taskDao.update(task.copy(nextRunTime = computed))
                }
            }
        }
    }

    // 脚本列表：从 ScriptDao 实时订阅，与脚本管理页联动
    val dbScripts by db.scriptDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val availableScriptNames = remember(dbScripts) { dbScripts.map { it.name } }

    var onlyFailed by remember { mutableStateOf(false) }

    // 调度引擎选择（从 SharedPreferences 读取，可热切换）
    var selectedSchedulerType by remember { mutableStateOf(SchedulerPreference.getType(context)) }

    var isCreatingNew by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<ScheduledTask?>(null) }
    var activeTerminalTask  by remember { mutableStateOf<ScheduledTask?>(null) }
    var activeLogViewerTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var pendingDelete by remember { mutableStateOf<ScheduledTask?>(null) }

    val failedCount = tasksList.count { !it.isSuccess }
    LaunchedEffect(failedCount) { if (failedCount == 0) onlyFailed = false }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isCreatingNew = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(50)
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
                .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = innerPadding.calculateBottomPadding())
        ) {
            // 调度引擎选择卡片
            SchedulerSelectorCard(
                currentType = selectedSchedulerType,
                modifier    = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                onSwitch    = { newType ->
                    scope.launch(Dispatchers.IO) {
                        TaskSchedulerManager.switchScheduler(context, newType, dbTasks)
                    }
                    selectedSchedulerType = newType
                }
            )

            if (failedCount > 0) {
                FailureBanner(count = failedCount, active = onlyFailed, onClick = { onlyFailed = !onlyFailed })
            }

            val filteredTasks = tasksList.filter { task ->
                !onlyFailed || !task.isSuccess
            }

            // 完美的空状态展示
            if (filteredTasks.isEmpty()) {
                EmptyTasksState(modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                dbTasks.filter { it.isEnabled }.forEach { task ->
                                    val computed = CronNextRunCalculator.nextRunTime(task.cronExpression)
                                    taskDao.update(task.copy(nextRunTime = computed))
                                }
                            }
                            kotlinx.coroutines.delay(500)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            CronTaskCard(
                                task = task,
                                onStatusToggle = { enabled ->
                                    scope.launch(Dispatchers.IO) {
                                        val updatedEntity = task.copy(
                                            initialStatus = if (enabled) CronTaskStatus.Enabled else CronTaskStatus.Disabled
                                        ).toEntity()
                                        taskDao.update(updatedEntity)
                                        if (enabled) {
                                            TaskSchedulerManager.scheduleTask(context, updatedEntity)
                                        } else {
                                            TaskSchedulerManager.cancelTask(context, task.id)
                                        }
                                    }
                                },
                                onExecuteNow = { activeTerminalTask = task },
                                onViewLog = { activeLogViewerTask = task },
                                onEdit = { editorTarget = task },
                                onDelete = { pendingDelete = task }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isCreatingNew || editorTarget != null) {
        TaskEditorSheet(
            existing = editorTarget,
            availableScripts = availableScriptNames,
            onDismiss = { isCreatingNew = false; editorTarget = null },
            onSave = { saved ->
                scope.launch(Dispatchers.IO) {
                    val entity = saved.toEntity()
                    taskDao.insert(entity)
                    if (saved.initialStatus == CronTaskStatus.Enabled) {
                        TaskSchedulerManager.scheduleTask(context, entity)
                    }
                    // 更新目标脚本的触发方式为"定时触发"
                    db.scriptDao().updateTrigger(saved.targetScript, "⏰ 定时触发")
                }
                isCreatingNew = false
                editorTarget = null
            }
        )
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除「${task.name}」？") },
            text = { Text("该定时任务将被永久移除，绑定的脚本文件本身不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        taskDao.deleteById(task.id)
                        TaskSchedulerManager.cancelTask(context, task.id)
                        // 若该脚本已无其他定时任务，则恢复为"手动触发"
                        val remaining = taskDao.getAllOnce().count {
                            it.id != task.id && it.targetScript == task.targetScript
                        }
                        if (remaining == 0) {
                            db.scriptDao().updateTrigger(task.targetScript, "⚡ 手动触发")
                        }
                    }
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    activeTerminalTask?.let { task ->
        TerminalConsoleBottomSheet(taskName = task.name, scriptName = task.targetScript, onDismiss = { activeTerminalTask = null })
    }

    activeLogViewerTask?.let { task ->
        LogViewerBottomSheet(scriptName = task.targetScript, onDismiss = { activeLogViewerTask = null })
    }
}

// ─────────────────────────────────────────────────────────────
// 调度引擎选择卡片
// ─────────────────────────────────────────────────────────────

@Composable
private fun SchedulerSelectorCard(
    currentType: SchedulerType,
    modifier: Modifier = Modifier,
    onSwitch: (SchedulerType) -> Unit
) {
    var showConfirm by remember { mutableStateOf<SchedulerType?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "调度引擎",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "已选: ${currentType.shortLabel}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SchedulerType.entries.forEach { type ->
                    val isSelected = type == currentType
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                    val contentColor   = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                    Card(
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isSelected) { showConfirm = type }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = type.label,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = contentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = type.pros,
                                fontSize = 10.sp,
                                color = contentColor.copy(alpha = 0.8f),
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "⚠ ${type.cons}",
                                fontSize = 9.sp,
                                color = contentColor.copy(alpha = if (isSelected) 0.7f else 0.5f),
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // 切换确认弹窗
    showConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { showConfirm = null },
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("切换至 ${target.label}？") },
            text = {
                Text(
                    "当前调度引擎（${currentType.label}）的所有任务将被取消，\n并用新引擎（${target.label}）重新注册所有已启用任务。\n\n${target.description}",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSwitch(target)
                    showConfirm = null
                }) {
                    Text("确认切换", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun FailureBanner(count: Int, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = if (active) 1f else 0.7f)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = if (active) "正在筛选 $count 个执行失败的任务" else "$count 个任务执行失败，点击仅看失败", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
        Text(text = if (active) "✕" else "›", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
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
            modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("没有配置调度任务", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text("点击右下角“新建定时”按钮创建任务", fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

private fun cronToHuman(expr: String): String {
    val parts = expr.trim().split(Regex("\\s+"))
    if (parts.size != 5) return expr
    val (min, hour, dom, month, dow) = parts

    // 每分钟
    if (min == "*" && hour == "*" && dom == "*" && month == "*" && dow == "*")
        return "每分钟运行一次"

    // */N 分钟
    if (min.startsWith("*/") && hour == "*" && dom == "*" && month == "*" && dow == "*") {
        val n = min.removePrefix("*/").toIntOrNull() ?: return expr
        return if (n == 1) "每分钟运行一次" else "每 $n 分钟运行一次"
    }

    // 固定分钟，每小时
    if (hour == "*" && dom == "*" && month == "*" && dow == "*") {
        return if (min == "0") "每小时整点运行" else "每小时第 $min 分运行"
    }

    // */N 小时
    if (min == "0" && hour.startsWith("*/") && dom == "*" && month == "*" && dow == "*") {
        val n = hour.removePrefix("*/").toIntOrNull() ?: return expr
        return "每 $n 小时运行一次"
    }

    // 每天固定时间
    if (dom == "*" && month == "*" && dow == "*") {
        val h = hour.toIntOrNull(); val m = min.toIntOrNull()
        if (h != null && m != null)
            return "每天 %02d:%02d 运行".format(h, m)
    }

    // 每周固定
    if (dom == "*" && month == "*" && dow != "*") {
        val dayMap = mapOf("0" to "周日", "1" to "周一", "2" to "周二", "3" to "周三",
                           "4" to "周四", "5" to "周五", "6" to "周六", "7" to "周日")
        val dayStr = dayMap[dow] ?: "周$dow"
        val h = hour.toIntOrNull(); val m = min.toIntOrNull()
        if (h != null && m != null)
            return "每$dayStr %02d:%02d 运行".format(h, m)
    }

    // 每月固定日
    if (month == "*" && dow == "*") {
        val d = dom.toIntOrNull(); val h = hour.toIntOrNull(); val m = min.toIntOrNull()
        if (d != null && h != null && m != null)
            return "每月 ${d}日 %02d:%02d 运行".format(h, m)
    }

    return expr
}

@Composable
private fun CronTaskCard(task: ScheduledTask, onStatusToggle: (Boolean) -> Unit, onExecuteNow: () -> Unit, onViewLog: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isEnabled = task.initialStatus == CronTaskStatus.Enabled
    val accent = task.resolvedColor
    val cardAlpha = if (isEnabled) 1f else 0.55f
    var menuExpanded by remember { mutableStateOf(false) }

    val statusColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        !task.isSuccess -> MaterialTheme.colorScheme.error
        task.isRunning -> StatusRunning
        else -> accent
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(statusColor.copy(alpha = if (isEnabled) 1f else 0.35f)))
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        if (task.isRunning && isEnabled) {
                            val pulse by rememberInfiniteTransition().animateFloat(initialValue = 0.35f, targetValue = 0.9f, animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse))
                            Box(modifier = Modifier.size(50.dp).background(accent.copy(alpha = pulse * 0.22f), taskIconBlobShape))
                        }
                        Box(modifier = Modifier.size(42.dp).background(color = if (isEnabled) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = taskIconBlobShape), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = if (isEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                        }
                        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(14.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape).padding(2.5.dp).background(statusColor, CircleShape))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = task.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = cardAlpha))
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.background(accent.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text(task.scriptType.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = accent)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = task.targetScript, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isEnabled) 0.7f else 0.4f))
                        }
                    }
                    Switch(checked = isEnabled, onCheckedChange = onStatusToggle, modifier = Modifier.scale(0.85f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cronToHuman(task.cronExpression),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = task.cronExpression,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("立即执行") }, leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }, onClick = { menuExpanded = false; onExecuteNow() })
                            DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { menuExpanded = false; onEdit() })
                            DropdownMenuItem(text = { Text("查看执行日志") }, leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }, onClick = { menuExpanded = false; onViewLog() })
                            DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onDelete() })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(existing: ScheduledTask?, availableScripts: List<String>, onDismiss: () -> Unit, onSave: (ScheduledTask) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var cron by remember { mutableStateOf(existing?.cronExpression ?: "*/10 * * * *") }
    var scriptDropdownExpanded by remember { mutableStateOf(false) }
    val scripts = availableScripts
    var selectedScript by remember { mutableStateOf(existing?.targetScript ?: scripts.firstOrNull() ?: "") }

    val cronValid = remember(cron) { cron.trim().split(Regex("\\s+")).size == 5 }
    val cronHint = remember(cron, cronValid) {
        if (!cronValid) "Cron 表达式需要 5 个字段（分 时 日 月 周）" else runCatching { CronTranslator.translate(cron) }.getOrDefault("无法解析该表达式，请检查语法")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = if (existing == null) "配置新自动化调度" else "编辑调度任务", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("任务代号 / 名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            if (scripts.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ 脚本库为空，请先前往「脚本管理」页扫描添加脚本，再回来配置定时任务。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                ExposedDropdownMenuBox(expanded = scriptDropdownExpanded, onExpandedChange = { scriptDropdownExpanded = it }) {
                    OutlinedTextField(value = selectedScript, onValueChange = {}, readOnly = true, label = { Text("绑定目标脚本") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scriptDropdownExpanded) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    ExposedDropdownMenu(expanded = scriptDropdownExpanded, onDismissRequest = { scriptDropdownExpanded = false }) {
                        scripts.forEach { script ->
                            DropdownMenuItem(text = { Text(script, fontFamily = FontFamily.Monospace) }, onClick = { selectedScript = script; scriptDropdownExpanded = false }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                        }
                    }
                }
            }
            OutlinedTextField(value = cron, onValueChange = { cron = it }, label = { Text("Cron 表达式 (分 时 日 月 周)") }, singleLine = true, isError = !cronValid, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), shape = RoundedCornerShape(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = if (cronValid) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.error.copy(alpha = 0.08f)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text = cronHint, fontSize = 12.sp, color = if (cronValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium, modifier = Modifier.padding(12.dp))
            }
            Button(
                onClick = {
                    val computedNext = CronNextRunCalculator.nextRunTime(cron)
                    val saved = existing?.copy(name = name, targetScript = selectedScript, cronExpression = cron, nextRunTime = computedNext) ?: ScheduledTask(id = "task_${System.currentTimeMillis()}", name = name, targetScript = selectedScript, cronExpression = cron, nextRunTime = computedNext, lastRunResult = "从未执行")
                    onSave(saved)
                },
                enabled = name.isNotBlank() && cronValid && selectedScript.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (existing == null) "挂载上线" else "保存修改", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}