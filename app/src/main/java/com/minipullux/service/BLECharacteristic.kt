package com.minipullux.service

import java.util.UUID

data class BLECharacteristic(
    val uuid: UUID,
    val properties: Int,
    val permissions: Int,
    var isNotifyEnabled: Boolean,
)