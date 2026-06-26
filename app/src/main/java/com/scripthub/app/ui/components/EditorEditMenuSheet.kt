package com.scripthub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class EditMenuItem(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val action: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorEditMenuSheet(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onFindReplace: () -> Unit,
    onCopyAll: () -> Unit,
    onCopyLine: () -> Unit,
    onCutLine: () -> Unit,
    onDeleteLine: () -> Unit,
    onClearLine: () -> Unit,
    onClearAll: () -> Unit,
    onFormat: () -> Unit,
    onToggleComment: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val items = remember(enabled) {
        listOf(
            EditMenuItem(Icons.Default.Search, "查找 / 替换", action = onFindReplace),
            EditMenuItem(Icons.Default.ContentCopy, "复制全部", action = onCopyAll),
            EditMenuItem(Icons.Default.Filter1, "复制行", action = onCopyLine),
            EditMenuItem(Icons.Default.ContentCut, "剪切行", action = onCutLine),
            EditMenuItem(Icons.Default.DeleteOutline, "删除行", action = onDeleteLine),
            EditMenuItem(Icons.Default.Clear, "清空行", action = onClearLine),
            EditMenuItem(Icons.Default.DeleteSweep, "清空", destructive = true, action = onClearAll),
            EditMenuItem(Icons.Default.AutoFixHigh, "格式化代码", action = onFormat),
            EditMenuItem(Icons.Default.Tag, "切换注释", action = onToggleComment),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(colors.primaryContainer, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "编辑",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "代码编辑与查找替换",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = colors.outlineVariant.copy(alpha = 0.5f)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(items, key = { it.label }) { item ->
                    val itemColor = when {
                        !enabled -> colors.onSurface.copy(alpha = 0.38f)
                        item.destructive -> colors.error
                        else -> colors.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled) {
                                onDismiss()
                                item.action()
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = itemColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = itemColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorFindReplaceSheet(
    onDismiss: () -> Unit,
    onFindNext: (String) -> Unit,
    onFindPrevious: (String) -> Unit,
    onReplaceOne: (find: String, replace: String) -> Unit,
    onReplaceAll: (find: String, replace: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "查找 / 替换",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = findText,
                onValueChange = { findText = it },
                label = { Text("查找") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (findText.isNotEmpty()) onFindNext(findText)
                })
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = replaceText,
                onValueChange = { replaceText = it },
                label = { Text("替换为") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { if (findText.isNotEmpty()) onFindPrevious(findText) },
                    enabled = findText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("上一个")
                }
                FilledTonalButton(
                    onClick = { if (findText.isNotEmpty()) onFindNext(findText) },
                    enabled = findText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("下一个")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onReplaceOne(findText, replaceText) },
                    enabled = findText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("替换") }

                Button(
                    onClick = { onReplaceAll(findText, replaceText) },
                    enabled = findText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("全部替换") }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
