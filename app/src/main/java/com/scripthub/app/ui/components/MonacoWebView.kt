package com.scripthub.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * Monaco 通过隐藏 textarea 接收输入。仅在用户明确点击编辑时才声明为文本编辑器，
 * 避免滚动浏览代码时系统自动弹出软键盘。
 */
@SuppressLint("ViewConstructor")
class MonacoWebView(context: Context) : WebView(context) {

    @Volatile
    var imeEnabled: Boolean = false

    override fun onCheckIsTextEditor(): Boolean = imeEnabled

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
