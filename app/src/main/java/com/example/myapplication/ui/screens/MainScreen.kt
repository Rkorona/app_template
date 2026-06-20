// app_template/app/src/main/java/com/example/myapplication/ui/screens/MainScreen.kt
package com.example.myapplication.ui.screens

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
import com.example.myapplication.ui.components.ExpressiveNavigationBar
import com.example.myapplication.ui.components.ExpressiveTopAppBar
import com.example.myapplication.utils.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var currentRoute by remember { mutableStateOf("Dashboard") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
            FileHelper.initDirectories()
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
                "Dashboard" -> DashboardScreen(contentPadding = innerPadding)
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