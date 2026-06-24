package com.scripthub.app.utils

import android.content.Context
import android.os.Environment
import java.io.File

object FileHelper {
    private var appContext: Context? = null

    /** 在 Application / ViewModel 初始化时调用一次，后续无需重复传入 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val rootPath: String
        get() = appContext?.let { WorkdirPreference.getWorkdir(it) }
            ?: (Environment.getExternalStorageDirectory().absolutePath + "/QLPanel")

    val scriptsDir: File
        get() = File(rootPath, "scripts")

    val logsDir: File
        get() = File(rootPath, "logs")

    fun initDirectories() {
        try {
            if (!scriptsDir.exists()) scriptsDir.mkdirs()
            if (!logsDir.exists()) logsDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scanPhysicalScripts(): List<PhysicalItem> {
        initDirectories()
        val list = mutableListOf<PhysicalItem>()
        val files = scriptsDir.listFiles() ?: return list
        for (file in files) {
            if (file.isDirectory) {
                val entryPoint = detectEntryPoint(file)
                list.add(PhysicalItem(name = file.name, isFolder = true, entryPoint = entryPoint))
            } else if (file.isFile && (file.extension == "js" || file.extension == "py" || file.extension == "sh")) {
                list.add(PhysicalItem(name = file.name, isFolder = false))
            }
        }
        return list
    }

    fun readScriptContent(fileName: String, isFolder: Boolean, entryPoint: String = ""): String {
        return try {
            val file = if (isFolder) File(scriptsDir, "$fileName/$entryPoint") else File(scriptsDir, fileName)
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            "读取失败: ${e.message}"
        }
    }

    fun writeScriptContent(fileName: String, isFolder: Boolean, entryPoint: String, content: String): Boolean {
        return try {
            val file = if (isFolder) File(scriptsDir, "$fileName/$entryPoint") else File(scriptsDir, fileName)
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createSingleFile(name: String): Boolean {
        return try {
            val file = File(scriptsDir, name)
            if (!file.exists()) file.createNewFile() else false
        } catch (e: Exception) {
            false
        }
    }

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

    fun deletePhysicalItem(name: String): Boolean {
        return try {
            val target = File(scriptsDir, name)
            if (target.exists()) target.deleteRecursively() else false
        } catch (e: Exception) {
            false
        }
    }

    // ── 文件夹工程内部操作 ──────────────────────────────────────

    /** 在文件夹工程内创建一个新文件（relPath 为相对于工程根的路径，如 "src/utils.py"） */
    fun createFileInFolder(folderName: String, relPath: String): Boolean {
        return try {
            val file = File(scriptsDir, "$folderName/$relPath")
            if (!file.parentFile!!.exists()) file.parentFile!!.mkdirs()
            if (!file.exists()) file.createNewFile() else false
        } catch (e: Exception) { false }
    }

    /** 在文件夹工程内创建一个子目录 */
    fun createSubfolderInFolder(folderName: String, relPath: String): Boolean {
        return try {
            File(scriptsDir, "$folderName/$relPath").mkdirs()
        } catch (e: Exception) { false }
    }

    /** 重命名文件夹工程内的一个文件或目录，返回实际新名（可能含路径变化） */
    fun renameInFolder(folderName: String, oldRelPath: String, newRelPath: String): Boolean {
        return try {
            val src = File(scriptsDir, "$folderName/$oldRelPath")
            val dst = File(scriptsDir, "$folderName/$newRelPath")
            if (!dst.parentFile!!.exists()) dst.parentFile!!.mkdirs()
            src.renameTo(dst)
        } catch (e: Exception) { false }
    }

    /** 删除文件夹工程内的一个文件或子目录 */
    fun deleteFromFolder(folderName: String, relPath: String): Boolean {
        return try {
            File(scriptsDir, "$folderName/$relPath").deleteRecursively()
        } catch (e: Exception) { false }
    }

    private fun detectEntryPoint(folder: File): String {
        val candidates = listOf("main.py", "index.js", "main.js", "server.js", "crawl.py")
        for (c in candidates) {
            if (File(folder, c).exists()) return c
        }
        return folder.listFiles()?.firstOrNull { it.isFile }?.name ?: "index.js"
    }

    data class PhysicalItem(val name: String, val isFolder: Boolean, val entryPoint: String = "")
}
