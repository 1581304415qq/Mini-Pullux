package com.minipullux

sealed class MyEvent<T>(val data: T?) {
    class OtaEvent<T>(data: T) : MyEvent<T>(data)
}


sealed class MyEventType {
    object OtaEventStartType : MyEventType()
    object OtaEventEndType : MyEventType()
    object OtaEventSeqType : MyEventType()
}