package dev.yomu.spike

/** Thin JNI adapter around the separate LGPL libmp3lame shared library. */
internal class LameEncoder(sampleRate: Int, channels: Int, bitRateKbps: Int) : AutoCloseable {
    private var handle = nativeCreate(sampleRate, channels, bitRateKbps)

    init { check(handle != 0L) { "LAME initialization failed" } }

    fun encode(samples: ShortArray, count: Int = samples.size): ByteArray =
        nativeEncode(handle, samples, count) ?: error("LAME encode failed")

    fun flush(): ByteArray = nativeFlush(handle) ?: error("LAME flush failed")

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int, bitRateKbps: Int): Long
    private external fun nativeEncode(handle: Long, pcm: ShortArray, sampleCount: Int): ByteArray?
    private external fun nativeFlush(handle: Long): ByteArray?
    private external fun nativeDestroy(handle: Long)

    companion object {
        init { System.loadLibrary("yomulame") }
    }
}
