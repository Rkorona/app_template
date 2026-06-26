package com.scripthub.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * Monaco 通过隐藏 textarea 接收输入。仅在用户明确点击编辑时才声明为文本编辑器，
 * 避免滚动浏览代码时系统自动弹出软键盘。
 *
 * Android 16 加强了 IME 唤起时机（ACTION_DOWN 阶段即建立连接），
 * 通过重写 onCreateInputConnection 从根本上阻断未授权的 IME 连接。
 */
@SuppressLint("ViewConstructor")
class MonacoWebView(context: Context) : WebView(context) {

    @Volatile
    var imeEnabled: Boolean = false

    override fun onCheckIsTextEditor(): Boolean = imeEnabled

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection? {
        if (!imeEnabled) return null
        return super.onCreateInputConnection(outAttrs)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!imeEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) {
            clearFocus()
            post {
                val imm = context.getSystemService(InputMethodManager::class.java) ?: return@post
                imm.hideSoftInputFromWindow(windowToken, 0)
            }
        }
        return super.onTouchEvent(event)
    }

    fun enableIme() {
        imeEnabled = true
        isFocusableInTouchMode = true
    }

    fun disableIme() {
        imeEnabled = false
        isFocusableInTouchMode = false
        clearFocus()
        hideIme()
    }

    private fun hideIme() {
        post {
            val imm = context.getSystemService(InputMethodManager::class.java) ?: return@post
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }
}
