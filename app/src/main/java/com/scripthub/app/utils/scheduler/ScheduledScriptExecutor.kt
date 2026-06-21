package com.scripthub.app.utils.scheduler

import android.content.Context
import android.util.Log
import com.scripthub.app.data.AppDatabase
import com.scripthub.app.data.RunLogEntity
import com.scripthub.app.utils.ProotRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
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
        val db        = AppDatabase.getDatabase(context)
        val runLogDao = db.runLogDao()

        val envVars = try {
            db.envVarDao().getAll().first()
                .filter { it.isEnabled }
                .associate { it.name to it.value }
        } catch (e: Exception) {
            Log.w(TAG, "[$taskName] 读取环境变量失败: ${e.message}")
            emptyMap()
        }
        if (envVars.isNotEmpty()) {
            Log.i(TAG, "[$taskName] 注入 ${envVars.size} 个环境变量: ${envVars.keys.joinToString(", ")}")
        }

        val startTime = System.currentTimeMillis()
        val rawLines  = mutableListOf<String>()
        var exitCode  = -1
        var process:  Process?       = null
        var reader:   BufferedReader? = null

        try {
            Log.i(TAG, "[$taskName] 启动 proot 进程执行脚本: $scriptName")

            process = ProotRunner.executeScript(
                context    = context,
                scriptName = scriptName,
                isFolder   = false,
                entryPoint = "",
                scriptType = scriptType,
                envVars    = envVars
            )

            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val finalLine = line!!
                rawLines.add(finalLine)
                if (finalLine.startsWith("[SYSTEM_EXIT_CODE]:")) {
                    exitCode = finalLine.removePrefix("[SYSTEM_EXIT_CODE]:").trim().toIntOrNull() ?: -1
                }
            }
            process.waitFor()

        } catch (e: IllegalStateException) {
            Log.e(TAG, "[$taskName] proot 环境未就绪: ${e.message}")
            rawLines.add("[ERROR] ${e.message}")
            rawLines.add("[INFO] 请在「配置中心 → Linux 运行环境」完成安装")
        } catch (e: Exception) {
            Log.e(TAG, "[$taskName] 执行异常: ${e.message}")
            rawLines.add("[ERROR] 定时任务执行异常: ${e.message}")
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { process?.destroy() } catch (_: Exception) {}
        }

        val durationMs = System.currentTimeMillis() - startTime
        val logText    = rawLines
            .filter { !it.startsWith("[SYSTEM_EXIT_CODE]:") }
            .joinToString("\n")

        try {
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
            Log.i(TAG, "[$taskName] 日志写入完成，exitCode=$exitCode，耗时=${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "[$taskName] 日志入库失败: ${e.message}")
        }

        try {
            val fmt   = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            val label = if (exitCode == 0) "✅ ${fmt.format(Date(startTime))}"
                        else "❌ ${fmt.format(Date(startTime))}"
            db.scriptDao().updateLastRun(scriptName, label)
        } catch (e: Exception) {
            Log.e(TAG, "[$taskName] 更新最后运行时间失败: ${e.message}")
        }
    }
}
