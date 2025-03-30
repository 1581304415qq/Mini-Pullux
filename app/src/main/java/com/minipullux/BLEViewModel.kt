package com.minipullux

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minipullux.event.EventDispatcher
import com.minipullux.service.BLECharacteristic
import com.minipullux.service.BLEService
import com.minipullux.service.BLEService.ConnectionState
import com.minipullux.service.OnCharacteristicReadListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

class BLEViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "BLEViewModel"

    private val _devices = MutableStateFlow(listOf<BLEDevice>())
    val devices get() = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning get() = _isScanning.asStateFlow()

    // 暴露连接状态给UI层
    private val _connectionEvent = MutableSharedFlow<Event<ConnectionState>>()
    val connectionEvent: SharedFlow<Event<ConnectionState>> = _connectionEvent

    private val _characteristics = MutableStateFlow<List<BLECharacteristic>>(listOf())
    val characteristics get() = _characteristics.asStateFlow()

    private val _values = MutableStateFlow<MutableMap<UUID, ByteArray>>(mutableMapOf())
    val values get() = _values.asStateFlow()

    private val _otaProgress = MutableStateFlow(0f)
    val otaProgress get() = _otaProgress.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private var bleService: BLEService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bleService = (service as BLEService.LocalBinder).getService()
            viewModelScope.launch {
                bleService?.connectionState?.collect {
                    Log.i("BLE Viewmodel", "$it")
                    if (it == ConnectionState.CONNECTED || it == ConnectionState.DISCONNECTED) {
                        _connectionEvent.emit(Event(it))
                    }
                }
            }

            bleService?.let {
                bleService!!.setOnCharacteristicReadListener(onCharacteristicReadListener)
                _characteristics.value = bleService!!.characteristics
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    init {
        bindService()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), BLEService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun connectToDevice(device: BLEDevice) {
        val bleDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (bleDevice != null) {
            bleService?.connectToDevice(bleDevice)
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.v(
                "BLE ViewModel",
                "onScanResult: ${result.device.address} - ${result.device.name} ${result.rssi}"
            )

            val device = BLEDevice(
                name = result.device.name,
                address = result.device.address,
                rssi = result.rssi
            )
            addDevice(device)
        }
    }

    // 添加设备逻辑
    private fun addDevice(device: BLEDevice) {
        _devices.update { currentList ->
            if (currentList.any { it.address == device.address }) {
                currentList // 已存在，不添加
            } else {
                currentList + device // 创建新列表
            }
        }
    }

    fun toggleScan() {
        if (_isScanning.value) {
            println("stop scan")
            stopScan()
        } else {
            println("start scan")
            startScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        _isScanning.value = true
        _devices.value = listOf()

        val scanFilter = ScanFilter.Builder().build()
        val scanFilters = mutableListOf<ScanFilter>()
        scanFilters.add(scanFilter)
        val scanSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0) // 立即上报结果
                .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED) // 启用所有PHY支持
                .setLegacy(false) // 明确关闭传统广播模式
                .build()
        } else {
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
        }
        bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        bleScanner?.stopScan(scanCallback)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun asyncWrite(characteristic: BLECharacteristic, value: ByteArray) {
        Log.i(TAG, "${characteristic.uuid} ${value[0].toHexString()}")
        bleService?.asyncWriteCharacteristic(characteristic, value)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun write(characteristic: BLECharacteristic, value: ByteArray) {
        Log.i(TAG, "${characteristic.uuid} ${value[0].toHexString()}")
        bleService?.writeCharacteristic(characteristic, value)
    }

    @SuppressLint("MissingPermission")
    fun read(characteristic: BLECharacteristic) {
        Log.i(TAG, "read ${characteristic.uuid}")
        bleService?.readCharacteristic(characteristic)
    }

    fun onToggleNotify(characteristic: BLECharacteristic, enable: Boolean) {
        bleService?.enableNotifications(characteristic, enable)

        _characteristics.update { currentList ->
            currentList.map { item ->
                if (item.uuid == characteristic.uuid) item.copy(isNotifyEnabled = enable) else item
            }
        }
    }

    val MTU = 247
    private var seq = 0
    private val dispatcher = EventDispatcher<MyEventType, MyEvent<*>>()
    private var otaUpdateJob: Job? = null
    fun update(bytes: ByteArray) {
        Log.i(TAG, "update ${bytes.size}")
        otaUpdateJob?.cancel() // 取消之前的任务
        otaUpdateJob = viewModelScope.launch {
            // 添加最大重试次数（例如5次）
            val maxRetries = 5
            var attempt = 0
            // 1.修改mtu 2.发送20 3.分段发送数据 4.发送24
            bleService?.setMTU(MTU)
            delay(100)
            startOta(0x20)
            var sent = 0
            while (sent < bytes.size) {
                val len = min(MTU - 4, bytes.size - sent)

                var ret = seqOta((0x28 + seq).toByte())
                while (!ret && attempt < maxRetries) {
                    ret = seqOta((0x28 + seq).toByte())
                    attempt++
                    delay(100)
                }

                attempt = 0
                ret = senOtaData(bytes.copyOfRange(sent, sent + len))
                while (!ret && attempt < maxRetries) {
                    ret = senOtaData(bytes.copyOfRange(sent, sent + len))
                    attempt++
                    delay(100)
                }

                seq = (seq + 1) % 4
                sent += len
                _otaProgress.value = sent.toFloat() / bytes.size
                println("$sent/${bytes.size}, ${_otaProgress.value}, $seq")
            }
            delay(3_000)
            startOta(0x24)
        }
    }

    fun updateCancel() {
        otaUpdateJob?.cancel()
        otaUpdateJob = null
    }

    private suspend fun senOtaData(data: ByteArray) = suspendCoroutine {
        write(characteristics.value[2], data)
        // wait 回应
        dispatcher.once(
            eventType = MyEventType.OtaEventSeqType,
            timeOut = 1000,
            onTimeOut = {
                it.resume(false)
            }) { event ->
            Log.i(TAG, "ack=${event.data} seq=${seq}")
            if (seq == event.data)
                it.resume(true)
            else
                it.resume(false)
        }
    }

    private suspend fun startOta(command: Byte) = suspendCoroutine {
        write(characteristics.value[1], byteArrayOf(command))
        // wait 回应
        dispatcher.once(MyEventType.OtaEventStartType,
            timeOut = 1000,
            onTimeOut = { it.resume(false) }) { event ->
            if (event.data == 0)
                it.resume(true)
            else
                it.resume(false)
        }
    }

    private suspend fun seqOta(command: Byte) = suspendCoroutine {
        write(characteristics.value[1], byteArrayOf(command))
        // wait 回应
        dispatcher.once(MyEventType.OtaEventSeqType,
            timeOut = 1000, onTimeOut = {
                it.resume(false)
            }) { event ->
            if (event.data == 0)
                it.resume(true)
            else
                it.resume(false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unbindService(serviceConnection)
    }

    val onCharacteristicReadListener = object : OnCharacteristicReadListener {
        override fun updateBluetoothData(
            characteristic: BluetoothGattCharacteristic,
            data: ByteArray
        ) {
            _values.update { currentMap ->
                currentMap.toMutableMap().apply {
                    put(characteristic.uuid, data) // 原子性修改
                }
            }

            if (characteristic.uuid == characteristics.value[1].uuid) {
                Log.i(TAG, "ctrl channel recv:${data.toHexString()}")
                data.forEach {
                    if ((it.toInt() and 0xFC) == 0x20) {
                        dispatcher.dispatch(
                            MyEventType.OtaEventStartType,
                            MyEvent.OtaEvent(it.toInt() and 0x3)
                        )
                    } else if ((it.toInt() and 0xFC) == 0x24) {
                        dispatcher.dispatch(
                            MyEventType.OtaEventEndType,
                            MyEvent.OtaEvent(it.toInt() and 0x3)
                        )
                    } else if ((it.toInt() and 0xFC) == 0x28) {
                        dispatcher.dispatch(
                            MyEventType.OtaEventSeqType,
                            MyEvent.OtaEvent(it.toInt() and 0x3)
                        )
                    }
                }
            }
        }
    }

    class Event<out T>(private val content: T) {
        private var hasBeenHandled = false
            private set

        fun getContentIfNotHandled(): T? {
            return if (hasBeenHandled) null else {
                hasBeenHandled = true
                content
            }
        }
    }
}

// 修改后的BLEDevice数据类
@SuppressLint("MissingPermission")
data class BLEDevice(
    val name: String?,
    val address: String,
    val rssi: Int
) {
    override fun equals(other: Any?): Boolean {
        return other is BLEDevice && address == other.address
    }

    override fun hashCode() = address.hashCode()

    companion object {
        fun fromDevice(device: BluetoothDevice): BLEDevice {
            return BLEDevice(
                name = device.name,
                address = device.address,
                rssi = -1 // 需要从扫描结果获取
            )
        }
    }
}
