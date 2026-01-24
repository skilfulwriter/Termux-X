// 文件名：com/davik/adbtools/screens/RemoteControlScreen.kt
package com.davik.adbtools.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davik.adbtools.adb.AdbConnectionManager
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 扩展后的按键定义
enum class RemoteKey(val code: Int) {
    // 导航
    UP(19), DOWN(20), LEFT(21), RIGHT(22), CENTER(23),
    BACK(4), HOME(3), MENU(82),
    // 媒体/音量
    VOL_UP(24), VOL_DOWN(25), MUTE(164),
    play_PAUSE(85), NEXT(87), PREV(88),
    // 电源
    POWER(26), SLEEP(223), WAKEUP(224),
    // 数字
    NUM_0(7), NUM_1(8), NUM_2(9), NUM_3(10), NUM_4(11),
    NUM_5(12), NUM_6(13), NUM_7(14), NUM_8(15), NUM_9(16),
    DEL(67), ENTER(66), SPACE(62)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(ip: String, initialConnection: Adb?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val localView = LocalView.current
    var dadbConnection by remember { mutableStateOf(AdbConnectionManager.getConnection(ip) ?: initialConnection) }

    // Tab 状态: 0=遥控器, 1=输入文字, 2=模拟按键
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("遥控器", "输入文字", "模拟按键")

    // 发送 KeyCode
    fun sendKey(keyCode: Int) {
        localView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        scope.launch(Dispatchers.IO) {
            try { dadbConnection?.shell("input keyevent $keyCode") } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 发送文本
    fun sendText(text: String) {
        if (text.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                // ADB 不支持直接空格，需要替换为 %s
                val safeText = text.replace(" ", "%s").replace("'", "\\'")
                dadbConnection?.shell("input text \"$safeText\"")
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                TopAppBar(
                    title = { Text("设备控制台", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = Color(0xFF5B93E6),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 3.dp,
                            color = Color(0xFF5B93E6)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
        ) {
            when (selectedTab) {
                0 -> RemoteTabContent(::sendKey)
                1 -> InputTextTabContent(::sendText, ::sendKey)
                2 -> KeyCodeTabContent(::sendKey)
            }
        }
    }
}

// ================== TAB 1: 遥控器 (集成 D-Pad, 导航, 数字) ==================
@Composable
fun RemoteTabContent(onSendKey: (Int) -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 电源与音量 (加大尺寸)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RemoteCircleButton(Icons.Outlined.PowerSettingsNew, Color(0xFFEF5350), 64.dp) { onSendKey(RemoteKey.POWER.code) }
            RemoteCircleButton(Icons.Outlined.VolumeOff, Color(0xFF7E57C2), 64.dp) { onSendKey(RemoteKey.MUTE.code) }
        }

        Spacer(Modifier.height(32.dp))

        // 2. 超大 D-Pad
        Box(
            modifier = Modifier
                .size(300.dp) // 加大尺寸
                .shadow(12.dp, CircleShape)
                .background(Color.White, CircleShape)
        ) {
            // 上
            Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                DpadButton(Icons.Default.KeyboardArrowUp) { onSendKey(RemoteKey.UP.code) }
            }
            // 下
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                DpadButton(Icons.Default.KeyboardArrowDown) { onSendKey(RemoteKey.DOWN.code) }
            }
            // 左
            Box(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
                DpadButton(Icons.Default.KeyboardArrowLeft) { onSendKey(RemoteKey.LEFT.code) }
            }
            // 右
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                DpadButton(Icons.Default.KeyboardArrowRight) { onSendKey(RemoteKey.RIGHT.code) }
            }
            // 中间确认 (加大)
            Box(Modifier.align(Alignment.Center)) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color(0xFF5B93E6), CircleShape)
                        .clip(CircleShape)
                        .clickable { onSendKey(RemoteKey.CENTER.code) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        // 3. 导航键 (横向排列，加大触控区)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavBigButton("返回", Icons.AutoMirrored.Filled.ArrowBack) { onSendKey(RemoteKey.BACK.code) }
            NavBigButton("主页", Icons.Default.Home) { onSendKey(RemoteKey.HOME.code) }
            NavBigButton("菜单", Icons.Default.Menu) { onSendKey(RemoteKey.MENU.code) }
        }

        Spacer(Modifier.height(32.dp))

        // 4. 音量长条
        Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 16.dp)) {
            // 音量 -
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 35.dp, bottomStart = 35.dp))
                    .background(Color.White).border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(topStart = 35.dp, bottomStart = 35.dp))
                    .clickable { onSendKey(RemoteKey.VOL_DOWN.code) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Remove, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }

            // 图标
            Box(
                Modifier.width(60.dp).fillMaxHeight().background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF5B93E6)) }

            // 音量 +
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 35.dp, bottomEnd = 35.dp))
                    .background(Color.White).border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(topEnd = 35.dp, bottomEnd = 35.dp))
                    .clickable { onSendKey(RemoteKey.VOL_UP.code) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }
        }

        Spacer(Modifier.height(32.dp))

        // 5. 数字键盘 (补全功能)
        Text("数字键盘", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, bottom = 12.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val numRows = listOf(
                listOf(RemoteKey.NUM_1, RemoteKey.NUM_2, RemoteKey.NUM_3),
                listOf(RemoteKey.NUM_4, RemoteKey.NUM_5, RemoteKey.NUM_6),
                listOf(RemoteKey.NUM_7, RemoteKey.NUM_8, RemoteKey.NUM_9),
                listOf(null, RemoteKey.NUM_0, RemoteKey.DEL) // null 代表空位
            )

            numRows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        if (key != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp) // 增加高度
                                    .shadow(2.dp, RoundedCornerShape(12.dp))
                                    .background(if(key == RemoteKey.DEL) Color(0xFFFFEBEE) else Color.White, RoundedCornerShape(12.dp))
                                    .clickable { onSendKey(key.code) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == RemoteKey.DEL) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color(0xFFD32F2F))
                                } else {
                                    Text(key.name.last().toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ================== TAB 2: 输入文字 ==================
@Composable
fun InputTextTabContent(onSendText: (String) -> Unit, onSendKey: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("发送文本到设备", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("支持中英文，如果设备未响应，请确保设备光标在输入框内。", fontSize = 13.sp, color = Color.Gray)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            placeholder = { Text("在此输入内容...") },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF5B93E6),
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendText(text); text = "" })
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onSendText(text); text = "" },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6))
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null)
            Spacer(Modifier.width(8.dp))
            Text("发送内容", fontSize = 18.sp)
        }

        Spacer(Modifier.height(32.dp))

        Text("常用编辑键", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EditKeyButton("回车 Enter", RemoteKey.ENTER.code, Modifier.weight(1f)) { onSendKey(it) }
            EditKeyButton("退格 Del", RemoteKey.DEL.code, Modifier.weight(1f), isDestructive = true) { onSendKey(it) }
        }
        Spacer(Modifier.height(16.dp))
        EditKeyButton("空格 Space", RemoteKey.SPACE.code, Modifier.fillMaxWidth()) { onSendKey(it) }
    }
}

// ================== TAB 3: 模拟按键 (高级功能) ==================
@Composable
fun KeyCodeTabContent(onSendKey: (Int) -> Unit) {
    var customCode by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("常用功能键", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))

        val commonKeys = listOf(
            "播放/暂停" to 85, "上一曲" to 88, "下一曲" to 87,
            "亮度+" to 221, "亮度-" to 220, "系统设置" to 176,
            "多任务" to 187, "搜索" to 84, "静音" to 164
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(260.dp) // 固定高度给 Grid
        ) {
            items(commonKeys) { (name, code) ->
                Box(
                    modifier = Modifier
                        .height(70.dp)
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .clickable { onSendKey(code) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF444444), textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Text("自定义 KeyCode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customCode,
                onValueChange = { if (it.all { char -> char.isDigit() }) customCode = it },
                label = { Text("输入 Key Code (数字)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    customCode.toIntOrNull()?.let { onSendKey(it) }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B93E6))
            ) {
                Text("发送")
            }
        }
    }
}

// --- 通用 UI 组件 ---

@Composable
fun DpadButton(icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(72.dp) // 加大触摸区域
            .clip(RoundedCornerShape(20.dp))
            .background(if (isPressed) Color(0xFFE3F2FD) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = if(isPressed) Color(0xFF5B93E6) else Color(0xFF555555))
    }
}

@Composable
fun RemoteCircleButton(icon: ImageVector, color: Color, size: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun NavBigButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Box(
            modifier = Modifier
                .size(70.dp) // 加大尺寸
                .shadow(2.dp, RoundedCornerShape(20.dp))
                .background(Color.White, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFF333333), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EditKeyButton(text: String, code: Int, modifier: Modifier = Modifier, isDestructive: Boolean = false, onClick: (Int) -> Unit) {
    Button(
        onClick = { onClick(code) },
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDestructive) Color(0xFFFFEBEE) else Color.White,
            contentColor = if (isDestructive) Color(0xFFD32F2F) else Color.Black
        ),
        elevation = ButtonDefaults.buttonElevation(2.dp)
    ) {
        Text(text, fontSize = 16.sp)
    }
}