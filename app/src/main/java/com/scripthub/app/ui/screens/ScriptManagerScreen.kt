// app_template/app/src/main/java/com/example/myapplication/ui/screens/ScriptManagerScreen.kt

package com.scripthub.app.ui.screens

import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scripthub.app.data.ScriptEntity
import com.scripthub.app.ui.components.TerminalConsoleBottomSheet
import com.scripthub.app.ui.components.LogViewerBottomSheet
import com.scripthub.app.ui.theme.TypeColorPython
import com.scripthub.app.ui.theme.StatusRunning
import com.scripthub.app.ui.theme.TerminalSuccess
import com.scripthub.app.utils.WorkdirPreference
import com.scripthub.app.viewmodel.ScriptViewModel

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DependencyStatus { None, Configured, Installed, Error }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onOpenDetail: (ScriptEntity) -> Unit = {},
    viewModel: ScriptViewModel = viewModel(), // 👈 注入物理存储同步的 ViewModel
    modifier: Modifier = Modifier
) {
    // 💡 实时观察物理文件夹与数据库同步后的纯净列表数据
    val scripts by viewModel.scriptsList.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "Python", "Shell", "Node.js")

    // 弹窗与控制状态
    var activeTerminalScript  by remember { mutableStateOf<ScriptEntity?>(null) }
    var activeLogViewerScript by remember { mutableStateOf<ScriptEntity?>(null) }
    var scriptPendingDelete by remember { mutableStateOf<ScriptEntity?>(null) }
    var isFabExpanded by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ─── 🛠️ 新建文件的表单对话框状态 ───
    var showSingleFileDialog by remember { mutableStateOf(false) }
    var singleFileName by remember { mutableStateOf("") }

    var showFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var folderEntryPoint by remember { mutableStateOf("main.py") }

    // 每次重新切入本页面时，自动执行一次静默双轨物理扫描
    LaunchedEffect(Unit) {
        viewModel.syncFilesWithDatabase()
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
                            onClick = { 
                                isFabExpanded = false
                                singleFileName = ""
                                showSingleFileDialog = true // 唤醒单文件创建对话框
                            }
                        )
                        FabMenuOption(
                            label = "新建工程项目",
                            icon = Icons.Default.Folder,
                            onClick = { 
                                isFabExpanded = false
                                folderName = ""
                                folderEntryPoint = "main.py"
                                showFolderDialog = true // 唤醒文件夹工程创建对话框
                            }
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
                    placeholder = { Text("搜索物理存储中的脚本...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
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
                    .sortedByDescending { it.isRunning }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        scope.launch {
                            viewModel.syncFilesWithDatabase()
                            delay(500)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                                    onViewLogs = { activeLogViewerScript = script },
                                    onDeleteRequest = { scriptPendingDelete = script }
                                )
                            }
                        }
                    }
                }
            }

            // 蒙层收起 FAB 菜单
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

    // ─── 🌟 终端审计舱：立即执行 ───
    activeTerminalScript?.let { script ->
        TerminalConsoleBottomSheet(
            taskName   = script.name,
            scriptName = if (script.isFolder) script.entryPoint else script.name,
            onDismiss  = { activeTerminalScript = null }
        )
    }

    // ─── 📋 历史日志查看器（只读，不执行脚本）───
    activeLogViewerScript?.let { script ->
        LogViewerBottomSheet(
            scriptName = if (script.isFolder) script.entryPoint else script.name,
            onDismiss  = { activeLogViewerScript = null }
        )
    }

    // ─── 🗑️ 物理删除二次确认弹窗 ───
    scriptPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { scriptPendingDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("物理删除「${target.name}」？") },
            text = { Text("这将永久从手机存储中彻底抹除此文件/文件夹。此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScript(target)
                    scriptPendingDelete = null
                }) {
                    Text("彻底删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scriptPendingDelete = null }) { Text("取消") }
            }
        )
    }

    // ─── 🛠️ 弹窗1：新建单文件对话框 ───
    if (showSingleFileDialog) {
        AlertDialog(
            onDismissRequest = { showSingleFileDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("新建单文件脚本", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("请指定带后缀的文件名：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = singleFileName,
                        onValueChange = { singleFileName = it.trim() },
                        placeholder = { Text("例如: task.py 或 check.js") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fileExt = singleFileName.substringAfterLast(".", "")
                        val isSupported = fileExt == "py" || fileExt == "js" || fileExt == "sh"
                        if (singleFileName.isNotBlank() && isSupported) {
                            val scriptType = when(fileExt) {
                                "py" -> "Python"
                                "js" -> "Node.js"
                                else -> "Shell"
                            }
                            viewModel.createSingleFile(singleFileName, scriptType)
                            showSingleFileDialog = false
                        }
                    },
                    enabled = singleFileName.contains(".") && singleFileName.length > 3
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSingleFileDialog = false }) { Text("取消") }
            }
        )
    }

    // ─── 🛠️ 弹窗2：新建文件夹工程对话框 ───
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("新建文件夹工程项目", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it.trim() },
                        label = { Text("工程文件夹名称") },
                        placeholder = { Text("例如: auto_task_hub") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = folderEntryPoint, // 临时在此重用 selectedScript 作为入口文件输入
                        onValueChange = { folderEntryPoint = it.trim() },
                        label = { Text("配置入口执行文件名") },
                        placeholder = { Text("例如: main.py 或 index.js") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                val isValid = folderName.isNotBlank() && folderEntryPoint.isNotBlank()
                Button(
                    onClick = {
                        // 👈 调用真实的物理文件夹工程创建方法
                        viewModel.createProjectFolder(folderName, folderEntryPoint)
                        showFolderDialog = false
                    },
                    enabled = isValid
                ) {
                    Text("创建工程")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text("取消") }
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

@Composable
private fun EmptyScriptsState(hasAnyScripts: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val workdir = WorkdirPreference.getWorkdir(context)
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
            text = if (hasAnyScripts) "系统未扫描到符合后缀的文件，请先创建或从电脑端导入" else "点击右下角“添加/新建”在 $workdir 下创建第一个代码文件",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun ScriptCard(
    script: ScriptEntity, // 👈 直接对齐数据库实体
    onExecuteNow: () -> Unit,
    onOpenDetail: () -> Unit = {},
    onViewLogs: () -> Unit = {},
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

    // 将数据库中 hex 的颜色安全反解析为 Compose 颜色
    val themeColor = remember(script.themeColorHex) {
        try {
            Color(android.graphics.Color.parseColor(script.themeColorHex))
        } catch (e: Exception) {
            TypeColorPython
        }
    }

    // 从数据库字符串中安全映射依赖状态枚举
    val dependencyStatusEnum = remember(script.dependencyStatus) {
        try {
            DependencyStatus.valueOf(script.dependencyStatus)
        } catch (e: Exception) {
            DependencyStatus.None
        }
    }

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
                            color = if (script.isRunning) themeColor.copy(alpha = pulseAlpha) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(themeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (script.isFolder) Icons.Default.Folder else Icons.Default.Terminal,
                            contentDescription = null,
                            tint = themeColor,
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
                        Box(modifier = Modifier.background(themeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(
                                script.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (script.isRunning) {
                            Box(modifier = Modifier.size(6.dp).background(StatusRunning, RoundedCornerShape(50)))
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
                        enabled = !script.isRunning,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = themeColor.copy(alpha = 0.1f),
                            contentColor = themeColor,
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
                                text = { Text("查看/编辑代码") },
                                leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                                onClick = { showMoreMenu = false; onOpenDetail() }
                            )
                            DropdownMenuItem(
                                text = { Text("查看日志记录") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = { showMoreMenu = false; onViewLogs() }
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

            AnimatedVisibility(visible = script.isFolder && dependencyStatusEnum != DependencyStatus.None) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val (statusText, statusColor) = when (dependencyStatusEnum) {
                                DependencyStatus.Configured -> "检测到未安装依赖环境" to MaterialTheme.colorScheme.tertiary
                                DependencyStatus.Installed -> "依赖环境已完全就绪" to TerminalSuccess
                                DependencyStatus.Error -> "依赖配置失败，环境异常" to MaterialTheme.colorScheme.error
                                else -> "" to Color.Unspecified
                            }
                            Box(modifier = Modifier.size(6.dp).background(statusColor, RoundedCornerShape(50)))
                            Text(text = statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.expressiveClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.97f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "cardPressScale")
    this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}