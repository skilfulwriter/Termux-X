// 文件名：com/davik/adbtools/screens/ToolItem.kt (实际是 UsefulToolsScreen)
package com.davik.adbtools.screens

import android.graphics.BitmapFactory
import android.widget.MediaController // ✅ 修复：导入 MediaController
import android.widget.VideoView       // ✅ 修复：导入 VideoView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas // ✅ 修复：导入 Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path // ✅ 修复：导入 Path
import androidx.compose.ui.graphics.StrokeCap // ✅ 修复：导入 StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke // ✅ 修复：导入 Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView // ✅ 修复：导入 AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.davik.adbtools.adb.AdbConnectionManager
import com.davik.adbtools.components.FileSaver
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- 数据模型 ---
data class ToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val themeColor: Color,
    val action: () -> Unit
)

// 性能数据模型
data class PerfData(val cpu: Float, val ramUsed: Long, val ramTotal: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsefulToolsScreen(
    ip: String,
    initialConnection: Adb?,
    onBack: () -> Unit,
    onOpenMirror: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var dadbConnection by remember { mutableStateOf(AdbConnectionManager.getConnection(ip) ?: initialConnection) }

    // --- 状态管理 ---
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("处理中...") }

    // 弹窗状态
    var showRebootDialog by remember { mutableStateOf(false) }
    var showTopActivityDialog by remember { mutableStateOf(false) }
    var topActivityInfo by remember { mutableStateOf("") }

    var showScreenshotDialog by remember { mutableStateOf(false) }
    var screenshotBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    var showVideoPreviewDialog by remember { mutableStateOf(false) }
    var videoPreviewFile by remember { mutableStateOf<File?>(null) }

    var showLogPreviewDialog by remember { mutableStateOf(false) }
    var logPreviewFile by remember { mutableStateOf<File?>(null) }
    var logPreviewContent by remember { mutableStateOf("") }

    // 性能监控状态
    var showPerfDialog by remember { mutableStateOf(false) }
    var isPerfMonitoring by remember { mutableStateOf(false) }
    val cpuHistory = remember { mutableStateListOf<Float>() }
    val ramHistory = remember { mutableStateListOf<Float>() }
    var currentPerfData by remember { mutableStateOf(PerfData(0f, 0, 0)) }

    // 任务状态
    var isRecording by remember { mutableStateOf(false) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var isLogging by remember { mutableStateOf(false) }
    var loggingJob by remember { mutableStateOf<Job?>(null) }

    // 计时器状态
    var logDurationSeconds by remember { mutableLongStateOf(0L) }
    var recordDurationSeconds by remember { mutableLongStateOf(0L) }

    // 文件保存状态
    var fileToSave by remember { mutableStateOf<File?>(null) }
    var saveFileName by remember { mutableStateOf("") }
    var saveMimeType by remember { mutableStateOf("*/*") }

    // --- 辅助函数 ---
    fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    // --- 业务逻辑 ---

    // 1. 性能监控
    fun startPerfMonitor() {
        showPerfDialog = true
        isPerfMonitoring = true
        cpuHistory.clear(); repeat(30) { cpuHistory.add(0f) }
        ramHistory.clear(); repeat(30) { ramHistory.add(0f) }

        scope.launch(Dispatchers.IO) {
            var prevTotal = 0L
            var prevIdle = 0L

            while (isActive && isPerfMonitoring) {
                try {
                    val statOutput = dadbConnection?.shell("cat /proc/stat | head -n 1")?.allOutput ?: ""
                    val parts = statOutput.trim().split(Regex("\\s+"))
                    if (parts.size >= 5) {
                        val user = parts[1].toLong()
                        val nice = parts[2].toLong()
                        val system = parts[3].toLong()
                        val idle = parts[4].toLong()
                        val iowait = parts.getOrNull(5)?.toLong() ?: 0L
                        val irq = parts.getOrNull(6)?.toLong() ?: 0L
                        val softirq = parts.getOrNull(7)?.toLong() ?: 0L

                        val currentTotal = user + nice + system + idle + iowait + irq + softirq
                        val currentIdle = idle + iowait

                        var cpuUsage = 0f
                        if (prevTotal != 0L) {
                            val totalDiff = currentTotal - prevTotal
                            val idleDiff = currentIdle - prevIdle
                            if (totalDiff > 0) {
                                cpuUsage = ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat()) * 100f
                            }
                        }
                        prevTotal = currentTotal
                        prevIdle = currentIdle

                        val memOutput = dadbConnection?.shell("cat /proc/meminfo")?.allOutput ?: ""
                        val memTotalLine = memOutput.lines().find { it.startsWith("MemTotal") }
                        val memAvailLine = memOutput.lines().find { it.startsWith("MemAvailable") }

                        var totalRam = 0L
                        var availRam = 0L

                        if (memTotalLine != null) {
                            totalRam = Regex("\\d+").find(memTotalLine)?.value?.toLong() ?: 1L
                        }
                        if (memAvailLine != null) {
                            availRam = Regex("\\d+").find(memAvailLine)?.value?.toLong() ?: 0L
                        }

                        val usedRam = totalRam - availRam
                        val ramPercent = (usedRam.toFloat() / totalRam.toFloat()) * 100f

                        withContext(Dispatchers.Main) {
                            currentPerfData = PerfData(cpuUsage, usedRam / 1024, totalRam / 1024)
                            if (cpuHistory.isNotEmpty()) cpuHistory.removeAt(0); cpuHistory.add(cpuUsage)
                            if (ramHistory.isNotEmpty()) ramHistory.removeAt(0); ramHistory.add(ramPercent)
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
                delay(1000)
            }
        }
    }

    // 2. 截图
    fun captureScreenshot() {
        scope.launch {
            isLoading = true; loadingMessage = "正在获取截图..."
            try {
                withContext(Dispatchers.IO) { dadbConnection?.shell("screencap -p /data/local/tmp/s.png") }
                val localFile = File(context.cacheDir, "screenshot.png")
                withContext(Dispatchers.IO) { dadbConnection?.pull(localFile, "/data/local/tmp/s.png") }
                val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                if (bitmap != null) { screenshotBitmap = bitmap.asImageBitmap(); showScreenshotDialog = true }
                else Toast.makeText(context, "截图解析失败", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            finally { isLoading = false }
        }
    }

    // 3. Top Activity
    fun checkTopActivity() {
        scope.launch {
            isLoading = true; loadingMessage = "分析窗口..."
            try {
                val output = withContext(Dispatchers.IO) {
                    val cmd1 = dadbConnection?.shell("dumpsys window displays | grep -E 'mCurrentFocus|mFocusedApp'")?.allOutput ?: ""
                    if (cmd1.contains("u0")) cmd1 else dadbConnection?.shell("dumpsys activity activities | grep mResumedActivity")?.allOutput ?: "未获取"
                }
                val regex = Regex("([a-zA-Z0-9._]+)/([a-zA-Z0-9._]+)")
                val match = regex.find(output)
                topActivityInfo = if (match != null) "PKG: ${match.groupValues[1]}\nACT: ${match.groupValues[2]}" else output.trim()
                showTopActivityDialog = true
            } catch (e: Exception) { topActivityInfo = "获取失败: ${e.message}"; showTopActivityDialog = true }
            finally { isLoading = false }
        }
    }

    // 4. Logcat
    fun toggleLogcat() {
        if (isLogging) {
            scope.launch {
                isLoading = true; loadingMessage = "正在处理日志..."
                try {
                    loggingJob?.cancel()
                    withContext(Dispatchers.IO) { dadbConnection?.shell("pkill -2 -f 'logcat -f'") }
                    val time = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                    val localFile = File(context.cacheDir, "log_$time.txt")
                    withContext(Dispatchers.IO) {
                        dadbConnection?.pull(localFile, "/data/local/tmp/adblog.txt")
                        dadbConnection?.shell("rm /data/local/tmp/adblog.txt")
                    }
                    val content = withContext(Dispatchers.IO) {
                        try {
                            val text = localFile.readText()
                            if (text.length > 10000) text.take(10000) + "\n\n... (日志过长，仅展示前 10KB) ..." else text
                        } catch (e: Exception) { "无法读取预览: ${e.message}" }
                    }
                    logPreviewContent = content; logPreviewFile = localFile; showLogPreviewDialog = true
                } catch (e: Exception) { Toast.makeText(context, "停止失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                finally { isLogging = false; isLoading = false; logDurationSeconds = 0 }
            }
        } else {
            scope.launch {
                try {
                    logDurationSeconds = 0
                    withContext(Dispatchers.IO) { dadbConnection?.shell("logcat -c") }
                    loggingJob = launch(Dispatchers.IO) {
                        launch { dadbConnection?.shell("logcat -f /data/local/tmp/adblog.txt") }
                        while (isActive) { delay(1000); logDurationSeconds++ }
                    }
                    isLogging = true; Toast.makeText(context, "开始抓取日志...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(context, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // 5. 录屏
    fun toggleScreenRecord() {
        if (isRecording) {
            scope.launch {
                isLoading = true; loadingMessage = "合成视频..."
                try {
                    recordingJob?.cancel()
                    withContext(Dispatchers.IO) { dadbConnection?.shell("pkill -2 screenrecord") }
                    delay(1500)
                    val time = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                    val localFile = File(context.cacheDir, "rec_$time.mp4")
                    withContext(Dispatchers.IO) {
                        dadbConnection?.pull(localFile, "/data/local/tmp/screen.mp4")
                        dadbConnection?.shell("rm /data/local/tmp/screen.mp4")
                    }
                    videoPreviewFile = localFile; showVideoPreviewDialog = true
                } catch (e: Exception) { Toast.makeText(context, "停止失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                finally { isRecording = false; isLoading = false; recordDurationSeconds = 0 }
            }
        } else {
            scope.launch {
                try {
                    recordDurationSeconds = 0
                    recordingJob = launch(Dispatchers.IO) {
                        launch { dadbConnection?.shell("screenrecord --time-limit 180 /data/local/tmp/screen.mp4") }
                        while (isActive) { delay(1000); recordDurationSeconds++ }
                    }
                    isRecording = true; Toast.makeText(context, "录屏中...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(context, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // 6. 重启
    fun rebootDevice(mode: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val cmd = when(mode) { "recovery" -> "reboot recovery"; "bootloader" -> "reboot bootloader"; else -> "reboot" }
                dadbConnection?.shell(cmd)
            }
            showRebootDialog = false; Toast.makeText(context, "指令已发送", Toast.LENGTH_SHORT).show(); onBack()
        }
    }

    // --- 工具列表 ---
    val tools = listOf(
        // 设备镜像入口
        ToolItem("设备镜像", "实时投屏控制", Icons.Outlined.CastConnected, Color(0xFF00BCD4)) { onOpenMirror() },

        // 截图
        ToolItem("屏幕截图", "捕获当前画面", Icons.Outlined.Screenshot, Color(0xFF5B93E6)) { captureScreenshot() },

        // 电源
        ToolItem("电源菜单", "重启/Recovery", Icons.Outlined.PowerSettingsNew, Color(0xFFEF5350)) { showRebootDialog = true },

        // Top Activity
        ToolItem("顶层窗口", "Activity/Package", Icons.Outlined.Layers, Color(0xFF7E57C2)) { checkTopActivity() },

        // 性能监控
        ToolItem("性能监控", "CPU/内存波形", Icons.Outlined.Speed, Color(0xFF26A69A)) { startPerfMonitor() },

        // Logcat
        ToolItem(
            if(isLogging) "停止抓取" else "Logcat",
            if(isLogging) "已抓取: ${formatDuration(logDurationSeconds)}" else "抓取系统日志",
            if(isLogging) Icons.Default.StopCircle else Icons.Outlined.Description,
            if(isLogging) Color(0xFFD32F2F) else Color(0xFFFFA726)
        ) { toggleLogcat() },

        // 录屏
        ToolItem(
            if(isRecording) "停止录屏" else "屏幕录制",
            if(isRecording) "REC: ${formatDuration(recordDurationSeconds)}" else "录制屏幕(MP4)",
            if(isRecording) Icons.Default.StopCircle else Icons.Outlined.Videocam,
            if(isRecording) Color(0xFFD32F2F) else Color(0xFF26A69A)
        ) { toggleScreenRecord() }
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.White).statusBarsPadding()) {
                TopAppBar(
                    title = { Text("常用工具", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF8F9FA))) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tools) { tool -> PremiumToolCard(tool) }
            }

            AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable(enabled = false){}, Alignment.Center) {
                    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 10.dp) {
                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(40.dp), color = Color(0xFF5B93E6), strokeWidth = 4.dp)
                            Spacer(Modifier.height(24.dp))
                            Text(loadingMessage, fontSize = 15.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // --- 弹窗区域 ---
    if (showPerfDialog) {
        Dialog(onDismissRequest = { showPerfDialog = false; isPerfMonitoring = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(16.dp), shadowElevation = 8.dp) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Speed, null, tint = Color(0xFF26A69A), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("实时性能监控", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("CPU 使用率: ${"%.1f".format(currentPerfData.cpu)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    WaveformChart(data = cpuHistory, color = Color(0xFFEF5350))
                    Spacer(Modifier.height(24.dp))
                    Text("内存: ${currentPerfData.ramUsed}MB / ${currentPerfData.ramTotal}MB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    WaveformChart(data = ramHistory, color = Color(0xFF42A5F5))
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showPerfDialog = false; isPerfMonitoring = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A)), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("关闭监控", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showRebootDialog) {
        Dialog(onDismissRequest = { showRebootDialog = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("电源选项", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    RebootOptionRow("正常重启", "Restart System", Color(0xFF42A5F5), Icons.Default.RestartAlt) { rebootDevice("normal") }
                    Spacer(Modifier.height(12.dp))
                    RebootOptionRow("恢复模式", "Recovery Mode", Color(0xFFFFA726), Icons.Default.Build) { rebootDevice("recovery") }
                    Spacer(Modifier.height(12.dp))
                    RebootOptionRow("引导模式", "Fastboot Mode", Color(0xFFAB47BC), Icons.Default.DeveloperMode) { rebootDevice("bootloader") }
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { showRebootDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("取消", fontSize = 16.sp, color = Color.Gray) }
                }
            }
        }
    }

    if (showScreenshotDialog && screenshotBitmap != null) {
        Dialog(onDismissRequest = { showScreenshotDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 20.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("截图预览", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            IconButton(onClick = { showScreenshotDialog = false }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                        }
                        Box(Modifier.fillMaxWidth().background(Color(0xFFF2F4F7)).heightIn(max = 500.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                            Image(bitmap = screenshotBitmap!!, contentDescription = null, contentScale = ContentScale.Inside, modifier = Modifier.shadow(6.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                saveFileName = "screenshot_${System.currentTimeMillis()}.png"
                                saveMimeType = "image/png"
                                fileToSave = File(context.cacheDir, "screenshot.png")
                                showScreenshotDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6)),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).height(54.dp)
                        ) {
                            Icon(Icons.Default.SaveAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("保存到文件", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    if (showVideoPreviewDialog && videoPreviewFile != null) {
        Dialog(onDismissRequest = { showVideoPreviewDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), Alignment.Center) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(), shadowElevation = 16.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("录屏预览", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            IconButton(onClick = { showVideoPreviewDialog = false }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                        }
                        Box(Modifier.fillMaxWidth().height(400.dp).background(Color.Black)) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoPath(videoPreviewFile!!.absolutePath)
                                        val mediaController = MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        start()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { showVideoPreviewDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(50.dp)) { Text("放弃", color = Color.Gray) }
                            Button(
                                onClick = {
                                    saveFileName = videoPreviewFile!!.name
                                    saveMimeType = "video/mp4"
                                    fileToSave = videoPreviewFile
                                    showVideoPreviewDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text("保存视频", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogPreviewDialog && logPreviewFile != null) {
        Dialog(onDismissRequest = { showLogPreviewDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), Alignment.Center) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f), shadowElevation = 16.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("日志预览", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            IconButton(onClick = { showLogPreviewDialog = false }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).background(Color(0xFFF5F7FA), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            val scrollState = rememberScrollState()
                            Text(text = logPreviewContent, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF333333), modifier = Modifier.verticalScroll(scrollState))
                        }
                        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { showLogPreviewDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(50.dp)) { Text("放弃", color = Color.Gray) }
                            Button(
                                onClick = {
                                    saveFileName = logPreviewFile!!.name
                                    saveMimeType = "text/plain"
                                    fileToSave = logPreviewFile
                                    showLogPreviewDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text("保存文件", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTopActivityDialog) {
        Dialog(onDismissRequest = { showTopActivityDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(16.dp), shadowElevation = 8.dp) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Layers, null, tint = Color(0xFF5B93E6), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("当前顶层窗口", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                    }
                    Spacer(Modifier.height(16.dp))
                    SelectionContainer {
                        Box(Modifier.fillMaxWidth().background(Color(0xFFF5F7FA), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)).padding(16.dp)) {
                            Text(text = topActivityInfo, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF333333), lineHeight = 22.sp)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { clipboardManager.setText(AnnotatedString(topActivityInfo)); Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE3F2FD), contentColor = Color(0xFF1976D2)), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp), elevation = ButtonDefaults.buttonElevation(0.dp)) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("复制", fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { showTopActivityDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6)), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)) { Text("确定", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    FileSaver(fileToSave, saveFileName, saveMimeType, { fileToSave = null }, { fileToSave = null })
}

@Composable
fun WaveformChart(data: List<Float>, color: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) {
        if (data.isEmpty()) return@Canvas
        val stepX = size.width / (data.size - 1)
        val maxVal = 100f

        val path = Path()
        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / maxVal * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()
        drawPath(path, color.copy(alpha = 0.2f))
    }
}

@Composable
fun PremiumToolCard(tool: ToolItem) {
    Surface(onClick = tool.action, shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().height(160.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.Start) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(tool.themeColor.copy(alpha = 0.1f)), Alignment.Center) { Icon(tool.icon, null, tint = tool.themeColor, modifier = Modifier.size(30.dp)) }
            Column {
                Text(tool.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                Spacer(Modifier.height(4.dp))
                Text(tool.subtitle, fontSize = 13.sp, color = if(tool.themeColor == Color(0xFFD32F2F)) Color(0xFFD32F2F) else Color(0xFF9E9E9E), maxLines = 1, lineHeight = 18.sp, fontWeight = if(tool.themeColor == Color(0xFFD32F2F)) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun RebootOptionRow(title: String, subtitle: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).background(Color(0xFFF8F9FA)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(color.copy(alpha = 0.15f), CircleShape), Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF333333)); Text(subtitle, fontSize = 12.sp, color = Color.Gray) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.LightGray)
    }
}