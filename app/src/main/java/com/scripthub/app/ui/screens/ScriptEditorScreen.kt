package com.scripthub.app.ui.screens

import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.ui.components.SoraEditorView
import com.scripthub.app.ui.components.TerminalConsoleBottomSheet
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

private fun detectLang(name: String): EditorLang = when {
    name.endsWith(".py",   true) || name.endsWith(".pyw", true)             -> EditorLang.PYTHON
    name.endsWith(".sh",   true) || name.endsWith(".bash", true)            -> EditorLang.SHELL
    name.endsWith(".js",   true) || name.endsWith(".mjs", true) ||
    name.endsWith(".cjs",  true) || name.endsWith(".ts",  true)             -> EditorLang.JAVASCRIPT
    else                                                                     -> EditorLang.PLAIN
}

// ──────────────────────────────────────────────────────────────────
// 工具栏按钮
// ──────────────────────────────────────────────────────────────────

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tinted: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (tinted) MaterialTheme.colorScheme.primary
                else        MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(54.dp)
            .widthIn(min = 46.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

// ──────────────────────────────────────────────────────────────────
// 代码键盘按键
// ──────────────────────────────────────────────────────────────────

@Composable
private fun CodeKey(label: String, wide: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(38.dp)
            .then(if (wide) Modifier.width(56.dp) else Modifier.widthIn(min = 44.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp)
    ) {
        Text(
            label,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color      = colors.onSurface
        )
    }
}

// ──────────────────────────────────────────────────────────────────
// 辅助：向编辑器输入文字
// ──────────────────────────────────────────────────────────────────

private fun CodeEditor.typeText(text: String) {
    val line = cursor.leftLine
    val col  = cursor.leftColumn
    this.text.insert(line, col, text)
}

private fun CodeEditor.sendKey(keyCode: Int) {
    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   keyCode))
}

// ──────────────────────────────────────────────────────────────────
// 主屏幕
// ──────────────────────────────────────────────────────────────────

@Composable
fun ScriptEditorScreen(
    fileName:   String,
    isFolder:   Boolean,
    entryPoint: String,
    onBack:     () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val lang        = remember(fileName, entryPoint) { detectLang(if (isFolder) entryPoint else fileName) }
    val displayName = if (isFolder) entryPoint else fileName

    var initialContent by remember { mutableStateOf("") }
    var isFileLoaded   by remember { mutableStateOf(false) }
    var isSaving       by remember { mutableStateOf(false) }
    var showTerminal   by remember { mutableStateOf(false) }
    val editorRef      = remember { mutableStateOf<CodeEditor?>(null) }

    var lineCount by remember { mutableIntStateOf(1) }
    var charCount by remember { mutableIntStateOf(0) }

    // ── 文件加载 ────────────────────────────────────────────────────
    LaunchedEffect(fileName, entryPoint) {
        val text = withContext(Dispatchers.IO) {
            FileHelper.readScriptContent(fileName, isFolder, entryPoint)
        }
        initialContent = text
        lineCount      = text.count { it == '\n' } + 1
        charCount      = text.length
        isFileLoaded   = true
    }

    LaunchedEffect(isFileLoaded, editorRef.value) {
        if (isFileLoaded) {
            editorRef.value?.also { e ->
                if (e.text.length == 0 && initialContent.isNotEmpty()) e.setText(initialContent)
            }
        }
    }

    // ── 动作函数 ────────────────────────────────────────────────────
    fun saveFile(silent: Boolean = false) {
        isSaving = true
        val content = editorRef.value?.text?.toString() ?: ""
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                FileHelper.writeScriptContent(fileName, isFolder, entryPoint, content)
            }
            isSaving = false
            if (!silent) Toast.makeText(context, if (ok) "已保存" else "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun runScript() {
        if (isSaving) return
        isSaving = true
        val content = editorRef.value?.text?.toString() ?: ""
        scope.launch {
            withContext(Dispatchers.IO) { FileHelper.writeScriptContent(fileName, isFolder, entryPoint, content) }
            isSaving = false
            showTerminal = true
        }
    }

    val colors = MaterialTheme.colorScheme

    Scaffold(
        // ── TopBar：工具栏 + 文件标签 ─────────────────────────────────
        topBar = {
            Surface(color = colors.surface, shadowElevation = 2.dp) {
                Column {
                    // ── 工具栏行 ─────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        // 左组：文件 编辑 调试 终端 其他
                        Row(modifier = Modifier.weight(1f)) {
                            ToolbarAction(Icons.Default.FolderOpen,  "文件") { onBack() }
                            ToolbarAction(Icons.Default.Edit,         "编辑") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                            ToolbarAction(Icons.Default.BugReport,   "调试") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                            ToolbarAction(Icons.Default.Terminal,    "终端") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                            ToolbarAction(Icons.Default.MoreHoriz,   "其他") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(36.dp))

                        // 右组：日志 运行 撤销 重做 保存
                        Row {
                            ToolbarAction(Icons.Default.Article,  "日志") { showTerminal = true }
                            ToolbarAction(
                                Icons.Default.PlayArrow, "运行",
                                enabled = isFileLoaded, tinted = true
                            ) { runScript() }
                            ToolbarAction(Icons.Default.Undo, "撤销", enabled = isFileLoaded) {
                                editorRef.value?.undo()
                            }
                            ToolbarAction(Icons.Default.Redo, "重做", enabled = isFileLoaded) {
                                editorRef.value?.redo()
                            }
                            ToolbarAction(
                                if (isSaving) Icons.Default.HourglassEmpty else Icons.Default.Save,
                                "保存",
                                enabled = isFileLoaded && !isSaving
                            ) { saveFile() }
                        }
                    }

                    HorizontalDivider()

                    // ── 文件标签行 ───────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 0.dp)
                    ) {
                        // 文件名 + 下划线（使用 TabRow 风格 inline）
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = displayName,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                modifier   = Modifier.padding(top = 7.dp, bottom = 4.dp)
                            )
                            // Primary 色下划线，宽度与文字宽度自动匹配
                            Box(
                                modifier = Modifier
                                    .wrapContentWidth(Alignment.Start)
                                    .height(2.5.dp)
                                    .background(
                                        color = colors.primary,
                                        shape = MaterialTheme.shapes.small
                                    )
                            ) {
                                // 透明文字撑开宽度
                                Text(
                                    text       = displayName,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    modifier   = Modifier.alpha(0f)
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                        }

                        // 右侧：行列信息 & 语言标签
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text  = "Ln $lineCount",
                                fontSize = 10.sp,
                                color = colors.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text  = lang.label,
                                fontSize = 10.sp,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    HorizontalDivider()
                }
            }
        },

        // ── BottomBar：代码键盘 ───────────────────────────────────────
        bottomBar = {
            Surface(color = colors.surfaceContainerLow) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider()

                    // ── 第一行：功能键 + 符号 ─────────────────────────
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CodeKey("fn", wide = true) {
                            Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                        }
                        VerticalDivider(Modifier.height(24.dp))
                        CodeKey("ESC", wide = true) {
                            val imm = context.getSystemService(InputMethodManager::class.java)
                            imm.hideSoftInputFromWindow(editorRef.value?.windowToken, 0)
                        }
                        VerticalDivider(Modifier.height(24.dp))
                        CodeKey("↑") { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
                        VerticalDivider(Modifier.height(24.dp))
                        CodeKey("TAB", wide = true) { editorRef.value?.typeText("    ") }
                        VerticalDivider(Modifier.height(24.dp))
                        listOf("(", ")", "/", "=", ",", ";", "\"", "'").forEach { sym ->
                            CodeKey(sym) { editorRef.value?.typeText(sym) }
                            VerticalDivider(Modifier.height(24.dp))
                        }
                    }

                    HorizontalDivider()

                    // ── 第二行：光标 + 括号符号 ───────────────────────
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CodeKey("⊞", wide = true) {
                            Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                        }
                        VerticalDivider(Modifier.height(24.dp))
                        CodeKey("←") { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
                        VerticalDivider(Modifier.height(24.dp))
                        CodeKey("↓") { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
                        VerticalDivider(Modifier.height(24.dp))
                        CodeKey("→") { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
                        VerticalDivider(Modifier.height(24.dp))
                        listOf("{", "}", "[", "]", "`", "<", ">", "-", "!").forEach { sym ->
                            CodeKey(sym) { editorRef.value?.typeText(sym) }
                            VerticalDivider(Modifier.height(24.dp))
                        }
                    }
                }
            }
        },

        containerColor = colors.surface

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
                    Text("加载中…", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            SoraEditorView(
                initialContent = initialContent,
                scopeName      = lang.scopeName,
                onEditorReady  = { editor -> editorRef.value = editor },
                onStats        = { lines, chars -> lineCount = lines; charCount = chars },
                modifier       = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    if (showTerminal) {
        TerminalConsoleBottomSheet(
            taskName   = fileName,
            scriptName = if (isFolder) entryPoint else fileName,
            onDismiss  = { showTerminal = false }
        )
    }
}
