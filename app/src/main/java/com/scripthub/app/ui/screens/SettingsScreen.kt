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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.utils.DistroPreference
import com.scripthub.app.utils.ProotManager

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val distro  = remember { DistroPreference.getDistro(context) }
    val isReady = remember { ProotManager.isDistroInstalled(context, distro) }

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
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onTertiaryContainer,
                    iconTint       = MaterialTheme.colorScheme.tertiary,
                    onClick        = { onNavigate("Dependencies") }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("LinuxEnv") }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (isReady) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                                CircleShape
                            ),
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
                            text       = "proot + ${distro.displayName}",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = if (isReady) "运行环境已就绪 · 点击管理或切换发行版"
                                    else "⚠ 尚未安装 · 点击立即安装",
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

        item { Spacer(Modifier.height(4.dp)) }

        item {
            Text(
                text       = "系统与扩展",
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Black,
                modifier   = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
            Card(
                shape  = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
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
