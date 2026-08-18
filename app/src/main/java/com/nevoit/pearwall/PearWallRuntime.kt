package com.nevoit.pearwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.nevoit.pearwall.media.ArtworkCache
import com.nevoit.pearwall.audio.GlobalAudioMeter
import com.nevoit.pearwall.pearmesh.PearMeshState
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object PearWallRuntime {
    private var audioMeter: GlobalAudioMeter? = null
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
        if (active) activeAudioConsumers += state else activeAudioConsumers -= state
        updateAudioMeterState()
    }

    @Synchronized
    fun setPlaybackPlaying(context: Context, playing: Boolean) {
        playbackPlaying.set(playing)
        states.forEach { it.setPlaybackPlaying(playing) }
        updateAudioMeterState()
        if (!playing) {
            val settings = PearWallSettings(context)
            if (settings.pauseUsesNoArtworkBehavior) applyNoArtworkBehavior(context, settings)
        }
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
        PearWallSettings(context).audioVisualizationEnabled = enabled
        states.forEach { it.setAudioVisualizationEnabled(enabled) }
        updateAudioMeterState()
    }

    @Synchronized
    private fun updateAudioMeterState() {
        val shouldRun = activeAudioConsumers.any {
            it.snapshot().audioVisualizationEnabled
        }
        if (shouldRun && playbackPlaying.get()) startAudioMeter() else stopAudioMeter()
    }

    @Synchronized
    private fun startAudioMeter() {
        if (audioMeter != null) return
        audioMeter = GlobalAudioMeter(
            onLevel = {},
            onBass = { bass -> publishAudio(floatArrayOf(bass, bass, bass, bass)) },
            onStatus = {},
        ).also { it.start() }
    }

    @Synchronized
    private fun stopAudioMeter() {
        audioMeter?.stop()
        audioMeter = null
        states.forEach { it.setAudioPower(FloatArray(4)) }
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
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)
            }.getOrNull()?.let { bitmap ->
                states.forEach { it.setArtwork(bitmap) }
            }
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
}
