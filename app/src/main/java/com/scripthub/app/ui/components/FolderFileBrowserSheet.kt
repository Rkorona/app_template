package com.scripthub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.utils.FileHelper
import java.io.File

private data class FolderEntry(
    val relativePath: String,
    val isDirectory: Boolean
)

private fun listFolderEntries(folderName: String): List<FolderEntry> {
    val root = File(FileHelper.scriptsDir, folderName)
    if (!root.exists() || !root.isDirectory) return emptyList()

    val result = mutableListOf<FolderEntry>()

    fun walk(dir: File, prefix: String) {
        val children = dir.listFiles()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: return
        for (child in children) {
            val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            result.add(FolderEntry(rel, child.isDirectory))
            if (child.isDirectory) walk(child, rel)
        }
    }

    walk(root, "")
    return result
}

private fun fileIcon(name: String) = when {
    name.endsWith(".py")                      -> Icons.Default.Code
    name.endsWith(".js") || name.endsWith(".ts") -> Icons.Default.Code
    name.endsWith(".sh")                      -> Icons.Default.Code
    name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".toml") || name.endsWith(".ini") -> Icons.Default.Settings
    name.endsWith("/") || !name.contains(".") -> Icons.Default.Folder
    else                                      -> Icons.Default.Description
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderFileBrowserSheet(
    folderName: String,
    entryPoint: String,
    onDismiss: () -> Unit,
    onSelectFile: (relativePath: String) -> Unit
) {
    val entries = remember(folderName) { listFolderEntries(folderName) }
    val c = MaterialTheme.colorScheme

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
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .background(c.primaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint     = c.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text       = folderName,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = c.onSurface
                    )
                    Text(
                        text  = "入口: $entryPoint",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color     = c.outlineVariant.copy(alpha = 0.5f)
            )

            if (entries.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "文件夹为空",
                        color = c.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start  = 12.dp,
                        end    = 12.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries, key = { it.relativePath }) { entry ->
                        val depth = entry.relativePath.count { it == '/' }
                        val displayName = entry.relativePath.substringAfterLast("/")
                        val isEntry = entry.relativePath == entryPoint

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!entry.isDirectory)
                                        Modifier.clickable { onSelectFile(entry.relativePath) }
                                    else Modifier
                                )
                                .background(
                                    color = when {
                                        isEntry          -> c.primaryContainer.copy(alpha = 0.6f)
                                        !entry.isDirectory -> c.surfaceContainerLow
                                        else             -> c.surfaceContainer.copy(alpha = 0.4f)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(
                                    start  = (16 + depth * 18).dp,
                                    end    = 12.dp,
                                    top    = 10.dp,
                                    bottom = 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector        = if (entry.isDirectory) Icons.Default.Folder else fileIcon(displayName),
                                contentDescription = null,
                                tint     = when {
                                    isEntry          -> c.primary
                                    entry.isDirectory -> c.onSurfaceVariant.copy(alpha = 0.6f)
                                    else             -> c.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text       = displayName,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isEntry) FontWeight.Bold else FontWeight.Normal,
                                color      = when {
                                    isEntry          -> c.primary
                                    entry.isDirectory -> c.onSurfaceVariant.copy(alpha = 0.7f)
                                    else             -> c.onSurface
                                },
                                modifier   = Modifier.weight(1f)
                            )
                            if (isEntry) {
                                Surface(
                                    color = c.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "入口",
                                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = c.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
