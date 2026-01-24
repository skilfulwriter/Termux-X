package com.davik.adbtools.tools

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object LanScanner {

    /**
     * 核心方法：扫描当前局域网段所有开放 5555 端口的设备
     */
    suspend fun scan(context: Context): List<String> = withContext(Dispatchers.IO) {
        // 1. 获取本机 IP
        val myIp = getLocalIpAddress(context)
        if (myIp == null || myIp == "0.0.0.0") {
            return@withContext emptyList()
        }

        // 2. 提取网段前缀 (例如 "192.168.2.")
        // 假设是 IPv4，取前三段
        val prefix = myIp.substring(0, myIp.lastIndexOf(".") + 1)

        // 3. 并发扫描 1~255
        // 使用 map + async 启动 255 个并行任务
        val jobs = (1..255).map { i ->
            async {
                val targetIp = "$prefix$i"
                // 排除本机自己 (可选)
                if (targetIp == myIp) return@async null

                // 尝试连接
                if (isPortOpen(targetIp, 5555)) {
                    targetIp
                } else {
                    null
                }
            }
        }

        // 4. 等待所有任务结束，过滤掉 null，返回成功列表
        jobs.awaitAll().filterNotNull()
    }

    /**
     * 检查指定 IP 的端口是否开放
     * timeout 设置为 200ms-500ms 足够局域网使用，太长会拖慢整体速度
     */
    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            // 建立连接，超时设置 300ms
            socket.connect(InetSocketAddress(ip, port), 300)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取本机 WiFi IP 地址
     */
    private fun getLocalIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return null
            
            // 将 int 转换为 ip 字符串
            return "${ipInt and 0xff}.${ipInt shr 8 and 0xff}.${ipInt shr 16 and 0xff}.${ipInt shr 24 and 0xff}"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}