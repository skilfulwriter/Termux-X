package com.davik.adbtools.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkFilePicker(
    onDismiss: () -> Unit,
    onFileSelected: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(false) }

    fun checkHasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = checkHasPermission()
    }

    val readStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        hasPermission = checkHasPermission()
    }

    // ✅ 使用 rememberSaveable 确保路径在配置更改时也不丢失
    var currentPath by rememberSaveable { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }

    // ✅ 核心：为每个路径手动管理滚动状态
    val scrollPositions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    val listState = rememberLazyListState()

    // 加载文件数据逻辑（不包含滚动控制）
    LaunchedEffect(currentPath, hasPermission) {
        if (hasPermission) {
            val loadedFiles = withContext(Dispatchers.IO) {
                currentPath.listFiles()?.filter {
                    it.isDirectory || it.name.contains(".apk", ignoreCase = true)
                }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
            }
            files = loadedFiles
        }
    }

    // ✅ 核心修复：统一的跳转函数，严格控制“保存 -> 切换 -> 恢复”的顺序
    fun navigateTo(newPath: File, isBack: Boolean = false) {
        scope.launch {
            // 1. 如果是进入，记录当前位置
            if (!isBack) {
                scrollPositions[currentPath.absolutePath] =
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }

            // 2. 更改路径
            currentPath = newPath

            // 3. 关键：等待一小段时间让 LazyColumn 完成数据重组渲染
            // 如果不等待，scrollToItem 会因为列表还没生成 Item 而失效
            if (isBack) {
                val savedPos = scrollPositions[newPath.absolutePath]
                if (savedPos != null) {
                    // 等待 UI 渲染完成
                    delay(50)
                    listState.scrollToItem(savedPos.first, savedPos.second)
                }
            } else {
                listState.scrollToItem(0)
            }
        }
    }

    BackHandler {
        if (currentPath == Environment.getExternalStorageDirectory()) {
            onDismiss()
        } else {
            currentPath.parentFile?.let { navigateTo(it, isBack = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text("选择安装包", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            if (hasPermission) {
                                Text(
                                    text = currentPath.absolutePath,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
                if (!hasPermission) {
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
                            readStorageLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    })
                } else {
                    // ✅ 关键：为 LazyColumn 绑定 state
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 100.dp)
                    ) {
                        if (currentPath != Environment.getExternalStorageDirectory()) {
                            item(key = "nav_back_${currentPath.absolutePath}") { // 使用动态 Key
                                FileRowItem(
                                    name = ".. (返回上一级)",
                                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                                    isDir = true,
                                    isBack = true,
                                    onClick = { currentPath.parentFile?.let { navigateTo(it, isBack = true) } }
                                )
                            }
                        }

                        if (files.isEmpty()) {
                            item(key = "empty_state_${currentPath.absolutePath}") {
                                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.FolderOpen, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("当前目录下没有 APK 文件", color = Color.LightGray, fontSize = 14.sp)
                                    }
                                }
                            }
                        } else {
                            // ✅ 关键：使用文件绝对路径作为 Key，防止重组丢失位置
                            items(files, key = { it.absolutePath }) { file ->
                                val sizeStr = if (file.isDirectory) "" else formatFileSize(file.length())
                                FileRowItem(
                                    name = file.name,
                                    subText = sizeStr,
                                    icon = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                    isDir = file.isDirectory,
                                    onClick = {
                                        if (file.isDirectory) {
                                            navigateTo(file, isBack = false)
                                        } else {
                                            onFileSelected(file)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRowItem(
    name: String,
    subText: String = "",
    icon: ImageVector,
    isDir: Boolean,
    isBack: Boolean = false,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isBack -> Color(0xFF673AB7)
                    isDir -> Color(0xFFFFC107)
                    else -> Color(0xFF607D8B)
                },
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontSize = 15.sp, fontWeight = if (isDir) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isBack) Color(0xFF673AB7) else Color.Black)
                if (subText.isNotEmpty()) {
                    Text(text = subText, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (isDir && !isBack) {
                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 48.dp), thickness = 0.5.dp, color = Color(0xFFF0F0F0))
    }
}

@Composable
private fun PermissionPlaceholder(onGrantRequest: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))
        Text("需要存储访问权限", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("为了安装 APK 文件，App 需要访问手机存储空间的权限。请在接下来的页面中授予权限。", color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrantRequest, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)) {
            Text("去授权", fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}