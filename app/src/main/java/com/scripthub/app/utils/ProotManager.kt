package com.scripthub.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import android.system.Os

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
     * 获取 proot 可执行文件路径（强制转换物理真实路径）
     */
    fun getProotBin(context: Context): File {
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libproot.so").canonicalFile
        if (nativeLib.exists()) return nativeLib

        // 备用：从 assets 复制并赋予执行权限
        val fallback = File(context.noBackupFilesDir.canonicalFile, "proot").canonicalFile
        if (!fallback.exists() || fallback.length() == 0L) {
            try {
                context.assets.open("proot").use { input ->
                    fallback.outputStream().use { input.copyTo(it) }
                }
                Runtime.getRuntime()
                    .exec(arrayOf("/system/bin/chmod", "755", fallback.canonicalPath))
                    .waitFor()
                Log.d(TAG, "从 assets 复制 proot 到 ${fallback.canonicalPath}")
            } catch (e: Exception) {
                Log.e(TAG, "assets 备用路径初始化失败: ${e.message}")
            }
        }
        return fallback
    }

    fun getRootfsDir(context: Context, distro: DistroType): File {
        val internal = File(context.filesDir.canonicalFile, "proot-rootfs/${distro.id}").canonicalFile
        if (internal.exists()) return internal

        val external = context.getExternalFilesDir("proot-rootfs")?.let { File(it.canonicalFile, distro.id).canonicalFile }
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
        if (marker.exists()) {
            // 增加双重校验：如果标记存在，但关键运行文件缺失，则触发重新安装
            val missing = verifyCriticalBinaries(dir)
            if (missing.isEmpty()) {
                return true
            } else {
                Log.w(TAG, "检测到环境标记存在，但关键文件 $missing 损坏或丢失，强制触发重新安装！")
                marker.delete()
                return false
            }
        }

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
                rootfsDir.deleteRecursively() // 清理可能存在的半吊子残存目录
                rootfsDir.mkdirs()
                val base = if (distro == DistroType.DEBIAN) DEBIAN_ROOTFS_BASE else UBUNTU_ROOTFS_BASE
                emit("正在查询 ${distro.displayName} 最新镜像版本...", 19)
                val url = fetchLatestRootfsUrl(base)
                Log.d(TAG, "rootfs URL: $url")

                val tarFile = File(context.cacheDir.canonicalFile, "${distro.id}-rootfs.tar.xz").canonicalFile
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
                extractTarXzJava(tarFile, rootfsDir)
                tarFile.delete()

                emit("配置系统运行环境...", 92)
                configureRootfs(context, rootfsDir, distro)

                // 解压完立刻校验，缺关键文件就直接清掉重来
                val missingFresh = verifyCriticalBinaries(rootfsDir)
                if (missingFresh.isNotEmpty()) {
                    rootfsDir.deleteRecursively()
                    throw IOException(
                        "根文件系统解压不完整，缺少: ${missingFresh.joinToString("、")}。" +
                            "可能是存储空间不足或解压出错，请卸载重试。"
                    )
                }
            } else {
                emit("${distro.displayName} 已安装，正在校验完整性...", 92)
                fixUsrMergeSymlinks(rootfsDir)

                val missingExisting = verifyCriticalBinaries(rootfsDir)
                if (missingExisting.isNotEmpty()) {
                    throw IOException(
                        "检测到已安装的 ${distro.displayName} 缺少: ${missingExisting.joinToString("、")}，" +
                            "运行环境不完整。请卸载该环境后重新安装"
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
     * 【核心升级】最小完整性实体校验
     * 我们只校验实实在在存在的二进制物理文件。
     * 因为绝对路径的软链接（如 /lib/...）在 Android 宿主机视角下全部是“损坏的死链”，
     * 且部分安全策略限制了软链接的查询。
     * 只要容器对应的实体物理文件存在，PRoot 在启动时通过 --link2symlink 就可以在内存中完美代理它！
     */
    private fun verifyCriticalBinaries(rootfsDir: File): List<String> {
        val mustHave = mapOf(
            "bash 可执行文件" to listOf(
                "usr/bin/bash",
                "bin/bash"                          // 非 usr-merge 布局兜底
            ),
            "动态链接器 ld-linux" to listOf(
                "usr/lib/ld-linux-aarch64.so.1",
                "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                "lib/ld-linux-aarch64.so.1",        // 旧式布局 / tar 绝对路径解压到 lib/
                "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                "lib64/ld-linux-aarch64.so.1"
            ),
            "libc" to listOf(
                "usr/lib/aarch64-linux-gnu/libc.so.6",
                "lib/aarch64-linux-gnu/libc.so.6",  // 旧式布局兜底
                "lib64/libc.so.6"
            )
        )
        return mustHave.filterValues { candidates ->
            candidates.none { relPath ->
                val file = File(rootfsDir, relPath)
                val path = file.toPath()
                // 实体文件存在，或软链接本身存在（即使目标暂时不可解析）
                file.exists() || Files.isSymbolicLink(path) || Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
            }
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

    /**
     * 【终极核心升级】
     * 1. 使用纯 Java 库（Apache Commons Compress）直接在内存流中解压 .tar.xz
     * 2. 【绝对路径链接本地相对化修复】：
     * 如果 tar 包中的符号链接指向绝对路径（以 / 开头，例如指向 /lib/...），我们通过计算，
     * 在物理写入时将其重写为以容器内部为根的相对路径，彻底解决高版本 Android 的写保护和死链接创建失败问题。
     */
    private fun extractTarXzJava(tarXzFile: File, destDir: File) {
    // 收集解压失败或 target 还不存在的硬链接，留待二阶段处理
        data class DeferredLink(val targetFile: File, val linkName: String)
        val deferredHardLinks = mutableListOf<DeferredLink>()
        val destCanonical = destDir.canonicalPath
    
        tarXzFile.inputStream().buffered(64 * 1024).use { fileIn ->
            XZInputStream(fileIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry: TarArchiveEntry? = tarIn.nextEntry
                    var count = 0
                    while (entry != null) {
                        // 【关键修复】去除条目名开头的 '/' 和 './'，
                        // Java 的 File(parent, "/abs") 会忽略 parent 导致路径逃逸，
                        // 导致安全检查 startsWith(destDir) 失败，文件被静默跳过
                        val entryName = entry.name.trimStart('/').let {
                            if (it.startsWith("./")) it.substring(2) else it
                        }
                        if (entryName.isEmpty() || entryName.contains("..")) { entry = tarIn.nextEntry; continue }
    
                        val targetFile = File(destDir, entryName).canonicalFile
                        // 使用 canonicalPath + 分隔符防止兄弟目录误匹配
                        if (!targetFile.canonicalPath.startsWith("$destCanonical/") &&
                            targetFile.canonicalPath != destCanonical) { entry = tarIn.nextEntry; continue }
    
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else if (entry.isSymbolicLink) {
                            // 软链接逻辑不变（省略）
                            try {
                                if (targetFile.exists() || Files.isSymbolicLink(targetFile.toPath())) targetFile.delete()
                                targetFile.parentFile?.mkdirs()
                                var symlinkTarget = entry.linkName
                                if (symlinkTarget.startsWith("/")) {
                                    val parentFile = targetFile.parentFile
                                    if (parentFile != null) {
                                        val relDepth = parentFile.canonicalPath
                                            .removePrefix(destDir.canonicalPath)
                                            .split(File.separator).filter { it.isNotEmpty() }.size
                                        val prefix = if (relDepth == 0) "./" else "../".repeat(relDepth)
                                        symlinkTarget = prefix + symlinkTarget.removePrefix("/")
                                    }
                                }
                                Os.symlink(symlinkTarget, targetFile.absolutePath)
                            } catch (e: Exception) {
                                Log.w(TAG, "符号链接失败: ${entry.name} -> ${entry.linkName} (${e.message})")
                            }
                        } else if (entry.isLink) {
                            val hardLinkTarget = File(destDir, entry.linkName).canonicalFile
                            var linked = false
                            if (hardLinkTarget.exists()) {
                                try {
                                    if (targetFile.exists() || Files.isSymbolicLink(targetFile.toPath())) targetFile.delete()
                                    targetFile.parentFile?.mkdirs()
                                    Os.link(hardLinkTarget.absolutePath, targetFile.absolutePath)
                                    linked = true
                                } catch (e: Exception) {
                                    Log.w(TAG, "硬链接失败（一阶段）: ${entry.name} -> ${entry.linkName} (${e.message})")
                                }
                            }
                            // ← 关键修复：target 不存在或失败，加入延迟列表
                            if (!linked) {
                                deferredHardLinks.add(DeferredLink(targetFile, entry.linkName))
                            }
                        } else {
                            // 普通文件（不变）
                            val parent = targetFile.parentFile
                            if (parent != null && !Files.isSymbolicLink(parent.toPath())) parent.mkdirs()
                            try {
                                if (targetFile.exists()) targetFile.delete()
                                FileOutputStream(targetFile).use { tarIn.copyTo(it) }
                                if (entry.mode and 0x49 != 0) targetFile.setExecutable(true, false)
                            } catch (e: Exception) {
                                Log.w(TAG, "普通文件写入失败: ${entry.name} (${e.message})")
                            }
                        }
    
                        count++
                        if (count % 300 == 0) {
                            val p = 76 + (count / 15000f * 15f).toInt().coerceAtMost(15)
                            emit("正在解压根文件系统 (已展开 $count 个文件)...", p)
                        }
                        entry = tarIn.nextEntry
                    }
                    Log.d(TAG, "一阶段解压完毕，共 $count 条目，待处理硬链接: ${deferredHardLinks.size} 个")
                }
            }
        }
    
        // ── 二阶段：重试所有延迟硬链接 ──────────────────────────────
        if (deferredHardLinks.isNotEmpty()) {
            emit("处理延迟硬链接 (${deferredHardLinks.size} 个)...", 91)
            var retryFailed = 0
            for ((targetFile, linkName) in deferredHardLinks) {
                val hardLinkTarget = File(destDir, linkName).canonicalFile
                try {
                    if (targetFile.exists() || Files.isSymbolicLink(targetFile.toPath())) targetFile.delete()
                    targetFile.parentFile?.mkdirs()
                    if (hardLinkTarget.exists()) {
                        Os.link(hardLinkTarget.absolutePath, targetFile.absolutePath)
                    } else {
                        // target 仍不存在：退化为复制（兜底）
                        hardLinkTarget.copyTo(targetFile, overwrite = true)
                        Log.w(TAG, "硬链接 target 仍缺失，已降级为复制: $linkName -> ${targetFile.name}")
                    }
                } catch (e: Exception) {
                    retryFailed++
                    Log.w(TAG, "延迟硬链接最终失败: ${targetFile.name} -> $linkName (${e.message})")
                }
            }
            Log.d(TAG, "二阶段处理完成，失败: $retryFailed / ${deferredHardLinks.size}")
        }
    }

    private fun configureRootfs(context: Context, rootfsDir: File, distro: DistroType) {
        fun ensureDir(path: String): File {
            val dir = File(rootfsDir, path).canonicalFile
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
            val resolvConf = File(rootfsDir, "etc/resolv.conf").canonicalFile
            if (!resolvConf.exists() || resolvConf.length() == 0L) {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolv.conf 写入失败: ${e.message}")
        }

        // 环境变量脚本
        try {
            File(rootfsDir, "etc/profile.d/scripthub.sh").canonicalFile.writeText(
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
        File(rootfsDir, ".scripthub_installed").canonicalFile.createNewFile()
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
            if (!targetDir.exists()) continue

            val isSymlink = java.nio.file.Files.isSymbolicLink(link.toPath())
            val isRealDir = link.exists() && !isSymlink

            if (isRealDir) {
                // 【关键修复】不再直接删除真实目录！
                // 真实目录可能包含从 tar 解压出来的文件（非 usr-merge 布局的包）
                // 只有当目标目录里已经有完整内容时，才安全地用软链接替换
                Log.d(TAG, "保留真实目录 $name，不替换为软链接，以避免文件丢失")
                continue
            }

            if (!isSymlink && !link.exists()) {
                try {
                    Os.symlink(target, link.absolutePath)
                    Log.d(TAG, "补建 UsrMerge symlink: $name → $target")
                } catch (e: Exception) {
                    Log.w(TAG, "symlink $name → $target 创建失败: ${e.message}")
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
        val prootBin   = getProotBin(context).canonicalPath
        val rootfsPath = getRootfsDir(context, distro).canonicalPath
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
        val dir = File(context.filesDir.canonicalFile, "pr_tmp").canonicalFile
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
     */
    fun buildProotProcess(
        context: Context,
        distro: DistroType,
        bashCommand: String
    ): ProcessBuilder {
        ensureTallocLib(context)
        val cmd       = buildProotCommand(context, distro, bashCommand)
        val libsDir   = getProotLibsDir(context).canonicalPath
        val nativeDir = File(context.applicationInfo.nativeLibraryDir).canonicalPath
        val ldPath    = "$libsDir:$nativeDir"

        val stableTmp = getStableTmpDir(context).canonicalFile

        return ProcessBuilder(cmd).apply {
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

            env.clear()

            // 1. 恢复 Android 必需的核心环境变量
            env.putAll(preservedEnv)
            
            // 2. 注入 PRoot 容器隔离环境变量
            env["HOME"]            = "/root"
            env["TERM"]            = "xterm-256color"
            env["LANG"]            = "C.UTF-8"
            
            // PATH 必须包含真实系统物理路径
            env["PATH"]            = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = ldPath
            
            // 3. 核心修复：临时物理路径强制规范化隔离
            env["PROOT_TMP_DIR"]   = stableTmp.canonicalPath
            env["TMPDIR"]          = stableTmp.canonicalPath
            
            // 4. 清除 LD_PRELOAD
            env.remove("LD_PRELOAD")
        }
    }

    /**
     * 获取 proot-libs 的物理真实目录
     */
    private fun getProotLibsDir(context: Context): File =
        File(context.noBackupFilesDir.canonicalFile, "proot-libs").also { it.mkdirs() }.canonicalFile

    fun ensureTallocLib(context: Context) {
        val dest = File(getProotLibsDir(context), "libtalloc.so.2").canonicalFile
        if (dest.exists() && dest.length() > 0L) return
        try {
            context.assets.open("libtalloc.so.2").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            Log.d(TAG, "libtalloc.so.2 已提取到 ${dest.canonicalPath}")
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
                Check("bash",        listOf("usr/bin/bash")),
                Check("ld-linux",    listOf(
                    "usr/lib/ld-linux-aarch64.so.1",
                    "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
                )),
                Check("libc",        listOf(
                    "usr/lib/aarch64-linux-gnu/libc.so.6"
                ))
            )

            for (check in checks) {
                val found = check.candidates.firstOrNull { 
                    val file = File(rootfsDir, it)
                    file.exists() || Files.isSymbolicLink(file.toPath())
                }
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