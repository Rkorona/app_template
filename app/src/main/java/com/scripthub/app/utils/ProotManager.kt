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

    private const val PROOT_URL_ARM64 =
        "https://github.com/termux/proot/releases/download/v5.4.0/proot-aarch64"
    private const val PROOT_URL_ARMV7 =
        "https://github.com/termux/proot/releases/download/v5.4.0/proot-arm"

    private const val DEBIAN_ROOTFS_URL =
        "https://github.com/termux/proot-distro/releases/download/v4.19.0/debian-aarch64-pd-v4.19.0.tar.xz"
    private const val UBUNTU_ROOTFS_URL =
        "https://cloud-images.ubuntu.com/minimal/releases/jammy/release/ubuntu-22.04-minimal-cloudimg-arm64-root.tar.xz"

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
                emit("正在下载 proot 核心引擎...", 5)
                downloadProot(context)
                emit("proot 引擎就绪", 15)
            } else {
                emit("proot 引擎已就绪，跳过下载", 15)
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

    private fun downloadProot(context: Context) {
        val prootDir = File(context.filesDir, "proot")
        prootDir.mkdirs()
        val prootBin = File(prootDir, "proot")
        val url = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) PROOT_URL_ARM64 else PROOT_URL_ARMV7
        downloadFile(url, prootBin)
        prootBin.setExecutable(true, false)
    }

    private fun downloadFile(
        urlStr: String,
        dest: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout    = 300_000
        conn.instanceFollowRedirects = true
        conn.connect()

        if (conn.responseCode !in 200..299) {
            throw IOException("HTTP ${conn.responseCode} 下载失败: $urlStr")
        }

        val total = conn.contentLengthLong
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
            "tar", "-xJf", tarFile.absolutePath, "-C", destDir.absolutePath, "--strip-components=0"
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
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
