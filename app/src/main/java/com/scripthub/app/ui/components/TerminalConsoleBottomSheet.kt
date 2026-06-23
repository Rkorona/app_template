package com.scripthub.app.ui.components

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
import com.scripthub.app.data.AppDatabase
import com.scripthub.app.data.RunLogEntity
import com.scripthub.app.utils.FileHelper
import com.scripthub.app.utils.ProotRunner
import com.scripthub.app.utils.ScriptForegroundService
import com.scripthub.app.utils.ShizukuHelper
import com.scripthub.app.ui.theme.TerminalInfo
import com.scripthub.app.ui.theme.TerminalSuccess
import com.scripthub.app.ui.theme.TerminalError
import com.scripthub.app.ui.theme.TerminalWarn
import com.scripthub.app.ui.theme.TerminalExec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogLine(val text: String, val color: Color = TerminalSuccess)

private fun lineColor(line: String): Color = when {
    line.contains("error",     ignoreCase = true) ||
    line.contains("failed",    ignoreCase = true) ||
    line.contains("errno",     ignoreCase = true)  -> TerminalError
    line.contains("success",   ignoreCase = true) ||
    line.contains("installed", ignoreCase = true)  -> TerminalSuccess
    line.contains("warning",   ignoreCase = true) ||
    line.contains("warn",      ignoreCase = true)  -> TerminalWarn
    else -> TerminalInfo
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalConsoleBottomSheet(
    taskName: String,
    scriptName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState  = rememberLazyListState()
    val context    = LocalContext.current

    val logs      = remember { mutableStateListOf<LogLine>() }
    var isRunning by remember { mutableStateOf(true) }

    val db        = remember { AppDatabase.getDatabase(context) }
    val scriptDao = remember { db.scriptDao() }
    val runLogDao = remember { db.runLogDao() }

    val separatorColor = MaterialTheme.colorScheme.outlineVariant

    LaunchedEffect(taskName) {
        val useShizuku = ShizukuHelper.state.value == ShizukuHelper.State.READY

        logs.add(LogLine(
            "[INFO] 初始化执行管线 (${if (useShizuku) "Shizuku Shell 权限" else "proot"})...",
            TerminalInfo
        ))

        val scriptEntity = withContext(Dispatchers.IO) {
            scriptDao.getByName(scriptName) ?: scriptDao.getByName(taskName)
        }

        if (scriptEntity == null) {
            logs.add(LogLine("[ERROR] 未在本地数据库中检测到该脚本的记录", TerminalError))
            logs.add(LogLine("[INFO] 请确保脚本已在《脚本管理》中完成扫描", TerminalInfo))
            isRunning = false
            return@LaunchedEffect
        }

        logs.add(LogLine("[INFO] 脚本类型: ${scriptEntity.type}", TerminalInfo))

        val envVars = withContext(Dispatchers.IO) {
            db.envVarDao().getAll().first()
                .filter { it.isEnabled }
                .associate { it.name to it.value }
        }
        if (envVars.isNotEmpty()) {
            logs.add(LogLine("[INFO] 已注入 ${envVars.size} 个环境变量: ${envVars.keys.joinToString(", ")}", TerminalInfo))
        }

        val startTime = System.currentTimeMillis()
        val rawLines  = mutableListOf<String>()
        var exitCode  = -1

        if (useShizuku && scriptEntity.type == "Shell") {
            // ── Shizuku 路径：以 shell 用户权限执行 sh 脚本 ──────────────────────────
            withContext(Dispatchers.IO) {
                try {
                    val scriptFile = if (scriptEntity.isFolder)
                        java.io.File(FileHelper.scriptsDir, "${scriptEntity.name}/${scriptEntity.entryPoint}")
                    else
                        java.io.File(FileHelper.scriptsDir, scriptEntity.name)

                    val scriptPath = scriptFile.absolutePath

                    withContext(Dispatchers.Main) {
                        logs.add(LogLine("[EXEC] 通过 Shizuku 以 shell 权限执行: $scriptPath", TerminalExec))
                    }

                    ScriptForegroundService.start(context, "正在手动执行: $taskName")

                    val envExports = if (envVars.isEmpty()) "" else
                        envVars.entries.joinToString(" && ") { (k, v) ->
                            val escaped = v.replace("'", "'\\''")
                            "export $k='$escaped'"
                        } + " && "

                    val result = ShizukuHelper.exec("${envExports}sh \"$scriptPath\"")
                    exitCode = result.exitCode

                    val output = (result.stdout + result.stderr).trimEnd()
                    output.lines().filter { it.isNotBlank() }.forEach { rawLines.add(it) }

                    withContext(Dispatchers.Main) {
                        if (output.isBlank()) {
                            logs.add(LogLine("[INFO] (无输出)", TerminalInfo))
                        } else {
                            output.lines().filter { it.isNotBlank() }.forEach { line ->
                                logs.add(LogLine(line, lineColor(line)))
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        logs.add(LogLine("[ERROR] Shizuku 执行异常: ${e.message}", TerminalError))
                    }
                } finally {
                    ScriptForegroundService.stop(context)
                    withContext(NonCancellable) {
                        val durationMs = System.currentTimeMillis() - startTime
                        try {
                            runLogDao.insert(
                                RunLogEntity(
                                    scriptName = scriptName,
                                    startTime  = startTime,
                                    durationMs = durationMs,
                                    exitCode   = exitCode,
                                    logText    = rawLines.joinToString("\n")
                                )
                            )
                            runLogDao.pruneOldLogs(scriptName)
                        } catch (_: Exception) {}

                        try {
                            val fmt   = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            val label = if (exitCode == 0) "✅ ${fmt.format(Date(startTime))}"
                                        else "❌ ${fmt.format(Date(startTime))}"
                            db.scriptDao().updateLastRun(scriptName, label)
                        } catch (_: Exception) {}

                        withContext(Dispatchers.Main) {
                            isRunning = false
                            logs.add(LogLine("─".repeat(48), separatorColor))
                            val exitMsg = when (exitCode) {
                                0    -> "进程正常退出 (Exit Code: 0)"
                                -1   -> "进程退出 (Exit Code: 未知)"
                                else -> "进程异常退出 (Exit Code: $exitCode)"
                            }
                            logs.add(LogLine(
                                "[FINISHED] $exitMsg",
                                if (exitCode == 0) TerminalSuccess else TerminalError
                            ))
                        }
                    }
                }
            }
        } else {
            // ── proot 路径：Python / Node.js 脚本，或 Shizuku 未就绪时的 Shell 脚本 ──
            withContext(Dispatchers.IO) {
                var process: Process?       = null
                var reader:  BufferedReader? = null

                try {
                    withContext(Dispatchers.Main) {
                        if (!useShizuku && scriptEntity.type == "Shell") {
                            logs.add(LogLine("[WARN] Shizuku 未就绪，改用 proot 执行（无法访问 Android/data）", TerminalWarn))
                        }
                        logs.add(LogLine("[EXEC] 启动 proot 进程...", TerminalExec))
                    }

                    ScriptForegroundService.start(context, "正在手动执行: $taskName")

                    process = ProotRunner.executeScript(
                        context    = context,
                        scriptName = scriptEntity.name,
                        isFolder   = scriptEntity.isFolder,
                        entryPoint = scriptEntity.entryPoint,
                        scriptType = scriptEntity.type,
                        envVars    = envVars
                    )

                    withContext(Dispatchers.Main) {
                        logs.add(LogLine("[INFO] proot 进程已启动，正在读取输出 ➔", TerminalSuccess))
                    }

                    reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val finalLine = line!!
                        rawLines.add(finalLine)

                        if (finalLine.startsWith("[SYSTEM_EXIT_CODE]:")) {
                            exitCode = finalLine.removePrefix("[SYSTEM_EXIT_CODE]:").trim().toIntOrNull() ?: -1
                            continue
                        }

                        withContext(Dispatchers.Main) {
                            logs.add(LogLine(finalLine, lineColor(finalLine)))
                        }
                    }

                    process.waitFor()

                } catch (e: IllegalStateException) {
                    withContext(Dispatchers.Main) {
                        logs.add(LogLine("[ERROR] ${e.message}", TerminalError))
                        logs.add(LogLine("[INFO] 请前往「配置中心 → Linux 运行环境」完成安装", TerminalWarn))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        logs.add(LogLine("[ERROR] 进程异常: ${e.message}", TerminalError))
                    }
                } finally {
                    ScriptForegroundService.stop(context)
                    withContext(NonCancellable) {
                        try {
                            reader?.close()
                            process?.destroy()
                        } catch (_: Exception) {}

                        val durationMs = System.currentTimeMillis() - startTime

                        try {
                            val logText = rawLines
                                .filter { !it.startsWith("[SYSTEM_EXIT_CODE]:") }
                                .joinToString("\n")
                            runLogDao.insert(
                                RunLogEntity(
                                    scriptName = scriptName,
                                    startTime  = startTime,
                                    durationMs = durationMs,
                                    exitCode   = exitCode,
                                    logText    = logText
                                )
                            )
                            runLogDao.pruneOldLogs(scriptName)
                        } catch (_: Exception) {}

                        try {
                            val fmt   = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            val label = if (exitCode == 0) "✅ ${fmt.format(Date(startTime))}"
                                        else "❌ ${fmt.format(Date(startTime))}"
                            db.scriptDao().updateLastRun(scriptName, label)
                        } catch (_: Exception) {}

                        withContext(Dispatchers.Main) {
                            isRunning = false
                            logs.add(LogLine("─".repeat(48), separatorColor))
                            val exitMsg = when (exitCode) {
                                0    -> "进程正常退出 (Exit Code: 0)"
                                -1   -> "进程退出 (Exit Code: 未知)"
                                else -> "进程异常退出 (Exit Code: $exitCode)"
                            }
                            logs.add(LogLine(
                                "[FINISHED] $exitMsg",
                                if (exitCode == 0) TerminalSuccess else TerminalError
                            ))
                        }
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
        sheetState       = sheetState,
        containerColor   = colors.surfaceContainerLow,
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
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = colors.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint     = colors.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text       = taskName,
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = colors.onSurface
                        )
                        Text(
                            text  = "终端输出",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isRunning) TerminalWarn.copy(alpha = 0.15f)
                                else TerminalSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = null,
                                tint     = if (isRunning) TerminalWarn else TerminalSuccess,
                                modifier = Modifier.size(7.dp)
                            )
                            Text(
                                text       = if (isRunning) "RUNNING" else "FINISHED",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = if (isRunning) TerminalWarn else TerminalSuccess
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint     = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text     = "${logs.size} 条日志",
                style    = MaterialTheme.typography.labelSmall,
                color    = colors.onSurfaceVariant,
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
                    state    = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text       = log.text,
                            color      = log.color,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
