// app_template/app/src/main/java/com/example/myapplication/ui/screens/DependencyManagerScreen.kt
package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.DependencyEntity
import com.example.myapplication.viewmodel.ConfigViewModel

// 枚举保留，用于数据库类型映射
enum class DepType { NodeJS, Python3, Linux }
enum class DepStatus { Installed, Installing, Failed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ConfigViewModel = viewModel() // 👈 注入 ViewModel
) {
    var selectedTab by remember { mutableStateOf(DepType.NodeJS) }
    
    // 💡 核心魔法：直接监听数据库中的依赖流
    val deps by viewModel.depsList.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            
            // M3 1.5 新特性 SecondaryTabRow：更富有表现力的滑块标签
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                DepType.values().forEach { type ->
                    Tab(
                        selected = selectedTab == type,
                        onClick = { selectedTab = type },
                        text = { 
                            Text(
                                text = type.name, 
                                fontWeight = if (selectedTab == type) FontWeight.Black else FontWeight.Medium,
                                fontSize = 15.sp
                            ) 
                        }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 根据当前选中的 Tab 过滤数据库数据
                val filteredDeps = deps.filter { it.type == selectedTab }
                
                items(filteredDeps, key = { it.id }) { dep ->
                    DependencyCard(
                        dep = dep,
                        onDelete = { viewModel.deleteDependency(dep) } // 👈 触发数据库删除
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { 
                // 🛠 测试：往数据库插入一条新依赖，当前是在哪个Tab下，就插入哪个类型的依赖
                viewModel.addDependency(
                    DependencyEntity(
                        name = "pkg_${System.currentTimeMillis().toString().takeLast(4)}",
                        type = selectedTab,
                        status = DepStatus.Installing // 默认插入为正在安装状态
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            Icon(Icons.Default.Build, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("安装新依赖", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DependencyCard(
    dep: DependencyEntity,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(dep.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                if (dep.version != "latest") {
                    Text("v${dep.version}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // M3 Tonal Status Indicator
            val (text, color, icon) = when (dep.status) {
                DepStatus.Installed -> Triple("已安装", Color(0xFF22C55E), Icons.Default.CheckCircle)
                DepStatus.Installing -> Triple("安装中", MaterialTheme.colorScheme.tertiary, Icons.Default.Sync)
                DepStatus.Failed -> Triple("失败", MaterialTheme.colorScheme.error, Icons.Default.Error)
            }

            Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(100)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dep.status == DepStatus.Installing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
                    } else {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }

            Spacer(Modifier.width(8.dp))

            // 更多操作下拉菜单
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多选项", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("重新安装") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = { expanded = false /* TODO */ }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("移除依赖", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            expanded = false
                            onDelete() 
                        }
                    )
                }
            }
        }
    }
}