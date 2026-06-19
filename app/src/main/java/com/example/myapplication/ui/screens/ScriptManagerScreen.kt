package com.example.myapplication.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.myapplication.ui.components.TerminalConsoleBottomSheet // 👈 引入共享终端

enum class DependencyStatus {
    None,       
    Configured, 
    Installed,  
    Error       
}

data class ScriptProject(
    val name: String,
    val type: String, 
    val trigger: String,
    val lastRun: String,
    val isRunning: Boolean = false,
    val themeColor: Color,
    val isFolder: Boolean,               
    val entryPoint: String = "",         
    val dependencyStatus: DependencyStatus = DependencyStatus.None
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val scripts = remember {
        listOf(
            ScriptProject("telegram_bot", "Python", "⏰ crontab: */10 * * * *", "上次成功: 2分钟前", true, Color(0xFF38BDF8), isFolder = true, entryPoint = "main.py", dependencyStatus = DependencyStatus.Installed),
            ScriptProject("daily_check.js", "Node.js", "⏰ crontab: 0 8 * * *", "上次成功: 今天 08:00", false, Color(0xFFA855F7), isFolder = false, dependencyStatus = DependencyStatus.None),
            ScriptProject("backup_db.sh", "Shell", "⚡ 手动触发", "上次成功: 昨天 23:00", false, Color(0xFF22C55E), isFolder = false, dependencyStatus = DependencyStatus.None),
            ScriptProject("web_crawler_v2", "Python", "⚡ 手动触发", "从未运行", false, Color(0xFF38BDF8), isFolder = true, entryPoint = "crawl.py", dependencyStatus = DependencyStatus.Configured),
            ScriptProject("clean_logs.sh", "Shell", "⏰ crontab: 0 0 * * 0", "上次失败: 1周前", false, Color(0xFFEF4444), isFolder = false, dependencyStatus = DependencyStatus.None),
            ScriptProject("api_gateway", "Node.js", "⏰ crontab: @reboot", "上次错误: 3小时前", false, Color(0xFFA855F7), isFolder = true, entryPoint = "server.js", dependencyStatus = DependencyStatus.Error)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "Python", "Shell", "Node.js")

    // ─── 🎛️ 终端联动状态 ───
    var activeTerminalScript by remember { mutableStateOf<ScriptProject?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        
        // ─── 🌟 升级完成：带精致微动效的多级悬浮扩展菜单 ───
        floatingActionButton = {
            var isFabExpanded by remember { mutableStateOf(false) }
            
            // 点击展开时加号顺时针旋转 45° 变成关闭交叉号
            val rotationAngle by animateFloatAsState(
                targetValue = if (isFabExpanded) 45f else 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "fabRotation"
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 展开的子选项菜单武器库
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(end = 6.dp) // 与主 FAB 轴线视觉对齐
                    ) {
                        // 选项一：新建单文件脚本
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "新建单文件脚本",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    /* TODO: 触发新建文件动作 */
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = "New File", modifier = Modifier.size(18.dp))
                            }
                        }

                        // 选项二：新建工程项目
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "新建工程项目",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    /* TODO: 触发新建项目动作 */
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = "New Project", modifier = Modifier.size(18.dp))
                            }
                        }

                        // 选项三：导入本地已有项目
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "导入本地已有项目",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    /* 执行原文件导入选择动作 */
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = "Import Project", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // ─── 🚀 主控制 FAB ───
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
                            modifier = Modifier.rotate(rotationAngle) // 注入旋转联动
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFabExpanded) "收起菜单" else "添加/新建", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
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
                        label = { Text(filter, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val filteredList = scripts.filter { 
                    (selectedFilter == "全部" || it.type == selectedFilter) && it.name.contains(searchQuery, ignoreCase = true)
                }
                items(filteredList) { script ->
                    ScriptCard(
                        script = script,
                        onExecuteNow = { activeTerminalScript = script }
                    )
                }
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
}

@Composable
fun ScriptCard(
    script: ScriptProject,
    onExecuteNow: () -> Unit
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

    val isExecutable = script.dependencyStatus == DependencyStatus.None || script.dependencyStatus == DependencyStatus.Installed

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp), 
        modifier = Modifier.fillMaxWidth()
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
                        Text(text = script.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Box(modifier = Modifier.background(script.themeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(script.type, fontSize = 10.sp, color = script.themeColor, fontWeight = FontWeight.Bold)
                        }
                        if (script.isRunning) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF22C55E), RoundedCornerShape(50)))
                        }
                    }
                    
                    if (script.isFolder) {
                        Text(
                            text = "入口文件 ➔ ${script.entryPoint}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = script.trigger, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    Text(text = script.lastRun, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(18.dp))
                    }
                    
                    IconButton(onClick = { /* 更多 */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                            val (statusText, statusColor) = when (script.dependencyStatus) {
                                DependencyStatus.Configured -> "检测到未安装依赖环境" to MaterialTheme.colorScheme.error
                                DependencyStatus.Installed -> "依赖环境已完全就绪" to Color(0xFF22C55E)
                                DependencyStatus.Error -> "依赖配置失败，环境异常" to MaterialTheme.colorScheme.error
                                else -> "" to Color.Unspecified
                            }
                            Box(modifier = Modifier.size(6.dp).background(statusColor, RoundedCornerShape(50)))
                            Text(text = statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }

                        if (script.dependencyStatus == DependencyStatus.Configured || script.dependencyStatus == DependencyStatus.Error) {
                            Button(
                                onClick = { /* 安装依赖 */ },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("一键安装", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TextButton(onClick = { /* 详情 */ }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("目录详情", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
