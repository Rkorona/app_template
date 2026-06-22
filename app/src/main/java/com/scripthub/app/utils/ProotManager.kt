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

        // 备用：从 assets 复制并赋予执行权限
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
        val internal = File(context.filesDir, "proot-rootfs/${distro.id}")
        if (internal.exists()) return internal

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

                // 解压完立刻校验，缺关键文件就直接清掉重来，
                // 不要让一个半成品环境顶着 .scripthub_installed 标记继续留着
                val missingFresh = verifyCriticalBinaries(rootfsDir)
                if (missingFresh.isNotEmpty()) {
                    rootfsDir.deleteRecursively()
                    throw IOException(
                        "根文件系统解压不完整，缺少: ${missingFresh.joinToString("、")}。" +
                            "可能是网络中断或存储空间不足导致，请重试安装"
                    )
                }
            } else {
                emit("${distro.displayName} 已安装，正在校验完整性...", 92)
                fixUsrMergeSymlinks(rootfsDir)

                val missingExisting = verifyCriticalBinaries(rootfsDir)
                if (missingExisting.isNotEmpty()) {
                    throw IOException(
                        "检测到已安装的 ${distro.displayName} 缺少: ${missingExisting.joinToString("、")}，" +
                            "运行环境不完整。请到设置中卸载该环境后重新安装"
                    )
                }
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

    /**
     * 解压/安装后的最小完整性校验：只检查"缺了就根本没法启动 shell"的几个关键文件。
     */
    private fun verifyCriticalBinaries(rootfsDir: File): List<String> {
        val mustHave = mapOf(
            "bash 可执行文件" to listOf("usr/bin/bash", "bin/bash"),
            "动态链接器 ld-linux" to listOf(
                "lib/ld-linux-aarch64.so.1",
                "usr/lib/ld-linux-aarch64.so.1",
                "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
            ),
            "libc" to listOf(
                "lib/aarch64-linux-gnu/libc.so.6",
                "usr/lib/aarch64-linux-gnu/libc.so.6"
            )
        )
        return mustHave.filterValues { candidates ->
            candidates.none { File(rootfsDir, it).exists() }
        }.keys.toList()
    }

    private fun emit(phase: String, percent: Int) {
        _progress.value = SetupProgress(phase = phase, percent = percent)
        Log.d(TAG, "[$percent%] $phase")
    }

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

        if (total > 0 && downloaded != total) {
            dest.delete()
            throw IOException("下载不完整: 期望 $total 字节，实际收到 $downloaded 字节，网络可能中断了，请重试")
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

        fixUsrMergeSymlinks(rootfsDir)
        File(rootfsDir, ".scripthub_installed").createNewFile()
    }

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
     */
    fun buildProotCommand(
        context: Context,
        distro: DistroType,
        bashCommand: String
    ): List<String> {
        val prootBin   = getProotBin(context).absolutePath
        val rootfsPath = getRootfsDir(context, distro).absolutePath
        val scriptsDir = "/sdcard/QLPanel/scripts"

        val commands = mutableListOf(
            prootBin,
            "--rootfs=$rootfsPath",
            "-0",
            "--link2symlink",
            "-b", "/proc:/proc",
            "-b", "/dev:/dev",
            "-b", "/sys:/sys",
            "-b", "/system:/system",
            "-b", "/vendor:/vendor"
        )

        // 检测并挂载 /apex，解决 Android 10+ 底层 runtime linker 依赖问题
        val apexDir = File("/apex")
        if (apexDir.exists()) {
            commands.add("-b")
            commands.add("/apex:/apex")
        }

        commands.addAll(listOf(
            "-b", "$scriptsDir:/data/scripts",
            "-w", "/root",
            "/bin/bash", "--login", "-c", bashCommand
        ))

        return commands
    }

    private fun getStableTmpDir(context: Context): File {
        val dir = File(context.filesDir, "pr_tmp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        try {
            dir.setReadable(true, true)
            dir.setWritable(true, true)
            dir.setExecutable(true, true)
        } catch (_: Exception) {}
        return dir
    }

    /**
     * 返回配置好运行环境的 ProcessBuilder
     *
     * 【修复细节说明】
     * 1. 绝对不能使用 environment().clear() 完全擦除环境。
     * Android 系统 Bionic linker 和 SELinux 机制高度依赖 ANDROID_* 等底层变量。
     * 2. 我们通过过滤、保留白名单的方式清理宿主机变量。
     * 3. 必须在 LD_LIBRARY_PATH 以及 PATH 中保留 Android 系统的原生支持。
     * 4. 修复 Temp 目录并发清理 Bug：不采用暴力 deleteRecursively 清理，避免多进程运行冲突。
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

        val stableTmp = getStableTmpDir(context)

        return ProcessBuilder(cmd).apply {
            // 获取原生环境，进行【选择性过滤】而非直接彻底 clear()
            val env = environment()
            
            // 备份需要保留的 Android 核心系统环境变量
            val preservedEnv = mutableMapOf<String, String>()
            val keepKeys = listOf(
                "ANDROID_ROOT", "ANDROID_DATA", "ANDROID_RUNTIME_ROOT",
                "ANDROID_TZDATA_ROOT", "ANDROID_I18N_ROOT", "BOOTCLASSPATH"
            )
            for (key in keepKeys) {
                env[key]?.let { preservedEnv[key] = it }
            }

            // 清空环境（防止 Termux 等外部环境变量污染）
            env.clear()

            // 1. 恢复 Android 必需的核心环境变量
            env.putAll(preservedEnv)
            
            // 2. 注入 PRoot 容器隔离环境变量
            env["HOME"]            = "/root"
            env["TERM"]            = "xterm-256color"
            env["LANG"]            = "C.UTF-8"
            
            // PATH 必须同时包含：容器 PATH 和 宿主机 PATH (确保 proot 能调用本机命令比如 chmod)
            env["PATH"]            = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = ldPath
            
            // 3. 核心修复：临时目录环境强制隔离，并让 PRoot 获取读写及 Socket 创建权限
            env["PROOT_TMP_DIR"]   = stableTmp.absolutePath
            env["TMPDIR"]          = stableTmp.absolutePath
            
            // 4. 清除 LD_PRELOAD。
            env.remove("LD_PRELOAD")
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