package com.scripthub.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─── 数据模型 ────────────────────────────────────────────────

data class FileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList()
)

private fun buildFileTree(folderName: String): List<FileNode> {
    val root = File(FileHelper.scriptsDir, folderName)
    return buildNodes(root, "")
}

private fun buildNodes(dir: File, prefix: String): List<FileNode> =
    dir.listFiles()
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        ?.map { file ->
            val rel = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
            FileNode(
                name        = file.name,
                relativePath = rel,
                isDirectory  = file.isDirectory,
                children     = if (file.isDirectory) buildNodes(file, rel) else emptyList()
            )
        } ?: emptyList()

// 展开状态下的扁平化可见节点
private data class VisibleNode(val node: FileNode, val depth: Int)

private fun computeVisible(
    nodes: List<FileNode>,
    expandedPaths: Set<String>,
    depth: Int = 0
): List<VisibleNode> {
    val result = mutableListOf<VisibleNode>()
    for (node in nodes) {
        result.add(VisibleNode(node, depth))
        if (node.isDirectory && node.relativePath in expandedPaths) {
            result.addAll(computeVisible(node.children, expandedPaths, depth + 1))
        }
    }
    return result
}

// ─── 对话框动作 ──────────────────────────────────────────────

private sealed interface DialogAction {
    data class NewFile(val parentPath: String)   : DialogAction
    data class NewFolder(val parentPath: String) : DialogAction
    data class Rename(val node: FileNode)        : DialogAction
    data class Delete(val node: FileNode)        : DialogAction
}

// ─── 工具函数 ────────────────────────────────────────────────

private fun String.isExecutable() =
    endsWith(".py", true) || endsWith(".js", true) || endsWith(".sh", true)

private fun fileIcon(name: String): ImageVector = when {
    name.endsWith(".py", true) ||
    name.endsWith(".js", true) ||
    name.endsWith(".ts", true) ||
    name.endsWith(".sh", true)  -> Icons.Default.Code
    name.endsWith(".json", true) ||
    name.endsWith(".yaml", true) ||
    name.endsWith(".yml", true)  ||
    name.endsWith(".toml", true) ||
    name.endsWith(".ini", true)  -> Icons.Default.Settings
    else                         -> Icons.Default.Description
}

// ─── 主体组件 ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderFileBrowserSheet(
    folderName: String,
    entryPoint: String,
    onDismiss: () -> Unit,
    onSelectFile: (relativePath: String) -> Unit,
    onEntryPointChanged: (newEntryPoint: String) -> Unit = {}
) {
    val c     = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // ── 树形状态 ──
    var refreshKey    by remember { mutableIntStateOf(0) }
    val tree          = remember(folderName, refreshKey) { buildFileTree(folderName) }
    val expandedPaths = remember { mutableStateSetOf<String>() }
    val visibleNodes  = remember(tree, expandedPaths.toSet()) {
        computeVisible(tree, expandedPaths)
    }

    // ── 当前入口文件（可在 Sheet 内改变）──
    var currentEntry by remember(entryPoint) { mutableStateOf(entryPoint) }

    // ── 对话框状态 ──
    var pendingAction by remember { mutableStateOf<DialogAction?>(null) }
    var dialogInput   by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor   = c.surface,
        dragHandle       = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(c.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── 标题区 ──
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier
                        .size(38.dp)
                        .background(c.primaryContainer, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, null, tint = c.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.onSurface)
                    Text(
                        text       = "入口: $currentEntry",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = c.primary.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = c.outlineVariant.copy(alpha = 0.5f))

            // ── 文件树列表 ──
            if (visibleNodes.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("工程目录为空", color = c.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier       = Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visibleNodes, key = { it.node.relativePath }) { visible ->
                        FileNodeRow(
                            node         = visible.node,
                            depth        = visible.depth,
                            isExpanded   = visible.node.relativePath in expandedPaths,
                            isEntry      = visible.node.relativePath == currentEntry,
                            onToggle     = {
                                if (visible.node.isDirectory) {
                                    if (visible.node.relativePath in expandedPaths)
                                        expandedPaths.remove(visible.node.relativePath)
                                    else
                                        expandedPaths.add(visible.node.relativePath)
                                } else {
                                    onSelectFile(visible.node.relativePath)
                                }
                            },
                            onMenuNewFile   = { pendingAction = DialogAction.NewFile(visible.node.relativePath);    dialogInput = "" },
                            onMenuNewFolder = { pendingAction = DialogAction.NewFolder(visible.node.relativePath);  dialogInput = "" },
                            onMenuSetEntry  = {
                                currentEntry = visible.node.relativePath
                                onEntryPointChanged(visible.node.relativePath)
                            },
                            onMenuRename = { pendingAction = DialogAction.Rename(visible.node); dialogInput = visible.node.name },
                            onMenuDelete = { pendingAction = DialogAction.Delete(visible.node); dialogInput = "" }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = c.outlineVariant.copy(alpha = 0.4f))

            // ── 底部操作栏（根目录创建）──
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick  = { pendingAction = DialogAction.NewFile(""); dialogInput = "" },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建文件", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick  = { pendingAction = DialogAction.NewFolder(""); dialogInput = "" },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建文件夹", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 对话框
    // ═══════════════════════════════════════════════════════════

    when (val action = pendingAction) {

        // ── 新建文件 ──
        is DialogAction.NewFile -> {
            val parentLabel = if (action.parentPath.isEmpty()) "工程根目录" else action.parentPath
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                shape  = RoundedCornerShape(24.dp),
                title  = { Text("新建文件", fontWeight = FontWeight.Bold) },
                text   = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("位置：$parentLabel", fontSize = 12.sp, color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        OutlinedTextField(
                            value         = dialogInput,
                            onValueChange = { dialogInput = it.trim() },
                            placeholder   = { Text("例如: utils.py 或 config.json") },
                            singleLine    = true,
                            shape         = RoundedCornerShape(12.dp),
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = dialogInput.isNotBlank(),
                        onClick = {
                            val rel = if (action.parentPath.isEmpty()) dialogInput else "${action.parentPath}/$dialogInput"
                            scope.launch(Dispatchers.IO) {
                                FileHelper.createFileInFolder(folderName, rel)
                                withContext(Dispatchers.Main) {
                                    refreshKey++
                                    expandedPaths.add(action.parentPath)
                                    pendingAction = null
                                }
                            }
                        }
                    ) { Text("创建") }
                },
                dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("取消") } }
            )
        }

        // ── 新建文件夹 ──
        is DialogAction.NewFolder -> {
            val parentLabel = if (action.parentPath.isEmpty()) "工程根目录" else action.parentPath
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                shape  = RoundedCornerShape(24.dp),
                title  = { Text("新建文件夹", fontWeight = FontWeight.Bold) },
                text   = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("位置：$parentLabel", fontSize = 12.sp, color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        OutlinedTextField(
                            value         = dialogInput,
                            onValueChange = { dialogInput = it.trim() },
                            placeholder   = { Text("例如: src 或 utils") },
                            singleLine    = true,
                            shape         = RoundedCornerShape(12.dp),
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = dialogInput.isNotBlank(),
                        onClick = {
                            val rel = if (action.parentPath.isEmpty()) dialogInput else "${action.parentPath}/$dialogInput"
                            scope.launch(Dispatchers.IO) {
                                FileHelper.createSubfolderInFolder(folderName, rel)
                                withContext(Dispatchers.Main) {
                                    refreshKey++
                                    expandedPaths.add(action.parentPath)
                                    pendingAction = null
                                }
                            }
                        }
                    ) { Text("创建") }
                },
                dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("取消") } }
            )
        }

        // ── 重命名 ──
        is DialogAction.Rename -> {
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                shape  = RoundedCornerShape(24.dp),
                title  = { Text("重命名", fontWeight = FontWeight.Bold) },
                text   = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("当前名称：${action.node.name}", fontSize = 12.sp, color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        OutlinedTextField(
                            value         = dialogInput,
                            onValueChange = { dialogInput = it.trim() },
                            placeholder   = { Text("新名称") },
                            singleLine    = true,
                            shape         = RoundedCornerShape(12.dp),
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = dialogInput.isNotBlank() && dialogInput != action.node.name,
                        onClick = {
                            val parentDir    = action.node.relativePath.substringBeforeLast("/", "")
                            val newRelPath   = if (parentDir.isEmpty()) dialogInput else "$parentDir/$dialogInput"
                            scope.launch(Dispatchers.IO) {
                                val ok = FileHelper.renameInFolder(folderName, action.node.relativePath, newRelPath)
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        // 如果重命名的是当前入口文件，同步更新
                                        if (action.node.relativePath == currentEntry) {
                                            currentEntry = newRelPath
                                            onEntryPointChanged(newRelPath)
                                        }
                                        refreshKey++
                                    }
                                    pendingAction = null
                                }
                            }
                        }
                    ) { Text("确认") }
                },
                dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("取消") } }
            )
        }

        // ── 删除 ──
        is DialogAction.Delete -> {
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                shape  = RoundedCornerShape(24.dp),
                icon   = { Icon(Icons.Default.Delete, null, tint = c.error) },
                title  = { Text("删除「${action.node.name}」？", fontWeight = FontWeight.Bold) },
                text   = {
                    Text(
                        if (action.node.isDirectory)
                            "将永久删除该文件夹及其所有内容，此操作无法撤销。"
                        else
                            "将永久删除该文件，此操作无法撤销。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            FileHelper.deleteFromFolder(folderName, action.node.relativePath)
                            withContext(Dispatchers.Main) {
                                if (action.node.relativePath == currentEntry) {
                                    currentEntry = ""
                                    onEntryPointChanged("")
                                }
                                refreshKey++
                                pendingAction = null
                            }
                        }
                    }) { Text("删除", color = c.error, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("取消") } }
            )
        }

        null -> {}
    }
}

// ─── 单行节点 ─────────────────────────────────────────────────

@Composable
private fun FileNodeRow(
    node: FileNode,
    depth: Int,
    isExpanded: Boolean,
    isEntry: Boolean,
    onToggle: () -> Unit,
    onMenuNewFile: () -> Unit,
    onMenuNewFolder: () -> Unit,
    onMenuSetEntry: () -> Unit,
    onMenuRename: () -> Unit,
    onMenuDelete: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }

    val chevronAngle by animateFloatAsState(
        targetValue   = if (isExpanded) 90f else 0f,
        animationSpec = tween(150),
        label         = "chevron"
    )

    val rowBg = when {
        isEntry           -> c.primaryContainer.copy(alpha = 0.55f)
        node.isDirectory  -> c.surfaceContainer.copy(alpha = 0.35f)
        else              -> c.surfaceContainerLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(start = (12 + depth * 20).dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开箭头（仅文件夹）
        if (node.isDirectory) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint     = c.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).rotate(chevronAngle)
            )
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(20.dp))
        }

        // 图标
        Icon(
            imageVector = if (node.isDirectory) {
                Icons.Default.Folder
            } else {
                fileIcon(node.name)
            },
            contentDescription = null,
            tint     = when {
                isEntry          -> c.primary
                node.isDirectory -> c.tertiary.copy(alpha = 0.85f)
                else             -> c.onSurfaceVariant
            },
            modifier = Modifier.size(15.dp)
        )

        Spacer(Modifier.width(9.dp))

        // 文件名
        Text(
            text       = node.name,
            style      = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isEntry) FontWeight.Bold else FontWeight.Normal,
            color      = when {
                isEntry          -> c.primary
                node.isDirectory -> c.onSurface.copy(alpha = 0.85f)
                else             -> c.onSurface
            },
            modifier   = Modifier.weight(1f)
        )

        // 入口标签
        if (isEntry) {
            Surface(
                color = c.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.Star, null, tint = c.primary, modifier = Modifier.size(9.dp))
                    Text("入口", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        // ··· 菜单按钮
        Box {
            IconButton(
                onClick  = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.MoreVert, null, tint = c.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (!node.isDirectory) {
                    DropdownMenuItem(
                        text        = { Text("打开编辑") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick     = { showMenu = false; onToggle() }
                    )
                    if (node.name.isExecutable() && !isEntry) {
                        DropdownMenuItem(
                            text        = { Text("设为入口文件") },
                            leadingIcon = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick     = { showMenu = false; onMenuSetEntry() }
                        )
                    }
                }
                if (node.isDirectory) {
                    DropdownMenuItem(
                        text        = { Text("在此新建文件") },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick     = { showMenu = false; onMenuNewFile() }
                    )
                    DropdownMenuItem(
                        text        = { Text("在此新建文件夹") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick     = { showMenu = false; onMenuNewFolder() }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text        = { Text("重命名") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick     = { showMenu = false; onMenuRename() }
                )
                DropdownMenuItem(
                    text        = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick     = { showMenu = false; onMenuDelete() }
                )
            }
        }
    }
}
