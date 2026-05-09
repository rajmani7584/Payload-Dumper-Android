package com.rajmani7584.payloaddumper.nativeHelper

object PayloadDumper {

    private external fun initNative(): String

    init {
        System.loadLibrary("payload_dumper")
    }

    fun init(): String {
        return initNative()
    }
}