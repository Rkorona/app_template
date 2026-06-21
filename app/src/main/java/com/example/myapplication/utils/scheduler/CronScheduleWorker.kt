package com.example.myapplication.utils.scheduler

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.myapplication.utils.CronNextRunCalculator
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class CronScheduleWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): Result {
        val taskId     = inputData.getString(KEY_TASK_ID)     ?: return Result.failure()
        val taskName   = inputData.getString(KEY_TASK_NAME)   ?: return Result.failure()
        val scriptName = inputData.getString(KEY_SCRIPT_NAME) ?: return Result.failure()
        val cronExpr   = inputData.getString(KEY_CRON_EXPR)   ?: return Result.failure()
        val scriptType = inputData.getString(KEY_SCRIPT_TYPE) ?: "Shell"

        Log.i(TAG, "WorkManager 触发任务[$taskName] 脚本:$scriptName")

        runBlocking {
            ScheduledScriptExecutor.executeAndLog(
                context    = applicationContext,
                scriptName = scriptName,
                scriptType = scriptType,
                taskName   = taskName
            )
        }

        scheduleNext(applicationContext, taskId, taskName, scriptName, cronExpr, scriptType)
        return Result.success()
    }

    companion object {
        private const val TAG = "CronScheduleWorker"

        const val KEY_TASK_ID     = "taskId"
        const val KEY_TASK_NAME   = "taskName"
        const val KEY_SCRIPT_NAME = "scriptName"
        const val KEY_CRON_EXPR   = "cronExpression"
        const val KEY_SCRIPT_TYPE = "scriptType"

        fun buildInputData(
            taskId: String,
            taskName: String,
            scriptName: String,
            cronExpression: String,
            scriptType: String
        ): Data = workDataOf(
            KEY_TASK_ID     to taskId,
            KEY_TASK_NAME   to taskName,
            KEY_SCRIPT_NAME to scriptName,
            KEY_CRON_EXPR   to cronExpression,
            KEY_SCRIPT_TYPE to scriptType
        )

        fun scheduleNext(
            context: Context,
            taskId: String,
            taskName: String,
            scriptName: String,
            cronExpression: String,
            scriptType: String
        ) {
            val nextMillis = CronNextRunCalculator.nextRunMillis(cronExpression)
            if (nextMillis == Long.MAX_VALUE) return

            val delayMs = (nextMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val data = buildInputData(taskId, taskName, scriptName, cronExpression, scriptType)

            val request = OneTimeWorkRequestBuilder<CronScheduleWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(taskId)
                .addTag(WorkManagerScheduler.TAG_ALL_CRON)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(taskId, androidx.work.ExistingWorkPolicy.REPLACE, request)

            Log.i(TAG, "下次执行[$taskName] 延迟 ${delayMs / 1000}s")
        }
    }
}
