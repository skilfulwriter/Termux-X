package com.davik.adbtools.adb // 建议放在这个包名下，或者你项目的对应位置

import android.content.Context
import com.davik.adbtools.cert.CertManager
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbShellResponse
import com.flyfishxu.kadb.shell.AdbShellStream
import com.flyfishxu.kadb.stream.AdbStream
import com.flyfishxu.kadb.stream.AdbSyncStream
import okio.Sink
import okio.Source
import java.io.File

/**
 * Adb 封装类 (基于 Kadb)
 * 封装了 Kadb 的所有公开方法，方便统一调用和管理。
 */
class Adb(private val kadb: Kadb) : AutoCloseable {



    // ========================================================================
    // 核心连接状态与功能
    // ========================================================================

    /**
     * 检查连接是否存活
     */
    fun connectionCheck(): Boolean {
        return kadb.connectionCheck()
    }

    /**
     * 检查是否支持特定功能 (例如 "cmd", "abb_exec" 等)
     */
    fun supportsFeature(feature: String): Boolean {
        return kadb.supportsFeature(feature)
    }

    /**
     * 打开一个到底层服务的流 (例如 "shell:...", "sync:", "tcp:...")
     */
    fun open(destination: String): AdbStream {
        return kadb.open(destination)
    }

    // ========================================================================
    // Shell 命令相关
    // ========================================================================

    /**
     * 执行 Shell 命令并获取一次性结果 (包含输出和退出码)
     */
    fun shell(command: String): AdbShellResponse {
        return kadb.shell(command)
    }

    /**
     * 打开一个交互式的 Shell 流 (适合长时间运行的命令或交互)
     */
    fun openShell(command: String = ""): AdbShellStream {
        return kadb.openShell(command)
    }

    /**
     * 执行 cmd 命令 (Android 7+ 支持)
     */
    fun execCmd(vararg command: String): AdbStream {
        return kadb.execCmd(*command)
    }

    /**
     * 执行 abb 命令 (Android 11+ 支持，更高效的 Binder 调用)
     */
    fun abbExec(vararg command: String): AdbStream {
        return kadb.abbExec(*command)
    }

    // ========================================================================
    // 文件传输 (Push / Pull / Sync)
    // ========================================================================

    /**
     * 推送文件到设备
     */
    fun push(src: File, remotePath: String, mode: Int = 0, lastModifiedMs: Long = src.lastModified()) {
        kadb.push(src, remotePath, mode, lastModifiedMs)
    }

    /**
     * 推送流数据到设备
     */
    fun push(source: Source, remotePath: String, mode: Int, lastModifiedMs: Long) {
        kadb.push(source, remotePath, mode, lastModifiedMs)
    }

    /**
     * 从设备拉取文件到本地文件
     */
    fun pull(dst: File, remotePath: String) {
        kadb.pull(dst, remotePath)
    }

    /**
     * 从设备拉取文件到流
     */
    fun pull(sink: Sink, remotePath: String) {
        kadb.pull(sink, remotePath)
    }

    /**
     * 打开原始的 Sync 流 (用于自定义文件操作)
     */
    fun openSync(): AdbSyncStream {
        return kadb.openSync()
    }

    // ========================================================================
    // 应用管理 (Install / Uninstall)
    // ========================================================================

    /**
     * 安装 APK 文件
     */
    fun install(file: File, vararg options: String) {
        kadb.install(file, *options)
    }

    /**
     * 通过流安装 APK
     */
    fun install(source: Source, size: Long, vararg options: String) {
        kadb.install(source, size, *options)
    }

    /**
     * 批量安装 APK (Split APKs / App Bundle)
     */
    fun installMultiple(apks: List<File>, vararg options: String) {
        kadb.installMultiple(apks, *options)
    }

    /**
     * 卸载应用
     */
    fun uninstall(packageName: String) {
        kadb.uninstall(packageName)
    }

    // ========================================================================
    // 高级功能 (Root / Forward)
    // ========================================================================

    /**
     * 尝试以 Root 权限重启 Adbd
     */
    fun root() {
        kadb.root()
    }

    /**
     * 取消 Root 权限重启 Adbd
     */
    fun unroot() {
        kadb.unroot()
    }

    /**
     * 设置 TCP 端口转发
     */
    fun tcpForward(hostPort: Int, targetPort: Int): AutoCloseable {
        return kadb.tcpForward(hostPort, targetPort)
    }

    // ========================================================================
    // 资源释放
    // ========================================================================

    override fun close() {
        kadb.close()
    }

    // ========================================================================
    // 静态工厂方法 (创建实例)
    // ========================================================================

    companion object {
        /**
         * 创建 Adb 连接实例
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            host: String,
            port: Int,
            connectTimeout: Int = 0,
            socketTimeout: Int = 0
        ): Adb {
            val kadb = Kadb.create(host, port, connectTimeout, socketTimeout)
            return Adb(kadb)
        }

        /**
         * 尝试连接并测试是否可用
         */
        @JvmStatic
        fun tryConnection(host: String, port: Int): Adb? {
            val kadb = Kadb.tryConnection(host, port) ?: return null
            return Adb(kadb)
        }
        @JvmStatic
        fun init(context: Context) {
            CertManager(context).initCert()
        }

        /**
         * 执行无线配对
         */
        suspend fun pair(host: String, port: Int, pairingCode: String): Boolean {
            return try {
                Kadb.pair(host, port, pairingCode)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}