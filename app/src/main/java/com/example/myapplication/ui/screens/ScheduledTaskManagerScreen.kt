package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 📌 定时任务状态枚举
enum class CronTaskStatus {
    Enabled,   // 处于监听状态，静候定时触发
    Disabled,  // 已被摆平，暂停调度
}

// 📌 定时任务数据模型
data class ScheduledTask(
    val id: String,
    val name: String,            // 任务名称 (如: 自动签到)
    val targetScript: String,    // 绑定的目标脚本 (如: daily_check.js)
    val cronExpression: String,  // Cron 表达式 (如: 0 8 * * *)
    val nextRunTime: String,     // 下一次预计运行时间
    val lastRunResult: String,   // 上次执行结果摘要
    val themeColor: Color,       // 视觉区分色彩
    val isSuccess: Boolean = true, // 上次运行是否成功
    val initialStatus: CronTaskStatus = CronTaskStatus.Enabled
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    // 模拟工业级定时任务队列假数据
    val tasksList = remember {
        mutableStateListOf(
            ScheduledTask("1", "全网币价监控", "crypto_monitor.py", "*/5 * * * *", "预计 5分钟后", "上次耗时: 1.2s", Color(0xFF38BDF8)),
            ScheduledTask("2", "数据库每日冷备", "backup_db.sh", "0 2 * * *", "明天凌晨 02:00", "备份成功 45MB", Color(0xFF22C55E)),
            ScheduledTask("3", "GitHub 绿墙自动打卡", "auto_commit.sh", "0 23 * * *", "今天 23:00 (剩 8小时)", "打卡完成", Color(0xFFEAB308)),
            ScheduledTask("4", "日志残渣自动清理", "clean_logs.sh", "0 0 * * 0", "本周日凌晨 00:00", "上次失败: 权限不足", Color(0xFFEF4444), isSuccess = false),
            ScheduledTask("5", "教务处官网抢课爬虫", "web_crawler_v2", "*/30 8-18 * * *", "已暂停调度", "未激活", Color(0xFFA855F7), initialStatus = CronTaskStatus.Disabled)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "激活中", "已暂停")

    Scaffold(
        // 🛠️ 核心防遮挡 1：底部边界卡在导航栏上方，绝不让 FAB 掉入屏幕盲区
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        floatingActionButton = {
            // 🌟 符合 Material 3 Expressive 规范的 Extended FAB
            ExtendedFloatingActionButton(
                onClick = { /* 弹出创建定时任务面板（选择脚本 -> 配置 Cron 周期） */ },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 6.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Cron Task",
                    modifier = Modifier.size(20.dp)
                )
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
            // 🔍 1. 战术过滤搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索定时任务或关联脚本...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            // 🏷️ 2. 状态切换过滤标签
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

            // 📜 3. 自动化任务调度长列表
            val filteredTasks = tasksList.filter {
                val matchesSearch = it.name.contains(searchQuery, ignoreCase = true) || it.targetScript.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    "激活中" -> it.initialStatus == CronTaskStatus.Enabled
                    "已暂停" -> it.initialStatus == CronTaskStatus.Disabled
                    else -> true
                }
                matchesSearch && matchesFilter
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                // 🛠️ 核心防遮挡 2：底部安全边距垫高到 96.dp，滑到最底下时卡片自动让出空间，绝不和 FAB 重叠
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    CronTaskCard(
                        task = task,
                        onStatusToggle = { isChecked ->
                            // 在这里处理后台开启/关闭系统定时器逻辑
                            val index = tasksList.indexOf(task)
                            if (index != -1) {
                                tasksList[index] = task.copy(
                                    initialStatus = if (isChecked) CronTaskStatus.Enabled else CronTaskStatus.Disabled,
                                    nextRunTime = if (isChecked) "计算中..." else "已暂停调度"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// 📌 核心单体：Material 3 Expressive 定时任务流控制卡片
@Composable
fun CronTaskCard(
    task: ScheduledTask,
    onStatusToggle: (Boolean) -> Unit
) {
    var isEnabled by remember(task.id) { mutableStateOf(task.initialStatus == CronTaskStatus.Enabled) }

    // 当任务被禁用时，整张卡片呈现轻微低调的半透明质感
    val cardAlpha = if (isEnabled) 1.0f else 0.6f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color.Transparent)
        ) {
            // ─── 头部：任务名称、绑定脚本、状态开关 ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧科技感时钟徽章
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = if (isEnabled) task.themeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = if (isEnabled) task.themeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 中间任务信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = cardAlpha)
                    )
                    Text(
                        text = "绑定 ➔ ${task.targetScript}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isEnabled) 0.7f else 0.4f),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // 右侧快捷激活总开关
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        onStatusToggle(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier.scaleScale(0.85f) // 适当微调尺寸让它在列表里显得精致
                )
            }

            // ─── 中部：极客级 Cron 表达式与触发预测 ───
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 等宽字体渲染 Cron
                Text(
                    text = task.cronExpression,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                
                // 下次运行预测
                Text(
                    text = task.nextRunTime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            // ─── 尾部：历史追溯审计痕迹舱 ───
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
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = task.lastRunResult,
                        fontSize = 11.sp,
                        color = if (!task.isSuccess && isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // 精简小动作面板：手动单次试火按钮 + 更多
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEnabled) {
                        IconButton(
                            onClick = { /* 绕过定时逻辑，强制立刻后台触发一次该任务 */ },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Trigger Now",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { /* 弹出单体任务更多管理菜单：查看历史日志流、修改Cron配置、删除 */ },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// 辅助微调组件尺寸的扩展修饰符
private fun Modifier.scaleScale(scale: Float): Modifier = this.then(
    modifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
            placeable.placeRelativeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
)
