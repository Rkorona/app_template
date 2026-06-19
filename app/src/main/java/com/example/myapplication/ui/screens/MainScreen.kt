package com.example.myapplication.ui.screens

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
    var currentRoute by remember { mutableStateOf("home") }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // 根据不同页面，给顶部栏换不同的标题
    val titleText = when (currentRoute) {
        "home" -> "Expressive Hub"
        "profile" -> "个人中心"
        "settings" -> "系统设置"
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
            "home" -> DashboardScreen(modifier = Modifier.padding(innerPadding))
            "settings" -> SettingsScreen(innerPadding = innerPadding)
            "profile" -> HomeScreen(innerPadding = innerPadding) // 暂时用同一个代替
        }
    }
}
