// app_template/app/src/main/java/com/example/myapplication/ui/screens/DependencyManagerScreen.kt
package com.scripthub.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.scripthub.app.data.DependencyEntity
import com.scripthub.app.viewmodel.ConfigViewModel
import com.scripthub.app.ui.theme.TerminalSuccess
import com.scripthub.app.ui.components.DepInstallConsoleBottomSheet
import java.util.UUID

// 枚举保留
enum class DepType { NodeJS, Python3, Linux }
enum class DepStatus { Installed, Installing, Failed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyManagerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ConfigViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(DepType.NodeJS) }
    var searchQuery by remember { mutableStateOf("") }
    val deps by viewModel.depsList.collectAsStateWithLifecycle()

    // ─── 依赖安装配置弹窗状态 ───
    var showInstaller  by remember { mutableStateOf(false) }
    var editingDep     by remember { mutableStateOf<DependencyEntity?>(null) }
    var installingDep  by remember { mutableStateOf<DependencyEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            
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

            // 搜索框
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索已配置的 ${selectedTab.name} 依赖...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        AnimatedVisibility(visible = searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "清除") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            val filteredDeps = deps.filter { it.type == selectedTab && it.name.contains(searchQuery, true) }

            if (filteredDeps.isEmpty()) {
                // 依赖项空状态
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("无匹配的 ${selectedTab.name} 依赖", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("执行脚本需要导入的依赖包可在本页统一调度安装", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDeps, key = { it.id }) { dep ->
                        DependencyCard(
                            dep = dep,
                            onEdit = {
                                editingDep = dep
                                showInstaller = true // 唤醒编辑修改弹窗
                            },
                            onDelete = { viewModel.deleteDependency(dep) }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { 
                editingDep = null // 全新安装
                showInstaller = true
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

    // ─── 依赖安装/配置弹窗 ───
    if (showInstaller) {
        DependencyInstallerSheet(
            existing    = editingDep,
            defaultType = selectedTab,
            onDismiss   = { showInstaller = false },
            onSave      = { savedDep ->
                viewModel.installDependency(savedDep)
                installingDep = savedDep
                showInstaller = false
            }
        )
    }

    // ─── 实时安装输出 Sheet ───
    installingDep?.let { dep ->
        DepInstallConsoleBottomSheet(
            dep       = dep,
            viewModel = viewModel,
            onDismiss = { installingDep = null }
        )
    }
}

@Composable
private fun DependencyCard(
    dep: DependencyEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
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

            val (text, color, icon) = when (dep.status) {
                DepStatus.Installed -> Triple("已安装", TerminalSuccess, Icons.Default.CheckCircle)
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

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多选项", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑/重新安装") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = { 
                            expanded = false
                            onEdit() // 触发编辑修改
                        }
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

// ─── 依赖包安装表单 ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DependencyInstallerSheet(
    existing: DependencyEntity?,
    defaultType: DepType,
    onDismiss: () -> Unit,
    onSave: (DependencyEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var version by remember { mutableStateOf(existing?.version ?: "latest") }
    var selectedType by remember { mutableStateOf(existing?.type ?: defaultType) }

    val isValid = name.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existing == null) "安装新依赖" else "变更依赖配置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 环境选择（分段单选按钮组合）
            Column {
                Text("运行环境 (运行时)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DepType.values().forEach { type ->
                        val isSelected = selectedType == type
                        Button(
                            onClick = { selectedType = type },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(type.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trim() },
                label = { Text("依赖包名") },
                placeholder = { Text("例如: axios 或 requests") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = version,
                onValueChange = { version = it.trim() },
                label = { Text("目标版本 (填 latest 则采用最新版)") },
                placeholder = { Text("例如: 1.2.0 或 latest") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    val saved = existing?.copy(name = name, type = selectedType, version = version, status = DepStatus.Installing)
                        ?: DependencyEntity(name = name, type = selectedType, version = version, status = DepStatus.Installing)
                    onSave(saved)
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (existing == null) "挂载安装" else "确认修改并重装", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}