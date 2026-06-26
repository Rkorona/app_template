package com.scripthub.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage

private const val TAG = "MonacoEditorView"

// ──────────────────────────────────────────────────────────────────
// MonacoEditorController — 替代 CodeEditor 引用，暴露给父级使用
// ──────────────────────────────────────────────────────────────────

class MonacoEditorController(private val webView: WebView) {

    /** 设置内容（Base64 安全编码，正确处理多语言字符）*/
    fun setContent(content: String) {
        val base64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        evalJs("setContent('$base64')")
    }

    /** 设置语言（Monaco language ID，如 "python"/"javascript"/"kotlin"）*/
    fun setLanguage(lang: String) {
        evalJs("setLanguage('$lang')")
    }

    /** 设置主题 */
    fun setTheme(dark: Boolean) {
        evalJs("setTheme('${if (dark) "scripthub-dark" else "scripthub-light"}')")
    }

    /**
     * 异步获取当前内容（在主线程回调）
     * 保存文件时调用，无需实时同步
     */
    fun getContent(callback: (String) -> Unit) {
        webView.evaluateJavascript("getContentBase64()") { result ->
            // evaluateJavascript 回调本身已在主线程
            val clean = result?.removeSurrounding("\"") ?: ""
            val content = try {
                String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "getContent 解码失败", e)
                ""
            }
            callback(content)
        }
    }

    /** 在光标处插入文字（代码键盘按键）*/
    fun typeText(text: String) {
        val base64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        evalJs("typeText('$base64')")
    }

    /** 撤销 */
    fun undo() = evalJs("editorUndo()")

    /** 重做 */
    fun redo() = evalJs("editorRedo()")

    /** 光标移动：left | right | up | down */
    fun moveCursor(direction: String) = evalJs("moveCursor('$direction')")

    /** 调整选区起点（左手柄），终点固定 */
    fun adjustSelectionStart(direction: String) = evalJs("adjustSelectionStart('$direction')")

    /** 调整选区终点（右手柄），起点固定 */
    fun adjustSelectionEnd(direction: String) = evalJs("adjustSelectionEnd('$direction')")

    /** 删除当前选区（剪切时使用） */
    fun deleteSelection() = evalJs("deleteSelection()")

    /** 全选 */
    fun selectAll() = evalJs("selectAllText()")

    fun getCurrentLine(callback: (String) -> Unit) {
        evalJsWithB64Result("getCurrentLineBase64()", callback)
    }

    fun cutCurrentLine(callback: (String) -> Unit) {
        evalJsWithB64Result("cutCurrentLineBase64()", callback)
    }

    fun deleteCurrentLine() = evalJs("deleteCurrentLine()")

    fun clearCurrentLine() = evalJs("clearCurrentLine()")

    fun clearAll() = evalJs("clearAllContent()")

    fun formatDocument() = evalJs("formatDocument()")

    fun toggleComment() = evalJs("toggleComment()")

    fun findNext(query: String, callback: (Boolean) -> Unit) {
        evalJsWithBoolResult("findNextText('${b64(query)}')", callback)
    }

    fun findPrevious(query: String, callback: (Boolean) -> Unit) {
        evalJsWithBoolResult("findPreviousText('${b64(query)}')", callback)
    }

    fun replaceOne(find: String, replace: String, callback: (Int) -> Unit) {
        evalJsWithIntResult("replaceOneText('${b64(find)}','${b64(replace)}')", callback)
    }

    fun replaceAll(find: String, replace: String, callback: (Int) -> Unit) {
        evalJsWithIntResult("replaceAllText('${b64(find)}','${b64(replace)}')", callback)
    }

    /** 聚焦编辑器并弹出系统键盘 */
    fun focus() {
        evalJs("focusEditor()")
    }

    /** 强制重新布局（Android WebView 尺寸变化时需手动触发） */
    fun layout() = evalJs("layoutEditor()")

    // ── 内部工具 ──────────────────────────────────────────────────

    private fun b64(text: String) =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decodeJsB64(result: String?): String {
        val clean = result?.removeSurrounding("\"") ?: ""
        return try {
            String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseJsString(result: String?): String =
        result?.trim()?.removeSurrounding("\"") ?: ""

    private fun evalJsWithB64Result(js: String, callback: (String) -> Unit) {
        webView.evaluateJavascript(js) { callback(decodeJsB64(it)) }
    }

    private fun evalJsWithBoolResult(js: String, callback: (Boolean) -> Unit) {
        webView.evaluateJavascript(js) { callback(parseJsString(it) == "1") }
    }

    private fun evalJsWithIntResult(js: String, callback: (Int) -> Unit) {
        webView.evaluateJavascript(js) { callback(parseJsString(it).toIntOrNull() ?: 0) }
    }

    private fun evalJs(js: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView.evaluateJavascript(js, null)
        } else {
            Handler(Looper.getMainLooper()).post {
                webView.evaluateJavascript(js, null)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// JS → Kotlin Bridge
// ──────────────────────────────────────────────────────────────────

private class MonacoBridge(
    private val webView: WebView,
    private val onStatsChanged: (lines: Int, chars: Int) -> Unit,
    private val onCursorChanged: (line: Int, col: Int) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val onSelectionChanged: (hasSelection: Boolean, selectedText: String) -> Unit = { _, _ -> },
    private val onLongPressEmpty: (x: Float, y: Float) -> Unit = { _, _ -> },
    private val onCursorLayout: (x: Float, y: Float, caretHeight: Float, visible: Boolean) -> Unit = { _, _, _, _ -> }
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onStatsChanged(lines: Int, chars: Int) {
        main.post { onStatsChanged.invoke(lines, chars) }
    }

    @JavascriptInterface
    fun onCursorChanged(line: Int, col: Int) {
        main.post { onCursorChanged.invoke(line, col) }
    }

    @JavascriptInterface
    fun onReady() {
        Log.d(TAG, "Monaco 就绪")
        main.post { onReady.invoke() }
    }

    /** 用户明确点击编辑器后才允许系统软键盘 */
    @JavascriptInterface
    fun onEditorTapped() {
        main.post {
            val wv = webView as? MonacoWebView ?: return@post
            wv.enableIme()
            wv.requestFocus(View.FOCUS_DOWN)
            val imm = wv.context.getSystemService(InputMethodManager::class.java) ?: return@post
            imm.showSoftInput(wv, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** 滚动或触摸浏览时关闭软键盘并禁止 WebView 声明为文本编辑器 */
    @JavascriptInterface
    fun onEditorDismissed() {
        main.post {
            (webView as? MonacoWebView)?.disableIme()
        }
    }

    @JavascriptInterface
    fun onError(message: String) {
        Log.e(TAG, "Monaco 错误: $message")
        main.post { onError.invoke(message) }
    }

    @JavascriptInterface
    fun onSelectionChanged(hasSelection: Boolean, base64Text: String) {
        val text = if (hasSelection && base64Text.isNotEmpty()) {
            try { String(Base64.decode(base64Text, Base64.DEFAULT), Charsets.UTF_8) }
            catch (e: Exception) { "" }
        } else ""
        main.post { onSelectionChanged.invoke(hasSelection, text) }
    }

    @JavascriptInterface
    fun onLongPressEmpty(x: Int, y: Int) {
        main.post { onLongPressEmpty.invoke(x.toFloat(), y.toFloat()) }
    }

    @JavascriptInterface
    fun onCursorLayout(x: Int, y: Int, caretHeight: Int, visible: Boolean) {
        main.post {
            onCursorLayout.invoke(x.toFloat(), y.toFloat(), caretHeight.toFloat(), visible)
        }
    }

}

// ──────────────────────────────────────────────────────────────────
// 对外暴露的 Composable（接口与 SoraEditorView 保持一致）
// ──────────────────────────────────────────────────────────────────

/**
 * Monaco Editor WebView 封装
 *
 * @param initialContent  文件初始内容（在编辑器就绪后一次性写入）
 * @param language        Monaco language ID，如 "python" / "javascript" / "kotlin"
 * @param onEditorReady   控制器回调，保存时通过 controller.getContent{} 读取内容
 * @param onStats         内容变化时回调行数/字符数
 * @param onCursor        光标移动时回调（行号/列号从 1 开始）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoEditorView(
    initialContent: String,
    language: String,
    onEditorReady: (MonacoEditorController) -> Unit = {},
    onStats: (lines: Int, chars: Int) -> Unit = { _, _ -> },
    onCursor: (line: Int, col: Int) -> Unit = { _, _ -> },
    onSelectionChanged: (hasSelection: Boolean, selectedText: String) -> Unit = { _, _ -> },
    onLongPressEmpty: (x: Float, y: Float) -> Unit = { _, _ -> },
    onCursorLayout: (x: Float, y: Float, caretHeight: Float, visible: Boolean) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    var isReady    by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    var initDone   by remember { mutableStateOf(false) }

    // controller 持有 WebView 引用；WebView 在 AndroidView factory 中创建
    val controllerRef = remember { mutableStateOf<MonacoEditorController?>(null) }

    Box(
        modifier = modifier.onSizeChanged { size ->
            if (isReady && size.width > 0 && size.height > 0) {
                controllerRef.value?.layout()
            }
        }
    ) {

        AndroidView(
            factory = { ctx ->
                MonacoWebView(ctx).apply {
                    // WebView 必须 MATCH_PARENT，否则在 Compose 中高度可能为 0
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = false
                    setBackgroundColor(Color.parseColor(if (isDark) "#0D0D0D" else "#FAFAFA"))

                    val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
                    var touchStartX = 0f
                    var touchStartY = 0f
                    var touchMoved = false

                    setOnTouchListener { v, event ->
                        val wv = v as MonacoWebView
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                touchStartX = event.x
                                touchStartY = event.y
                                touchMoved = false
                                // Android 16 在 ACTION_DOWN 阶段就可能建立 IME 连接，
                                // 此处提前压制，防止键盘在滚动前被唤起
                                if (!wv.imeEnabled) {
                                    wv.disableIme()
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!touchMoved) {
                                    val dx = event.x - touchStartX
                                    val dy = event.y - touchStartY
                                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                        touchMoved = true
                                        // 键盘已打开时，滑动浏览代码不自动收起
                                        if (!wv.imeEnabled) {
                                            wv.disableIme()
                                            wv.evaluateJavascript("dismissEditorInput()", null)
                                        }
                                    }
                                }
                            }
                        }
                        false
                    }

                    // ── WebView 基础与安全权限配置 ──────────────────────────────
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    
                    // 极其重要：显式允许访问文件系统资源，确保 assets 目录完全对 WebView 可读
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    
                    // 禁用缩放
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
        
                    // ── 新增：日志调试支持（再遇到空白屏时，可在 Logcat 过滤器输入 MonacoJS 查明真相） ──
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                val msg = "[Monaco JS] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                                if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                    Log.e(TAG, msg)
                                } else {
                                    Log.d(TAG, msg)
                                }
                            }
                            return true
                        }
                    }
        
                    // ── JS Bridge ─────────────────────────────────────
                    addJavascriptInterface(
                        MonacoBridge(
                            webView = this,
                            onStatsChanged = onStats,
                            onCursorChanged = onCursor,
                            onReady = { isReady = true },
                            onError = { msg -> errorMsg = msg },
                            onSelectionChanged = onSelectionChanged,
                            onLongPressEmpty = onLongPressEmpty,
                            onCursorLayout = onCursorLayout
                        ),
                        "AndroidBridge"
                    )
        
                    // ── 优化路由拦截 ─────────────────────────────────────
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.post { view.evaluateJavascript("layoutEditor()", null) }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            // 允许本地 assets 页面内的所有正常跳转和资源加载
                            return if (url.startsWith("file:///android_asset/")) {
                                false
                            } else {
                                // 如果点击了外部超链接，在这里可以选择拦截或用外部浏览器打开
                                true
                            }
                        }
                    }
        
                    // 页面加载后 WebView 尺寸变化时触发布局
                    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            if (width > 0 && height > 0) {
                                evaluateJavascript("layoutEditor()", null)
                            }
                        }
                    })

                    // 创建 controller 并加载宿主页面
                    controllerRef.value = MonacoEditorController(this)
                    loadUrl("file:///android_asset/monaco/editor.html")
                }
            },
            update = { /* 主题变化由 LaunchedEffect 处理 */ },
            modifier = Modifier.fillMaxSize()
        )


        // ── 就绪前显示 Loading ─────────────────────────────────────
        if (!isReady && errorMsg == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = MaterialTheme.colorScheme.primary
            )
        }

        // ── 错误提示（通常是 monaco/vs/ 资源缺失）──────────────────
        if (errorMsg != null) {
            Text(
                text  = "⚠ $errorMsg",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    // ── Monaco 就绪后初始化内容 ────────────────────────────────────
    LaunchedEffect(isReady) {
        if (!isReady || initDone) return@LaunchedEffect
        val controller = controllerRef.value ?: return@LaunchedEffect

        controller.setTheme(isDark)
        controller.setLanguage(language)
        if (initialContent.isNotEmpty()) {
            controller.setContent(initialContent)
        }
        controller.layout()
        initDone = true
        onEditorReady(controller)
    }

    // ── 主题跟随系统切换 ──────────────────────────────────────────
    LaunchedEffect(isDark) {
        if (isReady) {
            controllerRef.value?.setTheme(isDark)
            controllerRef.value?.layout()
        }
    }
}
