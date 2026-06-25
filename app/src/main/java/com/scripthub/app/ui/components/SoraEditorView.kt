package com.scripthub.app.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

private const val TAG = "SoraEditorView"

// ──────────────────────────────────────────────────────────────────
// TextMate 单例初始化 — 全局只执行一次
// ──────────────────────────────────────────────────────────────────

@Volatile private var tmReady = false
@Volatile private var tmFailed = false

private suspend fun ensureTextMateReady(context: Context) {
    if (tmReady || tmFailed) return
    withContext(Dispatchers.IO) {
        if (tmReady || tmFailed) return@withContext
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.applicationContext.assets)
            )

            val themeRegistry = ThemeRegistry.getInstance()
            for ((name, isDark) in listOf("sora_dark" to true, "sora_light" to false)) {
                val path = "textmate/$name.json"
                themeRegistry.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            FileProviderRegistry.getInstance().tryGetInputStream(path),
                            path,
                            null
                        ),
                        name
                    ).apply {
                        this.isDark = isDark
                    }
                )
            }

            GrammarRegistry.getInstance().loadGrammars("textmate/grammars.json")

            tmReady = true
            Log.d(TAG, "TextMate 初始化成功")
        } catch (e: Exception) {
            tmFailed = true
            Log.e(TAG, "TextMate 初始化失败", e)
        }
    }
}

private fun applyTheme(editor: CodeEditor, isDark: Boolean) {
    if (!tmReady) return
    try {
        ThemeRegistry.getInstance().setTheme(if (isDark) "sora_dark" else "sora_light")
        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    } catch (e: Exception) {
        Log.e(TAG, "主题应用失败", e)
    }
}

private fun applyLanguage(editor: CodeEditor, scopeName: String?) {
    if (!tmReady || scopeName == null) return
    try {
        editor.setEditorLanguage(TextMateLanguage.create(scopeName, true))
    } catch (e: Exception) {
        Log.e(TAG, "语言设置失败: $scopeName", e)
    }
}

// ──────────────────────────────────────────────────────────────────
// 对外暴露的 Composable
// ──────────────────────────────────────────────────────────────────

/**
 * @param initialContent  文件初始内容（在 factory 时一次性写入）
 * @param scopeName       TextMate scope，如 "source.js"，null 表示纯文本
 * @param onEditorReady   编辑器实例回调，保存时通过 editor.text.toString() 读取内容
 * @param onStats         每次内容变化时回调行数/字符数
 * @param onCursor        光标移动时回调 (行号从1开始, 列号从1开始)
 */
@Composable
fun SoraEditorView(
    initialContent: String,
    scopeName: String?,
    onEditorReady: (CodeEditor) -> Unit = {},
    onStats: (lines: Int, chars: Int) -> Unit = { _, _ -> },
    onCursor: (line: Int, col: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark   = isSystemInDarkTheme()

    var isReady by remember { mutableStateOf(tmReady) }

    LaunchedEffect(Unit) {
        ensureTextMateReady(context)
        isReady = true
    }

    if (!isReady) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val lastDark = remember { mutableStateOf<Boolean?>(null) }

    AndroidView(
        factory = { ctx ->
            CodeEditor(ctx).also { editor ->
                editor.setTextSize(13f)
                editor.tabWidth = 4
                editor.isLineNumberEnabled = true
                editor.setPinLineNumber(false)
                editor.isWordwrap = false
                editor.props.deleteEmptyLineFast = false
                editor.props.autoIndent = true

                applyTheme(editor, isDark)
                lastDark.value = isDark
                applyLanguage(editor, scopeName)

                editor.setText(initialContent)

                editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    onStats(editor.lineCount, editor.text.length)
                }
                editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                    val cursor = editor.cursor
                    onCursor(cursor.leftLine + 1, cursor.leftColumn + 1)
                }
                onEditorReady(editor)
            }
        },
        update = { editor ->
            if (lastDark.value != isDark) {
                lastDark.value = isDark
                applyTheme(editor, isDark)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
