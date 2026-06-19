package com.example.myapplication.ui.screens

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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // 根据不同页面，给顶部栏换不同的标题
    val titleText = when (currentRoute) {
        "Dashboard" -> "仪表盘"
        "ScriptManager" -> "脚本管理"
        "ScheduledTasks" -> "定时任务"
        "Settings" -> "系统设置"
        else -> "My Application"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExpressiveTopAppBar(titleText = titleText, scrollBehavior = scrollBehavior)
        },
        bottomBar = {
            ExpressiveNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { newRoute -> currentRoute = newRoute }
            )
        }
    ) { innerPadding ->
        // 📌 核心路由逻辑：根据选中的标签，渲染对应的独立页面文件！
        when (currentRoute) {
            "Dashboard" -> DashboardScreen(contentPadding = innerPadding)
            "ScriptManager" -> ScriptManagerScreen(contentPadding = innerPadding)
            "ScheduledTasks" -> ScheduledTaskManagerScreen(contentPadding = innerPadding)
            
            "Settings" -> SettingsScreen(innerPadding = innerPadding)
            
        }
    }
}
