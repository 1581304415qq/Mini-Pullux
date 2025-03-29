package com.minipullux.utils

import androidx.compose.runtime.mutableStateOf

object Debug {

    val protoDebugCmd = mutableStateOf("")

    fun push(v: String) {
        protoDebugCmd.value += v
    }
}