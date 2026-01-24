// 文件名：com/davik/adbtools/components/FileSaver.kt
package com.davik.adbtools.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// ✅ 1. 定义稳定的数据模型，提升 Compose 渲染性能
// 直接使用 File 对象会导致列表滑动时频繁重组，造成卡顿
@Immutable
data class FolderItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean
)

/**
 * ✅ 自定义目录选择与文件保存组件 (高性能 + 布局修复版)
 * 1. 使用 immutable data class 优化 LazyColumn 滑动性能。
 * 2. IO 线程预处理文件列表。
 * 3. 强制底部 padding 防止导航栏遮挡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSaver(
    fileToSave: File?,
    defaultFileName: String,
    mimeType: String = "*/*",
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(false) }

    // --- 权限检查 ---
    fun checkHasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasPermission = checkHasPermission() }

    val writeStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasPermission = isGranted }

    LaunchedEffect(Unit) { hasPermission = checkHasPermission() }

    // --- 状态管理 ---
    var currentPath by rememberSaveable { mutableStateOf(Environment.getExternalStorageDirectory()) }
    // ✅ 使用自定义数据类列表
    var folderList by remember { mutableStateOf<List<FolderItem>>(emptyList()) }

    val scrollPositions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    val listState = rememberLazyListState()

    // ✅ 在 IO 线程加载并映射数据，减轻主线程负担
    LaunchedEffect(currentPath, hasPermission) {
        if (hasPermission) {
            val items = withContext(Dispatchers.IO) {
                try {
                    currentPath.listFiles()
                        ?.filter { it.isDirectory && !it.name.startsWith(".") }
                        ?.sortedBy { it.name.lowercase() }
                        ?.map { FolderItem(it.name, it.absolutePath, it.isDirectory) } // 映射为稳定对象
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            folderList = items
        }
    }

    fun navigateTo(newPath: File, isBack: Boolean = false) {
        scope.launch {
            if (!isBack) {
                scrollPositions[currentPath.absolutePath] =
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
            currentPath = newPath
            if (isBack) {
                val savedPos = scrollPositions[newPath.absolutePath]
                if (savedPos != null) {
                    delay(50)
                    listState.scrollToItem(savedPos.first, savedPos.second)
                }
            } else {
                listState.scrollToItem(0)
            }
        }
    }

    BackHandler(enabled = fileToSave != null) {
        if (currentPath == Environment.getExternalStorageDirectory()) {
            onDismiss()
        } else {
            currentPath.parentFile?.let { navigateTo(it, isBack = true) }
        }
    }

    // --- 保存逻辑 ---
    fun performSave() {
        if (fileToSave == null) return
        val targetFile = File(currentPath, defaultFileName)

        scope.launch(Dispatchers.IO) {
            try {
                FileInputStream(fileToSave).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已保存到: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (fileToSave != null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White
            ) {
                // ✅ 使用 systemBarsPadding 确保内容不被状态栏和导航栏遮挡
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    // 1. 顶部栏
                    TopAppBar(
                        title = {
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text("选择保存位置", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
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
                    HorizontalDivider(color = Color(0xFFEEEEEE))

                    // 2. 文件夹列表区 (Weight 1f)
                    Box(modifier = Modifier.weight(1f)) {
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
                                    writeStorageLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            })
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 20.dp)
                            ) {
                                // 返回上一级项
                                if (currentPath != Environment.getExternalStorageDirectory()) {
                                    item(key = "nav_back_${currentPath.absolutePath}") {
                                        FolderRowItem(
                                            name = ".. (返回上一级)",
                                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                                            isBack = true,
                                            onClick = { currentPath.parentFile?.let { navigateTo(it, isBack = true) } }
                                        )
                                    }
                                }

                                if (folderList.isEmpty()) {
                                    item(key = "empty_state") {
                                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                            Text("空文件夹", color = Color.LightGray)
                                        }
                                    }
                                } else {
                                    // ✅ 使用 absolutePath 作为唯一 Key，避免复用错误
                                    items(folderList, key = { it.absolutePath }) { folder ->
                                        FolderRowItem(
                                            name = folder.name,
                                            icon = Icons.Default.Folder,
                                            isBack = false,
                                            onClick = { navigateTo(File(folder.absolutePath), isBack = false) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 底部保存按钮 (安全区)
                    if (hasPermission) {
                        Surface(
                            shadowElevation = 16.dp,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .padding(bottom = 60.dp) // 额外增加底部间距，确保视觉舒适
                            ) {
                                Text(
                                    text = "将保存为: $defaultFileName",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Button(
                                    onClick = { performSave() },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6))
                                ) {
                                    Text("保存到当前目录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRowItem(
    name: String,
    icon: ImageVector,
    isBack: Boolean = false,
    onClick: () -> Unit
) {
    // 简化的列表项布局，减少嵌套
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isBack) Color(0xFF673AB7) else Color(0xFFFFC107),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = if (!isBack) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isBack) Color(0xFF673AB7) else Color.Black,
                modifier = Modifier.weight(1f)
            )
            if (!isBack) {
                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 68.dp), thickness = 0.5.dp, color = Color(0xFFF0F0F0))
    }
}

@Composable
private fun PermissionPlaceholder(onGrantRequest: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))
        Text("需要存储访问权限", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("为了保存文件到指定目录，App 需要访问手机存储空间的权限。请在接下来的页面中授予权限。", color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrantRequest, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)) {
            Text("去授权", fontWeight = FontWeight.Bold)
        }
    }
}