package com.example.myapplication.utils

import android.os.Environment
import java.io.File

object FileHelper {
    // 基础工作路径：/sdcard/QLPanel
    private val rootPath: String
        get() = Environment.getExternalStorageDirectory().absolutePath + "/QLPanel"

    val scriptsDir = File("$rootPath/scripts")
    val logsDir = File("$rootPath/logs")

    /**
     * 初始化面板的基础目录结构
     */
    fun initDirectories() {
        try {
            if (!scriptsDir.exists()) scriptsDir.mkdirs()
            if (!logsDir.exists()) logsDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 物理扫描 /QLPanel/scripts 下的所有子项
     * 区分单文件与文件夹工程
     */
    fun scanPhysicalScripts(): List<PhysicalItem> {
        initDirectories()
        val list = mutableListOf<PhysicalItem>()
        val files = scriptsDir.listFiles() ?: return list
        for (file in files) {
            if (file.isDirectory) {
                // 扫描项目文件夹，探测入口文件（优先 main.py, index.js, main.js 等）
                val entryPoint = detectEntryPoint(file)
                list.add(PhysicalItem(name = file.name, isFolder = true, entryPoint = entryPoint))
            } else if (file.isFile && (file.extension == "js" || file.extension == "py" || file.extension == "sh")) {
                list.add(PhysicalItem(name = file.name, isFolder = false))
            }
        }
        return list
    }

    /**
     * 读取脚本的物理内容
     */
    fun readScriptContent(fileName: String, isFolder: Boolean, entryPoint: String = ""): String {
        return try {
            val file = if (isFolder) {
                File(scriptsDir, "$fileName/$entryPoint")
            } else {
                File(scriptsDir, fileName)
            }
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            "读取失败: ${e.message}"
        }
    }

    /**
     * 写入脚本内容
     */
    fun writeScriptContent(fileName: String, isFolder: Boolean, entryPoint: String, content: String): Boolean {
        return try {
            val file = if (isFolder) {
                File(scriptsDir, "$fileName/$entryPoint")
            } else {
                File(scriptsDir, fileName)
            }
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 新建单文件脚本
     */
    fun createSingleFile(name: String): Boolean {
        return try {
            val file = File(scriptsDir, name)
            if (!file.exists()) file.createNewFile() else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 新建文件夹项目目录 + 空入口文件
     */
    fun createFolderProject(folderName: String, entryPoint: String): Boolean {
        return try {
            val folder = File(scriptsDir, folderName)
            if (!folder.exists()) folder.mkdirs()
            val entryFile = File(folder, entryPoint)
            if (!entryFile.exists()) entryFile.createNewFile() else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 删除物理文件或整个工程目录
     */
    fun deletePhysicalItem(name: String): Boolean {
        return try {
            val target = File(scriptsDir, name)
            if (target.exists()) target.deleteRecursively() else false
        } catch (e: Exception) {
            false
        }
    }

    private fun detectEntryPoint(folder: File): String {
        val candidates = listOf("main.py", "index.js", "main.js", "server.js", "crawl.py")
        for (c in candidates) {
            if (File(folder, c).exists()) return c
        }
        // 如果找不到已知入口，默认取里面第一个文件
        return folder.listFiles()?.firstOrNull { it.isFile }?.name ?: "index.js"
    }

    data class PhysicalItem(val name: String, val isFolder: Boolean, val entryPoint: String = "")
}