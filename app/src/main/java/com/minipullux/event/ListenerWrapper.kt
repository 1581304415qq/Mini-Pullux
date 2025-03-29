package com.minipullux.event

open class ListenerWrapper<E>(
    open val listener: IEventListener<E>,
    open val registerTime: Long,
    open val timeout: Long,
)

class DisposableListenerWrapper<E>(
    override val listener: IEventListener<E>,
    override val registerTime: Long,
    override val timeout: Long,
    var state: Int,
) : ListenerWrapper<E>(listener, registerTime, timeout)