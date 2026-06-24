package com.scripthub.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scripthub.app.utils.DistroPreference
import com.scripthub.app.utils.FileHelper
import com.scripthub.app.utils.ProotManager
import com.scripthub.app.utils.ScriptForegroundService
import com.scripthub.app.utils.ShizukuHelper
import com.scripthub.app.utils.WorkdirPreference

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val distro  = remember { DistroPreference.getDistro(context) }
    val isReady = remember { ProotManager.isDistroInstalled(context, distro) }

    var workdir           by remember { mutableStateOf(WorkdirPreference.getWorkdir(context)) }
    var showWorkdirDialog by remember { mutableStateOf(false) }

    val shizukuState by ShizukuHelper.state.collectAsStateWithLifecycle()

    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var fgServiceEnabled by remember { mutableStateOf(prefs.getBoolean("fg_service_enabled", false)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top    = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text       = "核心环境引擎",
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                modifier   = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConfigCard(
                    modifier       = Modifier.weight(1f).aspectRatio(0.9f),
                    title          = "环境变量",
                    subtitle       = "统一管理全局凭证与参数",
                    icon           = Icons.Default.VpnKey,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconTint       = MaterialTheme.colorScheme.primary,
                    onClick        = { onNavigate("EnvVars") }
                )
                ConfigCard(
                    modifier       = Modifier.weight(1f).aspectRatio(0.9f),
                    title          = "依赖管理",
                    subtitle       = "Node / Python 等运行时包",
                    icon           = Icons.Default.Extension,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconTint       = MaterialTheme.colorScheme.secondary,
                    onClick        = { onNavigate("DependencyManager") }
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        item {
            Text(
                text       = "Linux 运行环境",
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Black,
                modifier   = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
            Card(
                shape  = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                val prootContainerColor = if (isReady)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("LinuxEnv") }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(prootContainerColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint     = if (isReady) MaterialTheme.colorScheme.onTertiaryContainer
                                       else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "PRoot",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = if (isReady) "${distro.displayName} · 运行环境已就绪"
                                    else "⚠ 尚未安装 · 点击立即配置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isReady) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item {
            Card(
                shape  = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    ConfigListItem(
                        icon     = Icons.Default.Folder,
                        title    = "工作目录",
                        subtitle = workdir
                    ) { showWorkdirDialog = true }

                    if (isReady) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        ConfigListItem(
                            icon     = Icons.Default.Terminal,
                            title    = "Shell 终端",
                            subtitle = "在 ${distro.displayName} 环境中执行命令"
                        ) { onNavigate("Terminal") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        item {
            Text(
                text       = "系统与扩展",
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Black,
                modifier   = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )

            ShizukuCard(state = shizukuState)

            Spacer(Modifier.height(12.dp))

            Card(
                shape  = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    ForegroundServiceToggle(
                        enabled   = fgServiceEnabled,
                        onToggle  = { enabled ->
                            prefs.edit().putBoolean("fg_service_enabled", enabled).apply()
                            fgServiceEnabled = enabled
                            if (enabled) {
                                ScriptForegroundService.start(context, "ScriptHub 后台守护运行中")
                            } else {
                                ScriptForegroundService.stop(context)
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ConfigListItem(
                        icon     = Icons.Default.NotificationsActive,
                        title    = "推送通知",
                        subtitle = "Telegram, Server酱等渠道"
                    ) { }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ConfigListItem(
                        icon     = Icons.Default.Security,
                        title    = "面板安全",
                        subtitle = "指纹验证与访问白名单"
                    ) { }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ConfigListItem(
                        icon     = Icons.Default.Info,
                        title    = "关于引擎",
                        subtitle = "v1.0.0 · 内置 proot 执行引擎"
                    ) { }
                }
            }
        }
    }

    if (showWorkdirDialog) {
        WorkdirEditDialog(
            current  = workdir,
            onDismiss = { showWorkdirDialog = false },
            onConfirm = { newPath ->
                WorkdirPreference.setWorkdir(context, newPath)
                FileHelper.init(context)
                workdir = WorkdirPreference.getWorkdir(context)
                showWorkdirDialog = false
            }
        )
    }
}

@Composable
private fun WorkdirEditDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf(current) }
    val isValid = input.trim().startsWith("/") && input.trim().length > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Folder, contentDescription = null) },
        title = { Text("自定义工作目录", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "脚本文件存放在 <工作目录>/scripts/ 下，proot 会将该目录挂载至容器内 /data/scripts。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it },
                    label         = { Text("绝对路径") },
                    placeholder   = { Text("/sdcard/QLPanel") },
                    singleLine    = true,
                    isError       = !isValid,
                    supportingText = if (!isValid) {
                        { Text("请输入以 / 开头的绝对路径") }
                    } else null,
                    textStyle     = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp)
                )
                TextButton(
                    onClick = { input = WorkdirPreference.defaultWorkdir() },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("重置为默认 (${WorkdirPreference.defaultWorkdir()})", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input.trim()) }, enabled = isValid) {
                Text("确认", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ShizukuCard(state: ShizukuHelper.State) {
    val containerColor = when (state) {
        ShizukuHelper.State.READY                -> MaterialTheme.colorScheme.tertiaryContainer
        ShizukuHelper.State.CONNECTED_NO_PERMISSION -> MaterialTheme.colorScheme.secondaryContainer
        ShizukuHelper.State.UNAVAILABLE          -> MaterialTheme.colorScheme.surfaceContainer
    }
    val iconTint = when (state) {
        ShizukuHelper.State.READY                -> MaterialTheme.colorScheme.onTertiaryContainer
        ShizukuHelper.State.CONNECTED_NO_PERMISSION -> MaterialTheme.colorScheme.onSecondaryContainer
        ShizukuHelper.State.UNAVAILABLE          -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (state) {
        ShizukuHelper.State.READY                -> "已授权 · 可访问 Android/data 目录"
        ShizukuHelper.State.CONNECTED_NO_PERMISSION -> "已连接，但尚未授权 · 点击授权"
        ShizukuHelper.State.UNAVAILABLE          -> "未检测到 Shizuku · 请先启动 Shizuku 服务"
    }
    val clickable = state == ShizukuHelper.State.CONNECTED_NO_PERMISSION

    Card(
        shape  = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (clickable) Modifier.clickable { ShizukuHelper.requestPermission() }
                    else Modifier
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint     = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Shizuku 提权",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (clickable) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ConfigCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape    = RoundedCornerShape(32.dp),
        colors   = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = modifier.expressiveClickable(onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(contentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f), lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun ForegroundServiceToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier        = Modifier
                .size(42.dp)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                tint     = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "前台守护服务",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (enabled) "后台常驻通知，防止系统杀进程" else "关闭时系统可能回收应用进程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked         = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun ConfigListItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

private fun Modifier.expressiveClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue    = if (pressed) 0.94f else 1f,
        animationSpec  = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label          = "scale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(32.dp))
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
