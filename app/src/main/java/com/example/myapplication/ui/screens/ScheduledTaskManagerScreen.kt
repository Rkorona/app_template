package com.example.myapplication.ui.screens

import androidx.compose.animation.core.scale
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.myapplication.ui.components.TerminalConsoleBottomSheet 
import com.example.myapplication.utils.CronTranslator 

enum class CronTaskStatus {
    Enabled,   
    Disabled,  
}

data class ScheduledTask(
    val id: String,
    val name: String,            
    val targetScript: String,    
    val cronExpression: String,  
    val nextRunTime: String,     
    val lastRunResult: String,   
    val themeColor: Color,       
    val isSuccess: Boolean = true, 
    val initialStatus: CronTaskStatus = CronTaskStatus.Enabled
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val tasksList = remember {
        mutableStateListOf(
            ScheduledTask("1", "全网币价监控", "crypto_monitor.py", "*/5 * * * *", "预计 5分钟后", "上次耗时: 1.2s", Color(0xFF38BDF8)),
            ScheduledTask("2", "数据库每日冷备", "backup_db.sh", "0 2 * * *", "明天凌晨 02:00", "备份成功 45MB", Color(0xFF22C55E)),
            ScheduledTask("3", "GitHub 绿墙自动打卡", "auto_commit.sh", "0 23 * * *", "今天 23:00", "打卡完成", Color(0xFFEAB308)),
            ScheduledTask("4", "日志残渣自动清理", "clean_logs.sh", "0 0 * * 0", "本周日凌晨 00:00", "上次失败: 权限不足", Color(0xFFEF4444), isSuccess = false)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "Python", "Shell", "Node.js")

    var showCreateSheet by remember { mutableStateOf(false) } 
    var activeTerminalTask by remember { mutableStateOf<ScheduledTask?>(null) } 

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true }, 
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
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
            // 🔍 补齐：高颜值战术搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索定时调度任务...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            // 🏷️ 补齐：横向滚动过滤标签
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
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
            
            // 自动化任务调度长列表
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val filteredTasks = tasksList.filter { 
                    val matchesSearch = it.name.contains(searchQuery, ignoreCase = true)
                    val matchesFilter = selectedFilter == "全部" || when(selectedFilter) {
                        "Python" -> it.targetScript.endsWith(".py")
                        "Shell" -> it.targetScript.endsWith(".sh")
                        "Node.js" -> it.targetScript.endsWith(".js")
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }
                
                items(filteredTasks, key = { it.id }) { task ->
                    CronTaskCard(
                        task = task,
                        onStatusToggle = { /* 状态切换逻辑 */ },
                        onExecuteNow = { activeTerminalTask = task } 
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        var newName by remember { mutableStateOf("") }
        var newCron by remember { mutableStateOf("*/10 * * * *") } 
        var selectedScript by remember { mutableStateOf("crypto_monitor.py") }

        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("配置新自动化调度", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("任务代号 / 名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = newCron,
                    onValueChange = { newCron = it },
                    label = { Text("Cron 表达式 (分 时 日 月 周)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(12.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = CronTranslator.translate(newCron), 
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            tasksList.add(ScheduledTask(
                                id = (tasksList.size + 1).toString(),
                                name = newName,
                                targetScript = selectedScript,
                                cronExpression = newCron,
                                nextRunTime = "计算中...",
                                lastRunResult = "从未执行",
                                themeColor = Color(0xFFA855F7)
                            ))
                        }
                        showCreateSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("挂载上线", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
fun CronTaskCard(
    task: ScheduledTask,
    onStatusToggle: (Boolean) -> Unit, // 👈 补齐了逗号！
    onExecuteNow: () -> Unit
) {
    var isEnabled by remember(task.id) { mutableStateOf(task.initialStatus == CronTaskStatus.Enabled) }
    val cardAlpha = if (isEnabled) 1.0f else 0.6f

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

                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        onStatusToggle(it)
                    },
                    modifier = Modifier.scale(0.85f) 
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.cronExpression,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = task.nextRunTime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
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
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                    Text(
                        text = task.lastRunResult,
                        fontSize = 11.sp,
                        color = if (!task.isSuccess && isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEnabled) {
                        IconButton(
                            onClick = onExecuteNow, // 👈 真正绑定了执行动作！
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Trigger Now", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = { /* 更多菜单 */ }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
