// 文件名：com/davik/adbtools/screens/DeviceControlScreen.kt
package com.davik.adbtools.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davik.adbtools.adb.Adb

// 定义功能菜单的数据结构
data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val description: String // 副标题
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    ip: String,
    dadbConnection: Adb?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    // 定义菜单列表数据
    val featureList = listOf(
        FeatureItem("设备信息", Icons.Default.Info, Color(0xFF2196F3), "查看配置、电量、存储详情"),
        FeatureItem("App管理", Icons.Default.Apps, Color(0xFF4CAF50), "安装、卸载、提取 APK、冻结"),
        FeatureItem("文件管理", Icons.Default.Folder, Color(0xFFFFC107), "浏览内部存储、上传下载文件"),
        FeatureItem("进程管理", Icons.Default.Memory, Color(0xFFF44336), "监控运行内存、结束卡死进程"),
        FeatureItem("虚拟遥控", Icons.Default.CastConnected, Color(0xFF9C27B0), "模拟按键、遥控器、输入文字"),
        FeatureItem("常用工具", Icons.Default.Build, Color(0xFFFF9800), "一键截图、重启设备、投屏"),
        FeatureItem("shell命令", Icons.Default.Terminal, Color(0xFF607D8B), "执行自定义 ADB Shell 命令")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备控制台", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8F9FA)), // 更柔和的背景色
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. 顶部设备状态卡片
            item {
                DeviceHeaderCard(ip = ip, isConnected = dadbConnection != null)
            }

            // 2. 分割标题
            item {
                Text(
                    text = "功能列表",
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 3. 垂直功能列表
            items(featureList) { item ->
                FeatureListRow(item = item, onClick = { onNavigate(item.title) })
            }
        }
    }
}

@Composable
fun DeviceHeaderCard(ip: String, isConnected: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF5B93E6)), // 使用主色调
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设备图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(
                    text = ip,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isConnected) Color(0xFFCCFF90) else Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "已连接" else "连接断开",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureListRow(item: FeatureItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.5.dp) // 轻微阴影
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 文字内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            // 右侧箭头
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.LightGray
            )
        }
    }
}