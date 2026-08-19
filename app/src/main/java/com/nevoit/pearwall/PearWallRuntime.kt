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
        if (playbackPlaying.get() == playing) {
            Log.d(TAG, "setPlaybackPlaying unchanged playing=$playing")
            return
        }

        val start = SystemClock.uptimeMillis()
        Log.d(TAG, "setPlaybackPlaying playing=$playing thread=${Thread.currentThread().name}")
        val settings = PearWallSettings(context)
        // Change the artwork while every playback state still reports the old value.
        if (playing) {
            restoreMediaArtwork(context)
        } else if (settings.pauseUsesNoArtworkBehavior) {
            applyNoArtworkBehavior(context, settings)
        }
        // Publish the playback transition only after the artwork transition is complete.
        playbackPlaying.set(playing)
        states.forEach { it.setPlaybackPlaying(playing) }
        updateAudioMeterState()
        Log.d(TAG, "setPlaybackPlaying done cost=${SystemClock.uptimeMillis() - start}ms")
    }

    @Synchronized
    fun setPauseUsesNoArtworkBehavior(context: Context, enabled: Boolean) {
        PearWallSettings(context).pauseUsesNoArtworkBehavior = enabled
        if (!playbackPlaying.get()) {
            if (enabled && PearWallSettings(context).noArtworkBehavior == PearWallSettings.CUSTOM_IMAGE) {
                applyNoArtworkBehavior(context, PearWallSettings(context))
            } else {
                restoreMediaArtwork(context)
            }
        }
    }

    @Synchronized
    fun setNoArtworkBehavior(context: Context, behavior: Int) {
        val settings = PearWallSettings(context)
        settings.noArtworkBehavior = behavior
        if (!playbackPlaying.get() && settings.pauseUsesNoArtworkBehavior) {
            if (behavior == PearWallSettings.CUSTOM_IMAGE) {
                applyNoArtworkBehavior(context, settings)
            } else {
                restoreMediaArtwork(context)
            }
        } else if (behavior == PearWallSettings.KEEP_LAST) {
            // Switching away from the custom fallback must refresh an already visible fallback.
            restoreMediaArtwork(context)
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
    fun updateCustomArtwork(context: Context, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        customArtworkCache(context).save(bitmap)
        val settings = PearWallSettings(context)
        val shouldShow = !playbackPlaying.get() &&
            settings.noArtworkBehavior == PearWallSettings.CUSTOM_IMAGE &&
            settings.pauseUsesNoArtworkBehavior
        if (shouldShow) {
            states.forEach { it.setArtwork(bitmap) }
        }
    }

    @Synchronized
    fun updateArtwork(context: Context, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val duplicate = lastMediaArtwork?.let { previous ->
            !previous.isRecycled && runCatching { previous.sameAs(bitmap) }.getOrDefault(false)
        } ?: false
        if (!duplicate) {
            lastMediaArtwork = bitmap
            ArtworkCache(context).save(bitmap)
        }

        val settings = PearWallSettings(context)
        val showingPauseArtwork = !playbackPlaying.get() &&
            settings.pauseUsesNoArtworkBehavior &&
            settings.noArtworkBehavior == PearWallSettings.CUSTOM_IMAGE
        if (!showingPauseArtwork && (!duplicate || states.any { it.snapshot().artwork == null })) {
            states.forEach { it.setArtwork(lastMediaArtwork ?: bitmap) }
        }
    }

    fun showNoArtworkFallback(context: Context) {
        applyNoArtworkBehavior(context, PearWallSettings(context))
    }

    private fun applyNoArtworkBehavior(context: Context, settings: PearWallSettings) {
        if (settings.noArtworkBehavior != PearWallSettings.CUSTOM_IMAGE) return
        val start = SystemClock.uptimeMillis()
        Log.d(TAG, "applyNoArtworkBehavior begin thread=${Thread.currentThread().name}")
        val bitmap = customArtworkCache(context).load()
            ?: settings.customArtworkUri?.let { value ->
                decodeArtwork(context, Uri.parse(value))
            }
        bitmap?.let { artwork ->
            customArtworkCache(context).save(artwork)
            states.forEach { it.setArtwork(artwork) }
        }
        Log.d(TAG, "applyNoArtworkBehavior end cost=${SystemClock.uptimeMillis() - start}ms")
    }

    private fun restoreMediaArtwork(context: Context) {
        val bitmap = lastMediaArtwork ?: ArtworkCache(context).load()?.also {
            lastMediaArtwork = it
        }
        bitmap?.let { artwork -> states.forEach { it.setArtwork(artwork) } }
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
        val mediaArtwork = ArtworkCache(context).load()
        val customArtwork = if (settings.noArtworkBehavior == PearWallSettings.CUSTOM_IMAGE) {
            customArtworkCache(context).load()
                ?: settings.customArtworkUri?.let { value ->
                    decodeArtwork(context, Uri.parse(value))
                }
        } else {
            null
        }

        // Never show a custom image over a currently playing session. If no media cover
        // is cached yet, wait for the media service instead of showing a wrong image.
        return if (!playbackPlaying.get() && settings.pauseUsesNoArtworkBehavior) {
            customArtwork ?: mediaArtwork
        } else {
            mediaArtwork
        }
    }

    private fun customArtworkCache(context: Context): ArtworkCache =
        ArtworkCache(context, "custom_artwork.webp")

    private fun decodeArtwork(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }.getOrElse { return null }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_ARTWORK_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_ARTWORK_DIMENSION
        ) {
            sampleSize *= 2
        }
        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            }
        }.getOrNull()
    }

    private const val MAX_ARTWORK_DIMENSION = 2048
    private const val TAG = "PearWallRuntime"
}
