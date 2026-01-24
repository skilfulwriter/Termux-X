// 文件名：com/davik/adbtools/screens/AppManagerScreen.kt
package com.davik.adbtools.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.davik.adbtools.adb.AdbConnectionManager
import com.davik.adbtools.components.ApkFilePicker
import com.davik.adbtools.components.FileSaver
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// --- 模型定义 ---
data class ClientAppItem(val packageName: String, val label: String, val icon: ImageBitmap?)
enum class InstallStatus { IDLE, INSTALLING, SUCCESS, FAILED }
enum class AppSortType(val label: String, val icon: ImageVector) {
    NAME("应用名称", Icons.Outlined.SortByAlpha),
    PACKAGE("应用包名", Icons.Outlined.Code),
    STATUS("启用状态", Icons.Outlined.ToggleOn)
}

data class AppItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isEnabled: MutableState<Boolean>,
    val sourceDir: String,
    var iconBitmap: MutableState<ImageBitmap?> = mutableStateOf(null)
)

data class AppDetails(
    var versionName: String = "获取中...",
    var apkSize: String = "计算中...",
    var minSdk: String = "-",
    var targetSdk: String = "-",
    var firstInstallTime: String = "-",
    var lastUpdateTime: String = "-",
    var mainActivity: String = "获取中..."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(ip: String, initialConnection: Adb?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 状态管理
    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var displayedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedApp by remember { mutableStateOf<AppItem?>(null) }
    var showActionDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var appDetails by remember { mutableStateOf(AppDetails()) }

    var sortBy by remember { mutableStateOf(AppSortType.NAME) }
    var showSortSheet by remember { mutableStateOf(false) }

    var showInstallOptions by remember { mutableStateOf(false) }
    var showClientAppSelector by remember { mutableStateOf(false) }

    var installStatus by remember { mutableStateOf(InstallStatus.IDLE) }
    var installErrorMsg by remember { mutableStateOf("") }
    var showStatusDialog by remember { mutableStateOf(false) }

    // 提取 APK 相关的状态
    var isExtracting by remember { mutableStateOf(false) }
    var extractingMessage by remember { mutableStateOf("正在提取...") } // 提取进度提示

    // FileSaver 状态
    var fileToSave by remember { mutableStateOf<File?>(null) }
    var saveFileName by remember { mutableStateOf("") }
    var saveMimeType by remember { mutableStateOf("*/*") }

    val iconQueue = remember { Channel<AppItem>(capacity = Channel.UNLIMITED) }
    var showFilePicker by remember { mutableStateOf(false) }

    var dadbConnection by remember { mutableStateOf(AdbConnectionManager.getConnection(ip) ?: initialConnection) }

    // 生命周期监听
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch(Dispatchers.IO) {
                    val currentConn = AdbConnectionManager.getConnection(ip)
                    if (currentConn == null || !AdbConnectionManager.isConnected(ip)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "ADB 连接已断开", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    } else {
                        dadbConnection = currentConn
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 客户端应用列表加载
    val clientApps by produceState<List<ClientAppItem>>(initialValue = emptyList(), showClientAppSelector) {
        if (showClientAppSelector) {
            value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                    .map { app -> ClientAppItem(app.packageName, app.loadLabel(pm).toString(), app.loadIcon(pm).toBitmap().asImageBitmap()) }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    // 核心功能：安装
    suspend fun runSecureInstall(file: File) {
        installStatus = InstallStatus.INSTALLING
        showStatusDialog = true
        val res = withContext(Dispatchers.IO) {
            try {
                withTimeoutOrNull(180000) { dadbConnection?.install(file) }
                "Success"
            } catch (e: Exception) {
                val msg = e.message ?: "未知错误"
                if (msg.contains("INSTALL_FAILED_MISSING_SPLIT")) "安装失败：分体APK错误" else msg
            } finally {
                if (file.parentFile == context.cacheDir) file.delete()
            }
        }
        withContext(Dispatchers.Main) {
            if (res == "Success") {
                installStatus = InstallStatus.SUCCESS
                loadApps(context, dadbConnection, scope, { isLoading = it }, { allApps = it }, iconQueue)
            } else {
                installStatus = InstallStatus.FAILED
                installErrorMsg = res
            }
        }
    }

    fun extractAndInstallClientApp(packageName: String) {
        scope.launch {
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                val sourceFile = File(appInfo.sourceDir)
                val cacheFile = File(context.cacheDir, "temp_client_${System.currentTimeMillis()}.apk")
                withContext(Dispatchers.IO) {
                    FileInputStream(sourceFile).use { input -> FileOutputStream(cacheFile).use { output -> input.copyTo(output) } }
                }
                runSecureInstall(cacheFile)
            } catch (e: Exception) {
                installStatus = InstallStatus.FAILED
                installErrorMsg = "提取应用失败"
                showStatusDialog = true
            }
        }
    }

    // 核心功能：详情
    suspend fun fetchDetailsViaAdb(packageName: String, sourceDir: String) {
        withContext(Dispatchers.IO) {
            try {
                val dumpsys = dadbConnection?.shell("dumpsys package $packageName")?.allOutput ?: ""
                val vName = dumpsys.lineAfter("versionName=")
                val min = dumpsys.lineAfter("minSdk=")
                val target = dumpsys.lineAfter("targetSdk=")
                val firstTime = dumpsys.lineAfter("firstInstallTime=")
                val updateTime = dumpsys.lineAfter("lastUpdateTime=")
                val activityRegex = Regex("$packageName/[\\w\\.]+")
                val mainActivity = activityRegex.find(dumpsys)?.value?.substringAfter("/") ?: "未知"
                // 计算 APK 总大小 (包括 splits)
                val appDir = sourceDir.substringBeforeLast("/") // 获取父目录
                val lsOutput = dadbConnection?.shell("ls -nl \"$appDir\"")?.allOutput ?: ""
                // 累加所有 .apk 文件的大小
                val totalSize = lsOutput.lines()
                    .filter { it.contains(".apk") }
                    .sumOf { line ->
                        line.trim().split(Regex("\\s+"))
                            .filter { it.matches(Regex("\\d+")) }
                            .mapNotNull { it.toLongOrNull() }
                            .find { it > 1000 } ?: 0L
                    }

                val sizeDisplay = if (totalSize > 0) {
                    if (totalSize > 1024 * 1024) String.format(Locale.US, "%.2f MB", totalSize / (1024.0 * 1024.0))
                    else String.format(Locale.US, "%.2f KB", totalSize / 1024.0)
                } else "未知"

                withContext(Dispatchers.Main) {
                    appDetails = AppDetails(vName.ifEmpty { "未知" }, sizeDisplay, min.ifEmpty { "-" }, target.ifEmpty { "-" }, firstTime.ifEmpty { "-" }, updateTime.ifEmpty { "-" }, mainActivity)
                }
            } catch (e: Exception) { Log.e("ADB", "Detail fetch failed", e) }
        }
    }

    LaunchedEffect(Unit) {
        loadApps(context, dadbConnection, scope, { isLoading = it }, { allApps = it }, iconQueue)
        repeat(4) {
            launch(Dispatchers.IO) {
                for (app in iconQueue) {
                    try {
                        val path = "/data/local/tmp/appicons/${app.packageName}.png"
                        val stream = dadbConnection?.open("shell:cat $path")
                        val bitmap = BitmapFactory.decodeStream(stream?.source?.inputStream())
                        stream?.close()
                        if (bitmap != null) app.iconBitmap.value = bitmap.asImageBitmap()
                    } catch (e: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(allApps, searchQuery, selectedTab, sortBy) {
        var filtered = allApps.filter { app ->
            val typeMatch = when (selectedTab) { 0 -> !app.isSystem; 1 -> app.isSystem; else -> true }
            val queryMatch = searchQuery.isEmpty() || app.label.contains(searchQuery, true) || app.packageName.contains(searchQuery, true)
            typeMatch && queryMatch
        }
        filtered = when (sortBy) {
            AppSortType.NAME -> filtered.sortedBy { it.label.lowercase() }
            AppSortType.PACKAGE -> filtered.sortedBy { it.packageName.lowercase() }
            AppSortType.STATUS -> filtered.sortedWith(compareByDescending<AppItem> { it.isEnabled.value }.thenBy { it.label.lowercase() })
        }
        displayedApps = filtered
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.White).statusBarsPadding().padding(top = 8.dp)) {
                // 顶栏容器
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Black)
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).height(44.dp).background(Color(0xFFF2F4F7), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = Color.Black),
                        cursorBrush = SolidColor(Color(0xFF5B93E6)),
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) Text("搜索应用...", color = Color(0xFFBDBDBD), fontSize = 15.sp)
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) Icon(Icons.Default.Cancel, null, tint = Color.Gray, modifier = Modifier.size(20.dp).clickable { searchQuery = "" })
                            }
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF2F4F7)).clickable { showSortSheet = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Sort, null, tint = Color(0xFF333333), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF2F4F7)).clickable { loadApps(context, dadbConnection, scope, { isLoading = it }, { allApps = it }, iconQueue) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Refresh, null, tint = Color(0xFF333333), modifier = Modifier.size(22.dp))
                    }
                }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = Color(0xFF5B93E6),
                    divider = { HorizontalDivider(color = Color(0xFFF0F0F0)) },
                    indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), height = 3.dp, color = Color(0xFF5B93E6)) }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("用户", fontWeight = FontWeight.SemiBold) }, unselectedContentColor = Color.Gray)
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("系统", fontWeight = FontWeight.SemiBold) }, unselectedContentColor = Color.Gray)
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("全部", fontWeight = FontWeight.SemiBold) }, unselectedContentColor = Color.Gray)
                }
                if (!isLoading) {
                    Text(text = "共找到 ${displayedApps.size} 个应用", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(vertical = 8.dp, horizontal = 20.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInstallOptions = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("安装应用") },
                containerColor = Color(0xFF5B93E6),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFF5B93E6))
            } else if (displayedApps.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Apps, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("暂无应用", color = Color.Gray)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(displayedApps, key = { it.packageName }) { app ->
                        AppListItem(
                            app = app,
                            onClick = { selectedApp = app; showActionDialog = true },
                            onLaunch = {
                                scope.launch { withContext(Dispatchers.IO) { dadbConnection?.shell("monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1") } }
                                Toast.makeText(context, "正在启动...", Toast.LENGTH_SHORT).show()
                            },
                            onUninstall = {
                                scope.launch { withContext(Dispatchers.IO) { dadbConnection?.shell("pm uninstall ${app.packageName}") }; loadApps(context, dadbConnection, scope, { isLoading = it }, { allApps = it }, iconQueue) }
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF5F5F5), modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }, containerColor = Color.White) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(text = "排序方式", modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AppSortType.entries.forEach { type ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { sortBy = type; showSortSheet = false }.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(type.icon, null, tint = if(sortBy == type) Color(0xFF5B93E6) else Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(20.dp))
                        Text(text = type.label, fontSize = 16.sp, color = if(sortBy == type) Color(0xFF5B93E6) else Color.Black, fontWeight = if(sortBy == type) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (sortBy == type) Icon(Icons.Default.Check, null, tint = Color(0xFF5B93E6), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    // --- 提取 APK 的 Loading 弹窗 ---
    if (isExtracting) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF5B93E6))
                    Spacer(Modifier.height(16.dp))
                    Text(extractingMessage, fontWeight = FontWeight.Bold) // 动态提示
                    Text("请稍候，文件传输中...", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    if (showFilePicker) {
        ApkFilePicker(
            onDismiss = { showFilePicker = false },
            onFileSelected = { originalFile ->
                showFilePicker = false
                scope.launch {
                    val cacheFile = File(context.cacheDir, "standard_install_task.apk")
                    withContext(Dispatchers.IO) {
                        try {
                            FileInputStream(originalFile).use { input -> FileOutputStream(cacheFile).use { output -> input.copyTo(output) } }
                            runSecureInstall(cacheFile)
                        } catch (e: Exception) {
                            installStatus = InstallStatus.FAILED; installErrorMsg = "预处理失败: ${e.message}"; showStatusDialog = true
                        }
                    }
                }
            }
        )
    }

    if (showStatusDialog) {
        Dialog(onDismissRequest = { if (installStatus != InstallStatus.INSTALLING) showStatusDialog = false }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (installStatus) {
                        InstallStatus.INSTALLING -> { CircularProgressIndicator(color = Color(0xFF5B93E6)); Spacer(Modifier.height(16.dp)); Text("正在安装...", fontWeight = FontWeight.Bold) }
                        InstallStatus.SUCCESS -> { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp)); Spacer(Modifier.height(16.dp)); Text("安装成功", fontWeight = FontWeight.Bold); Button(onClick = { showStatusDialog = false }, Modifier.padding(top = 16.dp).fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6))) { Text("确定") } }
                        InstallStatus.FAILED -> { Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(16.dp)); Text("安装失败", fontWeight = FontWeight.Bold); Text(installErrorMsg, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center); Button(onClick = { showStatusDialog = false }, Modifier.padding(top = 16.dp).fillMaxWidth()) { Text("关闭") } }
                        else -> {}
                    }
                }
            }
        }
    }

    if (showInstallOptions) {
        Dialog(onDismissRequest = { showInstallOptions = false }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text("安装选项", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    ActionListItem("从已安装应用同步", Icons.Outlined.AppShortcut) { showClientAppSelector = true; showInstallOptions = false }
                    ActionListItem("选择 APK 文件", Icons.Outlined.FolderZip) { showFilePicker = true; showInstallOptions = false }
                }
            }
        }
    }

    if (showClientAppSelector) {
        Dialog(onDismissRequest = { showClientAppSelector = false }) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxHeight(0.8f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text("选择应用", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
                    if (clientApps.isEmpty()) { Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF5B93E6)) } }
                    else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(clientApps) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { extractAndInstallClientApp(item.packageName); showClientAppSelector = false }.padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item.icon?.let { Image(BitmapPainter(it), null, Modifier.size(40.dp)) }
                                    Spacer(Modifier.width(16.dp))
                                    Column { Text(text = item.label, fontWeight = FontWeight.Medium); Text(text = item.packageName, fontSize = 11.sp, color = Color.Gray) }
                                }
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                            }
                        }
                    }
                    TextButton(onClick = { showClientAppSelector = false }, Modifier.align(Alignment.End)) { Text("取消", color = Color.Gray) }
                }
            }
        }
    }

    // 核心修改：应用操作弹窗
    if (showActionDialog && selectedApp != null) {
        val app = selectedApp!!
        val isEnabled by app.isEnabled
        Dialog(onDismissRequest = { showActionDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    // 应用头信息
                    Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(Color(0xFFF5F7FA), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            app.iconBitmap.value?.let { Image(BitmapPainter(it), null, Modifier.size(36.dp)) } ?: Icon(Icons.Default.Android, null, tint = Color.LightGray)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column { Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(app.packageName, fontSize = 12.sp, color = Color.Gray) }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    ActionListItem("应用详情", Icons.Outlined.Info) { showActionDialog = false; scope.launch { appDetails = AppDetails(); fetchDetailsViaAdb(app.packageName, app.sourceDir); showDetailDialog = true } }
                    ActionListItem("启动应用", Icons.AutoMirrored.Outlined.OpenInNew) { scope.launch { withContext(Dispatchers.IO) { dadbConnection?.shell("monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1") } }; showActionDialog = false }

                    // ✅ 新增：停止运行
                    ActionListItem("停止运行", Icons.Outlined.StopCircle, Color(0xFFD32F2F)) {
                        showActionDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) { dadbConnection?.shell("am force-stop ${app.packageName}") }
                            Toast.makeText(context, "已发送停止命令", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // ✅ 新增：提取 APK (智能打包 Split APK)
                    ActionListItem("提取 APK", Icons.Outlined.Save) {
                        showActionDialog = false
                        scope.launch {
                            isExtracting = true
                            try {
                                val cleanLabel = app.label.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]"), "_")
                                val appDir = app.sourceDir.substringBeforeLast("/") // 获取 APK 所在目录

                                // 1. 扫描目录下所有 APK
                                extractingMessage = "扫描应用文件..."
                                val lsOutput = withContext(Dispatchers.IO) { dadbConnection?.shell("ls \"$appDir\"")?.allOutput ?: "" }
                                val apkNames = lsOutput.lines().filter { it.trim().endsWith(".apk") }

                                if (apkNames.size > 1) {
                                    // Split APK -> 打包成 .xapk (Zip)
                                    extractingMessage = "正在打包 Split APK..."
                                    val zipFile = File(context.cacheDir, "${cleanLabel}.xapk")
                                    withContext(Dispatchers.IO) {
                                        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                                            for (apkName in apkNames) {
                                                val tempPart = File(context.cacheDir, apkName)
                                                dadbConnection?.pull(tempPart, "$appDir/$apkName") // 拉取
                                                if (tempPart.exists() && tempPart.length() > 0) {
                                                    zipOut.putNextEntry(ZipEntry(apkName))
                                                    FileInputStream(tempPart).use { it.copyTo(zipOut) }
                                                    zipOut.closeEntry()
                                                    tempPart.delete() // 删掉临时分包
                                                }
                                            }
                                        }
                                    }
                                    if (zipFile.exists() && zipFile.length() > 0) {
                                        saveFileName = "${cleanLabel}.xapk"
                                        saveMimeType = "application/zip"
                                        fileToSave = zipFile
                                    } else {
                                        throw Exception("打包文件为空")
                                    }
                                } else {
                                    // 单 APK -> 直接拉取
                                    extractingMessage = "正在提取 APK..."
                                    val tempFile = File(context.cacheDir, "${cleanLabel}.apk")
                                    withContext(Dispatchers.IO) {
                                        dadbConnection?.pull(tempFile, app.sourceDir)
                                    }
                                    if (tempFile.exists() && tempFile.length() > 0) {
                                        saveFileName = "${cleanLabel}.apk"
                                        saveMimeType = "application/vnd.android.package-archive"
                                        fileToSave = tempFile
                                    } else {
                                        throw Exception("文件提取失败")
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "提取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExtracting = false
                            }
                        }
                    }

                    ActionListItem(if (isEnabled) "停用应用" else "启用应用", Icons.Outlined.Block, if(isEnabled) Color(0xFFFF9800) else Color(0xFF4CAF50)) { val cmd = if (isEnabled) "pm disable-user ${app.packageName}" else "pm enable ${app.packageName}"; scope.launch { withContext(Dispatchers.IO) { dadbConnection?.shell(cmd) }; app.isEnabled.value = !app.isEnabled.value }; showActionDialog = false }
                    ActionListItem("卸载应用", Icons.Outlined.Delete, Color(0xFFF44336)) { scope.launch { withContext(Dispatchers.IO) { dadbConnection?.shell("pm uninstall ${app.packageName}") }; loadApps(context, dadbConnection, scope, { isLoading = it }, { allApps = it }, iconQueue) }; showActionDialog = false }
                    ActionListItem("清除数据", Icons.Outlined.CleaningServices, Color(0xFFF44336)) { scope.launch { withContext(Dispatchers.IO) { dadbConnection?.shell("pm clear ${app.packageName}") } }; showActionDialog = false }
                }
            }
        }
    }

    if (showDetailDialog && selectedApp != null) {
        val app = selectedApp!!
        Dialog(onDismissRequest = { showDetailDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 16.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("应用详情", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                    DetailItemRow("应用名称", app.label)
                    DetailItemRow("包名", app.packageName)
                    DetailItemRow("APK 大小", appDetails.apkSize)
                    DetailItemRow("版本名", appDetails.versionName)
                    DetailItemRow("目标 SDK", appDetails.targetSdk)
                    DetailItemRow("Min SDK", appDetails.minSdk)
                    DetailItemRow("安装时间", appDetails.firstInstallTime)
                    DetailItemRow("更新时间", appDetails.lastUpdateTime)
                    DetailItemRow("启动 Activity", appDetails.mainActivity)
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.CenterEnd) {
                        Button(onClick = { showDetailDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6))) { Text("关闭") }
                    }
                }
            }
        }
    }

    // ✅ 调用 FileSaver (用于保存提取的 APK)
    FileSaver(
        fileToSave = fileToSave,
        defaultFileName = saveFileName,
        mimeType = saveMimeType,
        onSuccess = { fileToSave = null },
        onDismiss = { fileToSave = null }
    )
}

// 列表项组件
@Composable
fun AppListItem(
    app: AppItem,
    onClick: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit
) {
    val isEnabled by app.isEnabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F7FA)),
            contentAlignment = Alignment.Center
        ) {
            app.iconBitmap.value?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(36.dp))
            } ?: Icon(Icons.Default.Android, null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isEnabled) Color(0xFF333333) else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.isSystem) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(Color(0xFFF0F0F0), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("SYS", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                if (!isEnabled) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("已停用", fontSize = 9.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = app.packageName,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onLaunch,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "启动",
                    tint = Color(0xFF5B93E6),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!app.isSystem) {
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "卸载",
                        tint = Color(0xFFE0E0E0),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.padding(start = 8.dp).size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ActionListItem(text: String, icon: ImageVector, color: Color = Color(0xFF333333), onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = if(color == Color(0xFF333333)) Color.Gray else color)
        Spacer(Modifier.width(20.dp))
        Text(text, fontSize = 16.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DetailItemRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = label, modifier = Modifier.width(100.dp), color = Color.Gray, fontSize = 14.sp)
        Text(text = value, modifier = Modifier.weight(1f), color = Color(0xFF333333), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun String.lineAfter(key: String): String = this.lines().find { it.contains(key) }?.substringAfter(key)?.trim() ?: ""

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

fun loadApps(context: android.content.Context, dadbConnection: Adb?, scope: kotlinx.coroutines.CoroutineScope, setLoading: (Boolean) -> Unit, setApps: (List<AppItem>) -> Unit, iconQueue: Channel<AppItem>) {
    if (dadbConnection == null) return
    setLoading(true)
    scope.launch {
        try {
            withTimeoutOrNull(20000) {
                val destPath = "/data/local/tmp/server.apk"
                val localSize = try {
                    context.assets.openFd("server.apk").use { it.length }
                } catch (e: Exception) {
                    context.assets.open("server.apk").use { it.available().toLong() }
                }

                val remoteInfo = withContext(Dispatchers.IO) {
                    dadbConnection.shell("ls -l $destPath").allOutput
                }

                val needPush = remoteInfo.contains("No such file") ||
                        !remoteInfo.split(Regex("\\s+")).contains(localSize.toString())

                if (needPush) {
                    val tempFile = File(context.cacheDir, "server_temp.apk")
                    context.assets.open("server.apk").use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.IO) {
                        dadbConnection.shell("rm -f $destPath")
                        dadbConnection.push(tempFile, destPath, 0b111111111, System.currentTimeMillis())
                        dadbConnection.shell("chmod 777 $destPath")
                    }
                    tempFile.delete()
                }

                val cmd = "export CLASSPATH=$destPath; app_process /data/local/tmp com.davik.adbserver.Server /data/local/tmp 2>&1"
                val list = mutableListOf<AppItem>()
                withContext(Dispatchers.IO) {
                    val output = dadbConnection.shell(cmd).allOutput
                    output.lines().forEach { line ->
                        if (line.trim().startsWith("{")) {
                            try {
                                val json = JSONObject(line)
                                list.add(AppItem(json.getString("package"), json.getString("label"), json.getBoolean("isSystem"), mutableStateOf(json.getBoolean("enabled")), json.optString("sourceDir", "")))
                            } catch (e: Exception) { Log.e("ADB", "JSON parse error") }
                        }
                    }
                }
                setApps(list); list.forEach { iconQueue.send(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ADB_LOAD", "Failed: ${e.message}") } finally { setLoading(false) }

    }
}