package com.example.localimagestudio

object NativeEngine {
    init { System.loadLibrary("local_image_engine") }
    external fun nativeBackendStatus(): String
}
