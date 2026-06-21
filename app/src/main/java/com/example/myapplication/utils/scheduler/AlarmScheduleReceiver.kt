package com.example.myapplication.utils.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId     = intent.getStringExtra(KEY_TASK_ID)     ?: return
        val taskName   = intent.getStringExtra(KEY_TASK_NAME)   ?: return
        val scriptName = intent.getStringExtra(KEY_SCRIPT_NAME) ?: return
        val cronExpr   = intent.getStringExtra(KEY_CRON_EXPR)   ?: return
        val scriptType = intent.getStringExtra(KEY_SCRIPT_TYPE) ?: "Shell"

        Log.i(TAG, "AlarmManager 触发任务[$taskName] 脚本:$scriptName")

        // 立即注册下次闹钟（续期），确保不受后续执行延迟影响
        AlarmManagerScheduler.scheduleNextAlarm(
            context    = context,
            taskId     = taskId,
            taskName   = taskName,
            scriptName = scriptName,
            cronExpr   = cronExpr,
            scriptType = scriptType
        )

        // 使用 goAsync() 延长 BroadcastReceiver 生命周期，以便异步捕获日志
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduledScriptExecutor.executeAndLog(
                    context    = context,
                    scriptName = scriptName,
                    scriptType = scriptType,
                    taskName   = taskName
                )
            } catch (e: Exception) {
                Log.e(TAG, "执行失败: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmScheduleReceiver"

        const val KEY_TASK_ID     = "taskId"
        const val KEY_TASK_NAME   = "taskName"
        const val KEY_SCRIPT_NAME = "scriptName"
        const val KEY_CRON_EXPR   = "cronExpression"
        const val KEY_SCRIPT_TYPE = "scriptType"
    }
}
