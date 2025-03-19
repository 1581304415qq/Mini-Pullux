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
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipullux.service.BLEService
import com.minipullux.service.BLEService.ConnectionState
import com.minipullux.service.OnCharacteristicReadListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class BLEViewModel(application: Application) : AndroidViewModel(application) {
    private val _devices = MutableStateFlow(listOf<BLEDevice>())
    val devices get() = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning get() = _isScanning.asStateFlow()

    // 暴露连接状态给UI层
    private val _connectionEvent = MutableSharedFlow<Event<ConnectionState>>()
    val connectionEvent: SharedFlow<Event<ConnectionState>> = _connectionEvent

    private val _characteristics = MutableStateFlow<List<BluetoothGattCharacteristic>>(listOf())
    val characteristics get() = _characteristics.asStateFlow()

    private val _values = MutableStateFlow<MutableMap<UUID, ByteArray>>(mutableMapOf())
    val values get() = _values.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private var bleService: BLEService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bleService = (service as BLEService.LocalBinder).getService()
            viewModelScope.launch {
                bleService?.connectionState?.collect {
                    Log.i("BLE Viewmodel", "$it")
                    if (it == ConnectionState.CONNECTED) {
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
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        bleScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray) {

    }

    @SuppressLint("MissingPermission")
    fun read(characteristic: BluetoothGattCharacteristic) {

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

open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) null else {
            hasBeenHandled = true
            content
        }
    }
}
