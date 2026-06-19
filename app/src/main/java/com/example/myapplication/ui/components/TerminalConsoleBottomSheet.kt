package com.example.myapplication.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 日志数据模型
data class LogLine(val text: String, val color: Color = Color(0xFF4ADE80))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalConsoleBottomSheet(
    taskName: String,
    scriptName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // 动态日志流状态
    val logs = remember { mutableStateListOf<LogLine>() }
    var isRunning by remember { mutableStateOf(true) }

    // 🔬 核心：模拟极客级后台日志流式输出
    LaunchedEffect(taskName) {
        logs.add(LogLine("[INFO] 初始化自动化执行管线...", Color(0xFF38BDF8)))
        delay(400)
        logs.add(LogLine("[INFO] 正在载入核心武器库环境: Node.js / Python3环境检测通过", Color(0xFF38BDF8)))
        delay(300)
        logs.add(LogLine("[EXEC] 正在触发目标脚本: $scriptName", Color(0xFFA855F7)))
        delay(600)
        
        val mockSteps = listOf(
            "[INFO] 连接远端数据库主机 [netcup_root_v4] 成功...",
            "[INFO] 开始同步战术审计残留残渣...",
            "[SUCCESS] 抓取目标 API 核心包数据: 200 OK",
            "[DATA] { status: \"success\", affected_rows: 42, latency: \"182ms\" }",
            "[WARN] 发现轻微内存抖动，触发垃圾搜集回收机制...",
            "[SUCCESS] 临时缓存已清理，物理空间释放 12.4MB"
        )

        for (step in mockSteps) {
            val color = when {
                step.contains("[SUCCESS]") -> Color(0xFF22C55E)
                step.contains("[WARN]") -> Color(0xFFEAB308)
                else -> Color(0xFF4ADE80)
            }
            logs.add(LogLine(step, color))
            delay(500) // 模拟流式延迟
        }

        delay(400)
        logs.add(LogLine("[INFO] ──────────────────────────────────────────", Color.Gray))
        logs.add(LogLine("[SUCCESS] 战术任务 [$taskName] 顺利跑通！进程正常退出 (Exit Code: 0)", Color(0xFF22C55E)))
        isRunning = false
    }

    // 📜 锁底自动流式滚动：只要有新日志，立刻无条件滚到最底下
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF090D16), // 纯正黑客暗黑夜幕蓝
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f) // 占屏幕 75% 高度，标准的工业终端视角
                .padding(horizontal = 16.dp, bottom = 24.dp)
        ) {
            // ─── 终端头部控制条 ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Terminal, 
                        contentDescription = null, 
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "终端审计舱: $taskName",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 状态呼吸灯
                Surface(
                    color = if (isRunning) Color(0xFFEAB308).copy(alpha = 0.2f) else Color(0xFF22C55E).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = if (isRunning) "• RUNNING" else "• FINISHED",
                        color = if (isRunning) Color(0xFFEAB308) else Color(0xFF22C55E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 黑客级命令行主体 ───
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF020617), shape = RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log.text,
                            color = log.color,
                            fontFamily = FontFamily.Monospace, // 极客专属等宽字体
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
