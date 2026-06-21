package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myapplication.ui.screens.DepStatus
import com.example.myapplication.ui.screens.DepType
import java.util.UUID

@Entity(tableName = "env_vars")
data class EnvVarEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: String,
    val remarks: String = "",
    val isEnabled: Boolean = true
)

@Entity(tableName = "dependencies")
data class DependencyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: DepType, // Room 需要转换器来存储枚举
    val status: DepStatus,
    val version: String = "latest"
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetScript: String,
    val cronExpression: String,
    val nextRunTime: String = "计算中...",
    val lastRunResult: String = "从未执行",
    val isEnabled: Boolean = true,
    val isSuccess: Boolean = true,
    val isRunning: Boolean = false,
    val themeColorHex: String? = null
)

@Entity(tableName = "run_logs")
data class RunLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val scriptName: String,
    val startTime: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val exitCode: Int = -1,
    val logText: String = ""
)

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,         // 单文件: "daily_check.js" | 文件夹项目: "telegram_bot"
    val type: String,         // "Python", "Node.js", "Shell", "Other"
    val trigger: String = "⚡ 手动触发",
    val lastRun: String = "从未运行",
    val isRunning: Boolean = false,
    val themeColorHex: String = "#38BDF8", // 存储 hex 颜色，如：#38BDF8
    val isFolder: Boolean,    // true = 文件夹工程项目，false = 单文件脚本
    val entryPoint: String = "", // 若是文件夹项目，指定入口文件如 "main.py"
    val dependencyStatus: String = "None" // "None", "Configured", "Installed", "Error"
)