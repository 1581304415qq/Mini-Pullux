package com.minipullux

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.minipullux.ui.theme.CyberpunkTheme
import kotlinx.coroutines.delay
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
    }
}

@Composable
fun ConnectedScreen(
    viewModel: BLEViewModel,
    modifier: Modifier = Modifier
) {
    val characteristics by viewModel.characteristics.collectAsState()
    val values by viewModel.values.collectAsState()
    ConnectedContent(
        characteristics = characteristics,
        values = values,
        onRead = { /* 预览中不处理实际逻辑 */ },
        onWrite = { _, _ -> /* 预览中不处理实际逻辑 */ },
        onToggleNotify = { /* 预览中不处理实际逻辑 */ }
    )
}

fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

@Preview
@Composable
fun ConnectedContentPre() {
    // 模拟一些数据
    val characteristic1 = BluetoothGattCharacteristic(
        UUID.randomUUID(),
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
        BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
    )
    val characteristic2 = BluetoothGattCharacteristic(
        UUID.randomUUID(),
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )
    val characteristics = listOf(characteristic1, characteristic2)
    val values = mapOf(
        characteristic1.uuid to "Sample value 1".toByteArray(),
        characteristic2.uuid to "Sample value 2".toByteArray()
    )

    CyberpunkTheme {
        ConnectedContent(
            characteristics = characteristics,
            values = values,
            onRead = { /* 预览中不处理实际逻辑 */ },
            onWrite = { _, _ -> /* 预览中不处理实际逻辑 */ },
            onToggleNotify = { /* 预览中不处理实际逻辑 */ }
        )
    }
}

@Composable
private fun ConnectedContent(
    characteristics: List<BluetoothGattCharacteristic>,
    values: Map<UUID, ByteArray>,
    onRead: (BluetoothGattCharacteristic) -> Unit,
    onWrite: (BluetoothGattCharacteristic, ByteArray) -> Unit,
    onToggleNotify: (BluetoothGattCharacteristic) -> Unit
) {
    var selectedChar by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .cyberpunkBorderEffect()
    ) {
        items(characteristics) { char ->
            CyberpunkCharacteristicCard(
                characteristic = char,
                value = values[char.uuid]?.toHexString() ?: "---",
                onAction = { action ->
                    when (action) {
                        CharacteristicAction.READ -> onRead(char)
                        CharacteristicAction.WRITE -> selectedChar = char
                        CharacteristicAction.NOTIFY -> onToggleNotify(char)
                    }
                }
            )
        }
    }

    selectedChar?.let { char ->
        CyberpunkWriteDialog(
            title = "char.service",
            message = "char.value",
            onDismiss = { selectedChar = null },
            onConfirm = { value ->
                onWrite(char, value.toByteArray())
                selectedChar = null
            }
        )
    }
}

enum class CharacteristicAction {
    READ,
    WRITE,
    NOTIFY,
}

// Cyberpunk 风格组件库
@Composable
fun CyberpunkCharacteristicCard(
    characteristic: BluetoothGattCharacteristic,
    value: String,
    onAction: (CharacteristicAction) -> Unit
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
            Row {
                if (isCharacteristicReadable(characteristic)) {
                    CyberpunkBadge(
                        text = "READ",
                        color = neonBlue,
                        onClick = { onAction(CharacteristicAction.READ) }
                    )
                }
                if (isCharacteristicWritable(characteristic)) {
                    CyberpunkBadge(
                        text = "WRITE",
                        color = neonPink,
                        onClick = { onAction(CharacteristicAction.WRITE) }
                    )
                }
                if (isCharacteristicNotifiable(characteristic)) {
                    CyberpunkBadge(
                        text = "NOTIFY",
                        color = Color(0xFF9D00FF),
                        onClick = { onAction(CharacteristicAction.NOTIFY) }
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
        // 自动检测内容宽度
//        Text(
//            text = text,
//            color = Color.Transparent,
//            fontSize = fontSize,
//            modifier = Modifier
//                .alpha(0f)
//                .onGloballyPositioned { layoutCoordinates ->
//                    val contentWidth = layoutCoordinates.size.width
//                    val containerWidth = layoutCoordinates.parentCoordinates?.size?.width ?: 0
//                    shouldScroll = contentWidth > containerWidth
//                }
//        )
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
    val infiniteTransition = rememberInfiniteTransition()
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
        )
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
        )
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
    val infiniteTransition = rememberInfiniteTransition()
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
        )
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

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = modifier
                .cyberpunkBorderEffect()  // 自定义边框效果
                .background(Color(0xFF0A0A1A))
                .padding(24.dp)
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

                // 消息内容带扫描线
//                BoxWithConstraints {
//                    CyberpunkScrollingText(
//                        text = message,
//                        scanOffset = scanlineOffset,
//                        modifier = Modifier
//                            .heightIn(max = maxHeight * 0.6f)
//                            .fillMaxWidth()
//                    )
//                }

                // 确认按钮
                CyberpunkButton(
                    text = "CONFIRM",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // 扫描线效果
//            DrawScanlines(scanOffset = scanlineOffset)
        }
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
            text = text.take(visibleChars),
            style = textStyle,
            modifier = Modifier
                .glitchEffect()  // 故障效果
//                .blurBorder()
        )

        // 光标闪烁
//        if (visibleChars < text.length) {
//            BlinkingCursor(
//                modifier = Modifier
//                    .offset(x = 4.dp)
//                    .align(Alignment.CenterStart)
//            )
//        }
    }
}
