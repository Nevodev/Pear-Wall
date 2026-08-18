package com.nevoit.pearwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.nevoit.pearwall.media.ArtworkCache
import com.nevoit.pearwall.audio.GlobalAudioMeter
import com.nevoit.pearwall.pearmesh.PearMeshState
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object PearWallRuntime {
    private var audioMeter: GlobalAudioMeter? = null
    @Volatile
    private var audioMeterRunning = false
    private val audioThread = HandlerThread("PearWall-AudioMeter").apply { start() }
    private val audioHandler = Handler(audioThread.looper)
    private val states = CopyOnWriteArraySet<PearMeshState>()
    private val activeAudioConsumers = CopyOnWriteArraySet<PearMeshState>()
    private var lastPublishedArtwork: Bitmap? = null
    private var lastMediaArtwork: Bitmap? = null
    private val playbackPlaying = AtomicBoolean(true)

    fun createState(context: Context): PearMeshState {
        val settings = PearWallSettings(context)
        return PearMeshState(
            portraitPresetIndex = settings.portraitPreset,
            landscapePresetIndex = settings.landscapePreset,
            renderScale = settings.renderScale,
            targetFrameRate = settings.frameRate,
            scrimAlpha = settings.scrimAlpha,
            blurMultiplier = settings.blurMultiplier,
            flowSpeed = settings.flowSpeed,
            audioVisualizationEnabled = settings.audioVisualizationEnabled,
            pauseFlowEnabled = settings.pauseFlowEnabled,
            moruStyle = settings.moruStyle,
        ).also { state ->
            states += state
            state.setPlaybackPlaying(playbackPlaying.get())
            initialArtwork(context, settings)?.let(state::setArtwork)
        }
    }

    fun release(state: PearMeshState) {
        states -= state
        activeAudioConsumers -= state
        updateAudioMeterState()
    }

    /** Marks whether this state currently has a visible surface that consumes audio data. */
    @Synchronized
    fun setAudioConsumerActive(state: PearMeshState, active: Boolean) {
        val alreadyActive = activeAudioConsumers.contains(state)
        if (alreadyActive == active) {
            Log.d(TAG, "setAudioConsumerActive unchanged active=$active")
            return
        }

        val start = SystemClock.uptimeMillis()
        Log.d(TAG, "setAudioConsumerActive active=$active thread=${Thread.currentThread().name}")
        if (active) activeAudioConsumers += state else activeAudioConsumers -= state
        updateAudioMeterState()
        Log.d(TAG, "setAudioConsumerActive done cost=${SystemClock.uptimeMillis() - start}ms")
    }

    @Synchronized
    fun setPlaybackPlaying(context: Context, playing: Boolean) {
        if (playbackPlaying.getAndSet(playing) == playing) {
            Log.d(TAG, "setPlaybackPlaying unchanged playing=$playing")
            return
        }

        val start = SystemClock.uptimeMillis()
        Log.d(TAG, "setPlaybackPlaying playing=$playing thread=${Thread.currentThread().name}")
        states.forEach { it.setPlaybackPlaying(playing) }
        updateAudioMeterState()
        if (!playing) {
            val settings = PearWallSettings(context)
            if (settings.pauseUsesNoArtworkBehavior) applyNoArtworkBehavior(context, settings)
        }
        Log.d(TAG, "setPlaybackPlaying done cost=${SystemClock.uptimeMillis() - start}ms")
    }

    @Synchronized
    fun setPauseUsesNoArtworkBehavior(context: Context, enabled: Boolean) {
        PearWallSettings(context).pauseUsesNoArtworkBehavior = enabled
        if (!playbackPlaying.get()) {
            if (enabled) {
                applyNoArtworkBehavior(context, PearWallSettings(context))
            } else {
                lastMediaArtwork?.let { bitmap ->
                    lastPublishedArtwork = bitmap
                    states.forEach { it.setArtwork(bitmap) }
                }
            }
        }
    }

    @Synchronized
    fun setAudioVisualizationEnabled(context: Context, enabled: Boolean) {
        val start = SystemClock.uptimeMillis()
        Log.d(TAG, "setAudioVisualizationEnabled enabled=$enabled thread=${Thread.currentThread().name}")
        PearWallSettings(context).audioVisualizationEnabled = enabled
        states.forEach { it.setAudioVisualizationEnabled(enabled) }
        updateAudioMeterState()
        Log.d(TAG, "setAudioVisualizationEnabled done cost=${SystemClock.uptimeMillis() - start}ms")
    }

    @Synchronized
    private fun updateAudioMeterState() {
        val shouldRun = activeAudioConsumers.any {
            it.snapshot().audioVisualizationEnabled
        }
        val playing = playbackPlaying.get()
        Log.d(TAG, "updateAudioMeterState consumers=${activeAudioConsumers.size} shouldRun=$shouldRun playing=$playing meter=$audioMeterRunning thread=${Thread.currentThread().name}")
        val shouldRunNow = shouldRun && playing
        audioHandler.post {
            if (shouldRunNow) startAudioMeterOnWorker() else stopAudioMeterOnWorker()
        }
    }

    private fun startAudioMeterOnWorker() {
        check(Thread.currentThread() === audioThread) { "Audio meter must run on its worker thread" }
        if (audioMeter != null) {
            Log.d(TAG, "startAudioMeter skipped already running thread=${Thread.currentThread().name}")
            return
        }
        val start = SystemClock.uptimeMillis()
        Log.d(TAG, "startAudioMeter begin thread=${Thread.currentThread().name}")
        audioMeter = GlobalAudioMeter(
            onLevel = {},
            onBass = { bass -> publishAudio(floatArrayOf(bass, bass, bass, bass)) },
            onStatus = {},
        ).also { it.start() }
        audioMeterRunning = true
        Log.d(TAG, "startAudioMeter end cost=${SystemClock.uptimeMillis() - start}ms")
    }

    private fun stopAudioMeterOnWorker() {
        check(Thread.currentThread() === audioThread) { "Audio meter must run on its worker thread" }
        val meter = audioMeter ?: return
        val start = SystemClock.uptimeMillis()
        Log.d(TAG, "stopAudioMeter begin thread=${Thread.currentThread().name}")
        audioMeter = null
        audioMeterRunning = false
        meter.stop()
        states.forEach { it.setAudioPower(FloatArray(4)) }
        Log.d(TAG, "stopAudioMeter end cost=${SystemClock.uptimeMillis() - start}ms")
    }

    private fun publishAudio(power: FloatArray) {
        states.forEach { state ->
            if (state.snapshot().audioVisualizationEnabled) state.setAudioPower(power)
        }
    }

    @Synchronized
    fun updateArtwork(context: Context, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val duplicate = lastPublishedArtwork?.let { previous ->
            !previous.isRecycled && runCatching { previous.sameAs(bitmap) }.getOrDefault(false)
        } ?: false
        if (duplicate) return

        lastPublishedArtwork = bitmap
        lastMediaArtwork = bitmap
        ArtworkCache(context).save(bitmap)
        states.forEach { it.setArtwork(bitmap) }
    }

    fun showNoArtworkFallback(context: Context) {
        applyNoArtworkBehavior(context, PearWallSettings(context))
    }

    private fun applyNoArtworkBehavior(context: Context, settings: PearWallSettings) {
        if (settings.noArtworkBehavior != PearWallSettings.CUSTOM_IMAGE) return
        settings.customArtworkUri?.let { value ->
            val start = SystemClock.uptimeMillis()
            Log.d(TAG, "applyNoArtworkBehavior begin thread=${Thread.currentThread().name}")
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)
            }.getOrNull()?.let { bitmap ->
                states.forEach { it.setArtwork(bitmap) }
            }
            Log.d(TAG, "applyNoArtworkBehavior end cost=${SystemClock.uptimeMillis() - start}ms")
        }
    }

    fun applySettings(context: Context) {
        val settings = PearWallSettings(context)
        states.forEach {
            it.setRenderScale(settings.renderScale)
            it.setTargetFrameRate(settings.frameRate)
            it.setScrimAlpha(settings.scrimAlpha)
            it.setBlurMultiplier(settings.blurMultiplier)
            it.setFlowSpeed(settings.flowSpeed)
            it.setAudioVisualizationEnabled(settings.audioVisualizationEnabled)
            it.setPauseFlowEnabled(settings.pauseFlowEnabled)
            it.setMoruStyle(settings.moruStyle)
            it.setPortraitPreset(settings.portraitPreset)
            it.setLandscapePreset(settings.landscapePreset)
        }
    }

    fun randomizePresets(context: Context) {
        val settings = PearWallSettings(context)
        if (!settings.randomizeOnScreenOn) return
        settings.portraitPreset = nextPreset(
            PearMeshState.PortraitPresetCount,
            settings.portraitPreset,
        )
        settings.landscapePreset = nextPreset(
            PearMeshState.LandscapePresetCount,
            settings.landscapePreset,
        )
        applySettings(context)
    }

    private fun nextPreset(count: Int, current: Int): Int {
        if (count <= 1) return 0
        return (0 until count).filter { it != current }.random()
    }

    private fun initialArtwork(context: Context, settings: PearWallSettings): Bitmap? {
        if (settings.noArtworkBehavior == PearWallSettings.CUSTOM_IMAGE) {
            settings.customArtworkUri?.let { value ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)
                }.getOrNull()?.let { return it }
            }
        }
        return ArtworkCache(context).load()
    }

    private const val TAG = "PearWallRuntime"
}
