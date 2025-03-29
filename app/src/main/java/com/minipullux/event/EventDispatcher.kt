package com.minipullux.event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

data class PendingRemovalsData<T, E>(
    val type: T,
    val listener: IEventListener<E>
)

const val CLEAN_INTERVAL = 100L

/**
 * T    EventType enum
 * E    Event
 */
open class EventDispatcher<T : Any, E> : IEventDispatcher<T, E> {

    private val binds = ConcurrentHashMap<T, CopyOnWriteArrayList<ListenerWrapper<E>>>()
    private val pendingRemovals = ArrayList<PendingRemovalsData<T, E>>()
    private val lock = Any()
    private val cleanThread: Thread
    private var cleaning = true

    init {
        cleanThread = thread {
            while (cleaning) {
                Thread.sleep(CLEAN_INTERVAL)

                synchronized(lock) {
                    cleanExpiredListeners()
                }
            }
        }

    }

    private fun cleanExpiredListeners() {
        // 移除超时的监听器
        val now = System.currentTimeMillis()
        binds.forEach { (eventType, wrappers) ->
            wrappers.forEach { wrapper ->
                if (wrapper.timeout != 0L && now - wrapper.registerTime > wrapper.timeout) {
                    if (wrapper is DisposableListenerWrapper && wrapper.state == 0) {
                        wrapper.listener.onTimeout()
                    }
                    pendingRemovals.add(PendingRemovalsData(eventType, wrapper.listener))
                }
            }
        }

        pendingRemovals.forEach {
            off(it.type, it.listener)
        }
    }

    override fun on(eventType: T, listener: IEventListener<E>, timeOut: Long) {
        val wrapper = ListenerWrapper(listener, System.currentTimeMillis(), timeOut)
        binds.getOrPut(eventType) { CopyOnWriteArrayList() }.add(wrapper)
    }

    override fun on(eventType: T, handler: (E) -> Unit, timeOut: Long) {
        val listener = EventListener(handler)
        on(eventType, listener)
    }

    override fun on(eventType: T, handler: (E) -> Unit) = on(eventType, handler, 0)

    override fun on(eventType: T, listener: IEventListener<E>) = on(eventType, listener, 0)

    override fun off(eventType: T, listener: IEventListener<E>) {
        val wrapper = findListenerWrapper(eventType, listener)
        if (wrapper != null) binds[eventType]?.remove(wrapper)
    }

    override fun off(eventType: T, handler: (E) -> Unit) {
        val listener = findListener(eventType, handler) ?: return
        off(eventType, listener)
    }

    override fun once(eventType: T, timeOut: Long, onTimeOut: () -> Unit, handler: (E) -> Unit) {
        val listener = EventListener<E>()
        listener.timeOutHandler = {
            onTimeOut()
        }
        listener.successHandler = { event: E ->
            handler(event)
            pendingRemovals.add(PendingRemovalsData(eventType, listener))
        }
        val wrapper = DisposableListenerWrapper(listener, System.currentTimeMillis(), timeOut, 0)
        binds.getOrPut(eventType) { CopyOnWriteArrayList() }.add(wrapper)
    }

    override fun once(eventType: T, timeOut: Long, handler: (E) -> Unit) {
        val listener = EventListener<E>()
        listener.successHandler = { event: E ->
            handler(event)
            pendingRemovals.add(PendingRemovalsData(eventType, listener))
        }
//        on(eventType, listener, timeOut)
        val wrapper = DisposableListenerWrapper(listener, System.currentTimeMillis(), timeOut, 0)
        binds.getOrPut(eventType) { CopyOnWriteArrayList() }.add(wrapper)
    }

    override fun once(eventType: T, handler: (E) -> Unit) = once(eventType, 0, handler)

    override fun removeAll() {
        synchronized(lock) {
            binds.clear()
        }
    }

    /**
     * 消息发布，订阅者有永久订阅和一次性订阅
     */
    @Synchronized
    override fun dispatch(eventType: T, event: E) {
        synchronized(lock) {
            val wrappers = binds[eventType] ?: return
            for (wrapper in wrappers) {
                when (wrapper) {
                    is DisposableListenerWrapper ->
                        if (wrapper.state == 0) {
                            wrapper.state = 1
                            wrapper.listener.onEvent(event)
                        }

                    is ListenerWrapper -> wrapper.listener.onEvent(event)
                }
            }
        }
    }

    private fun findListenerWrapper(
        eventType: T,
        listener: IEventListener<E>
    ): ListenerWrapper<E>? {
        // 找到对应listener的ListenerWrapper
        if (binds[eventType] == null) return null
        val res = binds[eventType]!!.filter { it.listener == listener }
        if (res.isNotEmpty())
            return res[0]
        return null
    }

    private fun findListener(eventType: T, handler: (E) -> Unit): IEventListener<E>? {
        // 找到对应handler的listener
        if (binds[eventType] == null) return null
        val res = binds[eventType]!!.filter { it.listener.successHandler == handler }
        if (res.isNotEmpty())
            return res[0].listener
        return null
    }


    override fun destroy() {
        cleaning = false
        cleanThread.join()
        binds.clear()
        pendingRemovals.clear()
    }

}

