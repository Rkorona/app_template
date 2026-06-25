package com.scripthub.app.ui.screens

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.scripthub.app.data.AppDatabase
import androidx.compose.material.icons.filled.History
import com.scripthub.app.data.ScriptEntity
import com.scripthub.app.ui.components.ExpressiveNavigationBar
import com.scripthub.app.ui.components.ExpressiveTopAppBar
import com.scripthub.app.ui.components.FolderFileBrowserSheet
import com.scripthub.app.ui.components.GlobalLogBottomSheet
import com.scripthub.app.utils.CronNextRunCalculator
import com.scripthub.app.utils.DistroPreference
import com.scripthub.app.utils.FileHelper
import com.scripthub.app.utils.ScriptForegroundService
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    // ─── 首次启动：检查 proot 环境是否已就绪 ───
    var needsSetup by remember { mutableStateOf(!DistroPreference.isSetupDone(context)) }

    // ─── 数据库订阅（为仪表盘提供实时数据）───
    val db = remember { AppDatabase.getDatabase(context) }
    val dbScripts  by db.scriptDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val dbTasks    by db.scheduledTaskDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentLogs by db.runLogDao().getAllRecent().collectAsStateWithLifecycle(initialValue = emptyList())

    val startOfDayMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val triggeredToday by db.runLogDao().countTodayFlow(startOfDayMs)
        .collectAsStateWithLifecycle(initialValue = 0)

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
            val nextTask = enabledTasks
                .filter { it.cronExpression.trim().split(Regex("\\s+")).size == 5 }
                .minByOrNull { CronNextRunCalculator.nextRunMillis(it.cronExpression) }
            val nextRun = if (nextTask != null) {
                val ms = CronNextRunCalculator.nextRunMillis(nextTask.cronExpression)
                NextRun(time = timeFmt.format(Date(ms)), scriptName = nextTask.name)
            } else {
                NextRun("--:--", "暂无调度任务")
            }

            val logEntries = recentLogs.take(5).map { log ->
                val status = when {
                    log.exitCode == 0  -> LogStatus.SUCCESS
                    log.exitCode == -1 -> LogStatus.RUNNING
                    else               -> LogStatus.FAILED
                }
                LogEntry(
                    time       = timeFmt.format(Date(log.startTime)),
                    scriptName = log.scriptName,
                    message    = if (log.exitCode == 0) "执行成功" else if (log.exitCode == -1) "未知退出" else "退出码 ${log.exitCode}",
                    status     = status
                )
            }

            DashboardUiState(
                totalScripts     = dbScripts.size,
                enabledTaskCount = dbTasks.count { it.isEnabled },
                triggeredToday   = triggeredToday,
                failedCount      = logEntries.count { it.status == LogStatus.FAILED },
                nextRun          = nextRun,
                recentLogs       = logEntries
            )
        }
    }

    var hasFilePermission    by remember { mutableStateOf(true) }
    var editingFileName      by remember { mutableStateOf("") }
    var editingIsFolder      by remember { mutableStateOf(false) }
    var editingEntryPoint    by remember { mutableStateOf("") }
    var showGlobalLog        by remember { mutableStateOf(false) }
    var folderBrowserTarget  by remember { mutableStateOf<ScriptEntity?>(null) }

    fun checkPermission() {
        hasFilePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (hasFilePermission) {
            scope.launch(Dispatchers.IO) { FileHelper.initDirectories() }
        }
    }

    LaunchedEffect(Unit) { checkPermission() }

    val navigateTo: (String) -> Unit = { newRoute -> currentRoute = newRoute }

    BackHandler(
        enabled = currentRoute == "ScriptEditor" ||
                  currentRoute == "EnvVars"       ||
                  currentRoute == "Dependencies"  ||
                  currentRoute == "LinuxEnv"      ||
                  currentRoute == "Terminal"
    ) {
        currentRoute = when (currentRoute) {
            "ScriptEditor" -> "ScriptManager"
            else           -> "Settings"
        }
    }

    // ─── 首次启动安装向导（覆盖整个 UI）───
    if (needsSetup) {
        Scaffold { innerPadding ->
            EnvironmentSetupScreen(
                contentPadding = innerPadding,
                onSetupComplete = { needsSetup = false }
            )
        }
        return
    }

    // ─── 存储权限拦截 UI ───
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
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "需要共享存储空间访问权限",
                    fontWeight = FontWeight.Black,
                    fontSize   = 18.sp,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "本面板采用公共存储作为脚本工作区，proot 执行引擎将通过绑定挂载访问这些文件。可在「配置中心 → 工作目录」中自定义路径。",
                    fontSize  = 13.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    shape    = RoundedCornerShape(12.dp)
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

    // ─── 正常 App 主体 ───
    val titleText = when (currentRoute) {
        "Dashboard"      -> "Script Hub"
        "ScriptManager"  -> "脚本管理"
        "ScheduledTasks" -> "定时任务"
        "Settings"       -> "配置中心"
        "EnvVars"        -> "环境变量"
        "Dependencies"   -> "依赖管理"
        "ScriptEditor"   -> "代码编辑"
        "LinuxEnv"       -> "Linux 运行环境"
        "Terminal"       -> "Shell 终端"
        else             -> "ScriptHub"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (currentRoute != "ScriptEditor") {
                ExpressiveTopAppBar(
                    titleText     = titleText,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (currentRoute == "ScheduledTasks") {
                            IconButton(onClick = { showGlobalLog = true }) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "全部日志",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentRoute != "ScriptEditor") {
                val isSubScreen = currentRoute == "EnvVars"     ||
                                  currentRoute == "Dependencies" ||
                                  currentRoute == "LinuxEnv"     ||
                                  currentRoute == "Terminal"
                ExpressiveNavigationBar(
                    currentRoute = if (isSubScreen) "Settings" else currentRoute,
                    onNavigate   = navigateTo
                )
            }
        }
    ) { innerPadding ->
        Crossfade(targetState = currentRoute, label = "Route Transition") { route ->
            when (route) {
                "Dashboard"      -> DashboardScreen(
                    state           = dashboardState,
                    contentPadding  = innerPadding,
                    onViewAllLogs   = { showGlobalLog = true },
                    onFailuresClick = { showGlobalLog = true }
                )
                "ScriptManager"  -> ScriptManagerScreen(
                    contentPadding = innerPadding,
                    onOpenDetail   = { script ->
                        if (script.isFolder) {
                            folderBrowserTarget = script
                        } else {
                            editingFileName   = script.name
                            editingIsFolder   = false
                            editingEntryPoint = script.name
                            navigateTo("ScriptEditor")
                        }
                    }
                )
                "ScheduledTasks" -> ScheduledTaskManagerScreen(contentPadding = innerPadding)
                "Settings"       -> SettingsScreen(contentPadding = innerPadding, onNavigate = navigateTo)
                "EnvVars"        -> EnvVarManagerScreen(contentPadding = innerPadding)
                "Dependencies"   -> DependencyManagerScreen(contentPadding = innerPadding)
                "LinuxEnv"       -> EnvironmentSetupScreen(
                    contentPadding  = innerPadding,
                    onSetupComplete = { navigateTo("Settings") }
                )
                "Terminal"       -> ShellTerminalScreen(contentPadding = innerPadding)
                "ScriptEditor"   -> ScriptEditorScreen(
                    fileName   = editingFileName,
                    isFolder   = editingIsFolder,
                    entryPoint = editingEntryPoint,
                    onBack     = { navigateTo("ScriptManager") }
                )
            }
        }
    }

    if (showGlobalLog) {
        GlobalLogBottomSheet(onDismiss = { showGlobalLog = false })
    }

    folderBrowserTarget?.let { folder ->
        FolderFileBrowserSheet(
            folderName          = folder.name,
            entryPoint          = folder.entryPoint,
            onDismiss           = { folderBrowserTarget = null },
            onSelectFile        = { relPath ->
                editingFileName     = folder.name
                editingIsFolder     = true
                editingEntryPoint   = relPath
                folderBrowserTarget = null
                navigateTo("ScriptEditor")
            },
            onEntryPointChanged = { newEntry ->
                scope.launch(Dispatchers.IO) {
                    db.scriptDao().updateEntryPoint(folder.name, newEntry)
                }
            }
        )
    }
}
