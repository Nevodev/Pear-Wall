package com.nevoit.pearwall.audio

/** JNI wrapper for the native classic analyzer. The instance is confined to the Visualizer callback thread. */
internal class ClassicAudioAnalyzer : AutoCloseable {
    private var handle = nativeCreate()

    fun processWaveform(waveform: ByteArray, sampleRateHz: Float, timestampNanos: Long) {
        if (handle != 0L) {
            nativeProcessWaveform(handle, waveform, sampleRateHz, timestampNanos)
        }
    }

    fun processFft(fft: ByteArray, sampleRateHz: Float, timestampNanos: Long): Float =
        if (handle == 0L) 0f else nativeProcessFft(handle, fft, sampleRateHz, timestampNanos)

    fun reset() {
        if (handle != 0L) nativeReset(handle)
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeProcessWaveform(
        handle: Long,
        waveform: ByteArray,
        sampleRateHz: Float,
        timestampNanos: Long,
    )
    private external fun nativeProcessFft(
        handle: Long,
        fft: ByteArray,
        sampleRateHz: Float,
        timestampNanos: Long,
    ): Float
    private external fun nativeReset(handle: Long)
    private external fun nativeDestroy(handle: Long)

    private companion object {
        init {
            System.loadLibrary("classic")
        }
    }
}
