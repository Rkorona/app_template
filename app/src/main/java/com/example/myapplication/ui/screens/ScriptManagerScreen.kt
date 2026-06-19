package com.example.myapplication.ui.screens

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

// 📌 升级版依赖环境状态枚举
enum class DependencyStatus {
    None,       // 无需依赖 (单文件孤勇者)
    Configured, // 检测到依赖文件 (如 requirements.txt / package.json) 但未安装
    Installed,  // 依赖已就绪
    Error       // 依赖安装失败
}

// 📌 升级版项目制脚本数据模型
data class ScriptProject(
    val name: String,
    val type: String, // Shell, Python, Node.js
    val trigger: String,
    val lastRun: String,
    val isRunning: Boolean = false,
    val themeColor: Color,
    val isFolder: Boolean,               // true: 项目文件夹, false: 单文件
    val entryPoint: String = "",         // 文件夹项目的启动入口 (如 main.py)
    val dependencyStatus: DependencyStatus = DependencyStatus.None
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    // 丰富假数据体系，完美对应 Dashboard 联动，并覆盖“单文件、全依赖、未安装、安装失败”全场景
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* 新建脚本或导入文件夹 */ },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导入项目", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
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
            // 🔍 1. 战术搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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

            // 🏷️ 2. 横向滚动过滤标签
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
                        label = { Text(filter, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.2.dp
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // 📜 3. 进化版自动化武器库列表
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scripts.filter { selectedFilter == "全部" || it.type == selectedFilter }) { script ->
                    ScriptCard(script = script)
                }
            }
        }
    }
}

// 📌 核心重构单体：支持文件夹项目与环境自检的 Expressive 卡片
@Composable
fun ScriptCard(script: ScriptProject) {
    // 慢速呼吸灯动画（运行中外圈散发对应色系微光）
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

    // 只有当依赖就绪或是单文件时，才允许一键快火运行
    val isExecutable = script.dependencyStatus == DependencyStatus.None || script.dependencyStatus == DependencyStatus.Installed

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp), 
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ─── 上半部分：核心项目信息 ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 左侧：智能视觉分流徽章（文件夹 VS 单文件）
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
                        modifier = Modifier
                            .fillMaxSize()
                            .background(script.themeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // 📁 核心改动：如果是项目文件夹，显示 Folder 图标；如果是单文件，显示 Terminal 极客图标
                        Icon(
                            imageVector = if (script.isFolder) Icons.Default.Folder else Icons.Default.Terminal,
                            contentDescription = null,
                            tint = script.themeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 2. 中间：极客信息舱
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = script.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // 优雅紧凑的语言高亮 Badge
                        Box(
                            modifier = Modifier
                                .background(script.themeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(script.type, fontSize = 10.sp, color = script.themeColor, fontWeight = FontWeight.Bold)
                        }

                        if (script.isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF22C55E), RoundedCornerShape(50))
                            )
                        }
                    }
                    
                    // 📁 核心改动：如果是文件夹，直观展示项目结构与运行入口
                    if (script.isFolder) {
                        Text(
                            text = "入口文件 ➔ ${script.entryPoint}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.0.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = script.trigger,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = script.lastRun,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // 3. 右侧：动作控制台
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = { /* 立即执行 */ },
                        enabled = isExecutable, // 🚨 如果环境依赖不满足，按钮优雅置灰防止盲目盲跑
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = script.themeColor.copy(alpha = 0.1f),
                            contentColor = script.themeColor,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(onClick = { /* 更多控制 */ }) {
                        Icon(
                            Icons.Default.MoreVert, 
                            contentDescription = "More", 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ─── 下半部分：🛠️ 核心优化——环境依赖智能管理舱 ───
            AnimatedVisibility(visible = script.isFolder && script.dependencyStatus != DependencyStatus.None) {
                Column {
                    // 精细的战术分割线
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 依赖自检状态描述
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val (statusText, statusColor) = when (script.dependencyStatus) {
                                DependencyStatus.Configured -> "检测到未安装依赖环境" to MaterialTheme.colorScheme.error
                                DependencyStatus.Installed -> "依赖环境已完全就绪" to Color(0xFF22C55E)
                                DependencyStatus.Error -> "依赖配置失败，环境异常" to MaterialTheme.colorScheme.error
                                else -> "" to Color.Unspecified
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, RoundedCornerShape(50))
                            )
                            Text(
                                text = statusText, 
                                fontSize = 11.sp, 
                                color = statusColor, 
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 环境交互手柄：未就绪提供一键安装，已就绪提供目录树跳转
                        if (script.dependencyStatus == DependencyStatus.Configured || script.dependencyStatus == DependencyStatus.Error) {
                            Button(
                                onClick = { /* 后台跑 pip install / npm install 逻辑 */ },
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
                            TextButton(
                                onClick = { /* 查看项目文件详情 */ },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("目录详情", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                Icon(
                                    imageVector = Icons.Default.ChevronRight, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(14.dp), 
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
