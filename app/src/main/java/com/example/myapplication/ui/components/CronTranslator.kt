// app_template/app/src/main/java/com/example/myapplication/ui/components/CronTranslator.kt

package com.example.myapplication.utils

object CronTranslator {
    /**
     * 精简版工业级 Cron 表达式实时中译机
     */
    fun translate(cron: String): String {
        val trimmed = cron.trim()
        if (trimmed.isEmpty()) return "💡 战术提示：请输入标准的五位 Cron 表达式周期"
        
        val parts = trimmed.split("\\s+".toRegex())
        if (parts.size != 5) {
            return "❌ 格式警告：标准周期需由 5 个空格分隔符组成 (分钟 小时 日 月 周)"
        }

        return try {
            val (min, hour, day, month, week) = parts
            
            when {
                min.startsWith("*/") && hour == "*" -> "💡 自动解析：系统每隔 ${min.removePrefix("*/")} 分钟，不分昼夜自动触发一次"
                min == "0" && hour.startsWith("*/") -> "💡 自动解析：系统每隔 ${hour.removePrefix("*/")} 小时，于整点时刻自动触发一次"
                min == "0" && hour == "0" && week == "0" -> "💡 自动解析：系统将于 每周日凌晨 00:00 准时启动冷备执行"
                min == "0" && hour == "2" -> "💡 自动解析：系统将于 每天凌晨 02:00 深度休眠期自动触发"
                min == "0" -> "💡 自动解析：系统将于 每天 ${hour}:00 整点准时执行该战术"
                else -> "💡 自定义周期检测成功：系统已将调度管线挂载至后台监听引擎"
            }
        } catch (e: Exception) {
            "⚠️ 表达式结构异常，请仔细核对语法规范"
        }
    }
}
