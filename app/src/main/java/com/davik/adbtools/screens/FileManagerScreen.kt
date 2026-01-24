// 文件名：com/davik/adbtools/screens/FileManagerScreen.kt
package com.davik.adbtools.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.davik.adbtools.adb.AdbConnectionManager
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// 远程文件模型
data class RemoteFile(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val date: String,
    val permissions: String
)

// 排序维度枚举
enum class SortOrder { NAME, TYPE, TIME, SIZE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(ip: String, initialConnection: Adb?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentPath by rememberSaveable { mutableStateOf("/sdcard") }
    var rawFileList by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 排序与过滤状态
    var sortOrder by rememberSaveable { mutableStateOf(SortOrder.NAME) }
    var showHiddenFiles by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // 计算展示的列表
    val fileList by remember(rawFileList, sortOrder, showHiddenFiles) {
        derivedStateOf {
            rawFileList
                .filter { if (showHiddenFiles) true else !it.name.startsWith(".") }
                .sortedWith(compareByDescending<RemoteFile> { it.isDir }.then(
                    when (sortOrder) {
                        SortOrder.NAME -> compareBy { it.name.lowercase() }
                        SortOrder.TYPE -> compareBy { it.name.substringAfterLast(".", "").lowercase() }
                        SortOrder.TIME -> compareByDescending { it.date }
                        SortOrder.SIZE -> compareByDescending { it.size }
                    }
                ))
        }
    }

    // 交互状态
    var isOperating by remember { mutableStateOf(false) }
    var operationMsg by remember { mutableStateOf("正在处理...") }
    var showLocalDownloadPicker by remember { mutableStateOf(false) }
    var showLocalUploadPicker by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<RemoteFile?>(null) }

    var clipboardFile by remember { mutableStateOf<RemoteFile?>(null) }
    var isCutMode by remember { mutableStateOf(false) }

    var showFabMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf("folder") }
    var inputName by remember { mutableStateOf("") }

    var selectedFile by remember { mutableStateOf<RemoteFile?>(null) }
    var showClickActionMenu by remember { mutableStateOf(false) }
    var showLongPressBottomMenu by remember { mutableStateOf(false) }
    var showMoreOptionsDialog by remember { mutableStateOf(false) }
    var showFileDetailDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scrollPositions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    var dadbConnection by remember { mutableStateOf(AdbConnectionManager.getConnection(ip) ?: initialConnection) }

    // 加载远程文件逻辑
    suspend fun loadFiles(path: String) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val safePath = if (path.endsWith("/")) path else "$path/"
                val output = dadbConnection?.shell("ls -al \"$safePath\"")?.allOutput ?: ""
                val parsedFiles = mutableListOf<RemoteFile>()
                output.lines().forEach { line ->
                    if (line.isBlank() || line.startsWith("total")) return@forEach
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 7) {
                        val permissions = parts[0]
                        val isDir = permissions.startsWith("d") || permissions.startsWith("l")
                        var name = parts.last()
                        if (permissions.startsWith("l") && line.contains(" -> ")) {
                            name = line.substringAfter(parts[parts.size - 2]).trim().split(" -> ")[0]
                        }
                        if (name == "." || name == "..") return@forEach
                        val size = if (isDir) 0L else parts.filter { it.matches(Regex("\\d+")) }.map { it.toLong() }.find { it > 1000 } ?: 0L
                        parsedFiles.add(RemoteFile(name, if (path.endsWith("/")) "$path$name" else "$path/$name", isDir, size, "${parts[parts.size-3]} ${parts[parts.size-2]}", permissions))
                    }
                }
                withContext(Dispatchers.Main) { rawFileList = parsedFiles; isLoading = false }
            } catch (e: Exception) {
                Log.e("FileManager", "Load error", e)
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun navigateTo(newPath: String, isBack: Boolean = false) {
        scope.launch {
            if (!isBack) scrollPositions[currentPath] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            currentPath = newPath
            loadFiles(newPath)
            if (isBack) {
                val savedPos = scrollPositions[newPath]
                if (savedPos != null) { delay(50); listState.scrollToItem(savedPos.first, savedPos.second) }
            } else listState.scrollToItem(0)
        }
    }

    suspend fun runPaste() {
        val source = clipboardFile ?: return
        isOperating = true
        operationMsg = if (isCutMode) "正在移动..." else "正在复制..."
        withContext(Dispatchers.IO) {
            try {
                val destPath = if(currentPath.endsWith("/")) "$currentPath${source.name}" else "$currentPath/${source.name}"
                if (isCutMode) {
                    dadbConnection?.shell("mv \"${source.path}\" \"$destPath\"")
                    clipboardFile = null
                } else {
                    dadbConnection?.shell("cp -r \"${source.path}\" \"$destPath\"")
                }
                loadFiles(currentPath)
                withContext(Dispatchers.Main) { Toast.makeText(context, "粘贴成功", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            } finally { withContext(Dispatchers.Main) { isOperating = false } }
        }
    }

    suspend fun uploadRecursive(localFile: File, remoteParentPath: String) {
        val remotePath = if (remoteParentPath.endsWith("/")) "$remoteParentPath${localFile.name}" else "$remoteParentPath/${localFile.name}"
        if (localFile.isDirectory) {
            dadbConnection?.shell("mkdir -p \"$remotePath\"")
            localFile.listFiles()?.forEach { child -> uploadRecursive(child, remotePath) }
        } else {
            withContext(Dispatchers.Main) { operationMsg = "上传中: ${localFile.name}" }
            dadbConnection?.push(localFile, remotePath)
        }
    }

    suspend fun runUpload(localSource: File) {
        isOperating = true
        operationMsg = "准备上传..."
        withContext(Dispatchers.IO) {
            try {
                uploadRecursive(localSource, currentPath)
                loadFiles(currentPath)
                withContext(Dispatchers.Main) { Toast.makeText(context, "上传成功", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "上传失败", Toast.LENGTH_SHORT).show() }
            } finally { withContext(Dispatchers.Main) { isOperating = false } }
        }
    }

    suspend fun runDownload(remote: RemoteFile, targetLocalDir: File, shouldOpen: Boolean) {
        isOperating = true
        operationMsg = "同步数据中..."
        withContext(Dispatchers.IO) {
            try {
                val localFile = File(targetLocalDir, remote.name)
                dadbConnection?.pull(localFile, remote.path)
                withContext(Dispatchers.Main) {
                    isOperating = false
                    if (shouldOpen) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(localFile.extension.lowercase()) ?: "application/octet-stream")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            putExtra(Intent.EXTRA_STREAM, uri)
                        }
                        context.startActivity(Intent.createChooser(intent, "选择应用打开"))
                    } else { Toast.makeText(context, "已下载到本机", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { isOperating = false } }
        }
    }

    LaunchedEffect(Unit) { loadFiles(currentPath) }

    BackHandler {
        if (showLocalDownloadPicker) showLocalDownloadPicker = false
        else if (showLocalUploadPicker) showLocalUploadPicker = false
        else if (currentPath == "/" || currentPath == "/sdcard") onBack()
        else navigateTo(File(currentPath).parent ?: "/", isBack = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = "远程文件管理", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(text = currentPath, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "排序")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(text = { Text("按名称") }, onClick = { sortOrder = SortOrder.NAME; showSortMenu = false }, leadingIcon = { if(sortOrder==SortOrder.NAME) Icon(Icons.Default.Check, null) })
                                DropdownMenuItem(text = { Text("按类型") }, onClick = { sortOrder = SortOrder.TYPE; showSortMenu = false }, leadingIcon = { if(sortOrder==SortOrder.TYPE) Icon(Icons.Default.Check, null) })
                                DropdownMenuItem(text = { Text("按时间") }, onClick = { sortOrder = SortOrder.TIME; showSortMenu = false }, leadingIcon = { if(sortOrder==SortOrder.TIME) Icon(Icons.Default.Check, null) })
                                DropdownMenuItem(text = { Text("按大小") }, onClick = { sortOrder = SortOrder.SIZE; showSortMenu = false }, leadingIcon = { if(sortOrder==SortOrder.SIZE) Icon(Icons.Default.Check, null) })
                            }
                        }
                        IconButton(onClick = { showHiddenFiles = !showHiddenFiles }) {
                            Icon(if (showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff, "切换隐藏文件")
                        }
                        IconButton(onClick = { scope.launch { loadFiles(currentPath) } }) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showFabMenu = true }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                    Icon(Icons.Default.Add, null)
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (currentPath != "/") {
                            item {
                                FileItemRow("..", "返回上一级", Icons.AutoMirrored.Filled.DriveFileMove, true) {
                                    navigateTo(File(currentPath).parent ?: "/", isBack = true)
                                }
                            }
                        }
                        items(fileList, key = { it.path }) { file ->
                            FileItemRow(
                                name = file.name,
                                subText = if (file.isDir) file.date else "${formatFileSize(file.size)} | ${file.date}",
                                icon = if (file.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                isDir = file.isDir,
                                onLongClick = { selectedFile = file; showLongPressBottomMenu = true },
                                onClick = {
                                    if (file.isDir) navigateTo(file.path)
                                    else { selectedFile = file; showClickActionMenu = true }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 传输 Loading
        if (isOperating) {
            Dialog(onDismissRequest = {}) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(operationMsg)
                    }
                }
            }
        }

        // 点击菜单
        if (showClickActionMenu && selectedFile != null) {
            val file = selectedFile!!
            Dialog(onDismissRequest = { showClickActionMenu = false }) {
                Surface(shape = RoundedCornerShape(28.dp), color = Color.White, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(text = file.name, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HorizontalDivider()
                        FileActionItem("在本机打开", Icons.Outlined.Launch) { showClickActionMenu = false; scope.launch { runDownload(file, context.cacheDir, true) } }
                        // 点击下载，打开本地目录选择器
                        FileActionItem("下载到本机目录", Icons.Outlined.FileDownload) {
                            showClickActionMenu = false
                            pendingFile = file
                            showLocalDownloadPicker = true // 直接打开，权限检查在组件内部做
                        }
                        FileActionItem("查看详情", Icons.Outlined.Info) { showClickActionMenu = false; showFileDetailDialog = true }
                    }
                }
            }
        }

        // 长按底部菜单
        if (showLongPressBottomMenu && selectedFile != null) {
            ModalBottomSheet(onDismissRequest = { showLongPressBottomMenu = false }) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                    Text(text = selectedFile!!.name, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        // 底部菜单下载，打开本地目录选择器
                        BottomMenuItem("下载", Icons.Outlined.Download) {
                            showLongPressBottomMenu = false
                            pendingFile = selectedFile
                            showLocalDownloadPicker = true
                        }
                        BottomMenuItem("复制", Icons.Outlined.ContentCopy) { showLongPressBottomMenu = false; clipboardFile = selectedFile; isCutMode = false; Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show() }
                        BottomMenuItem("剪切", Icons.Outlined.ContentCut) { showLongPressBottomMenu = false; clipboardFile = selectedFile; isCutMode = true; Toast.makeText(context, "已剪切", Toast.LENGTH_SHORT).show() }
                        BottomMenuItem("更多", Icons.Outlined.MoreHoriz) { showLongPressBottomMenu = false; showMoreOptionsDialog = true }
                    }
                }
            }
        }

        // 二级弹窗 (重命名, 删除, 详情)
        if (showMoreOptionsDialog && selectedFile != null) {
            Dialog(onDismissRequest = { showMoreOptionsDialog = false }) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("更多操作", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                        HorizontalDivider()
                        FileActionItem("重命名", Icons.Outlined.Edit) { showMoreOptionsDialog = false; inputName = selectedFile!!.name; showRenameDialog = true }
                        FileActionItem("删除此项", Icons.Outlined.Delete, Color.Red) {
                            showMoreOptionsDialog = false
                            scope.launch { withContext(Dispatchers.IO) { dadbConnection?.shell("rm -rf \"${selectedFile!!.path}\"") }; loadFiles(currentPath) }
                        }
                        FileActionItem("文件详情", Icons.Outlined.Info) { showMoreOptionsDialog = false; showFileDetailDialog = true }
                    }
                }
            }
        }

        // 重命名 Dialog
        if (showRenameDialog) {
            AlertDialog(onDismissRequest = { showRenameDialog = false }, title = { Text("重命名") },
                text = { OutlinedTextField(value = inputName, onValueChange = { inputName = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = { scope.launch { val dest = "${File(selectedFile!!.path).parent}/$inputName"
                    withContext(Dispatchers.IO) { dadbConnection?.shell("mv \"${selectedFile!!.path}\" \"$dest\"") }; showRenameDialog = false; loadFiles(currentPath) } }) { Text("确认") } }
            )
        }

        // FAB 菜单
        if (showFabMenu) {
            Dialog(onDismissRequest = { showFabMenu = false }) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("新建与上传", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                        HorizontalDivider()
                        if (clipboardFile != null) {
                            FileActionItem("粘贴 (${if(isCutMode) "移动" else "复制"}: ${clipboardFile!!.name})", Icons.Default.ContentPasteGo) {
                                showFabMenu = false; scope.launch { runPaste() }
                            }
                        }
                        FileActionItem("新建文件夹", Icons.Default.CreateNewFolder) { showFabMenu = false; createType = "folder"; inputName = ""; showCreateDialog = true }
                        FileActionItem("新建文件", Icons.Default.NoteAdd) { showFabMenu = false; createType = "file"; inputName = ""; showCreateDialog = true }
                        // 点击上传，打开本地文件选择器
                        FileActionItem("从本地上传", Icons.Default.CloudUpload) {
                            showFabMenu = false
                            showLocalUploadPicker = true // 直接打开，组件内部有权限检查
                        }
                    }
                }
            }
        }

        // ✅ 选择器全屏叠加层 (内部集成权限检查)
        AnimatedVisibility(visible = showLocalUploadPicker, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            LocalFileAndFolderPickerWithCheckbox("上传选中的本地项", "开始上传", onDismiss = { showLocalUploadPicker = false }, onSelected = { scope.launch { runUpload(it) } })
        }
        AnimatedVisibility(visible = showLocalDownloadPicker && pendingFile != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            LocalFileAndFolderPickerWithCheckbox("保存到本地路径", "确认保存", onlyDirectory = true, onDismiss = { showLocalDownloadPicker = false }, onSelected = { scope.launch { runDownload(pendingFile!!, it, false) } })
        }

        if (showCreateDialog) {
            AlertDialog(onDismissRequest = { showCreateDialog = false }, title = { Text(if(createType == "folder") "新建目录" else "新建文件") },
                text = { OutlinedTextField(value = inputName, onValueChange = { inputName = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = { scope.launch { val cmd = if(createType == "folder") "mkdir -p \"$currentPath/$inputName\"" else "touch \"$currentPath/$inputName\""
                    withContext(Dispatchers.IO) { dadbConnection?.shell(cmd) }; showCreateDialog = false; loadFiles(currentPath) } }) { Text("创建") } }
            )
        }

        if (showFileDetailDialog && selectedFile != null) {
            Dialog(onDismissRequest = { showFileDetailDialog = false }) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("项目详情", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(16.dp))
                        InternalDetailRow("名称", selectedFile!!.name); InternalDetailRow("路径", selectedFile!!.path)
                        InternalDetailRow("大小", if(selectedFile!!.isDir) "-" else formatFileSize(selectedFile!!.size))
                        InternalDetailRow("修改时间", selectedFile!!.date)
                        Spacer(Modifier.height(24.dp)); TextButton(onClick = { showFileDetailDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
                    }
                }
            }
        }
    }
}

// 辅助组件
@Composable
fun BottomMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable { onClick() }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp)); Text(text, fontSize = 12.sp)
    }
}

/**
 * ✅ 升级版本地文件选择器：内置权限检查和引导
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileAndFolderPickerWithCheckbox(title: String, buttonText: String, onlyDirectory: Boolean = false, onDismiss: () -> Unit, onSelected: (File) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    // --- 权限管理 START ---
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasPermission = checkPermission()
    }
    val requestLegacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = checkPermission()
    }

    LaunchedEffect(Unit) { hasPermission = checkPermission() }
    // --- 权限管理 END ---

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        // 如果没有权限，显示占位符
        if (!hasPermission) {
            Box(Modifier.fillMaxSize()) {
                // 顶部关闭按钮
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(top = 24.dp)) {
                    Icon(Icons.Default.Close, null)
                }
                PermissionPlaceholder(onGrantRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    } else {
                        requestLegacyLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                })
            }
        } else {
            // 有权限，显示正常文件列表
            var currentLocalPath by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
            var localItems by remember { mutableStateOf<List<File>>(emptyList()) }
            var selectedItem by remember { mutableStateOf<File?>(currentLocalPath) }

            LaunchedEffect(currentLocalPath) {
                withContext(Dispatchers.IO) {
                    localItems = currentLocalPath.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
                }
                // 如果只选文件夹，默认选中当前目录
                selectedItem = if (onlyDirectory) currentLocalPath else null
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.statusBarsPadding())
                TopAppBar(
                    title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) } }
                )
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(16.dp)) {
                    Text("📂 本地: ${currentLocalPath.absolutePath}", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (currentLocalPath.parentFile != null && currentLocalPath != Environment.getExternalStorageDirectory()) {
                        item {
                            Row(Modifier.fillMaxWidth().clickable { currentLocalPath = currentLocalPath.parentFile!! }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = Color.Gray); Spacer(Modifier.width(16.dp)); Text("返回上一层", color = Color.Gray)
                            }
                        }
                    }
                    items(localItems) { item ->
                        val isSelected = selectedItem == item
                        // 文件夹：点击进入；文件：点击选中（如果是目录模式，文件不可选）
                        val enabled = if (onlyDirectory) item.isDirectory else true

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    if (item.isDirectory) currentLocalPath = item
                                    else selectedItem = item
                                }
                                .background(if (isSelected) Color(0xFFE3F2FD) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!onlyDirectory && !item.isDirectory) {
                                Checkbox(checked = isSelected, onCheckedChange = { if (it) selectedItem = item else if (selectedItem == item) selectedItem = null })
                            } else if (onlyDirectory && item.isDirectory) {
                                // 目录模式下，不用checkbox，底部按钮直接确认当前目录
                                Icon(Icons.Default.Folder, null, tint = Color(0xFFFFC107), modifier = Modifier.size(32.dp))
                            } else {
                                // 目录模式下的文件，显示但置灰
                                Icon(Icons.Default.InsertDriveFile, null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
                            }

                            if (!onlyDirectory) {
                                if (item.isDirectory) Icon(Icons.Default.Folder, null, tint = Color(0xFFFFC107), modifier = Modifier.size(32.dp))
                                else if (!isSelected) Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF607D8B), modifier = Modifier.size(32.dp))
                            }

                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontSize = 14.sp, maxLines = 1, color = if(enabled) Color.Black else Color.LightGray)
                                Text(if (item.isDirectory) "文件夹" else formatFileSize(item.length()), fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF5F5F5), modifier = Modifier.padding(start = 56.dp))
                    }
                }

                Surface(modifier = Modifier.fillMaxWidth().shadow(16.dp), color = Color.White) {
                    Column(modifier = Modifier.navigationBarsPadding().padding(24.dp)) {
                        val selectedName = selectedItem?.name ?: "当前目录"
                        Text("已选: $selectedName", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                        Button(
                            onClick = { onSelected(selectedItem ?: currentLocalPath) },
                            enabled = selectedItem != null,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(buttonText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemRow(name: String, subText: String, icon: ImageVector, isDir: Boolean, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = onLongClick, onClick = onClick), color = Color.Transparent) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isDir) Color(0xFFFFC107) else Color(0xFF607D8B), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = name, fontSize = 15.sp, fontWeight = if (isDir) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subText, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun FileActionItem(text: String, icon: ImageVector, tint: Color = Color.Black, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = if(tint == Color.Red) Color.Red else Color.Gray)
            Spacer(modifier = Modifier.width(20.dp)); Text(text = text, fontSize = 16.sp, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InternalDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, modifier = Modifier.width(80.dp), color = Color.Gray, fontSize = 13.sp)
        Text(text = value, modifier = Modifier.weight(1f), color = Color.Black, fontSize = 13.sp)
    }
}

// 统一使用的权限引导页
@Composable
private fun PermissionPlaceholder(onGrantRequest: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderSpecial, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))
        Text("需要本地存储权限", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("为了读取或保存本地文件，App 需要访问手机存储空间的权限。", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrantRequest) { Text("去授权") }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}