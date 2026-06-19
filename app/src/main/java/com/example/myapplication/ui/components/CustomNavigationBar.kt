package com.example.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
        // Expressive 风格推荐使用较矮且色彩柔和的表面容器色
       // containerColor = MaterialTheme.colorScheme.surfaceContainer
        containerColor = MaterialTheme.colorScheme.surfaceContainer, 
    ) {
        // 1. 主页 (Q弹放大动画)
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            alwaysShowLabel = false, // 📌 核心需求1：只有选中时才显示标签
            label = { Text("主页") },
            icon = { AnimatedHomeIcon(selected = currentRoute == "home") }
        )

        // 2. 我的 (向上顶一下的果冻动画)
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = { onNavigate("profile") },
            alwaysShowLabel = false,
            label = { Text("我的") },
            icon = { AnimatedProfileIcon(selected = currentRoute == "profile") }
        )

        // 3. 设置 (📌 核心需求2：炫酷齿轮旋转一圈)
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick = { onNavigate("settings") },
            alwaysShowLabel = false,
            label = { Text("设置") },
            icon = { AnimatedSettingsIcon(selected = currentRoute == "settings") }
        )
    }
}

// ==================== 🛠️ 图标微动效组件库 ====================

/**
 * 设置图标：被选中时旋转一圈，并带有弹性回弹
 */
@Composable
fun AnimatedSettingsIcon(selected: Boolean) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (selected) {
            rotation.snapTo(0f) // 先重置角度
            rotation.animateTo(
                targetValue = 360f,
                // Spring 物理动效：中等阻尼，带来极具“Q弹”感的旋转
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Icon(
        imageVector = Icons.Filled.Settings,
        contentDescription = "Settings",
        // 通过 graphicsLayer 动态改变旋转角度
        modifier = Modifier.graphicsLayer(rotationZ = rotation.value)
    )
}

/**
 * 主页图标：被选中时先放大再缩回（类似 Play 商店的呼吸感）
 */
@Composable
fun AnimatedHomeIcon(selected: Boolean) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(1f)
            // 先快速放大到 1.25 倍
            scale.animateTo(1.25f, animationSpec = tween(durationMillis = 100))
            // 再用弹性动效温柔地回弹到 1.0 倍
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
    }

    Icon(
        imageVector = Icons.Filled.Home,
        contentDescription = "Home",
        modifier = Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value)
    )
}

/**
 * 个人图标：被选中时向上弹跳一下
 */
@Composable
fun AnimatedProfileIcon(selected: Boolean) {
    val translationY = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (selected) {
            translationY.snapTo(0f)
            // 向上移动 8 像素
            translationY.animateTo(-8f, animationSpec = tween(durationMillis = 100))
            // 落下并带有一点果冻回弹
            translationY.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
    }

    Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = "Profile",
        modifier = Modifier.graphicsLayer(translationY = translationY.value)
    )
}

// ==================== 🔍 预览 ====================

@Preview
@Composable
fun ExpressiveNavigationBarPreview() {
    var selectedTab by remember { mutableStateOf("home") }
    ExpressiveNavigationBar(
        currentRoute = selectedTab,
        onNavigate = { selectedTab = it }
    )
}
