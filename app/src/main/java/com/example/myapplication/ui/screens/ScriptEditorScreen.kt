package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ──────────────────────────────────────────────────────────────────
// 语法高亮辅助函数
// ──────────────────────────────────────────────────────────────────

private val PYTHON_KEYWORDS = setOf(
    "def", "class", "import", "from", "return", "if", "elif", "else",
    "for", "while", "in", "not", "and", "or", "is", "None", "True", "False",
    "try", "except", "finally", "with", "as", "pass", "break", "continue",
    "lambda", "yield", "async", "await", "raise", "del", "global", "nonlocal",
    "print", "len", "range", "type", "str", "int", "float", "list", "dict", "set"
)

private val SHELL_KEYWORDS = setOf(
    "echo", "if", "then", "else", "fi", "for", "do", "done", "while",
    "case", "esac", "function", "return", "exit", "export", "source",
    "cd", "ls", "mkdir", "rm", "cp", "mv", "grep", "awk", "sed", "cat",
    "chmod", "chown", "sudo", "apt", "pip", "npm", "node"
)

private val JS_KEYWORDS = setOf(
    "const", "let", "var", "function", "return", "if", "else", "for",
    "while", "class", "import", "export", "from", "async", "await",
    "new", "this", "typeof", "instanceof", "null", "undefined", "true", "false",
    "try", "catch", "finally", "throw", "switch", "case", "break", "continue",
    "console", "require", "module"
)

enum class EditorLang(val label: String, val ext: String) {
    PYTHON("Python", ".py"),
    SHELL("Shell", ".sh"),
    JAVASCRIPT("JavaScript", ".js"),
    TYPESCRIPT("TypeScript", ".ts"),
    PLAIN("Text", "")
}

private fun detectLang(fileName: String): EditorLang = when {
    fileName.endsWith(".py", true) -> EditorLang.PYTHON
    fileName.endsWith(".sh", true) -> EditorLang.SHELL
    fileName.endsWith(".js", true) -> EditorLang.JAVASCRIPT
    fileName.endsWith(".ts", true) -> EditorLang.TYPESCRIPT
    else -> EditorLang.PLAIN
}

private fun syntaxHighlight(
    text: String,
    lang: EditorLang,
    keywordColor: androidx.compose.ui.graphics.Color,
    stringColor: androidx.compose.ui.graphics.Color,
    commentColor: androidx.compose.ui.graphics.Color,
    numberColor: androidx.compose.ui.graphics.Color,
    defaultColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    val keywords = when (lang) {
        EditorLang.PYTHON -> PYTHON_KEYWORDS
        EditorLang.SHELL -> SHELL_KEYWORDS
        EditorLang.JAVASCRIPT, EditorLang.TYPESCRIPT -> JS_KEYWORDS
        EditorLang.PLAIN -> emptySet()
    }
    val commentPrefix = when (lang) {
        EditorLang.SHELL -> "#"
        EditorLang.PYTHON -> "#"
        EditorLang.JAVASCRIPT, EditorLang.TYPESCRIPT -> "//"
        EditorLang.PLAIN -> ""
    }

    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            var i = 0
            while (i < line.length) {
                // 注释
                if (commentPrefix.isNotEmpty() && line.startsWith(commentPrefix, i)) {
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                        append(line.substring(i))
                    }
                    i = line.length
                    continue
                }
                // 字符串 " 或 '
                if (line[i] == '"' || line[i] == '\'') {
                    val quote = line[i]
                    var j = i + 1
                    while (j < line.length && line[j] != quote) {
                        if (line[j] == '\\') j++
                        j++
                    }
                    val end = minOf(j + 1, line.length)
                    withStyle(SpanStyle(color = stringColor)) {
                        append(line.substring(i, end))
                    }
                    i = end
                    continue
                }
                // 数字
                if (line[i].isDigit()) {
                    var j = i
                    while (j < line.length && (line[j].isDigit() || line[j] == '.')) j++
                    withStyle(SpanStyle(color = numberColor)) {
                        append(line.substring(i, j))
                    }
                    i = j
                    continue
                }
                // 单词（关键字检测）
                if (line[i].isLetter() || line[i] == '_') {
                    var j = i
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                    val word = line.substring(i, j)
                    if (word in keywords) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(word)
                        }
                    }
                    i = j
                    continue
                }
                withStyle(SpanStyle(color = defaultColor)) { append(line[i]) }
                i++
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// 主编辑器 Screen
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
    var codeText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val lang = remember(fileName, entryPoint) { detectLang(if (isFolder) entryPoint else fileName) }

    LaunchedEffect(fileName) {
        withContext(Dispatchers.IO) {
            codeText = FileHelper.readScriptContent(fileName, isFolder, entryPoint)
        }
    }

    val colors = MaterialTheme.colorScheme
    val editorBg = colors.surfaceContainerLow
    val codeBg = colors.surfaceContainer
    val gutterBg = colors.surfaceContainerHigh
    val lineNumColor = colors.onSurfaceVariant.copy(alpha = 0.45f)
    val dividerColor = colors.outlineVariant.copy(alpha = 0.5f)
    val keywordColor = colors.primary
    val stringColor = colors.tertiary
    val commentColor = colors.onSurfaceVariant.copy(alpha = 0.6f)
    val numberColor = colors.secondary
    val codeDefaultColor = colors.onSurface

    val lineCount = codeText.count { it == '\n' } + 1
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(
                                text = if (isFolder) entryPoint else fileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isFolder) {
                                Text(
                                    text = "from $fileName/",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Surface(
                            color = colors.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = lang.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            isSaving = true
                            val success = FileHelper.writeScriptContent(fileName, isFolder, entryPoint, codeText)
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = editorBg
                )
            )
        },
        bottomBar = {
            Surface(
                color = gutterBg,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$lineCount 行  ·  ${codeText.length} 字符",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "UTF-8  ·  ${lang.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        containerColor = editorBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(codeBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScrollState)
                    .horizontalScroll(hScrollState)
            ) {
                // ── 行号槽 ──
                Column(
                    modifier = Modifier
                        .background(gutterBg)
                        .padding(start = 12.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    repeat(lineCount) { i ->
                        Text(
                            text = "${i + 1}",
                            style = TextStyle(
                                color = lineNumColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 22.sp
                            )
                        )
                    }
                }

                // ── 分隔线 ──
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(dividerColor)
                )

                // ── 代码输入区 ──
                val highlighted = remember(codeText, lang) {
                    syntaxHighlight(
                        text = codeText,
                        lang = lang,
                        keywordColor = keywordColor,
                        stringColor = stringColor,
                        commentColor = commentColor,
                        numberColor = numberColor,
                        defaultColor = codeDefaultColor
                    )
                }

                BasicTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    textStyle = TextStyle(
                        color = codeDefaultColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .widthIn(min = 600.dp)
                        .padding(start = 12.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    decorationBox = { innerTextField ->
                        if (codeText.isEmpty()) {
                            Text(
                                "// 开始编写你的代码...",
                                style = TextStyle(
                                    color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}
