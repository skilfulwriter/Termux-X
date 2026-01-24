package com.davik.adbtools.cert

import android.content.Context
import android.content.SharedPreferences
import com.flyfishxu.kadb.cert.KadbCert
import java.math.BigInteger
import java.security.SecureRandom

class CertManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("adb_cert_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CERT = "encoded_cert"
        private const val KEY_PRIVATE_KEY = "encoded_private_key"
        private const val EXPIRY_YEARS = 1L
    }

    /**
     * 初始化证书方法
     * 逻辑：判断本地是否已存储证书。
     * - 如果没有：生成一个新的 1 年期证书并存入本地，然后执行 set。
     * - 如果有：直接从本地读取并执行 set。
     */
    fun initCert() {
        val savedCert = prefs.getString(KEY_CERT, null)
        val savedKey = prefs.getString(KEY_PRIVATE_KEY, null)

        if (savedCert.isNullOrEmpty() || savedKey.isNullOrEmpty()) {
            // 1. 没生成过，执行生成逻辑
            generateAndSaveNewCert()
        } else {
            // 2. 已经生成过，直接加载到 Kadb
            loadExistingCert(savedCert, savedKey)
        }
    }

    /**
     * 生成并保存新证书
     */
    private fun generateAndSaveNewCert() {
        // 计算 1 年后的时间戳
        val oneYearInMs = 365L * 24 * 60 * 60 * 1000
        val expiryTime = System.currentTimeMillis() + oneYearInMs
        val mySerialNumber = BigInteger(64, SecureRandom())

        // 调用 KadbCert 生成
        val keyPair = KadbCert.get(
            notAfter = expiryTime,
            cn = "AdbToolsClient",
            ou = "adbTools",
            o = "adbTools",
            l = "adbTools",
            st = "adbTools",
            c = "CN",
            serialNumber = mySerialNumber
        )

        val certBytes = keyPair.first
        val keyBytes = keyPair.second

        // 存储到 SharedPreferences (转为 String)
        prefs.edit().apply {
            putString(KEY_CERT, certBytes.decodeToString())
            putString(KEY_PRIVATE_KEY, keyBytes.decodeToString())
            apply()
        }

        // 设置到当前运行环境
        KadbCert.set(certBytes, keyBytes)
    }

    /**
     * 从本地存储加载证书到 Kadb
     */
    private fun loadExistingCert(certStr: String, keyStr: String) {
        try {
            val certBytes = certStr.encodeToByteArray()
            val keyBytes = keyStr.encodeToByteArray()

            // 将读取到的证书设置给 Kadb
            KadbCert.set(certBytes, keyBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果读取或设置失败（比如数据损坏），重新生成
            generateAndSaveNewCert()
        }
    }

    /**
     * 清除本地证书（如需重新授权时使用）
     */
    fun clearCert() {
        prefs.edit().clear().apply()
    }
}