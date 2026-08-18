package com.nevoit.pearwall.pearmesh

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.nevoit.pearwall.pearmesh.gl.RenderDebugInfo

@Stable
class PearMeshState(
    behindLyricsProgress: Float = 1f,
    portraitPresetIndex: Int = DefaultPortraitPresetIndex,
    landscapePresetIndex: Int = DefaultLandscapePresetIndex,
    renderScale: Float = DefaultRenderScale,
    targetFrameRate: Int = DefaultTargetFrameRate,
    scrimAlpha: Float = DefaultScrimAlpha,
    blurMultiplier: Float = DefaultBlurMultiplier,
    flowSpeed: Int = StandardFlowSpeed,
) {
    private val artworkId = AtomicLong(0L)
    private val artwork = AtomicReference<ArtworkFrame?>(null)
    private val lyricsProgressBits = AtomicInteger(
        behindLyricsProgress.coerceIn(0f, 1f).toRawBits(),
    )
    private val demoPulse = AtomicReference(false)
    private val audioPower = AtomicReference(FloatArray(4))
    private val debugInfo = AtomicReference(RenderDebugInfo())
    private val portraitPreset = AtomicInteger(
        portraitPresetIndex.coerceIn(0, PortraitPresetCount - 1),
    )
    private val landscapePreset = AtomicInteger(
        landscapePresetIndex.coerceIn(0, LandscapePresetCount - 1),
    )
    private val renderScaleTarget = AtomicReference(
        renderScale.coerceIn(MinRenderScale, MaxRenderScale),
    )
    private val targetFrameRateTarget = AtomicInteger(
        targetFrameRate.coerceIn(MinTargetFrameRate, MaxTargetFrameRate),
    )
    private val scrimAlphaTarget = AtomicReference(scrimAlpha.coerceIn(0f, 1f))
    private val blurMultiplierTarget = AtomicReference(blurMultiplier.coerceAtLeast(0f))
    private val flowSpeedTarget = AtomicInteger(flowSpeed.coerceIn(StandardFlowSpeed, FastFlowSpeed))
    @Volatile
    private var artworkChangedListener: (() -> Unit)? = null

    private var demoPulseState by mutableStateOf(false)
    val isDemoPulseEnabled: Boolean
        get() = demoPulseState

    private var portraitPresetState by mutableIntStateOf(portraitPreset.get())
    val portraitPresetIndex: Int
        get() = portraitPresetState

    private var landscapePresetState by mutableIntStateOf(landscapePreset.get())
    val landscapePresetIndex: Int
        get() = landscapePresetState

    private var renderScaleState by mutableFloatStateOf(renderScaleTarget.get())
    val renderScale: Float
        get() = renderScaleState

    private var targetFrameRateState by mutableIntStateOf(targetFrameRateTarget.get())
    val targetFrameRate: Int
        get() = targetFrameRateState

    fun setBehindLyricsProgress(progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        lyricsProgressBits.set(clampedProgress.toRawBits())
    }

    fun setDemoPulseEnabled(enabled: Boolean) {
        demoPulseState = enabled
        demoPulse.set(enabled)
    }

    fun setPortraitPreset(index: Int) {
        val preset = index.coerceIn(0, PortraitPresetCount - 1)
        portraitPresetState = preset
        portraitPreset.set(preset)
    }

    fun setLandscapePreset(index: Int) {
        val preset = index.coerceIn(0, LandscapePresetCount - 1)
        landscapePresetState = preset
        landscapePreset.set(preset)
    }

    fun setRenderScale(scale: Float) {
        val clampedScale = scale.coerceIn(MinRenderScale, MaxRenderScale)
        renderScaleState = clampedScale
        renderScaleTarget.set(clampedScale)
    }

    fun setTargetFrameRate(frameRate: Int) {
        val clampedFrameRate = frameRate.coerceIn(MinTargetFrameRate, MaxTargetFrameRate)
        targetFrameRateState = clampedFrameRate
        targetFrameRateTarget.set(clampedFrameRate)
    }

    fun setScrimAlpha(alpha: Float) {
        scrimAlphaTarget.set(alpha.coerceIn(0f, 1f))
    }

    fun setBlurMultiplier(multiplier: Float) {
        blurMultiplierTarget.set(multiplier.coerceAtLeast(0f))
    }

    fun setFlowSpeed(speed: Int) {
        flowSpeedTarget.set(speed.coerceIn(StandardFlowSpeed, FastFlowSpeed))
    }

    /** The state retains this bitmap so a recreated Android surface can upload it again. */
    fun setRenderDebugInfo(info: RenderDebugInfo) {
        debugInfo.set(info)
    }

    fun getRenderDebugInfo(): RenderDebugInfo = debugInfo.get()

    fun setArtwork(bitmap: Bitmap) {
        require(!bitmap.isRecycled) { "Artwork bitmap has already been recycled" }
        artwork.set(ArtworkFrame(artworkId.incrementAndGet(), bitmap))
        artworkChangedListener?.invoke()
    }

    fun setArtworkChangedListener(listener: (() -> Unit)?) {
        artworkChangedListener = listener
    }

    /**
     * Supplies four power lanes. The original visual uses lanes 0..2 to scale the three
     * rotating artwork copies and leaves lane 3 unused.
     */
    fun setAudioPower(power: FloatArray) {
        require(power.size >= 4) { "Four audio power values are required" }
        audioPower.set(FloatArray(4) { power[it].coerceIn(0f, 1f) })
    }

    fun snapshot(): RendererState = RendererState(
        artwork = artwork.get(),
        behindLyricsProgress = Float.fromBits(lyricsProgressBits.get()),
        isDemoPulseEnabled = demoPulse.get(),
        audioPower = audioPower.get(),
        portraitPresetIndex = portraitPreset.get(),
        landscapePresetIndex = landscapePreset.get(),
        renderScale = renderScaleTarget.get(),
        targetFrameRate = targetFrameRateTarget.get(),
        scrimAlpha = scrimAlphaTarget.get(),
        blurMultiplier = blurMultiplierTarget.get(),
        flowSpeed = flowSpeedTarget.get(),
    )

    companion object {
        const val PortraitPresetCount = 4
        const val LandscapePresetCount = 5
        const val DefaultPortraitPresetIndex = 2
        const val DefaultLandscapePresetIndex = 4
        const val DefaultRenderScale = 0.25f
        const val MinRenderScale = 0.1f
        const val MaxRenderScale = 1f
        const val DefaultTargetFrameRate = 30
        const val MinTargetFrameRate = 1
        const val MaxTargetFrameRate = 240
        const val DefaultScrimAlpha = 0.4f
        const val DefaultBlurMultiplier = 1f
        const val StandardFlowSpeed = 0
        const val FastFlowSpeed = 1
    }
}

data class ArtworkFrame(val id: Long, val bitmap: Bitmap)

data class RendererState(
    val artwork: ArtworkFrame?,
    val behindLyricsProgress: Float,
    val isDemoPulseEnabled: Boolean,
    val audioPower: FloatArray,
    val portraitPresetIndex: Int,
    val landscapePresetIndex: Int,
    val renderScale: Float,
    val targetFrameRate: Int,
    val scrimAlpha: Float,
    val blurMultiplier: Float,
    val flowSpeed: Int,
)
