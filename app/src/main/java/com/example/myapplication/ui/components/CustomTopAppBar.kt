package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MediumTopAppBarExample() {
    // 1. 定义滚动行为（向上滚动时 AppBar 自动折叠隐藏，向下滚动时立即出现）[span_6](start_span)[span_6](end_span)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        // 2. 将滚动行为绑定到 Scaffold 容器上[span_7](start_span)[span_7](end_span)
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    // Expressive 风格推荐使用更加柔和或反差鲜明的容器颜色
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                title = {
                    Text(
                        "Expressive Hub", // 换个酷炫的名字
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Expressive 核心：通过重字重或独特的 Typography Token 传达视觉情绪
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold 
                        )
                    )
                },
                // navigationIcon = {
                    // IconButton(onClick = { /* 返回事件 */ }) {
                        // Icon(
                            // imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            // contentDescription = "Back"
                        // )
                    // }
                // },
                // actions = {
                    // IconButton(onClick = { /* 菜单事件 */ }) {
                        // Icon(
                            // imageVector = Icons.Filled.Menu,
                            // contentDescription = "Menu"
                        // )
                    // }
                // },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        // 3. 传入修复后的内部滚动内容
        ScrollContent(innerPadding)
    }
}

/**
 * 这是一个为了展示顶部栏滚动折叠效果而补全的模拟列表
 */
@Composable
fun ScrollContent(innerPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(50) { index ->
            ListItem(
                headlineContent = { Text("练手项目列表项 #$index") },
                supportingContent = { Text("滑动看看顶部栏的折叠动画") }
            )
        }
    }
}
