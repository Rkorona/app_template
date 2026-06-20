// app_template/app/src/main/java/com/example/myapplication/viewmodel/ScriptViewModel.kt
package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.ScriptEntity
import com.example.myapplication.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScriptViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.scriptDao()

    // 💡 1. 极其优雅的单轨只读观察流，由 Room 框架原生驱动实时更新，绝不发生冲突
    val scriptsList: StateFlow<List<ScriptEntity>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        syncFilesWithDatabase()
    }

    /**
     * 💡 2. 单次物理对齐：不再做 endless collect，防止新文件被旧协程“秒删”
     */
    fun syncFilesWithDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            FileHelper.initDirectories()
            val physicalItems = FileHelper.scanPhysicalScripts()

            // 2.1 补全数据库中缺失的物理文件记录
            for (pItem in physicalItems) {
                val dbItem = dao.getByName(pItem.name)
                if (dbItem == null) {
                    val scriptType = when {
                        pItem.name.endsWith(".py") || pItem.entryPoint.endsWith(".py") -> "Python"
                        pItem.name.endsWith(".js") || pItem.entryPoint.endsWith(".js") -> "Node.js"
                        pItem.name.endsWith(".sh") || pItem.entryPoint.endsWith(".sh") -> "Shell"
                        else -> "Other"
                    }
                    val colorHex = when(scriptType) {
                        "Python" -> "#38BDF8"
                        "Node.js" -> "#A855F7"
                        "Shell" -> "#22C55E"
                        else -> "#94A3B8"
                    }
                    dao.insert(
                        ScriptEntity(
                            name = pItem.name,
                            type = scriptType,
                            isFolder = pItem.isFolder,
                            entryPoint = pItem.entryPoint,
                            themeColorHex = colorHex
                        )
                    )
                }
            }

            // 2.2 一次性读出数据库，清理已被物理删除的无效记录
            val dbList = dao.getAllOnce() // 👈 使用一次性挂起查询
            val physicalNames = physicalItems.map { it.name }.toSet()
            for (dbItem in dbList) {
                if (!physicalNames.contains(dbItem.name)) {
                    dao.delete(dbItem)
                }
            }
        }
    }

    // 新建单文件
    fun createSingleFile(name: String, type: String) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { FileHelper.createSingleFile(name) }
            if (success) syncFilesWithDatabase()
        }
    }

    // 新建项目工程
    fun createProjectFolder(folderName: String, entryPoint: String) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { FileHelper.createFolderProject(folderName, entryPoint) }
            if (success) syncFilesWithDatabase()
        }
    }

    // 删除脚本
    fun deleteScript(script: ScriptEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                FileHelper.deletePhysicalItem(script.name)
                dao.deleteByName(script.name)
            }
            syncFilesWithDatabase()
        }
    }
}