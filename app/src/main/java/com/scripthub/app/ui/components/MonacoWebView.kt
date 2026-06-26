package com.scripthub.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Monaco 通过隐藏 textarea 接收输入。仅在用户明确点击编辑时才声明为文本编辑器，
 * 避免滚动浏览代码时系统自动弹出软键盘。
 *
 * Android 16 加强了 IME 唤起时机（ACTION_DOWN 阶段即建立连接），
 * 通过重写 onCreateInputConnection 从根本上阻断未授权的 IME 连接。
 *
 * 关键同步逻辑：每次触摸开始时检测键盘是否真正可见。若用户已通过返回键/
 * 手势关闭键盘，但 imeEnabled 仍为 true，则重置状态，防止再次滚动时键盘弹出。
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!imeEnabled) {
                clearFocus()
                post { hideIme() }
            } else if (!isImeVisible()) {
                // 键盘已被用户关闭（返回键/手势），但 imeEnabled 还未复位。
                // 在下一次触摸开始时同步状态，阻止 WebView 重新唤起键盘。
                disableIme()
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

    /** 通过 WindowInsets 判断系统键盘是否真正可见，与 imeEnabled 标志解耦 */
    private fun isImeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(this)
            ?.isVisible(WindowInsetsCompat.Type.ime()) ?: false

    private fun hideIme() {
        post {
            val imm = context.getSystemService(InputMethodManager::class.java) ?: return@post
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }
}
