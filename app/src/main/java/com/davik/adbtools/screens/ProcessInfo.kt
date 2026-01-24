// 文件名：com/davik/adbtools/screens/ProcessInfo.kt
package com.davik.adbtools.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davik.adbtools.adb.AdbConnectionManager
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// --- 缓存系统 (保持不变) ---
object AppResourceCache {
    val iconCache = LruCache<String, ImageBitmap>(200)
    val nameCache = mutableMapOf<String, String>()
    var isServerPushed = false
}

data class MemoryStats(
    val total: String = "0 MB",
    val used: String = "0 MB",
    val free: String = "0 MB",
    val ratio: Float = 0f
)

data class ProcessInfo(
    val pid: String,
    val packageName: String,
    val memoryUsage: Long,
    val memoryFormatted: String,
    var appName: MutableState<String>,
    var icon: MutableState<ImageBitmap?> = mutableStateOf(null),
    var hasResourceLoaded: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessManagerScreen(ip: String, initialConnection: Adb?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dadbConnection by remember { mutableStateOf(AdbConnectionManager.getConnection(ip) ?: initialConnection) }

    var isLoading by remember { mutableStateOf(true) }
    var processList by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var memoryStats by remember { mutableStateOf<MemoryStats>(MemoryStats()) }
    val diskCacheDir = File(context.cacheDir, "app_resource_cache").apply { if (!exists()) mkdirs() }
    val listState = rememberLazyListState()

    // --- 辅助函数 (保持不变) ---
    fun formatMemorySize(kb: Long): String {
        val mb = kb / 1024f
        return when {
            mb >= 1024 -> String.format(Locale.US, "%.2f GB", mb / 1024f)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            else -> "$kb KB"
        }
    }

    fun extractMemValue(raw: String, key: String): Long {
        return try {
            val line = raw.lines().find { it.contains(key, ignoreCase = true) } ?: return 0L
            val match = Regex("(\\d[\\d,.]*)").find(line)
            val valueString = match?.value?.replace(",", "")?.replace(".", "") ?: "0"
            valueString.filter { it.isDigit() }.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
    }

    suspend fun ensureServerReady() {
        if (AppResourceCache.isServerPushed) return
        withContext(Dispatchers.IO) {
            val check = dadbConnection?.shell("ls /data/local/tmp/server.apk")?.allOutput ?: ""
            if (check.contains("No such file")) {
                val bytes = context.assets.open("server.apk").use { it.readBytes() }
                val destPath = "/data/local/tmp/server.apk"
                dadbConnection?.shell("rm -f $destPath")
                for (i in bytes.indices step 16384) {
                    val end = minOf(i + 16384, bytes.size)
                    val hex = bytes.sliceArray(i until end).joinToString("") { "\\x%02x".format(it) }
                    dadbConnection?.shell("printf \"$hex\" >> $destPath")
                }
                dadbConnection?.shell("chmod 777 $destPath")
            }
            AppResourceCache.isServerPushed = true
        }
    }

    fun loadResourceIfNeeded(process: ProcessInfo) {
        if (process.hasResourceLoaded) return
        process.hasResourceLoaded = true

        val pkg = process.packageName
        AppResourceCache.nameCache[pkg]?.let { process.appName.value = it }
        AppResourceCache.iconCache.get(pkg)?.let { process.icon.value = it; return }

        scope.launch(Dispatchers.IO) {
            val cacheIcon = File(diskCacheDir, "$pkg.png")
            val cacheName = File(diskCacheDir, "$pkg.txt")
            if (cacheIcon.exists()) {
                val bitmap = BitmapFactory.decodeFile(cacheIcon.absolutePath)?.asImageBitmap()
                val name = if (cacheName.exists()) cacheName.readText() else null
                withContext(Dispatchers.Main) {
                    if (bitmap != null) { process.icon.value = bitmap; AppResourceCache.iconCache.put(pkg, bitmap) }
                    if (name != null) { process.appName.value = name; AppResourceCache.nameCache[pkg] = name }
                }
                if (bitmap != null) return@launch
            }

            try {
                val remotePath = "/data/local/tmp/appicons/$pkg.png"
                val stream = dadbConnection?.open("shell:cat $remotePath")
                val bitmap = BitmapFactory.decodeStream(stream?.source?.inputStream())
                stream?.close()
                bitmap?.let {
                    val img = it.asImageBitmap()
                    withContext(Dispatchers.Main) { process.icon.value = img; AppResourceCache.iconCache.put(pkg, img) }
                    FileOutputStream(cacheIcon).use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }
                }
            } catch (e: Exception) {}
        }
    }

    suspend fun refreshData() {
        if (dadbConnection == null) return
        isLoading = true
        withContext(Dispatchers.IO) {
            val memJob = async { dadbConnection?.shell("dumpsys meminfo")?.allOutput ?: "" }

            launch {
                ensureServerReady()
                val cmd = "export CLASSPATH=/data/local/tmp/server.apk; app_process /data/local/tmp com.davik.adbserver.Server /data/local/tmp 2>&1"
                dadbConnection?.shell(cmd)?.allOutput?.lines()?.forEach { line ->
                    if (line.trim().startsWith("{")) {
                        try {
                            val json = JSONObject(line)
                            val p = json.getString("package")
                            val l = json.getString("label")
                            AppResourceCache.nameCache[p] = l
                            File(diskCacheDir, "$p.txt").writeText(l)
                        } catch (e: Exception) {}
                    }
                }
            }

            val memRaw = memJob.await()
            val totalKb = extractMemValue(memRaw, "Total RAM")
            val freeKb = extractMemValue(memRaw, "Free RAM")
            val usedKb = if (totalKb > freeKb) totalKb - freeKb else 0L

            val psOutput = dadbConnection?.shell("ps -A -o PID,RSS,NAME")?.allOutput ?: ""
            val newList = psOutput.lines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val pkg = parts.last()
                    if (pkg.contains(".") && !pkg.startsWith("/")) {
                        val rss = parts[1].toLongOrNull() ?: 0L
                        ProcessInfo(
                            pid = parts[0],
                            packageName = pkg,
                            memoryUsage = rss,
                            memoryFormatted = formatMemorySize(rss),
                            appName = mutableStateOf(AppResourceCache.nameCache[pkg] ?: pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() })
                        )
                    } else null
                } else null
            }.sortedByDescending { it.memoryUsage }

            withContext(Dispatchers.Main) {
                memoryStats = MemoryStats(formatMemorySize(totalKb), formatMemorySize(usedKb), formatMemorySize(freeKb), if(totalKb>0) usedKb.toFloat()/totalKb else 0f)
                processList = newList
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("进程管理", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    // 顶部也可以保留一个刷新，方便操作
                    IconButton(onClick = { scope.launch { refreshData() } }) { Icon(Icons.Outlined.Refresh, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F7FA)) // 浅灰背景
        ) {
            // 1. 内存状态大卡片
            MemoryDashboard(memoryStats) { scope.launch { refreshData() } }

            Spacer(modifier = Modifier.height(8.dp))

            // 列表标题
            Text(
                text = "运行进程 (${processList.size})",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF5B93E6)) }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(processList, key = { it.pid + it.packageName }) { process ->
                        SideEffect { loadResourceIfNeeded(process) }

                        ProcessListItem(process = process, onKill = {
                            scope.launch {
                                withContext(Dispatchers.IO) { dadbConnection?.shell("am force-stop ${process.packageName}") }
                                processList = processList.filter { it.pid != process.pid }
                                Toast.makeText(context, "已结束: ${process.appName.value}", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
        }
    }
}

// ✅ 新设计的仪表盘卡片
@Composable
fun MemoryDashboard(stats: MemoryStats, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Memory, null, tint = Color(0xFF5B93E6), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("内存占用", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // 占用百分比
                Text(
                    text = "${(stats.ratio * 100).toInt()}%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if(stats.ratio > 0.85f) Color.Red else Color(0xFF5B93E6)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { stats.ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = if(stats.ratio > 0.85f) Color(0xFFFF5252) else Color(0xFF5B93E6),
                trackColor = Color(0xFFF0F0F0)
            )

            Spacer(Modifier.height(20.dp))

            // 底部详细数据
            Row(Modifier.fillMaxWidth()) {
                MemoryDetailItem(Modifier.weight(1f), "总内存", stats.total)
                MemoryDetailItem(Modifier.weight(1f), "已用", stats.used)
                MemoryDetailItem(Modifier.weight(1f), "空闲", stats.free)
            }
        }
    }
}

@Composable
fun MemoryDetailItem(modifier: Modifier, label: String, value: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

// ✅ 优化后的列表项
@Composable
fun ProcessListItem(process: ProcessInfo, onKill: () -> Unit) {
    // 内存占用超过 300MB 显示为警告色
    val isHeavy = (process.memoryUsage / 1024) > 300

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 图标容器
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F7FA)),
                contentAlignment = Alignment.Center
            ) {
                process.icon.value?.let {
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(32.dp))
                } ?: Icon(Icons.Default.Android, null, tint = Color.LightGray)
            }

            Spacer(Modifier.width(16.dp))

            // 2. 文字信息
            Column(Modifier.weight(1f)) {
                Text(
                    text = process.appName.value,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = process.packageName,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // 3. 内存数值
            Text(
                text = process.memoryFormatted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if(isHeavy) Color(0xFFFF9800) else Color.Gray
            )

            Spacer(Modifier.width(16.dp))

            // 4. 结束按钮
            IconButton(
                onClick = onKill,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFFFEBEE), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "结束",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}