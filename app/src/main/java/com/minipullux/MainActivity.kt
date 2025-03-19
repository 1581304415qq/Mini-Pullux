package com.minipullux

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.minipullux.service.BLEService
import com.minipullux.ui.theme.CyberpunkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val viewModel: BLEViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyberpunkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PermissionPage()
                    BLEMainScreen(
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
                            BLEService.ConnectionState.CONNECTED -> {
                                // 跳转到新 Activity
                                startActivity(
                                    Intent(
                                        this@MainActivity,
                                        ConnectedActivity::class.java
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

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScan()
    }
}

@Composable
fun BLEMainScreen(
    viewModel: BLEViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    BLEPage(modifier = modifier,
        isScanning = isScanning,
        devices = devices.toList(),
        scanFunc = { viewModel.toggleScan() },
        connectFunc = { viewModel.connectToDevice(it) })
}

@Composable
@Preview
fun BLEPagePre() {
    CyberpunkTheme {
        BLEPage(
            isScanning = true,
            devices = listOf(
                BLEDevice("media", "xx-xxx-xx", -10),
                BLEDevice("media", "xx-xxx-xx", 20),
                BLEDevice("media", "xx-xxx-xx", 30)
            ),
            scanFunc = {},
            connectFunc = {}
        )
    }
}

@Composable
fun BLEPage(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    devices: List<BLEDevice>,
    scanFunc: () -> Unit,
    connectFunc: (BLEDevice) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "CYBER-BLE SCANNER",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // 扫描控制
        CyberButton(
            text = if (isScanning) "STOP SCAN" else "START SCAN",
            onClick = { scanFunc() },
            modifier = Modifier.fillMaxWidth()
        )

        // 设备列表
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(devices) { device ->
                DeviceCard(
                    device = device,
                    onClick = { connectFunc(device) }
                )
            }
        }
    }
}

@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var borderColor by remember { mutableStateOf(Color(0xFF00FFE6)) }

//    LaunchedEffect(Unit) {
//        while (true) {
//            borderColor = Color(0xFF00FFE6)
//            delay(1000)
//            borderColor = Color(0xFFAA00FF)
//            delay(1000)
//        }
//    }

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
fun DeviceCard(device: BLEDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .shadow(
                elevation = 8.dp,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = device.address,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { abs(device.rssi) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
