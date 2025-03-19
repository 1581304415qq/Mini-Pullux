package com.minipullux.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.minipullux.BLEDevice
import com.minipullux.isCharacteristicNotifiable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue

class BLEService : Service() {
    private val binder = LocalBinder()
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState get() = _connectionState.asStateFlow()
    private val _connectedDevice = MutableStateFlow<BLEDevice?>(null)
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private var gatt: BluetoothGatt? = null
    val characteristics = createCharacteristics()

    inner class LocalBinder : Binder() {
        fun getService(): BLEService = this@BLEService
    }

    private val discoveryQueue = LinkedBlockingQueue<BluetoothGattService>()
    private var isDiscovering = false

    private val writeQueue = ConcurrentLinkedQueue<Pair<BluetoothGattCharacteristic, ByteArray>>()
    private var isWriting = false

    private var onCharacteristicReadListener: OnCharacteristicReadListener? = null

    fun setOnCharacteristicReadListener(listener: OnCharacteristicReadListener) {
        this.onCharacteristicReadListener = listener
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }

    private fun cleanupResources() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gatt?.close()
        writeQueue.clear()
        discoveryQueue.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.CONNECTING
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gatt = device.connectGatt(this, false, gattCallback, TRANSPORT_LE)
    }

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gatt?.disconnect()
        gatt?.close() // 明确释放资源
        gatt = null
    }

    // 在BLEService中添加
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        writeQueue.add(characteristic to value)
        if (!isWriting) {
            processWriteQueue()
        }
    }

    private fun processWriteQueue() {
        CoroutineScope(Dispatchers.IO).launch {
            while (!writeQueue.isEmpty()) {
                isWriting = true
                val (char, data) = writeQueue.poll()!!
                performSafeWrite(char, data)
                delay(50) // 根据MTU调整间隔
            }
            isWriting = false
        }
    }

    private fun performSafeWrite(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        synchronized(characteristic) {
            characteristic.value = data
            gatt?.writeCharacteristic(characteristic)
        }
    }

    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gatt?.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt?.writeDescriptor(descriptor)
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gatt?.readCharacteristic(characteristic)
    }

    // 创建特征的方法
    private fun createCharacteristics(): List<BluetoothGattCharacteristic> {
        val service = BluetoothGattService(SERVER_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val gnssCharacteristic = BluetoothGattCharacteristic(
            CHAR_GNSS_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val ctrlCharacteristic = BluetoothGattCharacteristic(
            CHAR_CTRL_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val otaCharacteristic = BluetoothGattCharacteristic(
            CHAR_OTA_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(gnssCharacteristic)
        service.addCharacteristic(ctrlCharacteristic)
        service.addCharacteristic(otaCharacteristic)

        return listOf(gnssCharacteristic, ctrlCharacteristic, otaCharacteristic)
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BLE Service", "Bluetooth device connected.")
                    _connectionState.value = ConnectionState.CONNECTED
                    _connectedDevice.value = BLEDevice.fromDevice(gatt.device)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _connectedDevice.value = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.printGattTable()
                // 处理服务发现
                val characteristic = gatt.getService(SERVER_UUID).getCharacteristic(CHAR_GNSS_UUID)
                // 开启GNSS服务通知
                gatt.setCharacteristicNotification(
                    characteristic,
                    true
                )
                val descriptor =
                    characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor) // 或使用 gatt.writeDescriptor 异步写入

//                gatt.services?.forEach { service ->
//                    service.characteristics.forEach { characteristic ->
//                        if (isCharacteristicNotifiable(characteristic))
//                            gatt.setCharacteristicNotification(characteristic, true)
//                    }
//                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "CCCD 写入成功，通知已启用")
            } else {
                Log.e("BLE", "CCCD 写入失败: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(
                "TAG",
                """characteristicChanged
                    |service ${characteristic.service.uuid}
                    |characteristic ${characteristic.uuid}
                    |length: ${characteristic.value.size}
                    |${String(characteristic.value)}
                    |${characteristic.value.toHexString()}""".trimMargin()
            )
            onCharacteristicReadListener?.updateBluetoothData(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(
                "TAG",
                """characteristicChanged
                    |service ${characteristic.service.uuid}
                    |characteristic ${characteristic.uuid}
                    |length: ${value.size}
                    |${String(value)}
                    |${value.toHexString()}""".trimMargin()
            )
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(
                    "BLE Server",
                    """characteristicChanged
                    |service ${characteristic.service.uuid}
                    |characteristic ${characteristic.uuid}
                    |length: ${value.size}
                    |${String(value)}
                    |${value.toHexString()}""".trimMargin()
                )
                onCharacteristicReadListener?.updateBluetoothData(characteristic, value)
            } else {
                Log.e("BLE Server", "Characteristic read failed with status: $status")
            }
        }


    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    companion object {
        val SERVER_UUID = makeUuid(0x2001)
        val CHAR_GNSS_UUID = makeUuid(0x2906)

        //        val SERVER_UUID = makeUuid(0x00FF)
//        val CHAR_GNSS_UUID = makeUuid(0xFF01)
        val CHAR_CTRL_UUID = makeUuid(0xFF02)
        val CHAR_OTA_UUID = makeUuid(0xFF03)
    }
}

interface OnCharacteristicReadListener {
    fun updateBluetoothData(characteristic: BluetoothGattCharacteristic, data: ByteArray)
}

fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

// 使用完整UUID格式定义蓝牙特征
private fun makeUuid(shortId: Int): UUID {
    val hexString = String.format("%04X", shortId and 0xFFFF)
    return UUID.fromString("0000${hexString}-0000-1000-8000-00805F9B34FB")
}

fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Log.i(
            "BluetoothGatt",
            "No service and characteristic available, call discoverServices() first?"
        )
        return
    }
    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--",
            prefix = "|--"
        ) { it.uuid.toString() }
        Log.i(
            "BluetoothGatt", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
        )
    }
}