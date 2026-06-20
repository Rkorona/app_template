package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    fileName: String,
    isFolder: Boolean,
    entryPoint: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var codeText by remember { mutableStateOf("正在加载代码文件...") }

    // 进入页面时读取物理文件内容
    LaunchedEffect(fileName) {
        withContext(Dispatchers.IO) {
            val content = FileHelper.readScriptContent(fileName, isFolder, entryPoint)
            codeText = content
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(fileName, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        if (isFolder) {
                            Text("入口 ➔ $entryPoint", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // 写入保存文件
                            val success = FileHelper.writeScriptContent(fileName, isFolder, entryPoint, codeText)
                            if (success) {
                                Toast.makeText(context, "代码保存成功！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A), // 暗黑科技蓝
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A)) // 荧光黑客极客背景
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(Color(0xFF020617), shape = RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                // 等宽代码输入框
                BasicTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    textStyle = TextStyle(
                        color = Color(0xFF38BDF8), // 亮荧光青色字体
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.fillMaxSize(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )
            }
        }
    }
}