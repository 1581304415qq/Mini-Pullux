package com.minipullux

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CyberpunkOtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val neonBlue = Color(0xFF00FFE0)

    Button(
        onClick = onClick,
        modifier = modifier
            .cyberpunkBorderEffect()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1A1A2F),
            contentColor = neonBlue
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "OTA升级",
                tint = neonBlue
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "固件升级",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 18.sp,
                    shadow = Shadow(
                        color = neonBlue.copy(alpha = 0.5f),
                        offset = Offset(2f, 2f),
                        blurRadius = 8f
                    )
                )
            )
        }
    }
}

// 新增OTA对话框组件
@Composable
fun CyberpunkOtaDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    progress: Float,
    modifier: Modifier = Modifier
) {
    var isUpgrading by remember { mutableStateOf(true) }
//    var progress by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "")
    val scanlineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ), label = ""
    )

    Dialog(onDismissRequest = {}) {
        Box(
            modifier = modifier
                .cyberpunkBorderEffect()
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
            Column {
                Text(
                    text = "固件升级",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color(0xFF00FFE0),
                        shadow = Shadow(
                            color = Color(0xFF00FFE0).copy(alpha = 0.5f),
                            offset = Offset(2f, 2f),
                            blurRadius = 8f
                        )
                    )
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "准备好升级固件了吗？\n此操作可能需要几分钟时间。",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )

                Spacer(Modifier.height(24.dp))
                // 动态内容区域
                if (isUpgrading) {
                    CyberpunkProgressBar(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(bottom = 24.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CyberpunkButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    CyberpunkButton(
                        text = "开始升级",
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

// 赛博朋克风格进度条组件
@Composable
private fun CyberpunkProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val neonBlue = Color(0xFF00FFE0)

    Box(
        modifier = modifier
            .cyberpunkBorderEffect()
            .drawWithContent {
                // 绘制背景
                drawRect(
                    color = Color(0xFF1A1A2F),
                    size = size
                )

                // 绘制进度条
                val progressWidth = size.width * progress
                drawRect(
                    color = neonBlue.copy(alpha = 0.3f),
                    topLeft = Offset.Zero,
                    size = Size(progressWidth, size.height)
                )

                // 绘制光效
                drawLine(
                    color = neonBlue.copy(alpha = 0.6f),
                    start = Offset(progressWidth, 0f),
                    end = Offset(progressWidth, size.height),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 绘制扫描线效果
                for (i in 0..3) {
                    drawLine(
                        color = neonBlue.copy(alpha = 0.2f),
                        start = Offset(0f, size.height * i / 4f),
                        end = Offset(size.width, size.height * i / 4f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
    )
}

@Composable
fun PickFile(pickFileHandler: (Context, Uri) -> Unit) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) pickFileHandler(context, uri)
        }

    Button(onClick = { launcher.launch("*/*") }) {
        Text(text = "打开文件")
    }
}