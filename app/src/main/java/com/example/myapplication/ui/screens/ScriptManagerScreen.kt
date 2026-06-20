// app_template/app/src/main/java/com/example/myapplication/ui/screens/ScriptManagerScreen.kt

package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

import com.example.myapplication.ui.components.TerminalConsoleBottomSheet // 👈 引入共享终端

enum class DependencyStatus {
    None,
    Configured,
    Installed,
    Error
}

data class ScriptProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val trigger: String,
    val lastRun: String,
    val isRunning: Boolean = false,
    val themeColor: Color,
    val isFolder: Boolean,
    val entryPoint: String = "",
    val dependencyStatus: DependencyStatus = DependencyStatus.None,
    val isInstalling: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onOpenDetail: (ScriptProject) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 改成 mutableStateListOf —— 安装依赖 / 删除 / 复制都需要真实改变这份列表
    val scripts = remember {
        mutableStateListOf(
            ScriptProject(name = "telegram_bot", type = "Python", trigger = "⏰ crontab: */10 * * * *", lastRun = "上次成功: 2分钟前", isRunning = true, themeColor = Color(0xFF38BDF8), isFolder = true, entryPoint = "main.py", dependencyStatus = DependencyStatus.Installed),
            ScriptProject(name = "daily_check.js", type = "Node.js", trigger = "⏰ crontab: 0 8 * * *", lastRun = "上次成功: 今天 08:00", themeColor = Color(0xFFA855F7), isFolder = false, dependencyStatus = DependencyStatus.None),
            ScriptProject(name = "backup_db.sh", type = "Shell", trigger = "⚡ 手动触发", lastRun = "上次成功: 昨天 23:00", themeColor = Color(0xFF22C55E), isFolder = false, dependencyStatus = DependencyStatus.None),
            ScriptProject(name = "web_crawler_v2", type = "Python", trigger = "⚡ 手动触发", lastRun = "从未运行", themeColor = Color(0xFF38BDF8), isFolder = true, entryPoint = "crawl.py", dependencyStatus = DependencyStatus.Configured),
            ScriptProject(name = "clean_logs.sh", type = "Shell", trigger = "⏰ crontab: 0 0 * * 0", lastRun = "上次失败: 1周前", themeColor = Color(0xFFEF4444), isFolder = false, dependencyStatus = DependencyStatus.None),
            ScriptProject(name = "api_gateway", type = "Node.js", trigger = "⏰ crontab: @reboot", lastRun = "上次错误: 3小时前", themeColor = Color(0xFFA855F7), isFolder = true, entryPoint = "server.js", dependencyStatus = DependencyStatus.Error)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "Python", "Shell", "Node.js")

    // ─── 🎛️ 终端联动状态 ───
    var activeTerminalScript by remember { mutableStateOf<ScriptProject?>(null) }

    // ─── 🗑️ 删除确认状态 ───
    var scriptPendingDelete by remember { mutableStateOf<ScriptProject?>(null) }

    // FAB 展开状态提到外层，这样下面的内容区才能在展开时铺一层可点击的蒙层
    var isFabExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun installDependencies(target: ScriptProject) {
        val idx = scripts.indexOfFirst { it.id == target.id }
        if (idx == -1) return
        scripts[idx] = scripts[idx].copy(isInstalling = true)
        coroutineScope.launch {
            delay(1500) // 模拟 pip / npm install 的真实耗时
            val current = scripts.indexOfFirst { it.id == target.id }
            if (current != -1) {
                scripts[current] = scripts[current].copy(isInstalling = false, dependencyStatus = DependencyStatus.Installed)
            }
        }
    }

    fun duplicateScript(target: ScriptProject) {
        val idx = scripts.indexOfFirst { it.id == target.id }
        if (idx == -1) return
        scripts.add(idx + 1, target.copy(id = UUID.randomUUID().toString(), name = "${target.name}_copy", isRunning = false))
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),

        // ─── 🌟 多级悬浮扩展菜单 ───
        floatingActionButton = {
            val rotationAngle by animateFloatAsState(
                targetValue = if (isFabExpanded) 45f else 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "fabRotation"
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        FabMenuOption(
                            label = "新建单文件脚本",
                            icon = Icons.Default.Terminal,
                            onClick = { isFabExpanded = false /* TODO: 触发新建文件动作 */ }
                        )
                        FabMenuOption(
                            label = "新建工程项目",
                            icon = Icons.Default.Folder,
                            onClick = { isFabExpanded = false /* TODO: 触发新建项目动作 */ }
                        )
                        FabMenuOption(
                            label = "导入本地已有项目",
                            icon = Icons.Default.Build,
                            onClick = { isFabExpanded = false /* TODO: 执行文件选择动作 */ }
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = if (isFabExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isFabExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Menu",
                            modifier = Modifier.rotate(rotationAngle)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFabExpanded) "收起菜单" else "添加/新建",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = innerPadding.calculateBottomPadding())
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("搜索脚本或工程文件夹...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        AnimatedVisibility(visible = searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
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
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                val filteredList = scripts
                    .filter { (selectedFilter == "全部" || it.type == selectedFilter) && it.name.contains(searchQuery, ignoreCase = true) }
                    .sortedByDescending { it.isRunning } // 正在运行的脚本浮到最上面，方便随时盯着

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (filteredList.isEmpty()) {
                        item { EmptyScriptsState(hasAnyScripts = scripts.isNotEmpty()) }
                    } else {
                        items(filteredList, key = { it.id }) { script ->
                            ScriptCard(
                                script = script,
                                onExecuteNow = { activeTerminalScript = script },
                                onOpenDetail = { onOpenDetail(script) },
                                onViewLogs = { activeTerminalScript = script },
                                onInstallDependencies = { installDependencies(script) },
                                onDuplicate = { duplicateScript(script) },
                                onDeleteRequest = { scriptPendingDelete = script }
                            )
                        }
                    }
                }
            }

            // 点击 FAB 以外的区域收起菜单
            AnimatedVisibility(
                visible = isFabExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isFabExpanded = false }
                )
            }
        }
    }

    // ─── 🌟 终端审计舱合流 ───
    activeTerminalScript?.let { script ->
        TerminalConsoleBottomSheet(
            taskName = script.name,
            scriptName = if (script.isFolder) script.entryPoint else script.name,
            onDismiss = { activeTerminalScript = null }
        )
    }

    // ─── 🗑️ 删除确认弹窗 ───
    scriptPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { scriptPendingDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除「${target.name}」？") },
            text = { Text("脚本文件、定时任务和已保存的执行记录会一并移除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scripts.removeAll { it.id == target.id }
                    scriptPendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scriptPendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FabMenuOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}

// =====================================================================================
// 空状态 —— 区分“一个脚本都没有”和“筛选/搜索没有命中”两种语境
// =====================================================================================

@Composable
private fun EmptyScriptsState(hasAnyScripts: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (hasAnyScripts) Icons.Default.Search else Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasAnyScripts) "没有找到匹配的脚本" else "还没有任何脚本",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hasAnyScripts) "试试其他关键词，或切换上方的分类筛选" else "点击右下角“添加/新建”创建第一个脚本",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================================
// 脚本卡片
// =====================================================================================

@Composable
fun ScriptCard(
    script: ScriptProject,
    onExecuteNow: () -> Unit,
    onOpenDetail: () -> Unit = {},
    onViewLogs: () -> Unit = {},
    onInstallDependencies: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onDeleteRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    var showMoreMenu by remember { mutableStateOf(false) }

    val isExecutable = !script.isInstalling &&
        (script.dependencyStatus == DependencyStatus.None || script.dependencyStatus == DependencyStatus.Installed)

    // 失败/错误的运行记录用 error 色提醒一下，而不是和成功记录一样淡灰
    val lastRunColor = if (script.lastRun.contains("失败") || script.lastRun.contains("错误")) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(onOpenDetail)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            color = if (script.isRunning) script.themeColor.copy(alpha = pulseAlpha) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(script.themeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (script.isFolder) Icons.Default.Folder else Icons.Default.Terminal,
                            contentDescription = null,
                            tint = script.themeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = script.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(modifier = Modifier.background(script.themeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(
                                script.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = script.themeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (script.isRunning) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF22C55E), RoundedCornerShape(50)))
                        }
                    }

                    if (script.isFolder) {
                        Text(
                            text = "入口文件 ➔ ${script.entryPoint}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = script.trigger,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = script.lastRun,
                        style = MaterialTheme.typography.bodySmall,
                        color = lastRunColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = onExecuteNow,
                        enabled = isExecutable,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = script.themeColor.copy(alpha = 0.1f),
                            contentColor = script.themeColor,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "立即执行", modifier = Modifier.size(18.dp))
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多操作",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("查看日志") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = { showMoreMenu = false; onViewLogs() }
                            )
                            DropdownMenuItem(
                                text = { Text("复制副本") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = { showMoreMenu = false; onDuplicate() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("删除脚本", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMoreMenu = false; onDeleteRequest() }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = script.isFolder && script.dependencyStatus != DependencyStatus.None) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // “待安装”不等于“出错”，所以语义色拆开：Configured 用 tertiary（提醒），Error 才用 error（警示）
                            val (statusText, statusColor) = when {
                                script.isInstalling -> "正在安装依赖环境…" to MaterialTheme.colorScheme.tertiary
                                script.dependencyStatus == DependencyStatus.Configured -> "检测到未安装依赖环境" to MaterialTheme.colorScheme.tertiary
                                script.dependencyStatus == DependencyStatus.Installed -> "依赖环境已完全就绪" to Color(0xFF22C55E)
                                script.dependencyStatus == DependencyStatus.Error -> "依赖配置失败，环境异常" to MaterialTheme.colorScheme.error
                                else -> "" to Color.Unspecified
                            }
                            Box(modifier = Modifier.size(6.dp).background(statusColor, RoundedCornerShape(50)))
                            Text(text = statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }

                        if (script.dependencyStatus == DependencyStatus.Configured || script.dependencyStatus == DependencyStatus.Error) {
                            Button(
                                onClick = onInstallDependencies,
                                enabled = !script.isInstalling,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.height(28.dp)
                            ) {
                                if (script.isInstalling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("安装中...", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("一键安装", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            TextButton(onClick = onViewLogs, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("目录详情", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// 触感反馈：与仪表盘页保持同一套点击缩放手感，整张卡片可点（跳转详情），
// 内部的执行 / 更多按钮各自处理自己的点击，互不影响
// =====================================================================================

private fun Modifier.expressiveClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardPressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
