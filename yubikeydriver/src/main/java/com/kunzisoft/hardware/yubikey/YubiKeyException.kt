package com.kunzisoft.hardware.yubikey

import java.lang.Exception

open class YubiKeyException : Exception {
    constructor() {}
    constructor(message: String?) : super(message) {}
    constructor(cause: Throwable?) : super(cause) {}
}