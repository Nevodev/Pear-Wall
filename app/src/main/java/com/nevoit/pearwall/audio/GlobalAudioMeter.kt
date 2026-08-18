package com.nevoit.pearwall.audio

import android.media.audiofx.Visualizer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sqrt

class GlobalAudioMeter(
    private val onLevel: (Float) -> Unit,
    private val onBass: (Float) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private var visualizer: Visualizer? = null
    private val analyzer = ClassicAudioAnalyzer()

    fun start() {
        stop()
        try {
            val v = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = range[1].coerceAtMost(2048)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int,
                    ) {
                        var sum = 0.0
                        for (sample in waveform) {
                            val centered = (sample.toInt() and 0xff) - 128
                            sum += centered * centered
                        }
                        val rms = sqrt(sum / waveform.size)
                        val sampleRateHz = samplingRate / 1000f
                        analyzer.processWaveform(waveform, sampleRateHz, System.nanoTime())
                        val db = 20.0 * log10((rms / 128.0) + 1e-6)
                        val level = ((db + 48.0) / 48.0)
                            .toFloat()
                            .coerceIn(0f, 1f)
                        onLevel(level)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int,
                    ) {
                        // Visualizer reports the rate in milliHertz.
                        val sampleRateHz = samplingRate / 1000f
                        onBass(analyzer.processFft(fft, sampleRateHz, System.nanoTime()))
                    }
                },
                Visualizer.getMaxCaptureRate(),
                true,
                true,
            )
            v.enabled = true
            visualizer = v
            onStatus("Visualizer(0) 已启动，正在监听全局混音")
        } catch (t: Throwable) {
            onStatus("启动失败：${t.javaClass.simpleName}: ${t.message ?: "unknown"}")
            onLevel(0f)
            onBass(0f)
        }
    }

    fun stop() {
        visualizer?.runCatching {
            enabled = false
            release()
        }
        visualizer = null
        analyzer.reset()
    }
}

private class SpectrumAnalyzer {
    private val recent = FloatArray(4)
    private var recentWrite = 0
    private var target = 0f
    private var power = 0f
    private var bassBaselineDb = SILENCE_DB
    private var referenceBaselineDb = SILENCE_DB
    private var previousBassDb = SILENCE_DB
    private var sharpAttack = 0f
    private val responseHistory = FloatArray(3)
    private var historyWrite = 0
    private var historyCount = 0
    private var initialized = false
    private val transientDetector = BassTransientDetector()
    private var transientResponse = 0f
    private var previousWaveformNanos = 0L
    private var previousFftNanos = 0L

    fun processWaveform(waveform: ByteArray, sampleRateHz: Float) {
        if (waveform.isEmpty() || sampleRateHz <= 0f) return
        val elapsedSeconds = elapsedSincePrevious(previousWaveformNanos)
        previousWaveformNanos = System.nanoTime()
        transientResponse = maxOf(
            transientDetector.process(waveform, sampleRateHz),
            transientResponse * decay(elapsedSeconds, TRANSIENT_RELEASE_SECONDS),
        )
    }

    fun process(fft: ByteArray, sampleRateHz: Float): Float {
        if (fft.size < 8 || sampleRateHz <= 0f) return 0f
        val elapsedSeconds = elapsedSincePrevious(previousFftNanos)
        previousFftNanos = System.nanoTime()

        val lowBassPower = bandPower(fft, sampleRateHz, 30f, 105f)
        val bassNotePower = bandPower(fft, sampleRateHz, 75f, 155f)
        val upperBassPower = bandPower(fft, sampleRateHz, 145f, 210f)
        val lowMidPower = bandPower(fft, sampleRateHz, 155f, 380f)
        val midPower = bandPower(fft, sampleRateHz, 380f, 760f)

        val coreBassPower = maxOf(lowBassPower, bassNotePower * 0.9f)
        val supportedUpperBass = minOf(upperBassPower, coreBassPower * 1.35f)
        val bassDb = powerToDb(coreBassPower + supportedUpperBass * 0.2f)
        val referenceDb = powerToDb(maxOf(lowMidPower * 2.3f, midPower * 1.6f))

        if (!initialized) {
            // Establish the current mix as the baseline. The source intentionally
            // leaves startup headroom, but that creates a false full-scale pulse
            // when Visualizer begins delivering an already-playing stream.
            // Leave attack headroom so a kick on the first report is still visible.
            bassBaselineDb = maxOf(SILENCE_DB, bassDb - BASS_RISE_CEILING_DB)
            referenceBaselineDb = referenceDb
            previousBassDb = bassDb
            initialized = true
            return 0f
        }

        val frameRise = maxOf(0f, bassDb - previousBassDb)
        previousBassDb = bassDb
        val rise = maxOf(0f, bassDb - bassBaselineDb)
        val referenceRise = maxOf(0f, referenceDb - referenceBaselineDb)
        val dominance = smoothRange(
            bassDb - referenceDb,
            BASS_DOMINANCE_FLOOR_DB,
            BASS_DOMINANCE_CEILING_DB,
        )
        val sharpTarget = if (bassDb >= SHARP_LEVEL_FLOOR_DB) {
            smoothRange(frameRise, SHARP_RISE_FLOOR_DB, SHARP_RISE_CEILING_DB)
        } else {
            0f
        }
        sharpAttack = maxOf(
            sharpTarget,
            sharpAttack * decay(elapsedSeconds, SHARP_ATTACK_RELEASE_SECONDS),
        )

        val harmonicConfidence = smoothRange(
            dominance,
            HARMONIC_CONFIDENCE_FLOOR,
            HARMONIC_CONFIDENCE_CEILING,
        ) * sharpAttack * HARMONIC_ATTACK_BOOST
        val bassConfidence = maxOf(dominance, harmonicConfidence)
        val referenceRiseRejection = 0.7f - dominance * 0.35f
        val bassOnlyRise = rise - referenceRise * referenceRiseRejection

        bassBaselineDb = followBaseline(
            bassBaselineDb,
            bassDb,
            attackSeconds = 1.1f,
            releaseSeconds = 0.16f,
            elapsedSeconds = elapsedSeconds,
        )
        referenceBaselineDb = followBaseline(
            referenceBaselineDb,
            referenceDb,
            attackSeconds = 1.1f,
            releaseSeconds = 0.16f,
            elapsedSeconds = elapsedSeconds,
        )

        val level = smoothRange(bassDb, BASS_LEVEL_FLOOR_DB, BASS_LEVEL_CEILING_DB)
        val transient = smoothRange(
            bassOnlyRise,
            BASS_RISE_FLOOR_DB,
            BASS_RISE_CEILING_DB,
        )
        val unprocessed = level * bassConfidence *
            (SUSTAINED_BASS_RESPONSE + (1f - SUSTAINED_BASS_RESPONSE) * transient)
        val immediate = sharpAttack >= IMMEDIATE_TRIGGER &&
            transient >= IMMEDIATE_SUPPORT
        val confirmed = confirmResponse(unprocessed, immediate)

        recent[recentWrite] = confirmed
        recentWrite = (recentWrite + 1) % recent.size
        var weighted = 0f
        for (i in recent.indices) {
            weighted += recent[(recentWrite + i) % recent.size] *
                SAMPLE_RAMP[i]
        }
        target = maxOf(weighted, target * decay(elapsedSeconds, TARGET_RELEASE_SECONDS))
        val follow = 1f - decay(elapsedSeconds, POWER_FOLLOW_SECONDS)
        power += (target - power) * follow
        return maxOf(power, transientResponse * level).coerceIn(0f, 1f)
    }

    fun reset() {
        recent.fill(0f)
        recentWrite = 0
        target = 0f
        power = 0f
        bassBaselineDb = SILENCE_DB
        referenceBaselineDb = SILENCE_DB
        previousBassDb = SILENCE_DB
        sharpAttack = 0f
        responseHistory.fill(0f)
        historyWrite = 0
        historyCount = 0
        initialized = false
        transientResponse = 0f
        previousWaveformNanos = 0L
        previousFftNanos = 0L
        transientDetector.reset()
    }

    private fun confirmResponse(value: Float, immediate: Boolean): Float {
        responseHistory[historyWrite] = value
        historyWrite = (historyWrite + 1) % responseHistory.size
        historyCount = minOf(historyCount + 1, responseHistory.size)
        if (immediate) return value
        if (historyCount == 1) return 0f
        if (historyCount == 2) return minOf(responseHistory[0], responseHistory[1])
        val a = responseHistory[0]
        val b = responseHistory[1]
        val c = responseHistory[2]
        return a + b + c - minOf(a, b, c) - maxOf(a, b, c)
    }

    private fun bandPower(
        fft: ByteArray,
        sampleRateHz: Float,
        minimumHz: Float,
        maximumHz: Float,
    ): Float {
        val fftSize = fft.size
        val firstBin = maxOf(1, ceil(minimumHz * fftSize / sampleRateHz).toInt())
        val lastBin = minOf(
            fftSize / 2 - 1,
            floor(maximumHz * fftSize / sampleRateHz).toInt(),
        )
        if (lastBin < firstBin) return 0f

        var power = 0.0
        for (bin in firstBin..lastBin) {
            val real = fft[bin * 2].toDouble() / 128.0
            val imaginary = fft[bin * 2 + 1].toDouble() / 128.0
            power += real * real + imaginary * imaginary
        }
        return (power / (lastBin - firstBin + 1)).toFloat()
    }

    private fun powerToDb(power: Float): Float =
        (10f * log10(maxOf(power, 1e-12f)))

    private fun smoothRange(value: Float, floorDb: Float, ceilingDb: Float): Float {
        val normalized = ((value - floorDb) / (ceilingDb - floorDb))
            .coerceIn(0f, 1f)
        return normalized * normalized * (3f - 2f * normalized)
    }

    private fun followBaseline(
        current: Float,
        target: Float,
        attackSeconds: Float,
        releaseSeconds: Float,
        elapsedSeconds: Float,
    ): Float {
        val timeConstant = if (target > current) attackSeconds else releaseSeconds
        val mix = 1f - decay(elapsedSeconds, timeConstant)
        return maxOf(SILENCE_DB, current + (target - current) * mix)
    }

    private fun elapsedSincePrevious(previousNanos: Long): Float {
        if (previousNanos == 0L) return DEFAULT_REPORT_INTERVAL_SECONDS
        return ((System.nanoTime() - previousNanos) / 1_000_000_000f)
            .coerceIn(MIN_REPORT_INTERVAL_SECONDS, MAX_REPORT_INTERVAL_SECONDS)
    }

    private fun decay(elapsedSeconds: Float, timeConstantSeconds: Float): Float =
        kotlin.math.exp(-elapsedSeconds / timeConstantSeconds)

    private fun smoothStep(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private companion object {
        val SAMPLE_RAMP = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        const val DEFAULT_REPORT_INTERVAL_SECONDS = 0.05f
        const val MIN_REPORT_INTERVAL_SECONDS = 0.001f
        const val MAX_REPORT_INTERVAL_SECONDS = 0.25f
        const val TARGET_RELEASE_SECONDS = 1f
        const val POWER_FOLLOW_SECONDS = 0.07f
        const val TRANSIENT_RELEASE_SECONDS = 0.063f
        const val SUSTAINED_BASS_RESPONSE = 0.1f
        const val IMMEDIATE_TRIGGER = 0.72f
        const val IMMEDIATE_SUPPORT = 0.32f
        const val HARMONIC_ATTACK_BOOST = 0.9f
        const val HARMONIC_CONFIDENCE_FLOOR = 0.12f
        const val HARMONIC_CONFIDENCE_CEILING = 0.3f
        const val SILENCE_DB = -72f
        const val BASS_LEVEL_FLOOR_DB = -50f
        const val BASS_LEVEL_CEILING_DB = -18f
        const val BASS_DOMINANCE_FLOOR_DB = 0f
        const val BASS_DOMINANCE_CEILING_DB = 8f
        const val BASS_RISE_FLOOR_DB = 1.2f
        const val BASS_RISE_CEILING_DB = 7f
        const val SHARP_LEVEL_FLOOR_DB = -45f
        const val SHARP_ATTACK_RELEASE_SECONDS = 0.09f
        const val SHARP_RISE_FLOOR_DB = 7f
        const val SHARP_RISE_CEILING_DB = 14f

    }
}

private class BassTransientDetector {
    private var sampleRateHz = 0f
    private var low = BiquadLowPass()
    private var bassTop = BiquadLowPass()
    private var referenceTop = BiquadLowPass()
    private var bassPower = 0.0
    private var referencePower = 0.0
    private var previous = 0f

    fun process(waveform: ByteArray, rate: Float): Float {
        if (rate <= 0f) return 0f
        if (rate != sampleRateHz) {
            sampleRateHz = rate
            low.configure(25f, rate)
            bassTop.configure(190f, rate)
            referenceTop.configure(760f, rate)
            bassPower = 0.0
            referencePower = 0.0
        }

        var strongest = 0f
        for (raw in waveform) {
            val sample = ((raw.toInt() and 0xff) - 128) / 128f
            val lowValue = low.process(sample)
            val bassValue = bassTop.process(sample) - lowValue
            val referenceValue = referenceTop.process(sample) - bassTop.lastOutput
            val bassTarget = (bassValue * bassValue).toDouble()
            val referenceTarget = (referenceValue * referenceValue).toDouble()
            bassPower = envelope(bassPower, bassTarget, 0.006f, 0.045f, rate)
            referencePower = envelope(referencePower, referenceTarget, 0.006f, 0.045f, rate)
            val bassDb = powerToDb(bassPower)
            val referenceDb = powerToDb(referencePower)
            val rise = maxOf(0f, bassDb - previous)
            previous = bassDb
            val response = smoothRange(
                bassDb - referenceDb,
                0f,
                8f,
            )
            val attack = smoothRange(rise, 7f, 14f)
            strongest = maxOf(strongest, response * attack)
        }
        return strongest * 0.82f
    }

    fun reset() {
        low.reset()
        bassTop.reset()
        referenceTop.reset()
        bassPower = 0.0
        referencePower = 0.0
        previous = 0f
    }

    private fun envelope(
        current: Double,
        target: Double,
        attackSeconds: Float,
        releaseSeconds: Float,
        rate: Float,
    ): Double {
        val seconds = if (target > current) attackSeconds else releaseSeconds
        val mix = 1.0 - kotlin.math.exp(-1.0 / (rate * seconds))
        return current + (target - current) * mix
    }

    private fun powerToDb(power: Double): Float =
        (10.0 * log10(maxOf(power, 1e-12))).toFloat()

    private fun smoothRange(value: Float, floor: Float, ceiling: Float): Float {
        val x = ((value - floor) / (ceiling - floor)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}

private class BiquadLowPass {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f
    var lastOutput = 0f
        private set

    fun configure(cutoff: Float, sampleRate: Float) {
        val omega = 2f * Math.PI.toFloat() * cutoff / sampleRate
        val alpha = kotlin.math.sin(omega) / (2f * 0.70710678f)
        val cos = kotlin.math.cos(omega)
        val a0 = 1f + alpha
        b0 = ((1f - cos) / 2f) / a0
        b1 = (1f - cos) / a0
        b2 = b0
        a1 = (-2f * cos) / a0
        a2 = (1f - alpha) / a0
        reset()
    }

    fun process(sample: Float): Float {
        val output = b0 * sample + z1
        z1 = b1 * sample - a1 * output + z2
        z2 = b2 * sample - a2 * output
        lastOutput = if (output.isFinite()) output else 0f
        return lastOutput
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
        lastOutput = 0f
    }
}
