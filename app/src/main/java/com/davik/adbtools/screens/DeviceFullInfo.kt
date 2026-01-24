// 文件名：com/davik/adbtools/screens/DeviceFullInfo.kt
package com.davik.adbtools.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// 数据模型
data class DeviceFullInfo(
    val model: String = "读取中...",
    val androidVer: String = "...",
    val resolution: String = "...",
    val cpuModel: String = "...",
    val cpuBrand: String = "...",
    val ramUsed: String = "0",
    val ramTotal: String = "0",
    val storageUsed: String = "0",
    val storageTotal: String = "0",
    val storagePercent: Float = 0f,
    val serial: String = "...",
    val sdk: String = "...",
    val density: String = "...",
    val brightness: String = "...", // 改名为 brightness 更准确
    val isCharging: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    ip: String,
    dadbConnection: Adb?,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var info by remember { mutableStateOf(DeviceFullInfo()) }

    LaunchedEffect(Unit) {
        if (dadbConnection != null) {
            info = fetchFullDeviceInfo(dadbConnection)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备详情", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F7FA))
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            DeviceHeader(info)
            Spacer(modifier = Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth()) {
                InfoGridCard(
                    modifier = Modifier.weight(1f),
                    title = "Android系统",
                    value = info.androidVer,
                    subValue = "API ${info.sdk}",
                    icon = Icons.Default.Android,
                    iconColor = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(12.dp))
                InfoGridCard(
                    modifier = Modifier.weight(1f),
                    title = "显示屏",
                    value = info.resolution,
                    subValue = "${info.density} dpi",
                    icon = Icons.Default.Smartphone,
                    iconColor = Color(0xFF2196F3)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                // ✅ 修改：显示屏幕亮度
                InfoGridCard(
                    modifier = Modifier.weight(1f),
                    title = "电池信息", // 标题改为亮度
                    value = info.brightness, // 显示 0-255 的数值
                    subValue = if(info.isCharging) "接通电源" else "使用电池",
                    icon = Icons.Default.BrightnessMedium, // 图标改为亮度
                    iconColor = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(12.dp))

                InfoGridCard(
                    modifier = Modifier.weight(1f),
                    title = "处理器",
                    value = info.cpuModel,
                    subValue = info.cpuBrand,
                    icon = Icons.Default.Memory,
                    iconColor = Color(0xFF9C27B0)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("存储空间", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            StorageCard(info)

            Spacer(modifier = Modifier.height(20.dp))
            Text("更多参数", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DetailItem(Icons.Outlined.PermDeviceInformation, "设备型号", info.model)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F0F0))
                    DetailItem(Icons.Outlined.Fingerprint, "序列号 (SN)", info.serial)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F0F0))
                    DetailItem(Icons.Outlined.Code, "SDK 版本", "Android SDK ${info.sdk}")
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// --- UI 组件 ---

@Composable
fun DeviceHeader(info: DeviceFullInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF5B93E6)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(info.model, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("S/N: ${info.serial}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun InfoGridCard(modifier: Modifier = Modifier, title: String, value: String, subValue: String, icon: ImageVector, iconColor: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subValue, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
    }
}

@Composable
fun StorageCard(info: DeviceFullInfo) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Color(0xFFE3F2FD), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Storage, null, tint = Color(0xFF2196F3)) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("内部存储", fontWeight = FontWeight.Bold); Text("共 ${info.storageTotal} GB", fontSize = 12.sp, color = Color.Gray) }
                }
                Text("${(info.storagePercent * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2196F3))
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { info.storagePercent }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFF2196F3), trackColor = Color(0xFFE3F2FD))
            Spacer(Modifier.height(8.dp))
            Text("已用 ${info.storageUsed} GB", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun DetailItem(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Medium)
    }
}

private fun String.toProperCase(): String {
    if (this.isEmpty()) return ""
    return this.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

private fun inferBrandFromClue(clue: String): String {
    val c = clue.lowercase()
    return when {
        c.contains("qualcomm") || c.contains("qcom") || c.contains("msm") || c.contains("sdm") || c.contains("sm") || c.contains("apq") -> "Qualcomm"
        c.contains("mediatek") || c.contains("mtk") || c.contains("mt") || c.contains("dimensity") || c.contains("helio") -> "MediaTek"
        c.contains("samsung") || c.contains("exynos") || c.contains("s5e") -> "Samsung"
        c.contains("hisilicon") || c.contains("kirin") || c.contains("hi3") || c.contains("hi6") -> "HiSilicon"
        c.contains("unisoc") || c.contains("spreadtrum") || c.contains("sc") -> "Unisoc"
        c.contains("rockchip") || c.contains("rk") -> "Rockchip"
        c.contains("amlogic") -> "Amlogic"
        else -> ""
    }
}

suspend fun fetchFullDeviceInfo(dadb: Adb): DeviceFullInfo {
    return withContext(Dispatchers.IO) {
        try {
            val brandRaw = dadb.shell("getprop ro.product.brand").allOutput.trim()
            val modelRaw = dadb.shell("getprop ro.product.model").allOutput.trim()
            val serial = dadb.shell("getprop ro.serialno").allOutput.trim()
            val brandProper = brandRaw.toProperCase()

            val fullModel = if (modelRaw.startsWith(brandRaw, ignoreCase = true)) {
                val suffix = modelRaw.substring(brandRaw.length).trim()
                if (suffix.isEmpty()) brandProper else "$brandProper ${suffix.toProperCase()}"
            } else { "$brandProper $modelRaw" }

            var detectedBrand = ""
            val propSocMan = dadb.shell("getprop ro.soc.manufacturer").allOutput.trim()
            if (propSocMan.isNotEmpty() && propSocMan.lowercase() != "unknown") {
                detectedBrand = propSocMan
            }

            if (detectedBrand.isEmpty()) {
                val cpuInfo = dadb.shell("cat /proc/cpuinfo").allOutput
                val hardwareLine = cpuInfo.lines().find { it.contains("Hardware") }
                if (hardwareLine != null) {
                    val hwValue = hardwareLine.substringAfter(":").trim()
                    detectedBrand = inferBrandFromClue(hwValue)
                }
            }

            val platform = dadb.shell("getprop ro.board.platform").allOutput.trim()
            val hardware = dadb.shell("getprop ro.hardware").allOutput.trim()

            if (detectedBrand.isEmpty()) {
                val clue = "$platform $hardware"
                detectedBrand = inferBrandFromClue(clue)
            }

            val finalCpuBrand = if (detectedBrand.isNotEmpty()) detectedBrand.toProperCase() else "SoC"
            val finalCpuModel = platform.ifEmpty { hardware.ifEmpty { "Unknown" } }

            val ver = dadb.shell("getprop ro.build.version.release").allOutput.trim()
            val sdk = dadb.shell("getprop ro.build.version.sdk").allOutput.trim()
            val density = dadb.shell("wm density").allOutput.replace("Physical density:", "").trim()
            val wmSize = dadb.shell("wm size").allOutput
            val resolution = if (wmSize.contains(":")) wmSize.split(":")[1].trim() else wmSize

            val dfData = dadb.shell("df -h /data").allOutput
            val dfLines = dfData.trim().lines()
            var sTotal = "0"; var sUsed = "0"; var sPercent = 0f
            if (dfLines.size >= 2) {
                val parts = dfLines.last().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    sTotal = parts[1].replace("G", "")
                    sUsed = parts[2].replace("G", "")
                    val percentStr = parts[4].replace("%", "")
                    sPercent = (percentStr.toFloatOrNull() ?: 0f) / 100f
                }
            }

            // ✅ 修改：使用 screen_brightness 获取数值
            val brightnessVal = dadb.shell("settings get system screen_brightness").allOutput.trim()
            val batStatus = dadb.shell("dumpsys battery | grep 'AC powered'").allOutput.contains("true")

            DeviceFullInfo(fullModel, ver, resolution, finalCpuModel, finalCpuBrand, "0", "0", sUsed, sTotal, sPercent, serial, sdk, density, if(brightnessVal.isEmpty()) "0" else brightnessVal, batStatus)
        } catch (e: Exception) {
            e.printStackTrace()
            DeviceFullInfo(model = "连接失败")
        }
    }
}