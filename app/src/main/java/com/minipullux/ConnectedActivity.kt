package com.minipullux

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.minipullux.service.BLECharacteristic
import com.minipullux.service.BLEService
import com.minipullux.ui.theme.CyberpunkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class ConnectedActivity : ComponentActivity() {
    private val viewModel: BLEViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CyberpunkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConnectedScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionEvent.collect { event ->
                    event.getContentIfNotHandled()?.let { state ->
                        when (state) {
                            BLEService.ConnectionState.DISCONNECTED -> {
                                // 跳转到新 Activity
                                startActivity(
                                    Intent(
                                        this@ConnectedActivity,
                                        MainActivity::class.java
                                    )
                                )
                                finish() // 可选：关闭当前 Activity
                            }

                            else -> { /* 其他状态处理 */
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedScreen(
    viewModel: BLEViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val characteristics by viewModel.characteristics.collectAsState()
    val values by viewModel.values.collectAsState()
    val progress by viewModel.otaProgress.collectAsState()

    var showOtaDialog by remember { mutableStateOf(false) }
    var otaFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // 从URI读取文件内容（需实现实际读取逻辑）
            otaFileBytes = readFileFromUri(context, it)
            showOtaDialog = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CyberpunkOtaButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        ConnectedContent(
            characteristics = characteristics,
            values = values,
            onRead = { viewModel.read(it) },
            onWrite = { char, value -> viewModel.write(char, value) },
            onToggleNotify = { characteristic, enable ->
                viewModel.onToggleNotify(
                    characteristic,
                    enable
                )
            }
        )
    }

    // OTA确认对话框
    if (showOtaDialog) {
        CyberpunkOtaDialog(
            progress = progress,
            onDismiss = { showOtaDialog = false },
            onConfirm = {
                otaFileBytes?.let { bytes ->
                    viewModel.update(bytes)
                }
//                showOtaDialog = false
            }
        )
    }
}

fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

@Preview
@Composable
fun ConnectedContentPre() {
    // 模拟一些数据
    val characteristic1 = BLECharacteristic(
        UUID.randomUUID(),
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
        BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
        false
    )
    val characteristic2 = BLECharacteristic(
        UUID.randomUUID(),
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ,
        true
    )
    val characteristics = listOf(characteristic1, characteristic2)
    val values = mapOf(
        characteristic1.uuid to "Sample value 1".toByteArray(),
        characteristic2.uuid to "Sample value 2".toByteArray()
    )
    var showOtaDialog by remember { mutableStateOf(false) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            showOtaDialog = true
        }
    }
    CyberpunkTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            CyberpunkOtaButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            ConnectedContent(
                characteristics = characteristics,
                values = values,
                onRead = { /* 预览中不处理实际逻辑 */ },
                onWrite = { _, _ -> /* 预览中不处理实际逻辑 */ },
                onToggleNotify = { _, _ ->/* 预览中不处理实际逻辑 */ }
            )
        }

        var progress by remember { mutableFloatStateOf(0f) }
        val coroutineScope = rememberCoroutineScope()
        // OTA确认对话框
        if (showOtaDialog) {
            CyberpunkOtaDialog(
                progress = progress,
                onDismiss = { showOtaDialog = false },
                onConfirm = {
                    //携程模拟progress进度
                    coroutineScope.launch {
                        // 线性进度模拟
                        while (progress < 1f) {
                            delay(500) // 每500ms更新一次
                            progress += 0.1f
                            progress = progress.coerceAtMost(1f)
                        }
                        showOtaDialog = false
                        progress = 0f
                    }
                }
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    characteristics: List<BLECharacteristic>,
    values: Map<UUID, ByteArray>,
    onRead: (BLECharacteristic) -> Unit,
    onWrite: (BLECharacteristic, ByteArray) -> Unit,
    onToggleNotify: (BLECharacteristic, Boolean) -> Unit
) {
    var selectedChar by remember { mutableStateOf<BLECharacteristic?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .cyberpunkBorderEffect()
    ) {
        items(characteristics) { char ->
            CyberpunkCharacteristicCard(
                characteristic = char,
                value = values[char.uuid]?.toHexString() ?: "---",
                onAction = { action, param ->
                    when (action) {
                        CharacteristicAction.READ -> onRead(char)
                        CharacteristicAction.WRITE -> selectedChar = char
                        CharacteristicAction.NOTIFY -> onToggleNotify(char, param!!)
                    }
                }
            )
        }
    }

    selectedChar?.let { char ->
        CyberpunkWriteDialog(
            title = char.uuid.toString(),
            message = "",
            onDismiss = { selectedChar = null },
            onConfirm = {
                if (it.isNotEmpty()) onWrite(char, it.hexStringToByteArray())
                selectedChar = null
            }
        )
    }
}

fun String.hexStringToByteArray(): ByteArray {
    val trimmed = this.replace("\\s".toRegex(), "") // 可选：移除空格
    require(trimmed.length % 2 == 0) { "长度需为偶数" }
    return trimmed.chunked(2).map {
        it.toInt(16).toByte()
    }.toByteArray()
}

enum class CharacteristicAction {
    READ,
    WRITE,
    NOTIFY,
}

// Cyberpunk 风格组件库
@Composable
fun CyberpunkCharacteristicCard(
    characteristic: BLECharacteristic,
    value: String,
    onAction: (CharacteristicAction, Boolean?) -> Unit
) {
    val neonBlue = Color(0xFF00FFE0)
    val neonPink = Color(0xFFFF00FF)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .cyberpunkGlow(animateColorAsState(neonBlue, label = "").value)
            .hoverCyberpunkEffect(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2F).copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            // UUID 显示
            Text(
                text = characteristic.uuid.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = neonBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            isCharacteristicNotifiable(characteristic)
            characteristic.properties
            // 属性徽章
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isCharacteristicReadable(characteristic)) {
                    CyberpunkBadge(
                        text = "READ",
                        color = neonBlue,
                        onClick = { onAction(CharacteristicAction.READ, null) }
                    )
                }
                if (isCharacteristicWritable(characteristic)) {
                    CyberpunkBadge(
                        text = "WRITE",
                        color = neonPink,
                        onClick = { onAction(CharacteristicAction.WRITE, null) }
                    )
                }
                if (isCharacteristicNotifiable(characteristic)) {
                    CyberpunkBadge(
                        text = "NOTIFY",
                        color = Color(0xFF9D00FF),
                        isActive = characteristic.isNotifyEnabled,
                        onClick = {
                            onAction(CharacteristicAction.NOTIFY, !characteristic.isNotifyEnabled)
                        }
                    )
                }
            }

            // 数值显示器
            AnimatedVisibility(visible = value.isNotEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "VALUE:",
                        style = MaterialTheme.typography.headlineLarge,
                        color = neonPink
                    )
                    MarqueeText(
                        text = value,
                        color = neonBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

// 自定义修饰符
@SuppressLint("SuspiciousModifierThen")
fun Modifier.cyberpunkGlow(color: Color) = this.then(
    drawBehind {
        val strokeWidth = 2.dp.toPx()
        drawRoundRect(
            color = color,
            style = Stroke(width = strokeWidth),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                radius = size.width / 2
            ),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
    }
)

@Composable
fun CyberpunkBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val neonColor = if (isActive) Color(0xFF00FFE0) else Color(0xFFFF00FF)
    val backgroundColor = Color(0xFF1A1A2F)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        color = neonColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        shadow = Shadow(
            color = neonColor.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 8f
        )
    )
    Box(
        modifier = modifier
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(neonColor, neonColor.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Text(
            text = text,
            style = labelStyle
        )
    }
}

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 16.sp,
    gradientEdgeWidth: Dp = 32.dp
) {
    val scrollState = rememberScrollState()
    var shouldScroll by remember { mutableStateOf(false) }

    LaunchedEffect(shouldScroll) {
        if (shouldScroll) {
            while (true) {
                scrollState.animateScrollTo(
                    value = scrollState.maxValue,
                    animationSpec = tween(5000, easing = LinearEasing)
                )
                scrollState.scrollTo(0)
            }
        }
    }

    Box(modifier = modifier) {
        // 文字容器
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .drawWithContent {
                    drawContent()
                    // 添加渐变遮罩
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black,
                                Color.Black,
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = size.width
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                modifier = Modifier.padding(horizontal = gradientEdgeWidth)
            )

        }
    }
}

@Composable
fun dpToPx(dp: Dp): Int {
    // 获取当前的 Density 对象
    val density = LocalDensity.current
    return with(density) { dp.toPx().toInt() }
}

@Composable
fun CyberpunkTerminalEffect() {
    var isHovered by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "")

    // 背景网格动画
    val gridOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = dpToPx(dp = 16.dp).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ), label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                // 绘制背景网格
                for (i in 0..size.width.toInt() step 32) {
                    drawLine(
                        color = Color(0x3300FFE0),
                        start = Offset(i.toFloat() + gridOffset, 0f),
                        end = Offset(i.toFloat() + gridOffset, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                drawContent()
            }
            .cyberpunkHoverWithScanline()
    ) {
        // 终端内容...
    }
}

@Composable
fun Modifier.cyberpunkHoverWithScanline(
    baseColor: Color = Color.Black,
    accentColor: Color = Color(0xFFFF00FF)
) = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ), label = ""
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    this
        .hoverable(interactionSource = interactionSource)
        .drawWithContent {
            drawContent()

            // 悬停时绘制特效
            if (isHovered) {
                // 扫描线
                drawLine(
                    color = accentColor.copy(alpha = 0.3f),
                    start = Offset(0f, size.height * scanOffset),
                    end = Offset(size.width, size.height * scanOffset),
                    strokeWidth = 2.dp.toPx(),
                    blendMode = BlendMode.Screen
                )

                // 像素干扰
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        color = accentColor
                        blendMode = BlendMode.Xor
                    }
                    repeat(20) {
                        canvas.drawRect(
                            left = Random.nextFloat() * size.width,
                            top = Random.nextFloat() * size.height,
                            right = Random.nextFloat() * size.width,
                            bottom = Random.nextFloat() * size.height,
                            paint = paint
                        )
                    }
                }
            }

            // 边框
            drawRoundRect(
                color = if (isHovered) accentColor else baseColor,
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(2.dp.toPx())
            )
        }
}

@Composable
fun Modifier.hoverCyberpunkEffect(
    baseColor: Color = Color(0xFF1A1A2F),
    glowColor: Color = Color(0xFF00FFE0),
    intensity: Float = 0.8f
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val animatedGlow by animateFloatAsState(
        targetValue = if (isHovered) intensity else 0f,
        animationSpec = tween(300), label = ""
    )

    this
        .hoverable(interactionSource = interactionSource)
//        .graphicsLayer {
//            renderEffect = RenderEffect
//                .createBlurEffect(
//                    radiusX = (animatedGlow * 20).dp.toPx(),
//                    radiusY = (animatedGlow * 20).dp.toPx(),
//                    Shader.TileMode.DECAL
//                )
//                .asComposeRenderEffect()
//        }
        .drawBehind {
            // 基础边框
            drawRoundRect(
                color = baseColor,
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(2.dp.toPx())
            )

            // 光晕层
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = animatedGlow * 0.6f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2 * (1 + animatedGlow)
                ),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke((2 + animatedGlow * 4).dp.toPx())
            )
        }
}

fun Modifier.glitchEffect() = composed {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 0
                2f at 200
                -3f at 400
                0f at 600
            }
        ), label = ""
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 0
                -2f at 300
                3f at 500
                0f at 700
            }
        ), label = ""
    )

    this.graphicsLayer {
        translationX = offsetX
        translationY = offsetY
        alpha = 0.95f
//        renderEffect = with(density) {
//            RenderEffect.createOffsetEffect(offsetX, offsetY)
//                .asComposeRenderEffect()
//        }
    }
}

fun Modifier.cyberpunkBorderEffect(
    baseColor: Color = Color(0xFF00FFE0),
    glowColor: Color = Color(0xFFFF00FF),
    thickness: Dp = 2.dp
) = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                0.5f at 0
                1f at 500
                0.8f at 1000
            }
        ), label = ""
    )

    this.drawBehind {
        // 基础边框
        drawRoundRect(
            color = baseColor,
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(thickness.toPx())
        )

        // 光晕层
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.7f * glowIntensity),
                    Color.Transparent
                )
            ),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke((thickness * 3 * glowIntensity).toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
@Preview
fun CyberpunkWriteDialogPre() {
    CyberpunkWriteDialog(
        title = "0000ff01-0000-1000-8000-00805F9B34FB",
        message = "",
        onConfirm = {},
        onDismiss = {}
    )
}

@Composable
fun CyberpunkWriteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val scanlineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ), label = ""
    )
    var inputText by remember { mutableStateOf("") }
    val isValidHex = remember(inputText) {
        inputText.matches("^[0-9A-Fa-f]*$".toRegex()) && inputText.length % 2 == 0
    }
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor = Color(0xFF00FFE0)
    )
    Dialog(onDismissRequest = onDismiss) {
        val scanlineProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            ), label = ""
        )
        Box(
            modifier = modifier
                .cyberpunkBorderEffect()  // 自定义边框效果
                .background(Color(0xFF0A0A1A))
                .padding(24.dp)
                .drawBehind {
                    // 绘制扫描线动画
                    drawLine(
                        color = Color(0xFF00FFE0).copy(alpha = 0.3f),
                        start = Offset(0f, size.height * scanlineProgress),
                        end = Offset(size.width, size.height * scanlineProgress),
                        strokeWidth = 2.dp.toPx()
                    )
                }
        ) {
            // 数字雨背景
//            DigitalRainBackground(modifier = Modifier.matchParentSize())

            Column {
                // 标题带打字机效果
                CyberpunkTypewriterText(
                    text = title,
                    textStyle = MaterialTheme.typography.headlineMedium
                        .copy(color = Color(0xFF00FFE0)),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 新增十六进制输入框
                CyberpunkTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        inputText = newValue
                            .uppercase()
                            .filter { it.isLetterOrDigit() }
                    },
                    label = "HEX DATA",
                    isValid = isValidHex,
                    colors = textFieldColors,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .cyberpunkBorderEffect(
                            baseColor = if (isValidHex) Color(0xFF00FFE0) else Color(0xFFFF0044)
                        )
                )
                // 确认按钮
                CyberpunkButton(
                    text = "CONFIRM",
                    onClick = { onConfirm(inputText) },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// 自定义赛博朋克风格输入框
@Composable
private fun CyberpunkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isValid: Boolean,
    colors: TextFieldColors,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val strokeWidth = 2.dp
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    Box(modifier.padding(strokeWidth)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    text = label,
                    color = Color(0xFF00FFE0).copy(alpha = 0.7f)
                )
            },
            colors = colors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            textStyle = LocalTextStyle.current.copy(
                color = Color(0xFF00FFE0),
                fontFamily = FontFamily.Monospace
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .focusRequester(focusRequester)
                .onFocusChanged { hasFocus = it.isFocused }
                .focusable(),
        )
    }
}

@Composable
fun CyberpunkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by remember { mutableStateOf(Color(0xFF00FFE6)) }

    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(text = text.uppercase())
    }
}

@Composable
fun CyberpunkTypewriterText(
    text: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    var visibleChars by remember { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        visibleChars = 0
        while (visibleChars < text.length) {
            delay(50)
            visibleChars++
        }
    }

    Box(modifier) {
        Text(
            fontSize = 13.sp,
            text = text.take(visibleChars),
            style = textStyle,
            modifier = Modifier
                .glitchEffect()  // 故障效果
        )
    }
}

@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    cursorColor: Color = Color(0xFF00FFE0),
    cursorWidth: Dp = 2.dp,
    animationDuration: Int = 800
) {
    // 创建无限闪烁动画
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    // 光标图形绘制
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(cursorWidth)
        ) {
            drawRect(
                color = cursorColor.copy(alpha = alpha),
                size = Size(cursorWidth.toPx(), size.height)
            )
        }
    }
}

// 文件读取工具函数（需在Activity中处理实际文件读取）
private fun readFileFromUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}