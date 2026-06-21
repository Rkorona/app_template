// app_template/app/src/main/java/com/example/myapplication/utils/TermuxRunner.kt
package com.example.myapplication.utils

import android.content.Context
import android.content.Intent
import android.os.Build

object TermuxRunner {
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_PACKAGE = "com.termux"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    /**
     * 自动执行脚本的主引擎
     */
    fun executeScript(
        context: Context,
        scriptName: String,
        isFolder: Boolean,
        entryPoint: String,
        scriptType: String,
        socketPort: Int // 这里保持动态接收端口
    ) {
        // Termux 默认标准 Shell 路径
        val executablePath = "/data/data/com.termux/files/usr/bin/bash"
        
        // 判定编译器
        val runCmd = when (scriptType) {
            "Python" -> "python3"
            "Node.js" -> "node"
            "Shell" -> "bash"
            else -> "bash"
        }

        // 拼接目标脚本
        val targetFile = if (isFolder) "$scriptName/$entryPoint" else scriptName
        
        // 极客加固指令：显式强制注入 PATH 变量（防后台环境变量丢失），并通过 nc 实时回传动态端口
        val fullBashCommand = """
            export PATH=/data/data/com.termux/files/usr/bin:${"$"}PATH && \
            cd /sdcard/QLPanel/scripts && \
            $runCmd $targetFile 2>&1 | nc 127.0.0.1 $socketPort
        """.trimIndent()

        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.$TERMUX_SERVICE")
            putExtra("com.termux.RUN_COMMAND_PATH", executablePath)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", fullBashCommand)) // 改为 -c
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true) // 放回后台静默运行
        }

        try {
            // 👈 核心安全修改：在 Android 8.0/14+ 系统上，强制使用 startForegroundService 穿透系统封锁
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}