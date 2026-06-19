package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.components.MediumTopAppBarExample
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启全面屏沉浸式体验
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                // 使用 Surface 作为根容器，承载全屏的主题背景色
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 📌 替换掉原本的 Greeting，直接加载你的顶部栏练手组件
                    MediumTopAppBarExample()
                }
            }
        }
    }
}
