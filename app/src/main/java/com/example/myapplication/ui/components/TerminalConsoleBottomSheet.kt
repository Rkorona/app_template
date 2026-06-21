package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.RunLogEntity
import com.example.myapplication.utils.TermuxRunner
import com.example.myapplication.ui.theme.TerminalInfo
import com.example.myapplication.ui.theme.TerminalSuccess
import com.example.myapplication.ui.theme.TerminalError
import com.example.myapplication.ui.theme.TerminalWarn
import com.example.myapplication.ui.theme.TerminalExec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogLine(val text: String, val color: Color = TerminalSuccess)

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

    val logs = remember { mutableStateListOf<LogLine>() }
    var isRunning by remember { mutableStateOf(true) }

    val db = remember { AppDatabase.getDatabase(context) }
    val scriptDao = remember { db.scriptDao() }
    val runLogDao = remember { db.runLogDao() }

    val separatorColor = MaterialTheme.colorScheme.outlineVariant

    LaunchedEffect(taskName) {
        logs.add(LogLine("[INFO] 初始化自动化执行管线...", TerminalInfo))

        val scriptEntity = withContext(Dispatchers.IO) {
            scriptDao.getByName(scriptName) ?: scriptDao.getByName(taskName)
        }

        if (scriptEntity == null) {
            logs.add(LogLine("[ERROR] 未在本地数据库中检测到该脚本的环境映射缓存", TerminalError))
            logs.add(LogLine("[INFO] 请确保脚本已在《脚本管理》物理对齐扫描完毕", TerminalInfo))
            isRunning = false
            return@LaunchedEffect
        }

        logs.add(LogLine("[INFO] 检测运行环境: ${scriptEntity.type} (物理扫描正常)...", TerminalInfo))

        val startTime = System.currentTimeMillis()
        val rawLines = mutableListOf<String>()
        var exitCode = -1

        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            var reader: BufferedReader? = null

            try {
                serverSocket = ServerSocket(0).apply {
                    reuseAddress = true
                    soTimeout = 15000
                }
                val allocatedPort = serverSocket.localPort

                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[INFO] 已分配本地物理管道端口: $allocatedPort", TerminalInfo))
                }

                TermuxRunner.executeScript(
                    context    = context,
                    scriptName = scriptEntity.name,
                    isFolder   = scriptEntity.isFolder,
                    entryPoint = scriptEntity.entryPoint,
                    scriptType = scriptEntity.type,
                    socketPort = allocatedPort
                )

                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[EXEC] 调度指令已派发至 Termux 引擎，等待物理管道连通...", TerminalExec))
                }

                clientSocket = serverSocket.accept()

                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[INFO] 物理数据管道双向连通建立成功，开始承接运行日志 ➔", TerminalSuccess))
                }

                reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val finalLine = line!!
                    rawLines.add(finalLine)

                    // 解析退出码
                    if (finalLine.startsWith("[SYSTEM_EXIT_CODE]:")) {
                        exitCode = finalLine.removePrefix("[SYSTEM_EXIT_CODE]:").trim().toIntOrNull() ?: -1
                    }

                    val logColor = when {
                        finalLine.startsWith("[SYSTEM_EXIT_CODE]:") -> TerminalInfo
                        finalLine.contains("error", ignoreCase = true) || finalLine.contains("failed", ignoreCase = true) -> TerminalError
                        finalLine.contains("success", ignoreCase = true) || finalLine.contains("installed", ignoreCase = true) -> TerminalSuccess
                        finalLine.contains("warning", ignoreCase = true) -> TerminalWarn
                        else -> TerminalInfo
                    }
                    withContext(Dispatchers.Main) {
                        logs.add(LogLine(finalLine, logColor))
                    }
                }

            } catch (e: java.io.InterruptedIOException) {
                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[WARN] 管道连通超时：请检查 Termux 后台是否在线，且已按照教程授予 App 跨应用调用权限", TerminalWarn))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[ERROR] 管道拦截异常: ${e.message}", TerminalError))
                }
            } finally {
                withContext(NonCancellable) {
                    try {
                        reader?.close()
                        clientSocket?.close()
                        serverSocket?.close()
                    } catch (ioe: Exception) {
                        ioe.printStackTrace()
                    }

                    val durationMs = System.currentTimeMillis() - startTime

                    // 将本次执行日志持久化到数据库
                    try {
                        val logText = rawLines
                            .filter { !it.startsWith("[SYSTEM_EXIT_CODE]:") }
                            .joinToString("\n")
                        val logEntity = RunLogEntity(
                            scriptName = scriptName,
                            startTime  = startTime,
                            durationMs = durationMs,
                            exitCode   = exitCode,
                            logText    = logText
                        )
                        runLogDao.insert(logEntity)
                        runLogDao.pruneOldLogs(scriptName)
                    } catch (e: Exception) {
                        // 日志入库失败不影响主流程
                    }

                    // 更新脚本的最后运行时间（在 ScriptCard 上实时显示）
                    try {
                        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        val label = if (exitCode == 0) "✅ ${fmt.format(Date(startTime))}"
                                    else "❌ ${fmt.format(Date(startTime))}"
                        db.scriptDao().updateLastRun(scriptName, label)
                    } catch (e: Exception) {
                        // 不影响主流程
                    }

                    withContext(Dispatchers.Main) {
                        isRunning = false
                        logs.add(LogLine("─".repeat(48), separatorColor))
                        val exitMsg = if (exitCode == 0) "自动化引擎完成调度。进程正常退出 (Exit Code: 0)"
                                      else if (exitCode == -1) "自动化引擎完成调度。进程退出 (Exit Code: 未知)"
                                      else "自动化引擎完成调度。进程异常退出 (Exit Code: $exitCode)"
                        logs.add(LogLine("[FINISHED] $exitMsg", if (exitCode == 0) TerminalSuccess else TerminalError))
                    }
                }
            }
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.onSurfaceVariant.copy(alpha = 0.4f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = colors.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = taskName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "终端执行日志",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isRunning) TerminalWarn.copy(alpha = 0.15f) else TerminalSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = null,
                                tint = if (isRunning) TerminalWarn else TerminalSuccess,
                                modifier = Modifier.size(7.dp)
                            )
                            Text(
                                text = if (isRunning) "RUNNING" else "FINISHED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) TerminalWarn else TerminalSuccess
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${logs.size} 条日志",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainer)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log.text,
                            color = log.color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
