package com.minipullux

import android.bluetooth.BluetoothGattCharacteristic


// 检查特征是否可通知
fun isCharacteristicNotifiable(characteristic: BluetoothGattCharacteristic): Boolean {
    return (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
}

// 检查特征是否可读
fun isCharacteristicReadable(characteristic: BluetoothGattCharacteristic): Boolean {
    return (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
}

// 检查特征是否可写
fun isCharacteristicWritable(characteristic: BluetoothGattCharacteristic): Boolean {
    return (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
}