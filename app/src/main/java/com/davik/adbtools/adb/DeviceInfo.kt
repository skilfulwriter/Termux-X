package com.davik.adbtools.adb

import android.util.Log
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext // 引入这个

data class DeviceInfo(
    val model: String = "未知型号",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val sdkVersion: String = "",
    var ipsum: String=""
) {
    val displayName: String
        get() = "$manufacturer $model".trim()
}

// 【修改点】加上 withContext(Dispatchers.IO)
suspend fun fetchDeviceInfo(dadb: Adb, ip: String): DeviceInfo {
    return withContext(Dispatchers.IO) { // 强制在 IO 线程执行

        fun getProp(key: String): String {
            return try {
                // dadb.shell 是网络操作，必须在后台线程
                val response = dadb.shell("getprop $key")
                if (response.exitCode == 0) response.output.trim() else ""
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        val model = getProp("ro.product.model")
        val manufacturer = getProp("ro.product.manufacturer")
        val version = getProp("ro.build.version.release")
        val sdk = getProp("ro.build.version.sdk")

        Log.e("DeviceInfo", "$model $manufacturer $version $sdk")

        // 返回结果
        DeviceInfo(model, manufacturer, version, sdk, ip)
    }
}