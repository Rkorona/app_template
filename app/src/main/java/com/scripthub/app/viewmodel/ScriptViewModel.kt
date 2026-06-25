// app_template/app/src/main/java/com/example/myapplication/viewmodel/ScriptViewModel.kt
package com.scripthub.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scripthub.app.data.AppDatabase
import com.scripthub.app.data.ScriptEntity
import com.scripthub.app.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScriptViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.scriptDao()

    val scriptsList: StateFlow<List<ScriptEntity>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        FileHelper.init(application)
        syncFilesWithDatabase()
    }

    /**
     * 💡 2. 单次物理对齐：不再做 endless collect，防止新文件被旧协程“秒删”
     */
    fun syncFilesWithDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            FileHelper.initDirectories()
            val physicalItems = FileHelper.scanPhysicalScripts()

            // 2.1 补全数据库中缺失的物理文件记录，并同步已有记录的 entryPoint
            for (pItem in physicalItems) {
                val dbItem = dao.getByName(pItem.name)
                if (dbItem == null) {
                    val scriptType = inferScriptType(pItem)
                    val colorHex = typeToColorHex(scriptType)
                    dao.insert(
                        ScriptEntity(
                            name = pItem.name,
                            type = scriptType,
                            isFolder = pItem.isFolder,
                            entryPoint = pItem.entryPoint,
                            themeColorHex = colorHex
                        )
                    )
                } else if (pItem.isFolder && pItem.entryPoint.isNotBlank() && dbItem.entryPoint != pItem.entryPoint) {
                    dao.updateEntryPoint(pItem.name, pItem.entryPoint)
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

    // 重命名工程项目文件夹
    fun renameProject(script: ScriptEntity, newName: String) {
        if (newName.isBlank() || newName == script.name) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                FileHelper.renameFolderProject(script.name, newName)
            }
            if (success) {
                withContext(Dispatchers.IO) {
                    dao.updateName(script.name, newName)
                }
                syncFilesWithDatabase()
            }
        }
    }

    private fun inferScriptType(pItem: FileHelper.PhysicalItem): String {
        val nodeExts = setOf("js", "mjs", "cjs")
        val entryExt = pItem.entryPoint.substringAfterLast(".", "").lowercase()
        val nameExt  = pItem.name.substringAfterLast(".", "").lowercase()
        return when {
            entryExt in nodeExts || nameExt in nodeExts -> "Node.js"
            entryExt == "py"     || nameExt == "py"     -> "Python"
            entryExt == "sh"     || nameExt == "sh"     -> "Shell"
            else -> "Other"
        }
    }

    private fun typeToColorHex(type: String): String = when (type) {
        "Python"  -> "#38BDF8"
        "Node.js" -> "#A855F7"
        "Shell"   -> "#22C55E"
        else      -> "#94A3B8"
    }
}