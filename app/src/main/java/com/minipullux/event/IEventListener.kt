package com.minipullux.event

/**
 * 监听器接口
 * E event类型
 */
interface IEventListener<E> {
    var successHandler: ((E) -> Unit)?
    var timeOutHandler: (() -> Unit)?
    fun onEvent(event: E)
    fun success(event: E)
    fun fail(event: E)
    fun error(error: E)
    fun onTimeout() {
        // 处理超时逻辑
        if (timeOutHandler != null) timeOutHandler!!()
    }
}