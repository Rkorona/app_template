package com.scripthub.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.utils.DistroPreference
import com.scripthub.app.utils.DistroType
import com.scripthub.app.utils.ProotManager
import kotlinx.coroutines.launch

@Composable
fun EnvironmentSetupScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onSetupComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var selectedDistro by remember { mutableStateOf(DistroPreference.getDistro(context)) }
    var isInstalling   by remember { mutableStateOf(false) }
    val progress by ProotManager.progress.collectAsState()

    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top    = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
                start  = 20.dp,
                end    = 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        Surface(
            color  = colors.primaryContainer,
            shape  = RoundedCornerShape(24.dp),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint     = colors.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "配置 Linux 运行环境",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color      = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "ScriptHub 内置 proot 引擎，无需安装 Termux 即可在设备上运行完整的 Linux 发行版环境。请选择一个发行版，首次安装约需下载 100-200MB 数据。",
                style     = MaterialTheme.typography.bodyMedium,
                color     = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text       = "选择发行版",
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color      = colors.primary
            )
            DistroType.entries.forEach { distro ->
                DistroOptionCard(
                    distro     = distro,
                    isSelected = selectedDistro == distro,
                    isInstalled = ProotManager.isDistroInstalled(context, distro),
                    enabled    = !isInstalling,
                    onClick    = { selectedDistro = distro }
                )
            }
        }

        if (isInstalling || progress.done || progress.error != null) {
            InstallProgressCard(progress = progress, colors = colors)
        }

        if (progress.done) {
            Button(
                onClick = { onSetupComplete?.invoke() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("进入 ScriptHub", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        } else if (!isInstalling) {
            val alreadyInstalled = ProotManager.isDistroInstalled(context, selectedDistro)

            Button(
                onClick = {
                    isInstalling = true
                    scope.launch {
                        ProotManager.setup(context, selectedDistro)
                        isInstalling = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (alreadyInstalled) "切换到 ${selectedDistro.displayName}" else "安装 ${selectedDistro.displayName}",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
            }

            if (ProotManager.isDistroInstalled(context, selectedDistro) && onSetupComplete != null) {
                TextButton(onClick = { onSetupComplete.invoke() }, modifier = Modifier.fillMaxWidth()) {
                    Text("跳过，使用已安装环境", color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DistroOptionCard(
    distro: DistroType,
    isSelected: Boolean,
    isInstalled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.outlineVariant,
        animationSpec = tween(200), label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = tween(200), label = "bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick  = null,
            enabled  = enabled,
            colors   = RadioButtonDefaults.colors(selectedColor = colors.primary)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text       = distro.displayName,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color      = colors.onSurface
                )
                if (isInstalled) {
                    Surface(
                        color  = colors.tertiaryContainer,
                        shape  = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text     = "已安装",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = colors.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text  = when (distro) {
                    DistroType.DEBIAN -> "稳定首选 · apt 包管理 · 约 150MB"
                    DistroType.UBUNTU -> "生态丰富 · apt 包管理 · 约 200MB"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InstallProgressCard(
    progress: com.scripthub.app.utils.SetupProgress,
    colors: ColorScheme
) {
    val animatedProgress by animateFloatAsState(
        targetValue    = progress.percent / 100f,
        animationSpec  = tween(400), label = "progress"
    )

    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                progress.error != null -> colors.errorContainer
                progress.done          -> colors.tertiaryContainer
                else                   -> colors.surfaceContainer
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        progress.error != null -> "安装失败"
                        progress.done          -> "安装成功"
                        else                   -> "正在安装..."
                    },
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        progress.error != null -> colors.onErrorContainer
                        progress.done          -> colors.onTertiaryContainer
                        else                   -> colors.onSurface
                    }
                )
                Text(
                    text  = "${progress.percent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.Black
                )
            }

            if (progress.error == null) {
                LinearProgressIndicator(
                    progress    = { animatedProgress },
                    modifier    = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color       = if (progress.done) colors.tertiary else colors.primary,
                    trackColor  = colors.surfaceContainerHigh
                )
            }

            Text(
                text  = progress.error ?: progress.phase,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    progress.error != null -> colors.onErrorContainer
                    progress.done          -> colors.onTertiaryContainer
                    else                   -> colors.onSurfaceVariant
                }
            )
        }
    }
}
