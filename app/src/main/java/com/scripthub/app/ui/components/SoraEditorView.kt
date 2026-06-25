package com.scripthub.app.ui.components

import android.content.Context
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

// ──────────────────────────────────────────────────────────────────
// TextMate 单例初始化
// ──────────────────────────────────────────────────────────────────

@Volatile private var tmReady = false

private suspend fun ensureTextMateReady(context: Context) {
    if (tmReady) return
    withContext(Dispatchers.IO) {
        if (tmReady) return@withContext
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.assets)
            )
            for ((name, path) in listOf(
                "sora_dark"  to "textmate/sora_dark.json",
                "sora_light" to "textmate/sora_light.json"
            )) {
                ThemeRegistry.getInstance().loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            context.assets.open(path), path, null
                        ),
                        name
                    )
                )
            }
            GrammarRegistry.getInstance().loadGrammars("textmate/grammars.json")
            tmReady = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun applyTheme(editor: CodeEditor, isDark: Boolean) {
    if (!tmReady) return
    try {
        ThemeRegistry.getInstance().setTheme(if (isDark) "sora_dark" else "sora_light")
        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun applyLanguage(editor: CodeEditor, scopeName: String?) {
    if (!tmReady || scopeName == null) return
    try {
        editor.setEditorLanguage(TextMateLanguage.create(scopeName, true))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ──────────────────────────────────────────────────────────────────
// 对外暴露的 Composable
// ──────────────────────────────────────────────────────────────────

/**
 * Sora Editor 封装组件
 *
 * @param initialContent  文件初始内容（只在 factory 时使用，避免反复 setText）
 * @param scopeName       TextMate 语言 scope，如 "source.js" / "source.python"，null 表示纯文本
 * @param onEditorReady   编辑器创建后回调，用于保存时读取内容
 * @param onStats         内容变化时回调行数/字符数，用于状态栏
 */
@Composable
fun SoraEditorView(
    initialContent: String,
    scopeName: String?,
    onEditorReady: (CodeEditor) -> Unit = {},
    onStats: (lines: Int, chars: Int) -> Unit = { _, _ -> },
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

    DisposableEffect(Unit) {
        onDispose { /* CodeEditor cleans up itself on detach */ }
    }

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
                    val lines = editor.lineCount
                    val chars = editor.text.length
                    onStats(lines, chars)
                }

                onEditorReady(editor)
            }
        },
        update = { editor ->
            val newDark = isDark
            if (lastDark.value != newDark) {
                lastDark.value = newDark
                applyTheme(editor, newDark)
            }
        },
        modifier = modifier
    )
}
