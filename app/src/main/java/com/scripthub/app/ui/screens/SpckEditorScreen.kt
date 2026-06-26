package com.scripthub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scripthub.app.ui.components.SpckEditorController
import com.scripthub.app.ui.components.SpckEditorView
import com.scripthub.app.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpckEditorScreen(
    fileName: String,
    isFolder: Boolean,
    entryPoint: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSaving by remember { mutableStateOf(false) }
    var controllerRef by remember { mutableStateOf<SpckEditorController?>(null) }

    val hasFile = fileName.isNotBlank()

    val initialContent = remember(fileName, isFolder, entryPoint) {
        if (hasFile) FileHelper.readScriptContent(fileName, isFolder, entryPoint) else ""
    }

    val displayName = when {
        !hasFile        -> "Spck 编辑器"
        isFolder        -> "$fileName / $entryPoint"
        else            -> fileName
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (hasFile) {
                        IconButton(
                            enabled = !isSaving,
                            onClick = {
                                val controller = controllerRef ?: return@IconButton
                                isSaving = true
                                controller.getContentSafe { content ->
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            FileHelper.writeScriptContent(
                                                fileName   = fileName,
                                                isFolder   = isFolder,
                                                entryPoint = entryPoint,
                                                content    = content
                                            )
                                        }
                                        isSaving = false
                                        snackbarHostState.showSnackbar(
                                            if (ok) "已保存" else "保存失败"
                                        )
                                    }
                                }
                            }
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = "保存"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        SpckEditorView(
            initialContent = initialContent,
            onControllerReady = { controller -> controllerRef = controller },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
