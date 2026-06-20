// app_template/app/src/main/java/com/example/myapplication/ui/screens/EnvVarManagerScreen.kt
package com.example.myapplication.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

data class EnvVar(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: String,
    val remarks: String = "",
    val isEnabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvVarManagerScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val envVars = remember {
        mutableStateListOf(
            EnvVar(name = "TG_BOT_TOKEN", value = "7868495765:AAHPKgFmtqJ...", remarks = "Telegram 机器人凭证"),
            EnvVar(name = "JD_COOKIE", value = "pt_key=app_openaa;pt_pin=jd_xxx;", remarks = "主账号"),
            EnvVar(name = "NOTIFY_SKIP_LIST", value = "api_test&daily_check", remarks = "免打扰名单", isEnabled = false)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding() + 8.dp)
        ) {
            // M3 Expressive 超大圆角搜索框
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索变量名/值/备注...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    shape = RoundedCornerShape(100), // M3 药丸形状
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(envVars.filter { it.name.contains(searchQuery, true) || it.remarks.contains(searchQuery, true) }) { env ->
                    EnvVarCard(
                        env = env,
                        onToggle = { isChecked -> envVars[envVars.indexOf(env)] = env.copy(isEnabled = isChecked) },
                        onCopy = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("EnvValue", env.value))
                            Toast.makeText(context, "已复制 ${env.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // 悬浮按钮 - 位于右下角
        ExtendedFloatingActionButton(
            onClick = { /* TODO */ },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("新建变量", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EnvVarCard(env: EnvVar, onToggle: (Boolean) -> Unit, onCopy: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).alpha(if (env.isEnabled) 1f else 0.4f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = env.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    if (env.remarks.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(env.remarks, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = env.value,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = env.isEnabled, onCheckedChange = onToggle)
        }
    }
}