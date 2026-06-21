// app_template/app/src/main/java/com/example/myapplication/ui/screens/MainScreen.kt
package com.example.myapplication.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.ui.components.ExpressiveNavigationBar
import com.example.myapplication.ui.components.ExpressiveTopAppBar
import com.example.myapplication.utils.CronNextRunCalculator
import com.example.myapplication.utils.FileHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var currentRoute by remember { mutableStateOf("Dashboard") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()

    // ─── 数据库订阅（为仪表盘提供实时数据）───
    val db = remember { AppDatabase.getDatabase(context) }
    val dbScripts  by db.scriptDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val dbTasks    by db.scheduledTaskDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentLogs by db.runLogDao().getAllRecent().collectAsStateWithLifecycle(initialValue = emptyList())

    // 每天零点的时间戳（用于统计"今日触发"）
    val startOfDayMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val triggeredToday by db.runLogDao().countTodayFlow(startOfDayMs)
        .collectAsStateWithLifecycle(initialValue = 0)

    // 每分钟 tick 一次，驱动"下次执行"时间实时更新
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            tickMs = System.currentTimeMillis()
        }
    }

    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    val dashboardState by remember(dbScripts, dbTasks, recentLogs, triggeredToday, tickMs) {
        derivedStateOf {
            val enabledTasks = dbTasks.filter { it.isEnabled }
            // 实时计算最早下次执行时间（不依赖 DB 中的旧字符串）
            val nextTask = enabledTasks
                .filter { it.cronExpression.trim().split(Regex("\\s+")).size == 5 }
                .minByOrNull { CronNextRunCalculator.nextRunMillis(it.cronExpression) }
            val nextRun = if (nextTask != null) {
                val ms = CronNextRunCalculator.nextRunMillis(nextTask.cronExpression)
                NextRun(
                    time       = timeFmt.format(Date(ms)),
                    scriptName = nextTask.name
                )
            } else {
                NextRun("--:--", "暂无调度任务")
            }

            // 将 RunLogEntity 转换为 LogEntry 列表（最近5条）
            val logEntries = recentLogs.take(5).map { log ->
                val status = when {
                    log.exitCode == 0    -> LogStatus.SUCCESS
                    log.exitCode == -1   -> LogStatus.RUNNING
                    else                 -> LogStatus.FAILED
                }
                val timeStr = timeFmt.format(Date(log.startTime))
                LogEntry(
                    time       = timeStr,
                    scriptName = log.scriptName,
                    message    = if (log.exitCode == 0) "执行成功" else if (log.exitCode == -1) "未知退出" else "退出码 ${log.exitCode}",
                    status     = status
                )
            }

            DashboardUiState(
                serviceRunning  = true,
                uptimeLabel     = "服务运行中",
                totalScripts    = dbScripts.size,
                triggeredToday  = triggeredToday,
                nextRun         = nextRun,
                recentLogs      = logEntries
            )
        }
    }

    // ─── 权限阻断状态 ───
    var hasFilePermission by remember { mutableStateOf(true) }
    
    // ─── 传递给编辑器的临时状态 ───
    var editingFileName by remember { mutableStateOf("") }
    var editingIsFolder by remember { mutableStateOf(false) }
    var editingEntryPoint by remember { mutableStateOf("") }

    // 检查并请求 MANAGE_EXTERNAL_STORAGE 权限
    fun checkPermission() {
        hasFilePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // 旧版本默认系统控制
        }
        if (hasFilePermission) {
            // 权限通过，顺便初始化物理目录结构
            
            scope.launch(Dispatchers.IO) {
                FileHelper.initDirectories()
            }
        }
    }

    LaunchedEffect(Unit) {
        checkPermission()
    }

    val navigateTo: (String) -> Unit = { newRoute ->
        currentRoute = newRoute
    }

    // 页面拦截返回逻辑
    BackHandler(enabled = currentRoute == "ScriptEditor" || currentRoute == "EnvVars" || currentRoute == "Dependencies") {
        currentRoute = if (currentRoute == "ScriptEditor") "ScriptManager" else "Settings"
    }

    // ─── 权限拦截 UI ───
    if (!hasFilePermission) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(60.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("需要共享存储空间访问权限", fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "本面板采用公共存储（/sdcard/QLPanel）作为核心工作区。我们需要此权限以便和 Termux 执行引擎同步运行依赖和脚本。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("前往设置授予权限", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { checkPermission() }) {
                    Text("我已授权，点击刷新", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // ─── 正常的 App 主体逻辑 ───
    val titleText = when (currentRoute) {
        "Dashboard" -> "仪表盘"
        "ScriptManager" -> "脚本管理"
        "ScheduledTasks" -> "定时任务"
        "Settings" -> "配置中心"
        "EnvVars" -> "环境变量"
        "Dependencies" -> "依赖管理"
        "ScriptEditor" -> "代码编辑"
        else -> "My Application"
    }

    Scaffold(
        topBar = {
            if (currentRoute != "ScriptEditor") {
                ExpressiveTopAppBar(
                    titleText = titleText,
                    scrollBehavior = scrollBehavior
                    
                )
            }
        },
        bottomBar = {
            if (currentRoute != "ScriptEditor") {
                val isSubScreen = currentRoute == "EnvVars" || currentRoute == "Dependencies"
                ExpressiveNavigationBar(
                    currentRoute = if (isSubScreen) "Settings" else currentRoute,
                    onNavigate = navigateTo
                )
            }
        }
    ) { innerPadding ->
        Crossfade(targetState = currentRoute, label = "Route Transition") { route ->
            when (route) {
                "Dashboard" -> DashboardScreen(state = dashboardState, contentPadding = innerPadding)
                "ScriptManager" -> {
                    // 我们即将在下一节彻底重构这个页面，接入物理文件系统！
                    ScriptManagerScreen(
                        contentPadding = innerPadding,
                        onOpenDetail = { script ->
                            // 拦截点击事件，路由直接切到代码编辑器！
                            editingFileName = script.name
                            editingIsFolder = script.isFolder
                            editingEntryPoint = script.entryPoint
                            navigateTo("ScriptEditor")
                        }
                    )
                }
                "ScheduledTasks" -> ScheduledTaskManagerScreen(contentPadding = innerPadding)
                "Settings" -> SettingsScreen(contentPadding = innerPadding, onNavigate = navigateTo)
                "EnvVars" -> EnvVarManagerScreen(contentPadding = innerPadding)
                "Dependencies" -> DependencyManagerScreen(contentPadding = innerPadding)
                
                // 💡 极客编辑器页面
                "ScriptEditor" -> {
                    ScriptEditorScreen(
                        fileName = editingFileName,
                        isFolder = editingIsFolder,
                        entryPoint = editingEntryPoint,
                        onBack = { navigateTo("ScriptManager") }
                    )
                }
            }
        }
    }
}