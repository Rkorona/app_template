package com.scripthub.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val SPCK_TAG = "SpckEditorView"

// ──────────────────────────────────────────────────────────────────
// SpckEditorController — 对外暴露文件内容操作接口
// ──────────────────────────────────────────────────────────────────

class SpckEditorController(private val webView: WebView) {

    /** 异步获取当前编辑器内容（在主线程回调） */
    fun getContent(callback: (String) -> Unit) {
        val js = """
            (function() {
                try {
                    if (window._spckAndroidEditor) {
                        return window._spckAndroidEditor.getValue();
                    }
                    var els = document.querySelectorAll('.ace_editor');
                    if (els.length > 0 && window.ace) {
                        var ed = ace.edit(els[0]);
                        return ed.getValue();
                    }
                } catch(e) {}
                return '';
            })()
        """.trimIndent()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView.evaluateJavascript(js) { result ->
                val content = result?.removeSurrounding("\"")
                    ?.replace("\\n", "\n")
                    ?.replace("\\t", "\t")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\'", "'")
                    ?: ""
                callback(content)
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                webView.evaluateJavascript(js) { result ->
                    val content = result?.removeSurrounding("\"")
                        ?.replace("\\n", "\n")
                        ?.replace("\\t", "\t")
                        ?.replace("\\\"", "\"")
                        ?.replace("\\'", "'")
                        ?: ""
                    callback(content)
                }
            }
        }
    }

    /** 通过 Base64 安全获取内容（推荐用于保存） */
    fun getContentSafe(callback: (String) -> Unit) {
        val js = """
            (function() {
                try {
                    var content = '';
                    if (window._spckAndroidEditor) {
                        content = window._spckAndroidEditor.getValue();
                    } else {
                        var els = document.querySelectorAll('.ace_editor');
                        if (els.length > 0 && window.ace) {
                            content = ace.edit(els[0]).getValue();
                        }
                    }
                    return btoa(unescape(encodeURIComponent(content)));
                } catch(e) { return ''; }
            })()
        """.trimIndent()
        val main = Handler(Looper.getMainLooper())
        val run = Runnable {
            webView.evaluateJavascript(js) { result ->
                val b64 = result?.removeSurrounding("\"") ?: ""
                val content = try {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) {
                    Log.e(SPCK_TAG, "getContentSafe 解码失败", e)
                    ""
                }
                callback(content)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run()
        else main.post(run)
    }
}

// ──────────────────────────────────────────────────────────────────
// JS → Kotlin Bridge
// ──────────────────────────────────────────────────────────────────

private class SpckBridge(
    private val initialContentB64: String,
    private val onReady: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getInitialContent(): String = initialContentB64

    @JavascriptInterface
    fun onEditorReady() {
        Log.d(SPCK_TAG, "Spck Ace 编辑器就绪，内容已注入")
        main.post { onReady() }
    }
}

// ──────────────────────────────────────────────────────────────────
// 对外暴露的 Composable
// ──────────────────────────────────────────────────────────────────

/**
 * Spck Editor WebView 封装
 *
 * @param initialContent   文件初始内容（编辑器就绪后自动注入 Ace 实例）
 * @param onControllerReady 控制器回调，保存时通过 controller.getContentSafe{} 读取内容
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpckEditorView(
    initialContent: String = "",
    onControllerReady: (SpckEditorController) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isLoaded by remember { mutableStateOf(false) }
    val controllerRef = remember { mutableStateOf<SpckEditorController?>(null) }

    val initialContentB64 = remember(initialContent) {
        Base64.encodeToString(initialContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.parseColor("#23293c"))

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                    addJavascriptInterface(
                        SpckBridge(
                            initialContentB64 = initialContentB64,
                            onReady = { isLoaded = true }
                        ),
                        "SpckAndroidBridge"
                    )

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            msg?.let {
                                Log.d(SPCK_TAG, "[JS] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                            }
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.postDelayed({
                                injectContentScript(view)
                            }, 1500)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            return !url.startsWith("file:///android_asset/")
                        }
                    }

                    controllerRef.value = SpckEditorController(this)
                    loadUrl("file:///android_asset/spck/index.html")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isLoaded) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    LaunchedEffect(isLoaded) {
        if (isLoaded) {
            controllerRef.value?.let { onControllerReady(it) }
        }
    }
}

/**
 * 在 SPCK 页面加载完成后注入脚本，等待 Ace 编辑器实例就绪，
 * 然后通过 SpckAndroidBridge.getInitialContent() 拿到内容并写入编辑器。
 */
private fun injectContentScript(view: WebView) {
    val js = """
        (function() {
            var MAX_TRIES = 120;
            var tries = 0;
            var interval = setInterval(function() {
                tries++;
                try {
                    var editorEls = document.querySelectorAll('.ace_editor');
                    if (editorEls.length > 0 && window.ace) {
                        var editor = ace.edit(editorEls[0]);
                        if (editor && typeof editor.getValue === 'function') {
                            var b64 = window.SpckAndroidBridge.getInitialContent();
                            if (b64 && b64.length > 0) {
                                try {
                                    var decoded = decodeURIComponent(escape(atob(b64)));
                                    editor.setValue(decoded, -1);
                                } catch(e) {
                                    editor.setValue(atob(b64), -1);
                                }
                            }
                            window._spckAndroidEditor = editor;
                            clearInterval(interval);
                            window.SpckAndroidBridge.onEditorReady();
                            return;
                        }
                    }
                } catch(e) {
                    console.log('SpckBridge inject error: ' + e.message);
                }
                if (tries >= MAX_TRIES) {
                    clearInterval(interval);
                    console.log('SpckBridge: Ace editor not found after timeout');
                    window.SpckAndroidBridge.onEditorReady();
                }
            }, 500);
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
}
