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
import java.util.UUID

enum class DepType { NodeJS, Python3, Linux }
enum class DepStatus { Installed, Installing, Failed }

data class DependencyItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: DepType,
    val status: DepStatus,
    val version: String = "latest"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyManagerScreen(contentPadding: PaddingValues = PaddingValues()) {
    var selectedTab by remember { mutableStateOf(DepType.NodeJS) }
    
    val deps = remember {
        mutableStateListOf(
            DependencyItem(name = "puppeteer", type = DepType.NodeJS, status = DepStatus.Installed, version = "19.7.2"),
            DependencyItem(name = "axios", type = DepType.NodeJS, status = DepStatus.Installing),
            DependencyItem(name = "requests", type = DepType.Python3, status = DepStatus.Installed),
            DependencyItem(name = "git", type = DepType.Linux, status = DepStatus.Installed)
        )
    }

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
                items(deps.filter { it.type == selectedTab }) { dep ->
                    DependencyCard(dep)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { /* TODO */ },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            Icon(Icons.Default.Build, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("安装新依赖", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DependencyCard(dep: DependencyItem) {
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
        }
    }
}