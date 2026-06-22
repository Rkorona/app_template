package com.scripthub.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class SetupProgress(
    val phase: String = "",
    val percent: Int = 0,
    val done: Boolean = false,
    val error: String? = null
)

object ProotManager {
    private const val TAG = "ProotManager"

    // LXC 官方镜像基础路径，自动抓取最新日期目录
    private const val DEBIAN_ROOTFS_BASE =
        "https://images.linuxcontainers.org/images/debian/bookworm/arm64/default/"
    private const val UBUNTU_ROOTFS_BASE =
        "https://images.linuxcontainers.org/images/ubuntu/jammy/arm64/default/"

    private val _progress = MutableStateFlow(SetupProgress())
    val progress: StateFlow<SetupProgress> = _progress

    /**
     * 获取 proot 可执行文件路径
     */
    fun getProotBin(context: Context): File {
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        if (nativeLib.exists()) return nativeLib

        // 备用：从 assets 复制到应用私有目录，再用系统 chmod 赋予执行权限
        val fallback = File(context.noBackupFilesDir, "proot")
        if (!fallback.exists() || fallback.length() == 0L) {
            try {
                context.assets.open("proot").use { input ->
                    fallback.outputStream().use { input.copyTo(it) }
                }
                Runtime.getRuntime()
                    .exec(arrayOf("/system/bin/chmod", "755", fallback.absolutePath))
                    .waitFor()
                Log.d(TAG, "从 assets 复制 proot 到 ${fallback.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "assets 备用路径初始化失败: ${e.message}")
            }
        }
        return fallback
    }

    fun getRootfsDir(context: Context, distro: DistroType): File {
        // 优先使用内部存储：支持真正的 symlink（外部存储 FUSE 不支持，导致 bin→usr/bin 失效）
        val internal = File(context.filesDir, "proot-rootfs/${distro.id}")
        if (internal.exists()) return internal

        // 迁移旧版：外部存储已有安装 → 自动迁移到内部存储
        val external = context.getExternalFilesDir("proot-rootfs")?.let { File(it, distro.id) }
        if (external != null && external.exists() && File(external, ".scripthub_installed").exists()) {
            Log.d(TAG, "迁移 rootfs 从外部存储到内部存储...")
            try {
                external.copyRecursively(internal, overwrite = true)
                external.deleteRecursively()
                Log.d(TAG, "rootfs 迁移完成")
            } catch (e: Exception) {
                Log.w(TAG, "rootfs 迁移失败，继续使用内部路径: ${e.message}")
            }
        }
        return internal
    }

    fun isProotReady(context: Context): Boolean {
        val bin = getProotBin(context)
        return bin.exists() && (bin.canExecute() || bin.length() > 0)
    }

    fun isDistroInstalled(context: Context, distro: DistroType): Boolean {
        val dir = getRootfsDir(context, distro)
        if (!dir.exists()) return false

        val marker = File(dir, ".scripthub_installed")
        if (marker.exists()) return true

        // 迁移：旧版安装没有标记文件，检测到 usr/bin 目录即认为已安装，补写标记
        val usrBin = File(dir, "usr/bin")
        if (usrBin.exists() && (usrBin.listFiles()?.isNotEmpty() == true)) {
            Log.d(TAG, "迁移：检测到已有 rootfs，补写 .scripthub_installed")
            try { marker.createNewFile() } catch (_: Exception) {}
            return true
        }

        return false
    }

    suspend fun setup(context: Context, distro: DistroType) = withContext(Dispatchers.IO) {
        try {
            _progress.value = SetupProgress()

            // proot 已打包进 APK (jniLibs/arm64-v8a/libproot.so)，安装时自动解压，无需下载
            if (!isProotReady(context)) {
                throw IOException("proot 引擎未找到，请重新安装应用")
            }
            emit("proot 引擎就绪", 18)

            val rootfsDir = getRootfsDir(context, distro)
            if (!isDistroInstalled(context, distro)) {
                rootfsDir.mkdirs()
                val base = if (distro == DistroType.DEBIAN) DEBIAN_ROOTFS_BASE else UBUNTU_ROOTFS_BASE
                emit("正在查询 ${distro.displayName} 最新镜像版本...", 19)
                val url = fetchLatestRootfsUrl(base)
                Log.d(TAG, "rootfs URL: $url")

                val tarFile = File(context.cacheDir, "${distro.id}-rootfs.tar.xz")
                emit("正在下载 ${distro.displayName} 根文件系统...", 20)
                downloadFile(url, tarFile) { downloaded, total ->
                    if (total > 0) {
                        val p = 20 + ((downloaded.toFloat() / total) * 55).toInt()
                        _progress.value = SetupProgress(
                            phase   = "下载中 ${downloaded / 1024 / 1024}MB / ${total / 1024 / 1024}MB",
                            percent = p.coerceAtMost(75)
                        )
                    }
                }

                emit("正在解压根文件系统 (首次安装约需 1-3 分钟)...", 76)
                extractTarXz(tarFile, rootfsDir)
                tarFile.delete()

                emit("配置系统运行环境...", 92)
                configureRootfs(context, rootfsDir, distro)
            } else {
                emit("${distro.displayName} 已安装，跳过下载", 92)
            }

            DistroPreference.setDistro(context, distro)
            DistroPreference.markSetupDone(context)

            _progress.value = SetupProgress(phase = "安装完成！", percent = 100, done = true)

        } catch (e: Exception) {
            Log.e(TAG, "Setup failed", e)
            _progress.value = SetupProgress(error = "安装失败: ${e.message}")
        }
    }

    suspend fun uninstallDistro(context: Context, distro: DistroType) = withContext(Dispatchers.IO) {
        val dir = getRootfsDir(context, distro)
        if (dir.exists()) dir.deleteRecursively()
        DistroPreference.resetSetup(context)
    }

    private fun emit(phase: String, percent: Int) {
        _progress.value = SetupProgress(phase = phase, percent = percent)
        Log.d(TAG, "[$percent%] $phase")
    }

    // ─── rootfs URL 解析 ──────────────────────────────────────────────────────

    private fun fetchLatestRootfsUrl(baseUrl: String): String {
        val html = httpGetText(baseUrl)
        val regex = Regex("""href="(\d{8}_\d{2}(?:%3A|:)\d{2})/?"""")
        val entries = regex.findAll(html)
            .map { it.groupValues[1] }
            .toList()

        if (entries.isEmpty()) {
            throw IOException("无法从目录列表解析到镜像版本: $baseUrl")
        }

        val latest = entries.maxByOrNull { it.replace("%3A", ":") }!!
        return "${baseUrl}${latest}/rootfs.tar.xz"
    }

    // ─── 通用工具 ─────────────────────────────────────────────────────────────

    private fun httpGetText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout    = 60_000
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IOException("HTTP ${conn.responseCode}: $urlStr")
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun downloadFile(
        urlStr: String,
        dest: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout    = 300_000
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IOException("HTTP ${conn.responseCode} 下载失败: $urlStr")
        }

        val total      = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(32 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    onProgress?.invoke(downloaded, total)
                }
            }
        }
    }

    private fun extractTarXz(tarXzFile: File, destDir: File) {
        val tarFile = File(tarXzFile.parent, tarXzFile.nameWithoutExtension)
        try {
            emit("正在解压 XZ 流...", 77)
            decompressXz(tarXzFile, tarFile)

            emit("正在展开 tar 归档...", 82)
            val process = ProcessBuilder(
                "tar", "-xf", tarFile.absolutePath,
                "-C", destDir.absolutePath,
                "--no-same-owner"
            ).redirectErrorStream(true).start()
            val output   = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode > 1) {
                throw IOException("tar 展开失败 (exit $exitCode):\n$output")
            }
            if (exitCode == 1) {
                Log.w(TAG, "tar 展开有警告 (exit 1)，继续:\n$output")
            }
        } finally {
            tarFile.delete()
        }
    }

    private fun decompressXz(src: File, dest: File) {
        src.inputStream().buffered(64 * 1024).use { raw ->
            XZInputStream(raw).use { xz ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(32 * 1024)
                    var n: Int
                    while (xz.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                    }
                }
            }
        }
    }

    private fun configureRootfs(context: Context, rootfsDir: File, distro: DistroType) {
        fun ensureDir(path: String): File {
            val dir = File(rootfsDir, path)
            if (!dir.exists()) {
                val ok = dir.mkdirs()
                if (!ok && !dir.exists()) {
                    Log.w(TAG, "无法创建目录: ${dir.absolutePath}")
                }
            }
            return dir
        }

        ensureDir("etc")
        ensureDir("etc/profile.d")
        ensureDir("root")
        ensureDir("tmp")
        ensureDir("data/scripts")

        // resolv.conf：DNS 配置
        try {
            val resolvConf = File(rootfsDir, "etc/resolv.conf")
            if (!resolvConf.exists() || resolvConf.length() == 0L) {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolv.conf 写入失败: ${e.message}")
        }

        // 环境变量脚本
        try {
            File(rootfsDir, "etc/profile.d/scripthub.sh").writeText(
                """
                export HOME=/root
                export TERM=xterm-256color
                export LANG=C.UTF-8
                export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
                export SCRIPTS_DIR=/data/scripts
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.w(TAG, "scripthub.sh 写入失败: ${e.message}")
        }

        // 修复 UsrMerge 符号链接
        fixUsrMergeSymlinks(rootfsDir)

        // 写入安装完成标记
        File(rootfsDir, ".scripthub_installed").createNewFile()
    }

    /**
     * 修复 UsrMerge 符号链接
     */
    private fun fixUsrMergeSymlinks(rootfsDir: File) {
        val links = mapOf(
            "bin"    to "usr/bin",
            "sbin"   to "usr/sbin",
            "lib"    to "usr/lib",
            "lib64"  to "usr/lib64",
            "lib32"  to "usr/lib32",
            "libx32" to "usr/libx32"
        )
        for ((name, target) in links) {
            val link = File(rootfsDir, name)
            val targetDir = File(rootfsDir, target)
            if (targetDir.exists()) {
                if (link.exists() && !java.nio.file.Files.isSymbolicLink(link.toPath())) {
                    Log.w(TAG, "清理普通文件目录，准备重建符号链接: ${link.absolutePath}")
                    link.deleteRecursively()
                }
                
                if (!link.exists()) {
                    try {
                        val path = link.toPath()
                        val targetPath = java.nio.file.Paths.get(target)
                        java.nio.file.Files.createSymbolicLink(path, targetPath)
                        Log.d(TAG, "补建 UsrMerge symlink: $name → $target")
                    } catch (e: Exception) {
                        Log.w(TAG, "symlink $name → $target 创建失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 构建 Proot 命令行
     *
     * 【重要修复】
     * 1. 挂载系统的 /system、/vendor 目录。
     * 2. 移除了上一版本对 "/tmp" 的外部 bind-mount 绑定！让容器内的 PRoot 采用 link2symlink 机制自行管理内部的 /tmp。
     * 容器内应用对外部绑定挂载的目录进行 chmod 往往会触发 Android 的安全策略报错。
     */
    fun buildProotCommand(
        context: Context,
        distro: DistroType,
        bashCommand: String
    ): List<String> {
        val prootBin   = getProotBin(context).absolutePath
        val rootfsPath = getRootfsDir(context, distro).absolutePath
        val scriptsDir = "/sdcard/QLPanel/scripts"

        return listOf(
            prootBin,
            "--rootfs=$rootfsPath",
            "-0",
            "--link2symlink",
            "-b", "/proc:/proc",
            "-b", "/dev:/dev",
            "-b", "/sys:/sys",
            "-b", "/system:/system",
            "-b", "/vendor:/vendor", // 补全系统库可能需要的底层驱动/连接器绑定
            "-b", "$scriptsDir:/data/scripts",
            "-w", "/root",
            "/bin/bash", "--login", "-c", bashCommand
        )
    }

    /**
     * 获取安全且独立的 PRoot 运行期专属临时目录
     */
    private fun getStableTmpDir(context: Context): File {
        // 使用 files 目录下的一个稳定子目录，权限等级最高，比 cache 更不易被 Android 系统干扰。
        val dir = File(context.filesDir, "pr_tmp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // 赋予严格的所有者读写执行权限（700）防止 chmod 冲突
        try {
            dir.setReadable(true, true)
            dir.setWritable(true, true)
            dir.setExecutable(true, true)
        } catch (_: Exception) {}
        return dir
    }

    /**
     * 返回配置好运行环境的 ProcessBuilder
     */
    fun buildProotProcess(
        context: Context,
        distro: DistroType,
        bashCommand: String
    ): ProcessBuilder {
        ensureTallocLib(context)
        val cmd       = buildProotCommand(context, distro, bashCommand)
        val libsDir   = getProotLibsDir(context).absolutePath
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val ldPath    = "$libsDir:$nativeDir"

        val shmemLib  = File(nativeDir, "libandroid-shmem.so")
        val ldPreload = if (shmemLib.exists()) shmemLib.absolutePath else ""

        // 获取并重置纯净的临时运行目录
        val stableTmp = getStableTmpDir(context)
        try {
            // 清理上一次运行残留的、可能导致 chmod 失败的脏文件
            stableTmp.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {}

        return ProcessBuilder(cmd).apply {
            // 彻底净化宿主机的临时变量污染，确保 PRoot 在纯净沙盒环境中解析其临时文件
            environment().clear()
            
            // 重新设置容器所必须的标准环境变量
            environment()["HOME"]            = "/root"
            environment()["TERM"]            = "xterm-256color"
            environment()["LANG"]            = "C.UTF-8"
            environment()["PATH"]            = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            environment()["LD_LIBRARY_PATH"] = ldPath
            
            // 核心修复：强制重设 PROOT 运行期环境变量，指向 files 下的安全专用路径
            environment()["PROOT_TMP_DIR"]   = stableTmp.absolutePath
            environment()["TMPDIR"]          = stableTmp.absolutePath
            
            if (ldPreload.isNotEmpty()) {
                environment()["LD_PRELOAD"] = ldPreload
            }
        }
    }

    private fun getProotLibsDir(context: Context): File =
        File(context.noBackupFilesDir, "proot-libs").also { it.mkdirs() }

    fun ensureTallocLib(context: Context) {
        val dest = File(getProotLibsDir(context), "libtalloc.so.2")
        if (dest.exists() && dest.length() > 0L) return
        try {
            context.assets.open("libtalloc.so.2").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            Log.d(TAG, "libtalloc.so.2 已提取到 ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "libtalloc.so.2 提取失败: ${e.message}")
        }
    }

    suspend fun repairEnvironment(context: Context, distro: DistroType): String =
        withContext(Dispatchers.IO) {
            val rootfsDir = getRootfsDir(context, distro)
            if (!rootfsDir.exists()) {
                return@withContext "rootfs 目录不存在，请重新安装 ${distro.displayName}"
            }

            val sb = StringBuilder()

            sb.appendLine("▶ 修复 UsrMerge 符号链接...")
            fixUsrMergeSymlinks(rootfsDir)

            sb.appendLine("▶ 重建配置文件...")
            try {
                configureRootfs(context, rootfsDir, distro)
            } catch (e: Exception) {
                sb.appendLine("  配置写入出错（非致命）: ${e.message}")
            }

            sb.appendLine()
            sb.appendLine("── 诊断结果 ──")

            data class Check(val label: String, val candidates: List<String>)
            val checks = listOf(
                Check("bash",        listOf("usr/bin/bash", "bin/bash")),
                Check("sh",          listOf("usr/bin/sh",   "bin/sh")),
                Check("ld-linux",    listOf(
                    "lib/ld-linux-aarch64.so.1",
                    "usr/lib/ld-linux-aarch64.so.1",
                    "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                    "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
                )),
                Check("libc",        listOf(
                    "lib/aarch64-linux-gnu/libc.so.6",
                    "usr/lib/aarch64-linux-gnu/libc.so.6"
                )),
                Check("bin→usr/bin", listOf("bin")),
                Check("lib→usr/lib", listOf("lib"))
            )

            for (check in checks) {
                val found = check.candidates.firstOrNull { File(rootfsDir, it).exists() }
                if (found != null) {
                    sb.appendLine("  ✓ ${check.label}  ($found)")
                } else {
                    sb.appendLine("  ✗ ${check.label}  缺失！")
                }
            }

            sb.toString().trimEnd()
        }
}

private fun InputStream.readNBytes(n: Int): ByteArray {
    val buf = ByteArray(n)
    var offset = 0
    while (offset < n) {
        val read = read(buf, offset, n - offset)
        if (read < 0) break
        offset += read
    }
    return buf
}