package com.minipullux.event

sealed class Event<T>(val data: T?) {
    class Error(value: Int, val message: String) : Event<Int>(value)
    class Success : Event<Int>(null)
}