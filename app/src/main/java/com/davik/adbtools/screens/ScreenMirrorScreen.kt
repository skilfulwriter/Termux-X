// 文件名：com/davik/adbtools/screens/ScreenMirrorScreen.kt
package com.davik.adbtools.screens

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.davik.adbtools.adb.AdbConnectionManager
import com.davik.adbtools.adb.Adb
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.stream.AdbStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

const val TAG = "ScrcpyLog"

enum class StreamMode(val label: String, val targetWidth: Int, val bitrate: Int) {
    Low("标准 (720P)", 720, 2000000),      // 默认
    High("高清 (1080P)", 1080, 4000000),
}

data class ScrcpyTouch(val action: Int, val x: Int, val y: Int, val width: Int, val height: Int, val pressure: Int)

class ScrcpyController(private val stream: AdbStream?, scope: CoroutineScope) {
    private val buffer = ByteBuffer.allocate(32)
    private val channel = Channel<ScrcpyTouch>(Channel.UNLIMITED)

    init {
        if (stream != null) {
            scope.launch(Dispatchers.IO) {
                for (t in channel) {
                    try {
                        synchronized(buffer) {
                            buffer.clear()
                            buffer.put(2); buffer.put(t.action.toByte()); buffer.putLong(-1L)
                            buffer.putInt(t.x); buffer.putInt(t.y)
                            buffer.putShort(t.width.toShort()); buffer.putShort(t.height.toShort())
                            buffer.putShort(t.pressure.toShort()); buffer.putInt(1)
                            stream.sink.write(buffer.array(), 0, 28)
                            stream.sink.flush()
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun injectTouch(action: Int, x: Int, y: Int, w: Int, h: Int, p: Float) {
        val pressureInt = (p * 65535).toInt().coerceIn(0, 65535)
        channel.trySend(ScrcpyTouch(action, x, y, w, h, pressureInt))
    }

    fun close() { channel.close(); try { stream?.close() } catch (_: Exception) {} }
}

@Composable
fun ScreenMirrorScreen(ip: String, initialConnection: Adb?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dadbConnection = remember { AdbConnectionManager.getConnection(ip) ?: initialConnection }

    var statusText by remember { mutableStateOf("初始化...") }
    var currentMode by remember { mutableStateOf(StreamMode.Low) }
    var showMenu by remember { mutableStateOf(false) }

    val streamJob = remember { mutableStateOf<Job?>(null) }
    var controller by remember { mutableStateOf<ScrcpyController?>(null) }

    var videoWidth by remember { mutableIntStateOf(720) }
    var videoHeight by remember { mutableIntStateOf(1600) }

    fun align(value: Int): Int = value and 7.inv()

    fun createSoftwareCodec(format: MediaFormat): MediaCodec {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        val softwareCodecs = arrayOf("c2.android.avc.decoder", "OMX.google.h264.decoder")
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mime, ignoreCase = true)) {
                    if (softwareCodecs.contains(info.name)) {
                        Log.d(TAG, "Using Soft Codec: ${info.name}")
                        return MediaCodec.createByCodecName(info.name)
                    }
                }
            }
        }
        return MediaCodec.createDecoderByType(mime)
    }

    fun startStream(surface: Surface) {
        scope.launch {
            streamJob.value?.cancelAndJoin()

            streamJob.value = launch(Dispatchers.IO) {
                var codec: MediaCodec? = null
                var videoStream: AdbStream? = null

                try {
                    Log.d(TAG, "1. Cleanup")
                    try { dadbConnection?.shell("pkill -f scrcpy-server") } catch (_: Exception) {}
                    delay(500)

                    Log.d(TAG, "2. Get Info")
                    withContext(Dispatchers.Main) { statusText = "准备中..." }
                    var deviceW = 1080
                    var deviceH = 2400
                    try {
                        val sizeOutput = dadbConnection?.shell("wm size")?.allOutput ?: ""
                        val regex = Regex("(\\d+)x(\\d+)")
                        val match = regex.find(sizeOutput)
                        if (match != null) {
                            deviceW = match.groupValues[1].toInt()
                            deviceH = match.groupValues[2].toInt()
                        }
                    } catch (_: Exception) {}

                    val mode = currentMode
                    var scrcpyMaxSize = 0
                    if (mode.targetWidth > 0 && mode.targetWidth < min(deviceW, deviceH)) {
                        val scale = mode.targetWidth.toFloat() / min(deviceW, deviceH)
                        scrcpyMaxSize = align((max(deviceW, deviceH) * scale).toInt())
                    }

                    // --- 修正：正确计算初始化分辨率 ---
                    var initWidth = deviceW
                    var initHeight = deviceH

                    if (scrcpyMaxSize > 0) {
                        if (deviceH > deviceW) {
                            // 竖屏
                            initHeight = scrcpyMaxSize
                            initWidth = (scrcpyMaxSize * (deviceW.toFloat() / deviceH.toFloat())).toInt()
                        } else {
                            // 横屏
                            initWidth = scrcpyMaxSize
                            initHeight = (scrcpyMaxSize * (deviceH.toFloat() / deviceW.toFloat())).toInt()
                        }
                    }
                    // 对齐
                    initWidth = align(initWidth)
                    initHeight = align(initHeight)

                    videoWidth = initWidth
                    videoHeight = initHeight

                    Log.d(TAG, "Calculated Resolution: ${initWidth}x${initHeight}")

                    withContext(Dispatchers.Main) { statusText = "部署中..." }

                    // 3. 部署
                    val serverPath = "/data/local/tmp/scrcpy-server.jar"
                    try {
                        dadbConnection?.shell("rm -f $serverPath")
                        context.assets.open("scrcpy-server.jar").use { input ->
                            val temp = File(context.cacheDir, "scrcpy-server.jar")
                            FileOutputStream(temp).use { output -> input.copyTo(output) }
                            dadbConnection?.push(temp, serverPath)
                            dadbConnection?.shell("chmod 777 $serverPath")
                        }
                    } catch (_: Exception) {}

                    // 4. 启动 Scrcpy (Raw Mode)
                    val cmd = "CLASSPATH=$serverPath app_process / com.genymobile.scrcpy.Server 2.4 " +
                            "video_codec=h264 " +
                            "audio=false " +
                            "max_size=$scrcpyMaxSize " +
                            "video_bit_rate=${mode.bitrate} " +
                            "max_fps=30 " +
                            "log_level=debug " +
                            "lock_video_orientation=-1 tunnel_forward=true control=true " +
                            "display_id=0 show_touches=false stay_awake=true " +
                            "codec_options=i-frame-interval=1 encoder_name=- " +
                            "power_off_on_close=true downsize_on_error=true cleanup=true " +
                            "send_device_meta=false send_frame_meta=false send_dummy_byte=false " +
                            "raw_stream=true"

                    Log.d(TAG, "4. Executing: $cmd")
                    launch { try { dadbConnection?.shell(cmd) } catch (_: Exception) {} }
                    delay(800)

                    // 5. 连接
                    withContext(Dispatchers.Main) { statusText = "连接中..." }
                    for (i in 1..5) {
                        try { videoStream = dadbConnection?.open("localabstract:scrcpy"); if(videoStream!=null) break } catch (_: Exception) { delay(300) }
                    }
                    if (videoStream == null) throw Exception("连接超时")

                    try {
                        val ctrlStream = dadbConnection?.open("localabstract:scrcpy")
                        controller = ScrcpyController(ctrlStream, this)
                    } catch (_: Exception) {}

                    Log.d(TAG, "6. Skipped Handshake (Raw Mode)")
                    withContext(Dispatchers.Main) { statusText = "软解渲染 (Raw)..." }

                    // 7. 解码
                    // 使用修正后的分辨率初始化
                    val format = MediaFormat.createVideoFormat("video/avc", initWidth, initHeight)
                    // 设置一个安全的 Max 值，防止动态切分辨率时崩溃
                    format.setInteger(MediaFormat.KEY_MAX_WIDTH, 1920)
                    format.setInteger(MediaFormat.KEY_MAX_HEIGHT, 1920)

                    codec = createSoftwareCodec(format)
                    codec.configure(format, surface, null, 0)
                    codec.start()

                    // 8. 循环
                    val info = MediaCodec.BufferInfo()
                    val dataBuf = ByteArray(512 * 1024)
                    var presentationTimeUs = 0L
                    val frameIntervalUs = 33333L

                    Log.d(TAG, "8. Raw Loop Started")

                    while (isActive) {
                        val len = videoStream.source.read(dataBuf)
                        if (len < 0) break

                        val inIdx = codec.dequeueInputBuffer(2000)
                        if (inIdx >= 0) {
                            codec.getInputBuffer(inIdx)?.let {
                                it.clear()
                                it.put(dataBuf, 0, len)
                            }
                            codec.queueInputBuffer(inIdx, 0, len, presentationTimeUs, 0)
                            presentationTimeUs += frameIntervalUs
                        }

                        var outIdx = codec.dequeueOutputBuffer(info, 2000)
                        while (outIdx >= 0) {
                            codec.releaseOutputBuffer(outIdx, true)
                            outIdx = codec.dequeueOutputBuffer(info, 0)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error: ${e.message}", e)
                    if (e !is CancellationException) {
                        withContext(Dispatchers.Main) { statusText = "异常: ${e.message}" }
                    }
                } finally {
                    try { codec?.stop(); codec?.release() } catch (_: Exception) {}
                    try { videoStream?.close() } catch (_: Exception) {}
                    try { controller?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    fun hideBars(view: View) {
        val window = (view.context as? android.app.Activity)?.window ?: return
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    LaunchedEffect(Unit) {
        if (dadbConnection == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try { dadbConnection.shell("svc power stayon true") } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
//                    layerType = View.LAYER_TYPE_HARDWARE
                    hideBars(this)
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            startStream(Surface(st))
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            streamJob.value?.cancel()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                    setOnTouchListener { v, event ->
                        val ctl = controller
                        if (ctl != null && videoWidth > 0 && videoHeight > 0) {
                            val action = when(event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> 0
                                MotionEvent.ACTION_UP -> 1
                                MotionEvent.ACTION_MOVE -> 2
                                else -> -1
                            }
                            if (action >= 0) {
                                val tx = (event.x / v.width * videoWidth).toInt()
                                val ty = (event.y / v.height * videoHeight).toInt()
                                ctl.injectTouch(action, tx, ty, videoWidth, videoHeight, event.pressure)
                            }
                        }
                        true
                    }
                }
            }
        )

        if (!statusText.startsWith("软解")) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(statusText, color = Color.White, fontSize = 14.sp)
                Button(onClick = { onBack() }) { Text("退出重试") }
            }
        }

        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f))
                .clickable { showMenu = true }.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(currentMode.label.substringBefore(" "), color = Color.White, fontSize = 12.sp)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                StreamMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            currentMode = mode
                            showMenu = false
                            statusText = "请退出重进以应用画质"
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("退出", color = Color.Red) },
                    leadingIcon = { Icon(Icons.Default.Close, null, tint = Color.Red) },
                    onClick = { onBack() }
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
    }
}