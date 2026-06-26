package com.scripthub.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.scripthub.app.ui.components.EditorEditMenuSheet
import com.scripthub.app.ui.components.EditorFindReplaceSheet
import com.scripthub.app.ui.components.FolderFileBrowserSheet
import com.scripthub.app.ui.components.MonacoEditorController
import com.scripthub.app.ui.components.MonacoEditorView
import com.scripthub.app.ui.components.TerminalConsoleBottomSheet
import com.scripthub.app.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ──────────────────────────────────────────────────────────────────
// 语言枚举 & 检测（Monaco language ID）
// ──────────────────────────────────────────────────────────────────

enum class EditorLang(val label: String, val monacoId: String) {
    JAVASCRIPT("JavaScript",  "javascript"),
    TYPESCRIPT("TypeScript",  "typescript"),
    HTML("HTML",              "html"),
    CSS("CSS",                "css"),
    SCSS("SCSS",              "scss"),
    JSON("JSON",              "json"),
    PYTHON("Python",          "python"),
    KOTLIN("Kotlin",          "kotlin"),
    JAVA("Java",              "java"),
    CPP("C++",                "cpp"),
    C("C",                    "c"),
    RUST("Rust",              "rust"),
    GO("Go",                  "go"),
    SWIFT("Swift",            "swift"),
    SHELL("Shell",            "shell"),
    LUA("Lua",                "lua"),
    RUBY("Ruby",              "ruby"),
    PHP("PHP",                "php"),
    YAML("YAML",              "yaml"),
    TOML("TOML",              "ini"),
    XML("XML",                "xml"),
    SQL("SQL",                "sql"),
    MARKDOWN("Markdown",      "markdown"),
    DOCKERFILE("Dockerfile",  "dockerfile"),
    PLAIN("Text",             "plaintext")
}

private fun detectLang(name: String): EditorLang {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "js", "mjs", "cjs"         -> EditorLang.JAVASCRIPT
        "ts", "mts", "cts"         -> EditorLang.TYPESCRIPT
        "html", "htm"              -> EditorLang.HTML
        "css"                      -> EditorLang.CSS
        "scss", "sass"             -> EditorLang.SCSS
        "json", "jsonc"            -> EditorLang.JSON
        "py", "pyw", "pyi"         -> EditorLang.PYTHON
        "kt", "kts"                -> EditorLang.KOTLIN
        "java"                     -> EditorLang.JAVA
        "cpp", "cc", "cxx", "hpp" -> EditorLang.CPP
        "c", "h"                   -> EditorLang.C
        "rs"                       -> EditorLang.RUST
        "go"                       -> EditorLang.GO
        "swift"                    -> EditorLang.SWIFT
        "sh", "bash", "zsh"        -> EditorLang.SHELL
        "lua"                      -> EditorLang.LUA
        "rb"                       -> EditorLang.RUBY
        "php"                      -> EditorLang.PHP
        "yaml", "yml"              -> EditorLang.YAML
        "toml"                     -> EditorLang.TOML
        "xml", "svg"               -> EditorLang.XML
        "sql"                      -> EditorLang.SQL
        "md", "markdown"           -> EditorLang.MARKDOWN
        "dockerfile"               -> EditorLang.DOCKERFILE
        else -> when {
            name.equals("Dockerfile", ignoreCase = true) -> EditorLang.DOCKERFILE
            name.equals("Makefile",   ignoreCase = true) -> EditorLang.SHELL
            else -> EditorLang.PLAIN
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// 工具栏按钮
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
    enabled:  Boolean = true,
    isSaving: Boolean = false,
    onClick:  () -> Unit
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
            shape    = RoundedCornerShape(10.dp),
            color    = colors.primaryContainer,
            modifier = Modifier.size(width = 36.dp, height = 26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(14.dp),
                        color       = colors.onPrimaryContainer,
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
    label:   String,
    wide:    Boolean = false,
    special: Boolean = false,
    onClick: () -> Unit
) {
    val colors     = MaterialTheme.colorScheme
    val bgColor    = if (special) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val labelColor = if (special) colors.onSurface               else colors.onSurfaceVariant

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(7.dp),
        color    = bgColor,
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

    // ── 当前活动文件状态 ──────────────────────────────────────────
    var activeFileName  by remember { mutableStateOf(fileName) }
    var activeIsFolder  by remember { mutableStateOf(isFolder) }
    var activeEntry     by remember { mutableStateOf(entryPoint) }

    val lang        = remember(activeFileName, activeEntry, activeIsFolder) {
        detectLang(if (activeIsFolder) activeEntry else activeFileName)
    }
    val displayName = if (activeIsFolder) activeEntry else activeFileName

    var initialContent  by remember { mutableStateOf("") }
    var isFileLoaded    by remember { mutableStateOf(false) }
    var isSaving        by remember { mutableStateOf(false) }
    var showTerminal    by remember { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showEditMenu    by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val controllerRef = remember { mutableStateOf<MonacoEditorController?>(null) }

    var lineCount    by remember { mutableIntStateOf(1) }
    var charCount    by remember { mutableIntStateOf(0) }
    var cursorLine   by remember { mutableIntStateOf(1) }
    var cursorCol    by remember { mutableIntStateOf(1) }
    var hasSelection by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var adjustingStart by remember { mutableStateOf(false) }

    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // 检测系统剪贴板是否有文本
    val hasClipboardText = remember(clipboard) {
        derivedStateOf {
            clipboard.hasPrimaryClip() && 
            clipboard.primaryClipDescription?.hasMimeType("text/*") == true
        }
    }

    // ── 文件加载 ──────────────────────────────────────────────────
    LaunchedEffect(activeFileName, activeEntry, activeIsFolder) {
        isFileLoaded = false
        controllerRef.value = null
        val text = withContext(Dispatchers.IO) {
            FileHelper.readScriptContent(activeFileName, activeIsFolder, activeEntry)
        }
        initialContent = text
        lineCount      = text.count { it == '\n' } + 1
        charCount      = text.length
        isFileLoaded   = true
    }

    fun copyToClipboard(text: String, toast: String = "已复制") {
        if (text.isEmpty()) {
            Toast.makeText(context, "内容为空", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("code", text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    // ── 动作函数 ────────────────────────────────────────────────────

    fun saveFile(silent: Boolean = false, onDone: (() -> Unit)? = null) {
        val controller = controllerRef.value ?: return
        isSaving = true
        controller.getContent { content ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    FileHelper.writeScriptContent(activeFileName, activeIsFolder, activeEntry, content)
                }
                isSaving = false
                if (!silent) {
                    Toast.makeText(context, if (ok) "已保存" else "保存失败", Toast.LENGTH_SHORT).show()
                }
                onDone?.invoke()
            }
        }
    }

    fun runScript() {
        if (isSaving) return
        isSaving = true
        val controller = controllerRef.value ?: run { isSaving = false; return }
        controller.getContent { content ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    FileHelper.writeScriptContent(activeFileName, activeIsFolder, activeEntry, content)
                }
                isSaving = false
                showTerminal = true
            }
        }
    }

    // 执行粘贴逻辑
    fun performPaste() {
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        if (text.isNotEmpty()) {
            controllerRef.value?.typeText(text)
            Toast.makeText(context, "已粘贴", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
        }
    }

    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.imePadding(),

        // ── TopBar ───────────────────────────────────────────────────
        topBar = {
            Surface(
                color          = colors.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ToolbarAction(Icons.Default.FolderOpen, "文件") {
                                showFileBrowser = true
                            }
                            ToolbarAction(Icons.Default.Edit, "编辑", enabled = isFileLoaded) {
                                showEditMenu = true
                            }
                            
                            ToolbarAction(Icons.Default.Terminal, "终端") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                            ToolbarAction(Icons.Default.MoreHoriz, "其他") {
                                Toast.makeText(context, "即将推出", Toast.LENGTH_SHORT).show()
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.height(32.dp),
                            color    = colors.outlineVariant
                        )

                        Row {
                            ToolbarAction(Icons.Default.Article, "日志") { showTerminal = true }

                            RunToolbarAction(
                                enabled  = isFileLoaded,
                                isSaving = isSaving
                            ) { runScript() }

                            ToolbarAction(Icons.Default.Undo, "撤销", enabled = isFileLoaded) {
                                controllerRef.value?.undo()
                            }
                            ToolbarAction(Icons.Default.Redo, "重做", enabled = isFileLoaded) {
                                controllerRef.value?.redo()
                            }
                            ToolbarAction(
                                Icons.Default.Save, "保存",
                                enabled = isFileLoaded && !isSaving
                            ) { saveFile() }
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

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

                        // Row(
                            // verticalAlignment     = Alignment.CenterVertically,
                            // horizontalArrangement = Arrangement.spacedBy(4.dp)
                        // ) {
                            // Text(
                                // text       = "Ln $lineCount",
                                // fontSize   = 10.sp,
                                // fontFamily = FontFamily.Monospace,
                                // color      = colors.onSurfaceVariant
                            // )
                            // Text(
                                // text     = "·",
                                // fontSize = 10.sp,
                                // color    = colors.outlineVariant
                            // )
                            // Surface(
                                // shape = RoundedCornerShape(4.dp),
                                // color = colors.primaryContainer.copy(alpha = 0.6f)
                            // ) {
                                // Text(
                                    // text       = lang.label,
                                    // fontSize   = 9.5.sp,
                                    // fontWeight = FontWeight.Bold,
                                    // fontFamily = FontFamily.Monospace,
                                    // color      = colors.primary,
                                    // modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                // )
                            // }
                        // }
                    }

                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        },

        // ── BottomBar ────────────────────────────────────────────────
        bottomBar = {
            Surface(color = colors.surfaceContainerLow) {
                Column(modifier = Modifier.navigationBarsPadding()) {

                    // ── 状态栏 ──────────────────────────────────────
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(horizontal = 12.dp)
                    ) {
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
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = colors.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text       = lang.label,
                                fontSize   = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color      = colors.primary,
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    // ── 符号键盘行 ──────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        // ── 光标工具栏：有选区时自动出现在最前面 ────────────────
                        if (hasSelection) {
                            TextButton(
                                onClick = { controllerRef.value?.selectAll() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier       = Modifier.height(34.dp)
                            ) { Text("全选", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                            TextButton(
                                onClick = {
                                    if (selectedText.isNotEmpty()) {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("code", selectedText))
                                    }
                                    controllerRef.value?.deleteSelection()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier       = Modifier.height(34.dp)
                            ) { Text("剪切", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                            TextButton(
                                onClick = {
                                    if (selectedText.isNotEmpty()) {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("code", selectedText))
                                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier       = Modifier.height(34.dp)
                            ) { Text("复制", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                            TextButton(
                                onClick        = { performPaste() },
                                enabled        = hasClipboardText.value,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier       = Modifier.height(34.dp)
                            ) { Text("粘贴", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                            VerticalDivider(
                                modifier = Modifier.height(22.dp).padding(horizontal = 2.dp),
                                color    = colors.outlineVariant
                            )

                            // ── 选区端点切换按钮 ────────────────────────────────
                            Surface(
                                onClick  = { adjustingStart = !adjustingStart },
                                shape    = RoundedCornerShape(7.dp),
                                color    = if (adjustingStart)
                                               colors.primaryContainer
                                           else
                                               colors.surfaceContainerHigh,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier         = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text       = if (adjustingStart) "◀调起点" else "调终点▶",
                                        fontSize   = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color      = if (adjustingStart)
                                                         colors.onPrimaryContainer
                                                     else
                                                         colors.onSurfaceVariant
                                    )
                                }
                            }

                            VerticalDivider(
                                modifier = Modifier.height(22.dp).padding(horizontal = 2.dp),
                                color    = colors.outlineVariant
                            )
                        }

                        // 方向移动键：有选区时根据 adjustingStart 决定调整哪一端
                        CodeKey("←", special = true) {
                            if (hasSelection) {
                                if (adjustingStart) controllerRef.value?.adjustSelectionStart("left")
                                else                controllerRef.value?.adjustSelectionEnd("left")
                            } else {
                                controllerRef.value?.moveCursor("left")
                            }
                        }
                        CodeKey("↑", special = true) {
                            if (hasSelection) {
                                if (adjustingStart) controllerRef.value?.adjustSelectionStart("up")
                                else                controllerRef.value?.adjustSelectionEnd("up")
                            } else {
                                controllerRef.value?.moveCursor("up")
                            }
                        }
                        CodeKey("↓", special = true) {
                            if (hasSelection) {
                                if (adjustingStart) controllerRef.value?.adjustSelectionStart("down")
                                else                controllerRef.value?.adjustSelectionEnd("down")
                            } else {
                                controllerRef.value?.moveCursor("down")
                            }
                        }
                        CodeKey("→", special = true) {
                            if (hasSelection) {
                                if (adjustingStart) controllerRef.value?.adjustSelectionStart("right")
                                else                controllerRef.value?.adjustSelectionEnd("right")
                            } else {
                                controllerRef.value?.moveCursor("right")
                            }
                        }

                        // 常用符号
                        listOf("(", ")", "{", "}", "[", "]", "/", "=", ",", ";", "\"", "'", "<", ">", "`", "-", "!").forEach { sym ->
                            CodeKey(sym) {
                                controllerRef.value?.typeText(sym)
                            }
                        }
                    }
                }
            }
        },

        containerColor = colors.surface

    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isFileLoaded) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
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
                MonacoEditorView(
                    initialContent     = initialContent,
                    language           = lang.monacoId,
                    onEditorReady      = { controller -> controllerRef.value = controller },
                    onStats            = { lines, chars -> lineCount = lines; charCount = chars },
                    onCursor           = { line, col -> cursorLine = line; cursorCol = col },
                    onSelectionChanged = { has, text ->
                        hasSelection = has
                        selectedText = text
                        if (!has) adjustingStart = false
                    },
                    modifier           = Modifier.fillMaxSize()
                )
            }

        }
    }

    if (showTerminal) {
        TerminalConsoleBottomSheet(
            taskName   = activeFileName,
            scriptName = if (activeIsFolder) activeEntry else activeFileName,
            onDismiss  = { showTerminal = false }
        )
    }

    if (showFileBrowser) {
        FolderFileBrowserSheet(
            folderName          = "",
            entryPoint          = if (activeIsFolder) "" else activeFileName,
            onDismiss           = { showFileBrowser = false },
            onSelectFile        = { relPath ->
                if (!relPath.contains("/")) {
                    activeFileName = relPath
                    activeIsFolder = false
                    activeEntry    = relPath
                } else {
                    val projectFolder = relPath.substringBefore("/")
                    val fileInProject = relPath.substringAfter("/")
                    activeFileName = projectFolder
                    activeIsFolder = true
                    activeEntry    = fileInProject
                }
                showFileBrowser = false
            },
            onEntryPointChanged = {}
        )
    }

    if (showEditMenu) {
        val controller = controllerRef.value
        EditorEditMenuSheet(
            enabled = isFileLoaded && controller != null,
            onDismiss = { showEditMenu = false },
            onFindReplace = { showFindReplace = true },
            onCopyAll = {
                controller?.getContent { copyToClipboard(it, "已复制全部内容") }
            },
            onCopyLine = {
                controller?.getCurrentLine { copyToClipboard(it, "已复制当前行") }
            },
            onCutLine = {
                controller?.cutCurrentLine { copyToClipboard(it, "已剪切当前行") }
            },
            onDeleteLine = {
                controller?.deleteCurrentLine()
                Toast.makeText(context, "已删除当前行", Toast.LENGTH_SHORT).show()
            },
            onClearLine = {
                controller?.clearCurrentLine()
                Toast.makeText(context, "已清空当前行", Toast.LENGTH_SHORT).show()
            },
            onClearAll = { showClearAllConfirm = true },
            onFormat = {
                controller?.formatDocument()
                Toast.makeText(context, "已执行格式化", Toast.LENGTH_SHORT).show()
            },
            onToggleComment = {
                controller?.toggleComment()
            }
        )
    }

    if (showFindReplace) {
        val controller = controllerRef.value
        EditorFindReplaceSheet(
            onDismiss = { showFindReplace = false },
            onFindNext = { query ->
                controller?.findNext(query) { found ->
                    if (!found) Toast.makeText(context, "未找到", Toast.LENGTH_SHORT).show()
                }
            },
            onFindPrevious = { query ->
                controller?.findPrevious(query) { found ->
                    if (!found) Toast.makeText(context, "未找到", Toast.LENGTH_SHORT).show()
                }
            },
            onReplaceOne = { find, replace ->
                controller?.replaceOne(find, replace) { count ->
                    Toast.makeText(
                        context,
                        if (count > 0) "已替换 1 处" else "未找到匹配项",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onReplaceAll = { find, replace ->
                controller?.replaceAll(find, replace) { count ->
                    Toast.makeText(
                        context,
                        if (count > 0) "已替换 $count 处" else "未找到匹配项",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清空文件") },
            text = { Text("确定清空当前文件的全部代码？此操作可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    controllerRef.value?.clearAll()
                    showClearAllConfirm = false
                    Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("取消") }
            }
        )
    }
}