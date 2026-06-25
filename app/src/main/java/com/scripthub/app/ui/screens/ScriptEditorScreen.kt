package com.scripthub.app.ui.screens

import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scripthub.app.ui.components.FolderFileBrowserSheet
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
    JSON("JSON",             "source.json"),
    PLAIN("Text",            null)
}

private fun detectLang(name: String): EditorLang = when {
    name.endsWith(".py",   true) || name.endsWith(".pyw", true)          -> EditorLang.PYTHON
    name.endsWith(".sh",   true) || name.endsWith(".bash", true)         -> EditorLang.SHELL
    name.endsWith(".js",   true) || name.endsWith(".mjs", true) ||
    name.endsWith(".cjs",  true) || name.endsWith(".ts",  true)          -> EditorLang.JAVASCRIPT
    name.endsWith(".json", true)                                          -> EditorLang.JSON
    else                                                                  -> EditorLang.PLAIN
}

// ──────────────────────────────────────────────────────────────────
// 工具栏按钮 — 重新设计
// ──────────────────────────────────────────────────────────────────

@Composable
private fun ToolbarAction(
    icon:    ImageVector,
    label:   String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(58.dp)
            .widthIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = contentColor,
            modifier           = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text       = label,
            fontSize   = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color      = contentColor,
            maxLines   = 1
        )
    }
}

@Composable
private fun RunToolbarAction(
    enabled: Boolean = true,
    isSaving: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(58.dp)
            .widthIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.primaryContainer,
            modifier = Modifier.size(width = 36.dp, height = 26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(14.dp),
                        color     = colors.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Default.PlayArrow,
                        contentDescription = "运行",
                        tint               = colors.onPrimaryContainer,
                        modifier           = Modifier.size(17.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text       = "运行",
            fontSize   = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color      = colors.primary,
            maxLines   = 1
        )
    }
}

// ──────────────────────────────────────────────────────────────────
// 代码键盘按键
// ──────────────────────────────────────────────────────────────────

@Composable
private fun CodeKey(
    label:    String,
    wide:     Boolean  = false,
    special:  Boolean  = false,
    onClick:  () -> Unit
) {
    val colors     = MaterialTheme.colorScheme
    val bgColor    = if (special) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val labelColor = if (special) colors.onSurface               else colors.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(7.dp),
        color   = bgColor,
        modifier = Modifier
            .height(34.dp)
            .then(if (wide) Modifier.width(54.dp) else Modifier.widthIn(min = 40.dp))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text       = label,
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (special) FontWeight.SemiBold else FontWeight.Medium,
                color      = labelColor
            )
        }
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
    var showFileBrowser by remember { mutableStateOf(false) }
    val editorRef      = remember { mutableStateOf<CodeEditor?>(null) }

    var lineCount   by remember { mutableIntStateOf(1) }
    var charCount   by remember { mutableIntStateOf(0) }
    var cursorLine  by remember { mutableIntStateOf(1) }
    var cursorCol   by remember { mutableIntStateOf(1) }

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
            Surface(
                color          = colors.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                Column {
                    // ── 工具栏行 ─────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        // 左组：可横向滚动，避免按钮被挤压
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ToolbarAction(Icons.Default.FolderOpen, "文件") {
                                if (isFolder) {
                                    showFileBrowser = true
                                } else {
                                    Toast.makeText(context, "单文件模式，无工作区", Toast.LENGTH_SHORT).show()
                                }
                            }
                            ToolbarAction(Icons.Default.Edit, "编辑") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                            ToolbarAction(Icons.Default.BugReport, "调试") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                            ToolbarAction(Icons.Default.Terminal, "终端") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                            ToolbarAction(Icons.Default.MoreHoriz, "其他") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // 分隔线
                        VerticalDivider(
                            modifier = Modifier.height(32.dp),
                            color    = colors.outlineVariant
                        )

                        // 右组：日志  [运行]  撤销  重做  保存
                        Row {
                            ToolbarAction(Icons.Default.Article, "日志") { showTerminal = true }

                            RunToolbarAction(
                                enabled  = isFileLoaded,
                                isSaving = isSaving
                            ) { runScript() }

                            ToolbarAction(Icons.Default.Undo, "撤销", enabled = isFileLoaded) {
                                editorRef.value?.undo()
                            }
                            ToolbarAction(Icons.Default.Redo, "重做", enabled = isFileLoaded) {
                                editorRef.value?.redo()
                            }
                            ToolbarAction(
                                Icons.Default.Save, "保存",
                                enabled = isFileLoaded && !isSaving
                            ) { saveFile() }
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    // ── 文件标签行 ───────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 12.dp)
                            .height(38.dp)
                    ) {
                        Column(modifier = Modifier.wrapContentWidth()) {
                            Text(
                                text       = displayName,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color      = colors.onSurface,
                                modifier   = Modifier.padding(top = 6.dp, bottom = 3.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .wrapContentWidth(Alignment.Start)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(colors.primary)
                            ) {
                                Text(
                                    text       = displayName,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier   = Modifier.alpha(0f)
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text       = "Ln $lineCount",
                                fontSize   = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color      = colors.onSurfaceVariant
                            )
                            Text(
                                text  = "·",
                                fontSize = 10.sp,
                                color = colors.outlineVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text       = lang.label,
                                    fontSize   = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color      = colors.primary,
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        },

        // ── BottomBar：状态栏 + 代码键盘 ─────────────────────────────
        bottomBar = {
            Surface(color = colors.surfaceContainerLow) {
                Column(modifier = Modifier.navigationBarsPadding()) {

                    // ── 状态栏 ──────────────────────────────────────
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        // 左侧：光标位置
                        Text(
                            text       = "Ln $cursorLine, Col $cursorCol",
                            fontSize   = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color      = colors.onSurfaceVariant
                        )
                        Text(
                            text     = "  ·  ",
                            fontSize = 10.sp,
                            color    = colors.outlineVariant
                        )
                        Text(
                            text       = "$charCount 字符",
                            fontSize   = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color      = colors.onSurfaceVariant
                        )

                        Spacer(Modifier.weight(1f))

                        // 右侧：语言标签
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = colors.surfaceContainerHigh
                        ) {
                            Text(
                                text       = lang.label,
                                fontSize   = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color      = colors.onSurfaceVariant,
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    // ── 第一行：常用符号（去掉 fn/ESC/↑/TAB）──────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        listOf("(", ")", "/", "=", ",", ";", "\"", "'").forEach { sym ->
                            CodeKey(sym) { editorRef.value?.typeText(sym) }
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    // ── 第二行：光标导航 + 括号符号 ──────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        CodeKey("←",  special = true) { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
                        CodeKey("↑",  special = true) { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
                        CodeKey("↓",  special = true) { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
                        CodeKey("→",  special = true) { editorRef.value?.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }

                        listOf("{", "}", "[", "]", "`", "<", ">", "-", "!").forEach { sym ->
                            CodeKey(sym) { editorRef.value?.typeText(sym) }
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
                    Text(
                        "加载中…",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            SoraEditorView(
                initialContent = initialContent,
                scopeName      = lang.scopeName,
                onEditorReady  = { editor -> editorRef.value = editor },
                onStats        = { lines, chars -> lineCount = lines; charCount = chars },
                onCursor       = { line, col -> cursorLine = line; cursorCol = col },
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

    if (showFileBrowser && isFolder) {
        FolderFileBrowserSheet(
            folderName          = fileName,
            entryPoint          = entryPoint,
            onDismiss           = { showFileBrowser = false },
            onSelectFile        = { showFileBrowser = false },
            onEntryPointChanged = {}
        )
    }
}
