package com.minipullux.event

/**
 * T EventType  事件类型
 * E Event 类
 */
interface IEventDispatcher<T, E> {
    fun on(eventType: T, listener: IEventListener<E>, timeOut: Long)
    fun on(eventType: T, listener: IEventListener<E>)

    fun on(eventType: T, handler: (E) -> Unit, timeOut: Long)
    fun on(eventType: T, handler: (E) -> Unit)
    fun once(eventType: T, timeOut: Long, onTimeOut: () -> Unit, handler: (E) -> Unit)

    fun once(eventType: T, timeOut: Long, handler: (E) -> Unit)
    fun once(eventType: T, handler: (E) -> Unit)

    fun off(eventType: T, handler: (E) -> Unit)
    fun off(eventType: T, listener: IEventListener<E>)
    fun removeAll()
    fun dispatch(eventType: T, event: E)

    fun destroy()
}