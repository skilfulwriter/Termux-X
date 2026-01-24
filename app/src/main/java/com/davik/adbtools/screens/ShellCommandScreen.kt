// 文件名：com/davik/adbtools/screens/ShellCommandScreen.kt
package com.davik.adbtools.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.davik.adbtools.adb.Adb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================= 配色方案 (Light Mode 适配版) =================
private val BgPage = Color(0xFFF5F5F5)          // 页面背景
private val BgTerminal = Color(0xFFFFFFFF)      // 终端背景
private val BgInput = Color(0xFFFFFFFF)         // 输入框背景
private val TextMain = Color(0xFF333333)        // 主要文字
private val TextHint = Color(0xFF9E9E9E)        // 提示文字

// 语法高亮色
private val ColorPrompt = Color(0xFF00796B)     // 提示符: 深青色
private val ColorCommand = Color(0xFFE65100)    // 用户输入: 深橙色
private val ColorDir = Color(0xFF1565C0)        // 文件夹: 深蓝
private val ColorLink = Color(0xFF7B1FA2)       // 链接: 深紫
private val ColorError = Color(0xFFD32F2F)      // 错误: 红
private val ColorPingData = Color(0xFF455A64)   // Ping数据: 蓝灰

// 按钮色
private val AccentBlue = Color(0xFF2196F3)
private val StopRed = Color(0xFFE91E63)

data class ShellShortcut(val name: String, val cmd: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellCommandScreen(
    ip: String,
    dadbConnection: Adb?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val terminalLines = remember { mutableStateListOf<AnnotatedString>() }
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    // 状态管理
    var commandText by remember { mutableStateOf("") }

    // 【核心新增】记录当前工作目录，默认为根目录 /
    var currentWorkDir by remember { mutableStateOf("/") }

    // 动态生成提示符：root@ip:/当前路径 #
    val promptText = "root@$ip:$currentWorkDir # "

    // 控制是否换行
    var isWrapEnabled by remember { mutableStateOf(false) }

    // 初始化提示符
    LaunchedEffect(Unit) {
        if (terminalLines.isEmpty()) {
            terminalLines.add(buildAnnotatedString {
                withStyle(SpanStyle(color = ColorPrompt, fontWeight = FontWeight.Bold)) { append(promptText) }
            })
        }
    }

    var currentJob by remember { mutableStateOf<Job?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    var showMenu by remember { mutableStateOf(false) }
    var showShortcutDialog by remember { mutableStateOf(false) }
    var showAddShortcutDialog by remember { mutableStateOf(false) }

    val shortcuts = remember { mutableStateListOf(
        ShellShortcut("查看文件列表", "ls -l"),
        ShellShortcut("Ping 测试", "ping 127.0.0.1"),
        ShellShortcut("查看进程", "ps -A"),
        ShellShortcut("重启设备", "reboot"),
        ShellShortcut("查看IP", "ifconfig")
    ) }

    // 自动滚动
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.scrollToItem(terminalLines.lastIndex)
        }
    }

    // 智能解析行颜色
    fun parseLineStyle(line: String): AnnotatedString {
        return buildAnnotatedString {
            when {
                // 文件夹
                line.trimStart().startsWith("d") && line.contains(Regex("[rwx-]{9}")) -> {
                    withStyle(SpanStyle(color = ColorDir, fontWeight = FontWeight.Bold)) { append(line) }
                }
                // 软链接
                line.trimStart().startsWith("l") && line.contains(Regex("[rwx-]{9}")) -> {
                    withStyle(SpanStyle(color = ColorLink)) { append(line) }
                }
                // 错误
                line.contains("Error", ignoreCase = true) || line.contains("Permission denied") || line.contains("No such file") -> {
                    withStyle(SpanStyle(color = ColorError)) { append(line) }
                }
                // Ping 数据
                line.contains("bytes from") && line.contains("time=") -> {
                    withStyle(SpanStyle(color = ColorPingData)) { append(line) }
                }
                else -> {
                    withStyle(SpanStyle(color = TextMain)) { append(line) }
                }
            }
        }
    }

    fun executeOrStopCommand(cmdInput: String? = null) {
        if (isRunning) {
            currentJob?.cancel()
            currentJob = null
            isRunning = false
            terminalLines.add(AnnotatedString("[用户已终止操作]", spanStyle = SpanStyle(color = ColorError)))
            // 停止后显示最新的提示符
            terminalLines.add(AnnotatedString(promptText, spanStyle = SpanStyle(color = ColorPrompt, fontWeight = FontWeight.Bold)))
            return
        }

        var cmd = cmdInput ?: commandText.trim()
        if (cmd.isBlank()) return

        // 仅对 ls 做增强显示，不改变其他命令
        if (cmd == "ls") cmd = "ls -l"

        if (commandHistory.isEmpty() || commandHistory.last() != cmd) {
            commandHistory.add(cmd)
        }
        historyIndex = commandHistory.size

        // UI: 显示用户输入的命令
        terminalLines.add(buildAnnotatedString {
            withStyle(SpanStyle(color = ColorCommand, fontWeight = FontWeight.Bold)) { append("$cmd\n") }
        })

        if (cmdInput == null) commandText = ""
        isRunning = true

        // 检测是否是 cd 命令
        // 逻辑：如果是 cd 命令，我们需要执行 "cd 旧路径 && cd 新路径 && pwd" 来验证并获取新路径
        val isCdCommand = cmd.startsWith("cd ") || cmd == "cd"

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                if (dadbConnection == null) {
                    withContext(Dispatchers.Main) {
                        terminalLines.add(AnnotatedString("Error: Device not connected", spanStyle = SpanStyle(color = ColorError)))
                        isRunning = false
                    }
                    return@launch
                }

                // 【核心黑科技】
                // 无论什么命令，都在前面加上 "cd $currentWorkDir &&"
                // 这样就能模拟出“保持在当前目录”的效果
                val fullCmd = if (isCdCommand) {
                    // 如果是跳转目录，多加一个 pwd 以便我们获取跳转后的绝对路径
                    "cd \"$currentWorkDir\" && $cmd && pwd"
                } else {
                    // 普通命令
                    "cd \"$currentWorkDir\" && $cmd"
                }

                val stream = dadbConnection.open("shell:$fullCmd")
                val source = stream.source

                // 临时变量，用于 cd 命令成功后更新路径
                var newPathFound: String? = null

                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        // 如果是 CD 命令，最后一行通常是 pwd 的结果 (新路径)
                        if (isCdCommand && line.startsWith("/")) {
                            newPathFound = line.trim()
                        }

                        // 如果不是纯粹的路径输出(或者是普通命令)，则打印到屏幕
                        // 对于 cd 命令，如果输出了路径，我们不一定要打印出来，只更新提示符即可
                        // 但为了报错信息可见，如果有 error 还是要打印
                        if (!isCdCommand || line.contains("No such file") || line.contains("can't cd")) {
                            withContext(Dispatchers.Main) {
                                terminalLines.add(parseLineStyle(line))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore stream close
                } finally {
                    stream.close()
                }

                // 更新 UI 状态
                withContext(Dispatchers.Main) {
                    // 如果 cd 成功拿到了新路径，更新状态
                    if (isCdCommand && newPathFound != null && !newPathFound!!.contains("No such file")) {
                        currentWorkDir = newPathFound!!
                    }
                    // 更新提示符
                    val newPrompt = "root@$ip:$currentWorkDir # "
                    terminalLines.add(AnnotatedString(newPrompt, spanStyle = SpanStyle(color = ColorPrompt, fontWeight = FontWeight.Bold)))
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        terminalLines.add(AnnotatedString("Execution Error: ${e.message}", spanStyle = SpanStyle(color = ColorError)))
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    currentJob = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Shell 终端", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { currentJob?.cancel(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = { showShortcutDialog = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Shortcuts", tint = AccentBlue)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.Black)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("自动换行", color = Color.Black)
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (isWrapEnabled) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    isWrapEnabled = !isWrapEnabled
                                    showMenu = false
                                }
                            )
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                            DropdownMenuItem(
                                text = { Text("重启终端", color = Color.Black) },
                                onClick = {
                                    terminalLines.clear()
                                    // 重置路径为根目录
                                    currentWorkDir = "/"
                                    val resetPrompt = "root@$ip:/ # "
                                    terminalLines.add(AnnotatedString(resetPrompt, spanStyle = SpanStyle(color = ColorPrompt, fontWeight = FontWeight.Bold)))
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清空输出", color = Color.Black) },
                                onClick = { terminalLines.clear(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("复制所有日志", color = Color.Black) },
                                onClick = {
                                    val fullLog = terminalLines.joinToString("\n") { it.text }
                                    clipboardManager.setText(AnnotatedString(fullLog))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color.Black)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgPage)
                .padding(16.dp)
        ) {
            // 输入卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = BgInput),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(" >_", color = ColorPrompt, modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = commandText, onValueChange = { commandText = it }, placeholder = { Text("输入命令...", color = TextHint) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = AccentBlue
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { executeOrStopCommand() })
                    )
                    // ... 历史记录按钮和运行按钮保持不变 ...
                    Column {
                        SmallArrowButton(Icons.Default.ArrowUpward) {
                            if (commandHistory.isNotEmpty()) {
                                if (historyIndex > 0) historyIndex--
                                if (historyIndex in commandHistory.indices) commandText = commandHistory[historyIndex]
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        SmallArrowButton(Icons.Default.ArrowDownward) {
                            if (commandHistory.isNotEmpty()) {
                                if (historyIndex < commandHistory.size - 1) {
                                    historyIndex++
                                    commandText = commandHistory[historyIndex]
                                } else {
                                    historyIndex = commandHistory.size
                                    commandText = ""
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { executeOrStopCommand() },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (isRunning) StopRed else AccentBlue)
                    ) {
                        if (isRunning) Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        else Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 终端显示区
            Card(
                colors = CardDefaults.cardColors(containerColor = BgTerminal),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .then(if (!isWrapEnabled) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(8.dp),
                        modifier = if (isWrapEnabled) Modifier.fillMaxSize() else Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                    ) {
                        items(terminalLines) { line ->
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                softWrap = isWrapEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // ... ShortcutDialog 等代码保持不变 ...
    if (showShortcutDialog) {
        Dialog(onDismissRequest = { showShortcutDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("快捷指令", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
                        TextButton(onClick = { showShortcutDialog = false; showAddShortcutDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentBlue); Spacer(modifier = Modifier.width(4.dp)); Text("新增", color = AccentBlue)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))
                    LazyColumn {
                        items(shortcuts) { item ->
                            ShortcutItem(item = item, onClick = { executeOrStopCommand(item.cmd); showShortcutDialog = false }, onDelete = { shortcuts.remove(item) })
                        }
                    }
                }
            }
        }
    }
    if (showAddShortcutDialog) {
        AddShortcutDialog(initialCmd = commandText, onDismiss = { showAddShortcutDialog = false }, onConfirm = { n, c -> shortcuts.add(ShellShortcut(n, c)); showAddShortcutDialog = false; showShortcutDialog = true })
    }
}

// ... AddShortcutDialog, ShortcutItem, SmallArrowButton 保持不变 (代码复用) ...
@Composable
fun AddShortcutDialog(initialCmd: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf(initialCmd) }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("新增快捷指令", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("名称", color = TextHint) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain, focusedBorderColor = AccentBlue, unfocusedBorderColor = TextHint)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cmd, onValueChange = { cmd = it }, label = { Text("命令", color = TextHint) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain, focusedBorderColor = AccentBlue, unfocusedBorderColor = TextHint)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消", color = TextHint) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (name.isNotBlank() && cmd.isNotBlank()) onConfirm(name, cmd) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("保存") }
                }
            }
        }
    }
}

@Composable
fun ShortcutItem(item: ShellShortcut, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
            Text(item.cmd, color = ColorPrompt, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ColorError) }
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

@Composable
fun SmallArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(width = 32.dp, height = 22.dp), shape = RoundedCornerShape(4.dp), color = Color(0xFFEEEEEE), contentColor = TextMain) {
        Box(contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp)) }
    }
}