package com.davik.adbtools.adb


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ✅ 全局连接管理器：确保切后台或页面跳转时连接不丢失
 */
object AdbConnectionManager {
    // 存储所有活跃连接，Key 为 IP:Port
    private val connections = mutableMapOf<String, Adb>()

    fun setConnection(ip: String, dadb: Adb) {
        connections[ip]?.close() // 覆盖前先关闭旧的
        connections[ip] = dadb
    }

    fun getConnection(ip: String): Adb? = connections[ip]

    fun removeConnection(ip: String) {
        connections[ip]?.close()
        connections.remove(ip)
    }

    fun getAllConnections(): Map<String, Adb> = connections

    // ✅ 检查 Socket 活性
    suspend fun isConnected(ip: String): Boolean {
        return withContext(Dispatchers.IO) {
            val conn = connections[ip]
            if (conn == null) return@withContext false
            try {
                conn.shell("echo 1")
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}