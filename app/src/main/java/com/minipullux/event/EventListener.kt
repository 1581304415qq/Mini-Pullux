package com.minipullux.event

open class EventListener<E>(handler: ((E) -> Unit)? = null) : IEventListener<E> {
    override var successHandler: ((E) -> Unit)? = handler
    override var timeOutHandler: (() -> Unit)? = null
    override fun onEvent(event: E) {
        successHandler?.let { it(event) }
    }

    override fun success(event: E) {
        TODO("Not yet implemented")
    }

    override fun fail(event: E) {
        TODO("Not yet implemented")
    }

    override fun error(error: E) {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        return successHandler.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventListener<*>

        if (successHandler != other.successHandler) return false

        return true
    }

}