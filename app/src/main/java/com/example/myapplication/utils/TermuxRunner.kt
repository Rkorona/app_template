// app_template/app/src/main/java/com/example/myapplication/utils/TermuxRunner.kt
package com.example.myapplication.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object TermuxRunner {
    private const val TAG = "TermuxRunner"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService" // 已经是全限定类名，不要再拼包名
    private const val TERMUX_PACKAGE = "com.termux"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    /**
     * 自动执行脚本的主引擎
     * 失败时直接抛异常给调用方，不在这里吞掉——
     * 否则上层只能傻等 socket 超时，看不到真实失败原因。
     */
    fun executeScript(
        context: Context,
        scriptName: String,
        isFolder: Boolean,
        entryPoint: String,
        scriptType: String,
        socketPort: Int = 9090
    ) {
        // 前置检查：Termux 是否已安装，避免后面莫名其妙的 15 秒超时
        if (!isTermuxInstalled(context)) {
            throw IllegalStateException("未检测到 Termux，请先安装 Termux 并完成基础环境初始化")
        }

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

        // 极客加固指令：显式强制注入 PATH 变量（防后台环境变量丢失），并通过 nc 实时回传端口
        val fullBashCommand = """
            export PATH=/data/data/com.termux/files/usr/bin:${"$"}PATH && \
            cd /sdcard/QLPanel/scripts && \
            $runCmd $targetFile 2>&1 | nc 127.0.0.1 $socketPort
        """.trimIndent()

        val intent = Intent(ACTION_RUN_COMMAND).apply {
            // 👈 修复：TERMUX_SERVICE 本身已经是 "com.termux.app.RunCommandService"，
            // 不能再用 "$TERMUX_PACKAGE.$TERMUX_SERVICE" 拼一次包名，否则会拼出一个不存在的类
            setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", executablePath)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", fullBashCommand)) // 改为 -c
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true) // 可以放回后台静默运行了
        }

        // 👈 核心安全修改：在 Android 8.0/14+ 系统上，强制使用 startForegroundService 穿透系统封锁
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun isTermuxInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "Termux 未安装: $TERMUX_PACKAGE")
        false
    }
}
