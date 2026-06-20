package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.ScriptEntity
import com.example.myapplication.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScriptViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.scriptDao()

    private val _scriptsList = MutableStateFlow<List<ScriptEntity>>(emptyList())
    val scriptsList: StateFlow<List<ScriptEntity>> = _scriptsList

    init {
        syncFilesWithDatabase()
    }

    /**
     * 核心双轨同步：物理文件夹 <==> 数据库
     */
    fun syncFilesWithDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 初始化并扫描 /sdcard/QLPanel/scripts
            FileHelper.initDirectories()
            val physicalItems = FileHelper.scanPhysicalScripts()

            // 2. 补全数据库中缺失的物理文件记录
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

            // 3. 从数据库中清理掉已经在物理磁盘上被删除的记录
            dao.getAll().collect { dbList ->
                val physicalNames = physicalItems.map { it.name }.toSet()
                for (dbItem in dbList) {
                    if (!physicalNames.contains(dbItem.name)) {
                        dao.delete(dbItem)
                    }
                }
                
                // 4. 将最新最干净的数据同步到 UI 的 MutableStateFlow 中
                _scriptsList.value = dbList
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