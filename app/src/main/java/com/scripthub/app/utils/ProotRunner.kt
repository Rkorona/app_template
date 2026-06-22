package com.scripthub.app.utils

import android.content.Context
import android.util.Log

object ProotRunner {
    private const val TAG = "ProotRunner"

    /** 
     * 通过 proot 执行脚本，直接返回 Process 对象。
     * 调用方通过 process.inputStream 实时读取 stdout+stderr。
     */
    fun executeScript(
        context: Context,
        scriptName: String,
        isFolder: Boolean,
        entryPoint: String,
        scriptType: String,
        envVars: Map<String, String> = emptyMap()
    ): Process {
        val distro = DistroPreference.getDistro(context)

        if (!ProotManager.isProotReady(context)) {
            throw IllegalStateException("proot 引擎未就绪，请先在设置中完成 Linux 环境安装")
        }
        if (!ProotManager.isDistroInstalled(context, distro)) {
            throw IllegalStateException("${distro.displayName} 尚未安装，请在设置中完成环境初始化")
        }

        val runCmd = when (scriptType) {
            "Python" -> "python3"
            "Node.js" -> "node"
            "Shell" -> "bash"
            else -> "bash"
        }

        val targetFile = if (isFolder) "$scriptName/$entryPoint" else scriptName

        val envExports = if (envVars.isEmpty()) {
            ""
        } else {
            envVars.entries.joinToString(" && ") { (k, v) ->
                val escaped = v.replace("'", "'\\''")
                "export $k='$escaped'"
            } + " && "
        }

        val bashCommand = """
            ${envExports}cd /data/scripts && \
            ( $runCmd $targetFile 2>&1 ; echo "[SYSTEM_EXIT_CODE]:${'$'}?" )
        """.trimIndent()

        Log.d(TAG, "启动 proot 进程: $distro / $scriptName")

        return ProotManager.buildProotProcess(context, distro, bashCommand)
            .redirectErrorStream(true)
            .start()
    }
}
