package com.example.myapplication.utils.scheduler

import android.content.Context
import android.util.Log
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.RunLogEntity
import com.example.myapplication.utils.TermuxRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScheduledScriptExecutor {
    private const val TAG = "ScheduledScriptExecutor"

    suspend fun executeAndLog(
        context: Context,
        scriptName: String,
        scriptType: String,
        taskName: String
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val runLogDao = db.runLogDao()

        val startTime = System.currentTimeMillis()
        val rawLines = mutableListOf<String>()
        var exitCode = -1

        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(0).apply {
                reuseAddress = true
                soTimeout = 120_000
            }
            val allocatedPort = serverSocket.localPort
            Log.i(TAG, "[$taskName] 分配端口: $allocatedPort")

            TermuxRunner.executeScript(
                context    = context,
                scriptName = scriptName,
                isFolder   = false,
                entryPoint = "",
                scriptType = scriptType,
                socketPort = allocatedPort
            )

            val clientSocket = serverSocket.accept()
            val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val finalLine = line!!
                rawLines.add(finalLine)
                if (finalLine.startsWith("[SYSTEM_EXIT_CODE]:")) {
                    exitCode = finalLine.removePrefix("[SYSTEM_EXIT_CODE]:").trim().toIntOrNull() ?: -1
                }
            }
            reader.close()
            clientSocket.close()

        } catch (e: java.io.InterruptedIOException) {
            Log.w(TAG, "[$taskName] 管道连通超时: ${e.message}")
            rawLines.add("[WARN] 定时任务管道超时，请确认 Termux 后台在线")
        } catch (e: Exception) {
            Log.e(TAG, "[$taskName] 执行异常: ${e.message}")
            rawLines.add("[ERROR] 定时任务执行异常: ${e.message}")
        } finally {
            try { serverSocket?.close() } catch (_: Exception) {}
        }

        val durationMs = System.currentTimeMillis() - startTime
        val logText = rawLines
            .filter { !it.startsWith("[SYSTEM_EXIT_CODE]:") }
            .joinToString("\n")

        try {
            val logEntity = RunLogEntity(
                scriptName = scriptName,
                startTime  = startTime,
                durationMs = durationMs,
                exitCode   = exitCode,
                logText    = logText
            )
            runLogDao.insert(logEntity)
            runLogDao.pruneOldLogs(scriptName)
            Log.i(TAG, "[$taskName] 日志已写入数据库，exitCode=$exitCode，耗时=${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "[$taskName] 日志入库失败: ${e.message}")
        }

        try {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            val label = if (exitCode == 0) "✅ ${fmt.format(Date(startTime))}"
                        else "❌ ${fmt.format(Date(startTime))}"
            db.scriptDao().updateLastRun(scriptName, label)
        } catch (e: Exception) {
            Log.e(TAG, "[$taskName] 更新最后运行时间失败: ${e.message}")
        }
    }
}
