package com.scripthub.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scripthub.app.ui.components.SoraEditorView
import com.scripthub.app.utils.FileHelper
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ──────────────────────────────────────────────────────────────────
// 语言枚举 & 检测
// ──────────────────────────────────────────────────────────────────

enum class EditorLang(val label: String, val scopeName: String?) {
    JAVASCRIPT("JavaScript", "source.js"),
    PYTHON("Python",         "source.python"),
    SHELL("Shell",           "source.shell"),
    PLAIN("Text",            null)
}

private fun detectLang(fileName: String): EditorLang = when {
    fileName.endsWith(".py",   true)                                     -> EditorLang.PYTHON
    fileName.endsWith(".sh",   true) || fileName.endsWith(".bash", true) -> EditorLang.SHELL
    fileName.endsWith(".js",   true) ||
    fileName.endsWith(".mjs",  true) ||
    fileName.endsWith(".cjs",  true) ||
    fileName.endsWith(".ts",   true)                                     -> EditorLang.JAVASCRIPT
    else                                                                 -> EditorLang.PLAIN
}

// ──────────────────────────────────────────────────────────────────
// 主屏幕
// ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    fileName: String,
    isFolder: Boolean,
    entryPoint: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val lang = remember(fileName, entryPoint) {
        detectLang(if (isFolder) entryPoint else fileName)
    }

    // 文件内容（异步加载后传给编辑器）
    var initialContent by remember { mutableStateOf("") }
    var isFileLoaded   by remember { mutableStateOf(false) }
    var isSaving       by remember { mutableStateOf(false) }

    // 编辑器实例引用，用于保存时读取内容
    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }

    // 状态栏统计
    var lineCount by remember { mutableIntStateOf(1) }
    var charCount by remember { mutableIntStateOf(0) }

    // 异步加载文件
    LaunchedEffect(fileName, entryPoint) {
        val text = withContext(Dispatchers.IO) {
            FileHelper.readScriptContent(fileName, isFolder, entryPoint)
        }
        initialContent = text
        lineCount      = text.count { it == '\n' } + 1
        charCount      = text.length
        isFileLoaded   = true
    }

    // 当编辑器实例创建后，若文件已加载则无需再 setText（factory 里已设置）
    // 若文件加载晚于 factory（极少情况），则在 isFileLoaded 变化时补设
    LaunchedEffect(isFileLoaded, editorRef.value) {
        if (isFileLoaded) {
            editorRef.value?.also { editor ->
                // 只在编辑器内容为空时才补设（防止覆盖已有编辑）
                if (editor.text.length == 0 && initialContent.isNotEmpty()) {
                    editor.setText(initialContent)
                }
            }
        }
    }

    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column {
                            Text(
                                text       = if (isFolder) entryPoint else fileName,
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isFolder) {
                                Text(
                                    text   = "from $fileName/",
                                    style  = MaterialTheme.typography.labelSmall,
                                    color  = colors.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Surface(
                            color = colors.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text     = lang.label,
                                style    = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color    = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            isSaving = true
                            val content = editorRef.value?.text?.toString() ?: ""
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    FileHelper.writeScriptContent(
                                        fileName, isFolder, entryPoint, content
                                    )
                                }
                                isSaving = false
                                val msg = if (success) "已保存" else "保存失败，请检查存储权限"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled  = !isSaving && isFileLoaded,
                        modifier = Modifier.padding(end = 8.dp),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color     = colors.onSecondaryContainer
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("保存", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            Surface(
                color          = colors.surfaceContainerHigh,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "$lineCount 行  ·  $charCount 字符",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text       = "UTF-8  ·  ${lang.label}",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        containerColor = colors.surfaceContainerLow
    ) { innerPadding ->
        if (!isFileLoaded) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "加载中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        } else {
            SoraEditorView(
                initialContent = initialContent,
                scopeName      = lang.scopeName,
                onEditorReady  = { editor -> editorRef.value = editor },
                onStats        = { lines, chars ->
                    lineCount = lines
                    charCount = chars
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}
