// app_template/app/src/main/java/com/example/myapplication/ui/screens/MainScreen.kt
package com.example.myapplication.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.myapplication.ui.components.ExpressiveNavigationBar
import com.example.myapplication.ui.components.ExpressiveTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentRoute by remember { mutableStateOf("Dashboard") }
    // 记住上一级路由，用于从子页面返回
    var previousRoute by remember { mutableStateOf("Dashboard") } 
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // 路由导航封装
    val navigateTo: (String) -> Unit = { newRoute ->
        if (newRoute != currentRoute) {
            previousRoute = currentRoute
            currentRoute = newRoute
        }
    }

    // 根据不同页面，给顶部栏换不同的标题
    val titleText = when (currentRoute) {
        "Dashboard" -> "仪表盘"
        "ScriptManager" -> "脚本管理"
        "ScheduledTasks" -> "定时任务"
        "Settings" -> "配置中心" // 👈 升级为配置中心
        "EnvVars" -> "环境变量"
        "Dependencies" -> "依赖管理"
        else -> "My Application"
    }

    // 如果处于子页面，拦截物理返回键
    val isSubScreen = currentRoute == "EnvVars" || currentRoute == "Dependencies"
    BackHandler(enabled = isSubScreen) {
        currentRoute = "Settings" // 子页面按返回键退回配置中心
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExpressiveTopAppBar(
                titleText = titleText, 
                scrollBehavior = scrollBehavior,
                // 如果你的 TopAppBar 支持返回按钮，可以传 isSubScreen 进去显示返回箭头
            )
        },
        bottomBar = {
            // 子页面隐藏底部导航栏，或者保持高亮在"设置"上
            ExpressiveNavigationBar(
                currentRoute = if (isSubScreen) "Settings" else currentRoute,
                onNavigate = navigateTo
            )
        }
    ) { innerPadding ->
        // 📌 核心路由逻辑：使用 Crossfade 增加极简的 M3 渐变切换动效
        Crossfade(targetState = currentRoute, label = "Route Transition") { route ->
            when (route) {
                "Dashboard" -> DashboardScreen(contentPadding = innerPadding)
                "ScriptManager" -> ScriptManagerScreen(contentPadding = innerPadding)
                "ScheduledTasks" -> ScheduledTaskManagerScreen(contentPadding = innerPadding)
                "Settings" -> SettingsScreen(contentPadding = innerPadding, onNavigate = navigateTo)
                
                // 💡 新增的两个环境配置子页面
                "EnvVars" -> EnvVarManagerScreen(contentPadding = innerPadding)
                "Dependencies" -> DependencyManagerScreen(contentPadding = innerPadding)
            }
        }
    }
}