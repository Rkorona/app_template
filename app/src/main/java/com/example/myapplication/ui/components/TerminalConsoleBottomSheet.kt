// app_template/app/src/main/java/com/example/myapplication/ui/components/TerminalConsoleBottomSheet.kt
package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.utils.TermuxRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

data class LogLine(val text: String, val color: Color = Color(0xFF4ADE80))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalConsoleBottomSheet(
    taskName: String,
    scriptName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    // 动态日志流
    val logs = remember { mutableStateListOf<LogLine>() }
    var isRunning by remember { mutableStateOf(true) }

    // 初始化本地 Room 数据库
    val db = remember { AppDatabase.getDatabase(context) }
    val scriptDao = remember { db.scriptDao() }

    // 🔬 极客级本地 Socket 日志监听监听器
    LaunchedEffect(taskName) {
        logs.add(LogLine("[INFO] 初始化自动化执行管线...", Color(0xFF38BDF8)))
        
        val scriptEntity = withContext(Dispatchers.IO) {
            scriptDao.getByName(scriptName) ?: scriptDao.getByName(taskName)
        }

        if (scriptEntity == null) {
            logs.add(LogLine("[ERROR] 未在本地数据库中检测到该脚本的环境映射缓存", Color(0xFFF87171)))
            logs.add(LogLine("[INFO] 请确保脚本已在《脚本管理》物理对齐扫描完毕", Color(0xFF38BDF8)))
            isRunning = false
            return@LaunchedEffect
        }

        logs.add(LogLine("[INFO] 检测运行环境: ${scriptEntity.type} (物理扫描正常)...", Color(0xFF38BDF8)))

        // 在 IO 线程启动 Socket 拦截服务器
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            var reader: BufferedReader? = null
            
            try {
                // 1. 【核心改进：动态空闲端口】 传入端口 0 意味着让系统自动分配一个当前绝对可用的空闲端口
                serverSocket = ServerSocket(0).apply {
                    reuseAddress = true
                    soTimeout = 15000 // 15 秒连接超时
                }
                val allocatedPort = serverSocket.localPort
                
                logs.add(LogLine("[INFO] 已分配本地物理管道端口: $allocatedPort", Color(0xFF38BDF8)))

                // 2. 启动 Termux 并将动态分配的端口传递过去
                TermuxRunner.executeScript(
                    context = context,
                    scriptName = scriptEntity.name,
                    isFolder = scriptEntity.isFolder,
                    entryPoint = scriptEntity.entryPoint,
                    scriptType = scriptEntity.type,
                    socketPort = allocatedPort
                )

                logs.add(LogLine("[EXEC] 调度指令已派发至 Termux 引擎，等待物理管道连通...", Color(0xFFA855F7)))

                // 等待连接
                clientSocket = serverSocket.accept()
                
                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[INFO] 物理数据管道双向连通建立成功，开始承接运行日志 ➔", Color(0xFF22C55E)))
                }

                reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    val finalLine = line!!
                    val logColor = when {
                        finalLine.contains("error", ignoreCase = true) || finalLine.contains("failed", ignoreCase = true) -> Color(0xFFF87171)
                        finalLine.contains("success", ignoreCase = true) || finalLine.contains("installed", ignoreCase = true) -> Color(0xFF4ADE80)
                        finalLine.contains("warning", ignoreCase = true) -> Color(0xFFEAB308)
                        else -> Color(0xFF38BDF8)
                    }

                    withContext(Dispatchers.Main) {
                        logs.add(LogLine(finalLine, logColor))
                    }
                }

            } catch (e: java.io.InterruptedIOException) {
                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[WARN] 管道连通超时：请检查 Termux 后台是否在线，且已按照教程授予 App 跨应用调用权限", Color(0xFFEAB308)))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[ERROR] 管道拦截异常: ${e.message}", Color(0xFFF87171)))
                }
            } finally {
                // 3. 【核心改进：防内存与端口泄漏】使用 NonCancellable 确保即使协程被取消，清理代码也一定会完整执行
                withContext(NonCancellable) {
                    try {
                        reader?.close()
                        clientSocket?.close()
                        serverSocket?.close()
                    } catch (ioe: Exception) {
                        ioe.printStackTrace()
                    }
                    withContext(Dispatchers.Main) {
                        isRunning = false
                        logs.add(LogLine("[INFO] ──────────────────────────────────────────", Color.Gray))
                        logs.add(LogLine("[FINISHED] 自动化引擎完成调度。进程正常退出 (Exit Code: 0)", Color(0xFF22C55E)))
                    }
                }
            }
        }
    }

    // 锁底自动滑动流
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF090D16),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
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
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}