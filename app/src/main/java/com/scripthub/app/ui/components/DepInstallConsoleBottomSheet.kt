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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.data.DependencyEntity
import com.scripthub.app.ui.screens.DepStatus
import com.scripthub.app.ui.theme.TerminalError
import com.scripthub.app.ui.theme.TerminalInfo
import com.scripthub.app.ui.theme.TerminalSuccess
import com.scripthub.app.ui.theme.TerminalWarn
import com.scripthub.app.utils.DistroPreference
import com.scripthub.app.utils.ProotManager
import com.scripthub.app.viewmodel.ConfigViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepInstallConsoleBottomSheet(
    dep: DependencyEntity,
    viewModel: ConfigViewModel,
    onDismiss: () -> Unit
) {
    val context    = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState  = rememberLazyListState()
    val colors     = MaterialTheme.colorScheme

    val logs      = remember { mutableStateListOf<LogLine>() }
    var isRunning by remember { mutableStateOf(true) }
    var exitCode  by remember { mutableStateOf(-1) }

    LaunchedEffect(dep.id) {
        val cmd = viewModel.buildInstallCommand(dep)
        logs.add(LogLine("[INFO] 依赖包: ${dep.name}  版本: ${dep.version}", TerminalInfo))
        logs.add(LogLine("[INFO] 安装命令: $cmd", TerminalInfo))

        withContext(Dispatchers.IO) {
            var process: Process?       = null
            var reader:  BufferedReader? = null
            try {
                val distro = DistroPreference.getDistro(context)
                if (!ProotManager.isDistroInstalled(context, distro)) {
                    withContext(Dispatchers.Main) {
                        logs.add(LogLine("[ERROR] Linux 运行环境未安装，请先在「配置中心」完成安装", TerminalError))
                    }
                    viewModel.updateDepStatus(dep, DepStatus.Failed)
                    return@withContext
                }

                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[EXEC] 正在启动 proot 容器...", TerminalInfo))
                }

                process = ProotManager.buildProotProcess(context, distro, cmd)
                    .redirectErrorStream(true)
                    .start()

                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[INFO] 进程已启动，等待包管理器输出 ➔", TerminalSuccess))
                }

                reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val finalLine = line!!
                    val logColor = when {
                        finalLine.contains("error",     ignoreCase = true) ||
                        finalLine.contains("failed",    ignoreCase = true) -> TerminalError
                        finalLine.contains("warning",   ignoreCase = true) ||
                        finalLine.contains("warn",      ignoreCase = true) -> TerminalWarn
                        finalLine.contains("success",   ignoreCase = true) ||
                        finalLine.contains("installed", ignoreCase = true) ||
                        finalLine.contains("unpacking", ignoreCase = true) -> TerminalSuccess
                        else -> TerminalInfo
                    }
                    withContext(Dispatchers.Main) { logs.add(LogLine(finalLine, logColor)) }
                }

                exitCode = process.waitFor()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logs.add(LogLine("[ERROR] 安装异常: ${e.message}", TerminalError))
                }
            } finally {
                withContext(NonCancellable) {
                    try { reader?.close(); process?.destroy() } catch (_: Exception) {}

                    val finalStatus = if (exitCode == 0) DepStatus.Installed else DepStatus.Failed
                    viewModel.updateDepStatus(dep, finalStatus)

                    withContext(Dispatchers.Main) {
                        isRunning = false
                        logs.add(LogLine("─".repeat(48), colors.outlineVariant))
                        logs.add(
                            LogLine(
                                if (exitCode == 0) "[完成] ${dep.name} 安装成功 ✓"
                                else "[失败] ${dep.name} 安装失败，退出码: $exitCode",
                                if (exitCode == 0) TerminalSuccess else TerminalError
                            )
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

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
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = colors.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint     = colors.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text       = "安装 ${dep.name}",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = colors.onSurface
                        )
                        Text(
                            text  = "${dep.type.name} · ${dep.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isRunning) TerminalWarn.copy(alpha = 0.15f)
                                else if (exitCode == 0) TerminalSuccess.copy(alpha = 0.15f)
                                else TerminalError.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = null,
                                tint     = if (isRunning) TerminalWarn
                                           else if (exitCode == 0) TerminalSuccess else TerminalError,
                                modifier = Modifier.size(7.dp)
                            )
                            Text(
                                text = when {
                                    isRunning    -> "INSTALLING"
                                    exitCode == 0 -> "SUCCESS"
                                    else          -> "FAILED"
                                },
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = if (isRunning) TerminalWarn
                                             else if (exitCode == 0) TerminalSuccess else TerminalError
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
