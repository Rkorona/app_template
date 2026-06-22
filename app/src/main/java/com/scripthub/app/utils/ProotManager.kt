package com.scripthub.app.utils

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
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

    // Termux apt 仓库包索引 - 用于查询 proot 最新版本
    private const val TERMUX_PACKAGES_ARM64 =
        "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages"
    private const val TERMUX_PACKAGES_ARMV7 =
        "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-arm/Packages"
    private const val TERMUX_APT_BASE =
        "https://packages.termux.dev/apt/termux-main/"

    // LXC 官方镜像 - current 指向最新每日构建
    private const val DEBIAN_ROOTFS_URL =
        "https://images.linuxcontainers.org/images/debian/bookworm/arm64/default/current/rootfs.tar.xz"
    private const val UBUNTU_ROOTFS_URL =
        "https://images.linuxcontainers.org/images/ubuntu/jammy/arm64/default/current/rootfs.tar.xz"

    private val _progress = MutableStateFlow(SetupProgress())
    val progress: StateFlow<SetupProgress> = _progress

    fun getProotBin(context: Context): File =
        File(context.filesDir, "proot/proot")

    fun getRootfsDir(context: Context, distro: DistroType): File =
        File(context.getExternalFilesDir("proot-rootfs"), distro.id)

    fun isProotReady(context: Context): Boolean {
        val bin = getProotBin(context)
        return bin.exists() && bin.canExecute()
    }

    fun isDistroInstalled(context: Context, distro: DistroType): Boolean {
        val dir = getRootfsDir(context, distro)
        return dir.exists() && File(dir, "bin/bash").exists()
    }

    suspend fun setup(context: Context, distro: DistroType) = withContext(Dispatchers.IO) {
        try {
            _progress.value = SetupProgress()

            if (!isProotReady(context)) {
                emit("正在从 Termux 仓库查询 proot 版本...", 3)
                downloadProotFromApt(context)
                emit("proot 引擎就绪", 18)
            } else {
                emit("proot 引擎已就绪，跳过下载", 18)
            }

            val rootfsDir = getRootfsDir(context, distro)
            if (!isDistroInstalled(context, distro)) {
                rootfsDir.mkdirs()
                val url = if (distro == DistroType.DEBIAN) DEBIAN_ROOTFS_URL else UBUNTU_ROOTFS_URL
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

    // ─── proot 从 apt 包仓库下载并提取 ───────────────────────────────────────

    private fun downloadProotFromApt(context: Context) {
        val isArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
        val arch    = if (isArm64) "aarch64" else "arm"
        val packagesUrl = if (isArm64) TERMUX_PACKAGES_ARM64 else TERMUX_PACKAGES_ARMV7

        // 1. 查询包索引，找到 proot 的 .deb 路径
        emit("正在查询 proot 最新版本 ($arch)...", 4)
        val packagesText = httpGetText(packagesUrl)
        val debRelPath   = parseDebPath(packagesText, "proot")
            ?: throw IOException("在 Termux 仓库中未找到 proot 包，请检查网络")
        val debUrl = "$TERMUX_APT_BASE$debRelPath"
        val debName = debRelPath.substringAfterLast('/')
        Log.d(TAG, "proot .deb URL: $debUrl")

        // 2. 下载 .deb 到缓存目录
        emit("正在下载 $debName ...", 6)
        val debFile = File(context.cacheDir, "proot.deb")
        downloadFile(debUrl, debFile)

        // 3. 从 .deb (AR 格式) 中提取 data.tar.xz
        emit("正在解析 .deb 包...", 11)
        val dataTarXz = File(context.cacheDir, "proot-data.tar.xz")
        extractArMember(debFile, "data.tar", dataTarXz)
        debFile.delete()

        // 4. 从 data.tar.xz 中提取 proot 二进制
        emit("正在提取 proot 二进制...", 14)
        val prootDir = File(context.filesDir, "proot").also { it.mkdirs() }
        val prootBin = File(prootDir, "proot")

        // 路径: ./data/data/com.termux/files/usr/bin/proot → 7 层目录前缀
        val binPath = "./data/data/com.termux/files/usr/bin/proot"
        val process = ProcessBuilder(
            "tar", "-xJf", dataTarXz.absolutePath,
            "-C", prootDir.absolutePath,
            "--strip-components=7",
            binPath
        ).redirectErrorStream(true).start()
        val output   = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        dataTarXz.delete()

        if (exitCode != 0 || !prootBin.exists()) {
            throw IOException("proot 二进制提取失败 (exit $exitCode): $output")
        }
        prootBin.setExecutable(true, false)
        Log.d(TAG, "proot 就绪: ${prootBin.absolutePath} (${prootBin.length()} bytes)")
    }

    /**
     * 解析 Termux Packages 文本，返回指定包的 Filename 字段值。
     * 格式：多个段落，段落间以空行分隔，每段描述一个包。
     */
    private fun parseDebPath(packagesText: String, packageName: String): String? {
        val blocks = packagesText.split("\n\n")
        for (block in blocks) {
            val fields = mutableMapOf<String, String>()
            for (line in block.lines()) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    fields[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
                }
            }
            if (fields["Package"] == packageName) {
                return fields["Filename"]
            }
        }
        return null
    }

    /**
     * 解析 AR 归档文件（.deb 的外层格式），提取名称以 memberPrefix 开头的成员到 dest。
     *
     * AR 格式规范：
     *   - Magic:  "!<arch>\n"（8 字节）
     *   - 每个成员 60 字节头 + 数据（奇数长度时末尾补 1 字节 '\n'）
     *     头部布局：Name[16] Mtime[12] UID[6] GID[6] Mode[8] Size[10] End[2]
     */
    private fun extractArMember(arFile: File, memberPrefix: String, dest: File) {
        arFile.inputStream().buffered(64 * 1024).use { input ->
            // 校验 magic
            val magic = input.readNBytes(8)
            if (String(magic) != "!<arch>\n") {
                throw IOException("文件不是有效的 AR 归档（.deb 格式异常）")
            }

            val headerBuf = ByteArray(60)
            while (true) {
                val read = input.readFully(headerBuf)
                if (read < 60) break

                val name = String(headerBuf, 0, 16).trim().trimEnd('/')
                val size = String(headerBuf, 48, 10).trim().toLongOrNull() ?: break

                if (name.startsWith(memberPrefix)) {
                    // 写出到目标文件
                    FileOutputStream(dest).use { out ->
                        var remaining = size
                        val buf = ByteArray(32 * 1024)
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val n = input.read(buf, 0, toRead)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                    return
                } else {
                    // 跳过：数据 + 可能的 1 字节对齐填充
                    val skip = size + (size % 2)
                    input.skipFully(skip)
                }
            }
            throw IOException("在 .deb 包中未找到成员: $memberPrefix")
        }
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

    private fun extractTarXz(tarFile: File, destDir: File) {
        val process = ProcessBuilder(
            "tar", "-xJf", tarFile.absolutePath, "-C", destDir.absolutePath
        )
            .redirectErrorStream(true)
            .start()
        val output   = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IOException("tar 解压失败 (exit $exitCode):\n$output")
        }
    }

    private fun configureRootfs(context: Context, rootfsDir: File, distro: DistroType) {
        File(rootfsDir, "etc").mkdirs()

        val resolvConf = File(rootfsDir, "etc/resolv.conf")
        if (!resolvConf.exists() || resolvConf.length() == 0L) {
            resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        }

        File(rootfsDir, "etc/profile.d").mkdirs()
        File(rootfsDir, "etc/profile.d/scripthub.sh").writeText(
            """
            export HOME=/root
            export TERM=xterm-256color
            export LANG=C.UTF-8
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export SCRIPTS_DIR=/data/scripts
            """.trimIndent()
        )

        File(rootfsDir, "root").mkdirs()
        File(rootfsDir, "tmp").mkdirs()
        File(rootfsDir, "data/scripts").mkdirs()
    }

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
            "-b", "$scriptsDir:/data/scripts",
            "-w", "/root",
            "/bin/bash", "--login", "-c", bashCommand
        )
    }
}

// ─── InputStream 扩展工具 ────────────────────────────────────────────────────

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

/** 精确读取 count 字节到缓冲区，返回实际读取字节数 */
private fun InputStream.readFully(buf: ByteArray): Int {
    var offset = 0
    while (offset < buf.size) {
        val n = read(buf, offset, buf.size - offset)
        if (n < 0) break
        offset += n
    }
    return offset
}

/** 跳过精确字节数 */
private fun InputStream.skipFully(n: Long) {
    var remaining = n
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
            // skip() 返回 0 时用 read() 消耗
            if (read() < 0) break
            remaining--
        } else {
            remaining -= skipped
        }
    }
}
