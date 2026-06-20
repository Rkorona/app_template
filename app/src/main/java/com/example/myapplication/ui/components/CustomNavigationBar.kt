// app_template/app/src/main/java/com/example/myapplication/ui/components/CustomNavigationBar.kt

package com.example.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm 
import androidx.compose.material.icons.filled.Code 
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ExpressiveNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer, 
    ) {
        // 1. 仪表盘 (Q弹放大动画)
        NavigationBarItem(
            selected = currentRoute == "Dashboard",
            onClick = { onNavigate("Dashboard") },
            alwaysShowLabel = false, // 只有选中时才显示标签
            label = { Text("仪表盘") },
            icon = { AnimatedHomeIcon(selected = currentRoute == "Dashboard") }
        )

        // 2. 脚本管理 (代码括号与敲击跃动动画)
        NavigationBarItem(
            selected = currentRoute == "ScriptManager",
            onClick = { onNavigate("ScriptManager") },
            alwaysShowLabel = false,
            label = { Text("脚本") },
            icon = { AnimatedCodeIcon(selected = currentRoute == "ScriptManager") }
        )

        // 3. 定时任务 (摇摆的小闹钟)
        NavigationBarItem(
            selected = currentRoute == "ScheduledTasks",
            onClick = { onNavigate("ScheduledTasks") },
            alwaysShowLabel = false,
            label = { Text("定时") },
            icon = { AnimatedAlarmIcon(selected = currentRoute == "ScheduledTasks") }
        )

        // 4. 配置中心 (炫酷齿轮旋转一圈) -> 路由改为大写 Settings，标签改为"配置"
        NavigationBarItem(
            selected = currentRoute == "Settings",
            onClick = { onNavigate("Settings") },
            alwaysShowLabel = false,
            label = { Text("配置") },
            icon = { AnimatedSettingsIcon(selected = currentRoute == "Settings") }
        )
    }
}

// ==================== 🛠️ 图标微动效组件库 ====================

/**
 * ✨ 闹钟图标：被选中时高频左右晃动模拟“闹钟打铃”，随后弹性复位
 */
@Composable
fun AnimatedAlarmIcon(selected: Boolean) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (selected) {
            rotation.snapTo(0f) 
            val duration = 50
            rotation.animateTo(18f, animationSpec = tween(durationMillis = duration))
            rotation.animateTo(-18f, animationSpec = tween(durationMillis = duration))
            rotation.animateTo(14f, animationSpec = tween(durationMillis = duration))
            rotation.animateTo(-14f, animationSpec = tween(durationMillis = duration))
            rotation.animateTo(10f, animationSpec = tween(durationMillis = duration))
            rotation.animateTo(-10f, animationSpec = tween(durationMillis = duration))
            
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Icon(
        imageVector = Icons.Filled.Alarm,
        contentDescription = "ScheduledTasks",
        modifier = Modifier.graphicsLayer(rotationZ = rotation.value)
    )
}

/**
 * ⚙️ 配置图标（原设置）：被选中时旋转一圈，并带有弹性回弹
 */
@Composable
fun AnimatedSettingsIcon(selected: Boolean) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (selected) {
            rotation.snapTo(0f) // 先重置角度
            rotation.animateTo(
                targetValue = 180f, // 齿轮转半圈/一圈视觉效果最好，180度配合Spring回弹有极佳的机械阻尼感
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Icon(
        imageVector = Icons.Filled.Settings,
        contentDescription = "Settings",
        modifier = Modifier.graphicsLayer(rotationZ = rotation.value)
    )
}

/**
 * 🏠 主页图标：被选中时先放大再缩回
 */
@Composable
fun AnimatedHomeIcon(selected: Boolean) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(1f)
            scale.animateTo(1.25f, animationSpec = tween(durationMillis = 100))
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
    }

    Icon(
        imageVector = Icons.Filled.Home,
        contentDescription = "Dashboard",
        modifier = Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value)
    )
}

/**
 * 💻 脚本代码图标：被选中时轻微放大并摇摆
 */
@Composable
fun AnimatedCodeIcon(selected: Boolean) {
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (selected) {
            launch {
                scale.snapTo(1f)
                scale.animateTo(1.28f, animationSpec = tween(durationMillis = 100))
                scale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            }
            launch {
                rotation.snapTo(0f)
                rotation.animateTo(-14f, animationSpec = tween(durationMillis = 60))
                rotation.animateTo(14f, animationSpec = tween(durationMillis = 60))
                rotation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    Icon(
        imageVector = Icons.Filled.Code,
        contentDescription = "ScriptManager",
        modifier = Modifier.graphicsLayer(
            scaleX = scale.value,
            scaleY = scale.value,
            rotationZ = rotation.value
        )
    )
}

// ==================== 🔍 预览 ====================

@Preview
@Composable
fun ExpressiveNavigationBarPreview() {
    var selectedTab by remember { mutableStateOf("Dashboard") }
    ExpressiveNavigationBar(
        currentRoute = selectedTab,
        onNavigate = { selectedTab = it }
    )
}