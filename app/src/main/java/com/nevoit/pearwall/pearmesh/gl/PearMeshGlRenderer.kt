package com.nevoit.pearwall.pearmesh.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLUtils
import androidx.core.graphics.createBitmap
import com.nevoit.pearwall.pearmesh.PearMeshState
import com.nevoit.pearwall.pearmesh.RendererState
import java.io.Closeable
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal class PearMeshGlRenderer(
    context: Context,
    initialState: RendererState,
) : Closeable {
    private val rotationProgram = GlProgram(
        context,
        "pearmesh/shaders/rotation.vert",
        "pearmesh/shaders/rotation.frag",
    )
    private val blurProgram = GlProgram(
        context,
        "pearmesh/shaders/fullscreen.vert",
        "pearmesh/shaders/blur.frag",
    )
    private val fullscreenMaterialProgram = GlProgram(
        context,
        "pearmesh/shaders/fullscreen.vert",
        "pearmesh/shaders/material.frag",
    )
    private val pinchMaterialProgram = GlProgram(
        context,
        "pearmesh/shaders/pinch.vert",
        "pearmesh/shaders/material.frag",
    )
    private val quad = GlGeometry.quad()
    private var mesh = GlGeometry.mesh(
        PearMeshMesh.create(
            isPortrait = true,
            presetIndex = PearMeshState.DefaultPortraitPresetIndex,
        ),
    )

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var outputWidth = 0
    private var outputHeight = 0
    private var isPortrait = true
    private var meshPresetIndex = PearMeshState.DefaultPortraitPresetIndex
    private var rotationTarget: RenderTarget? = null
    private var kawaseHalfTarget: RenderTarget? = null
    private var kawaseQuarterTarget: RenderTarget? = null
    private var kawaseEighthTarget: RenderTarget? = null
    private var lyricsBlurTarget: RenderTarget? = null
    private var ordinaryBlurTarget: RenderTarget? = null
    private var materialTarget: RenderTarget? = null

    val outputWidthForDebug: Int get() = outputWidth
    val outputHeightForDebug: Int get() = outputHeight
    val gpuRendererForDebug: String = GLES30.glGetString(GLES30.GL_RENDERER) ?: "Unknown"

    private val initialArtwork = initialState.artwork?.takeUnless { it.bitmap.isRecycled }
    private var currentArtwork = uploadTexture(
        initialArtwork?.bitmap ?: createGrayArtwork()
    )
    private var previousArtwork = currentArtwork
    private var uploadedArtworkId = initialArtwork?.id ?: 0L
    private var observedArtworkId = uploadedArtworkId
    private var pendingArtwork: Bitmap? = null
    private var pendingArtworkId = 0L
    private var artworkTransitionStart = Double.NEGATIVE_INFINITY

    private var lyricsInitialized = false
    private var lyricsTarget = true
    private var lyricsMix = 1f
    private var lyricsMixFrom = 1f
    private var lyricsMixTo = 1f
    private var lyricsTransitionStart = 0.0

    init {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    fun render(width: Int, height: Int, state: RendererState, time: Double) {
        ensureSize(width, height, state)
        updateArtwork(state, time)
        val transitionMix = artworkTransitionMix(time)
        val currentLyricsMix = updateLyricsMix(state.isBehindLyrics, time)
        val imageScales = imageScales(state, time)
        val blurSigma = lerp(
            ORDINARY_BLUR_SIGMA,
            LYRICS_BLUR_SIGMA,
            currentLyricsMix,
        ) * state.renderScale

        val needsOrdinary = currentLyricsMix < 0.9999f
        val needsLyrics = currentLyricsMix > 0.0001f
        val lyricTarget = checkNotNull(lyricsBlurTarget)
        val ordinaryTarget = checkNotNull(ordinaryBlurTarget)

        val lyricTexture: Int
        val ordinaryTexture: Int
        if (needsOrdinary && needsLyrics) {
            renderBackdrop(floatArrayOf(1f, 1f, 1f), blurSigma, ordinaryTarget, time, transitionMix)
            renderBackdrop(imageScales, blurSigma, lyricTarget, time, transitionMix)
            ordinaryTexture = ordinaryTarget.texture
            lyricTexture = lyricTarget.texture
        } else {
            renderBackdrop(
                if (needsLyrics) imageScales else floatArrayOf(1f, 1f, 1f),
                blurSigma,
                lyricTarget,
                time,
                transitionMix,
            )
            lyricTexture = lyricTarget.texture
            ordinaryTexture = lyricTarget.texture
        }

        renderMaterial(lyricTexture, ordinaryTexture, currentLyricsMix, time)
    }

    private fun renderBackdrop(
        imageScales: FloatArray,
        blurSigma: Float,
        target: RenderTarget,
        time: Double,
        transitionMix: Float,
    ) {
        val rotation = checkNotNull(rotationTarget)
        val half = checkNotNull(kawaseHalfTarget)
        val quarter = checkNotNull(kawaseQuarterTarget)
        val eighth = checkNotNull(kawaseEighthTarget)
        rotation.bind()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        rotationProgram.use()
        rotationProgram.float("uTime", time.toFloat())
        val aspect = outputWidth.toFloat() / outputHeight
        if (aspect >= 1f) {
            rotationProgram.vec2("uViewScale", 1f, aspect)
        } else {
            rotationProgram.vec2("uViewScale", 1f / aspect, 1f)
        }
        rotationProgram.vec3("uImageScales", imageScales[0], imageScales[1], imageScales[2])
        rotationProgram.float("uTransitionMix", transitionMix)
        rotationProgram.int("uCurrentArtwork", 0)
        rotationProgram.int("uPreviousArtwork", 1)
        bindTexture(0, currentArtwork)
        bindTexture(1, previousArtwork)

        rotationProgram.int("uArtworkFill", 1)
        rotationProgram.int("uInstance", 0)
        quad.draw()
        rotationProgram.int("uArtworkFill", 0)
        repeat(3) { instance ->
            rotationProgram.int("uInstance", instance)
            quad.draw()
        }

        val kawaseOffset = blurSigma / KAWASE_SIGMA_PER_OFFSET
        kawasePass(rotation, half, kawaseOffset, upsample = false)
        kawasePass(half, quarter, kawaseOffset, upsample = false)
        kawasePass(quarter, eighth, kawaseOffset, upsample = false)
        kawasePass(eighth, quarter, kawaseOffset, upsample = true)
        kawasePass(quarter, half, kawaseOffset, upsample = true)
        kawasePass(half, target, kawaseOffset, upsample = true)
    }

    private fun kawasePass(
        source: RenderTarget,
        target: RenderTarget,
        offset: Float,
        upsample: Boolean,
    ) {
        target.bind()
        blurProgram.use()
        blurProgram.int("uSource", 0)
        blurProgram.vec2("uTexelSize", 1f / source.width, 1f / source.height)
        blurProgram.float("uOffset", offset)
        blurProgram.int("uUpsample", if (upsample) 1 else 0)
        bindTexture(0, source.texture)
        quad.draw()
    }

    private fun renderMaterial(
        lyricTexture: Int,
        ordinaryTexture: Int,
        modeMix: Float,
        time: Double,
    ) {
        val lowResolutionTarget = materialTarget
        if (lowResolutionTarget == null) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        } else {
            lowResolutionTarget.bind()
        }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        bindTexture(0, lyricTexture)
        bindTexture(1, ordinaryTexture)

        when {
            modeMix <= 0f -> drawFullscreenMaterial(MATERIAL_ORDINARY, modeMix)
            modeMix >= 1f -> {
                if (isPortrait) {
                    drawFullscreenMaterial(MATERIAL_LYRICS, modeMix)
                }
                drawPinchMaterial(MATERIAL_LYRICS, modeMix, time)
            }

            else -> {
                drawFullscreenMaterial(MATERIAL_COMPOSITE, modeMix)
                drawPinchMaterial(MATERIAL_COMPOSITE, modeMix, time)
            }
        }

        if (lowResolutionTarget != null) {
            GLES30.glBindFramebuffer(
                GLES30.GL_READ_FRAMEBUFFER,
                lowResolutionTarget.framebuffer,
            )
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            GLES30.glBlitFramebuffer(
                0,
                0,
                lowResolutionTarget.width,
                lowResolutionTarget.height,
                0,
                0,
                surfaceWidth,
                surfaceHeight,
                GLES30.GL_COLOR_BUFFER_BIT,
                GLES30.GL_LINEAR,
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }
    }

    private fun drawFullscreenMaterial(mode: Int, modeMix: Float) {
        setMaterialUniforms(fullscreenMaterialProgram, mode, modeMix)
        quad.draw()
    }

    private fun drawPinchMaterial(mode: Int, modeMix: Float, time: Double) {
        setMaterialUniforms(pinchMaterialProgram, mode, modeMix)
        pinchMaterialProgram.float("uTime", time.toFloat())
        if (isPortrait) {
            pinchMaterialProgram.vec4("uTextureTransform", 1f, 1f, 0f, 0f)
        } else {
            pinchMaterialProgram.vec4("uTextureTransform", 0.8f, 0.8f, 0.1f, 0.1f)
        }
        mesh.draw()
    }

    private fun setMaterialUniforms(program: GlProgram, mode: Int, modeMix: Float) {
        program.use()
        program.int("uLyricsBackdrop", 0)
        program.int("uOrdinaryBackdrop", 1)
        program.float("uBlackScrimAlpha", 0.4f)
        program.float("uLyricsModeMix", modeMix)
        program.float("uDitherStrength", 1f)
        program.int("uMaterialMode", mode)
    }

    private fun ensureSize(width: Int, height: Int, state: RendererState) {
        val newPortrait = height >= width
        val newPresetIndex = if (newPortrait) {
            state.portraitPresetIndex
        } else {
            state.landscapePresetIndex
        }
        if (newPortrait != isPortrait || newPresetIndex != meshPresetIndex) {
            isPortrait = newPortrait
            meshPresetIndex = newPresetIndex
            mesh.close()
            mesh = GlGeometry.mesh(
                PearMeshMesh.create(
                    isPortrait = isPortrait,
                    presetIndex = meshPresetIndex,
                ),
            )
        }

        val scaledWidth = max(1, (width * state.renderScale).roundToInt())
        val scaledHeight = max(1, (height * state.renderScale).roundToInt())
        if (
            width == surfaceWidth &&
            height == surfaceHeight &&
            scaledWidth == outputWidth &&
            scaledHeight == outputHeight
        ) return
        surfaceWidth = width
        surfaceHeight = height
        outputWidth = scaledWidth
        outputHeight = scaledHeight

        rotationTarget?.close()
        kawaseHalfTarget?.close()
        kawaseQuarterTarget?.close()
        kawaseEighthTarget?.close()
        lyricsBlurTarget?.close()
        ordinaryBlurTarget?.close()
        materialTarget?.close()
        val backdropWidth = max(1, floor(outputWidth / BLUR_DOWNSAMPLE).toInt())
        val backdropHeight = max(1, floor(outputHeight / BLUR_DOWNSAMPLE).toInt())
        rotationTarget = RenderTarget.create(backdropWidth, backdropHeight)
        kawaseHalfTarget = RenderTarget.create(
            max(1, backdropWidth / 2),
            max(1, backdropHeight / 2),
        )
        kawaseQuarterTarget = RenderTarget.create(
            max(1, backdropWidth / 4),
            max(1, backdropHeight / 4),
        )
        kawaseEighthTarget = RenderTarget.create(
            max(1, backdropWidth / 8),
            max(1, backdropHeight / 8),
        )
        lyricsBlurTarget = RenderTarget.create(backdropWidth, backdropHeight)
        ordinaryBlurTarget = RenderTarget.create(backdropWidth, backdropHeight)
        materialTarget = if (outputWidth == width && outputHeight == height) {
            null
        } else {
            RenderTarget.create(outputWidth, outputHeight)
        }
    }

    private fun updateArtwork(state: RendererState, time: Double) {
        val artwork = state.artwork ?: return
        if (artwork.id == observedArtworkId || artwork.bitmap.isRecycled) return
        observedArtworkId = artwork.id

        if (artworkTransitionStart.isFinite()) {
            pendingArtwork = artwork.bitmap
            pendingArtworkId = artwork.id
        } else {
            startArtworkTransition(artwork.bitmap, artwork.id, time)
        }
    }

    private fun startArtworkTransition(bitmap: Bitmap, id: Long, time: Double) {
        if (bitmap.isRecycled) return
        previousArtwork = currentArtwork
        currentArtwork = uploadTexture(bitmap)
        uploadedArtworkId = id
        artworkTransitionStart = time
    }

    private fun artworkTransitionMix(time: Double): Float {
        if (!artworkTransitionStart.isFinite()) return 1f
        val progress = ((time - artworkTransitionStart) / ARTWORK_TRANSITION_SECONDS).toFloat()
        if (progress < 1f) return progress.coerceIn(0f, 1f)

        if (previousArtwork != currentArtwork) {
            GLES30.glDeleteTextures(1, intArrayOf(previousArtwork), 0)
            previousArtwork = currentArtwork
        }
        artworkTransitionStart = Double.NEGATIVE_INFINITY

        val nextArtwork = pendingArtwork
        val nextArtworkId = pendingArtworkId
        pendingArtwork = null
        pendingArtworkId = 0L
        if (nextArtwork != null && nextArtworkId != uploadedArtworkId && !nextArtwork.isRecycled) {
            startArtworkTransition(nextArtwork, nextArtworkId, time)
            return 0f
        }
        return 1f
    }

    private fun updateLyricsMix(target: Boolean, time: Double): Float {
        if (!lyricsInitialized) {
            lyricsInitialized = true
            lyricsTarget = target
            lyricsMix = if (target) 1f else 0f
            lyricsMixFrom = lyricsMix
            lyricsMixTo = lyricsMix
            return lyricsMix
        }
        if (target != lyricsTarget) {
            lyricsMix = evaluateLyricsMix(time)
            lyricsTarget = target
            lyricsMixFrom = lyricsMix
            lyricsMixTo = if (target) 1f else 0f
            lyricsTransitionStart = time
        }
        lyricsMix = evaluateLyricsMix(time)
        return lyricsMix
    }

    private fun evaluateLyricsMix(time: Double): Float {
        if (lyricsMix == lyricsMixTo) return lyricsMix
        val progress = ((time - lyricsTransitionStart) / LYRICS_TRANSITION_SECONDS).toFloat()
        if (progress >= 1f) return lyricsMixTo
        return lerp(lyricsMixFrom, lyricsMixTo, uiKitEaseInOut(progress.coerceIn(0f, 1f)))
    }

    private fun imageScales(state: RendererState, time: Double): FloatArray {
        val power = if (state.isDemoPulseEnabled) {
            val kick = max(0.0, sin(time * PI * 2.0 * 1.15)).pow(12.0)
            val body = max(0.0, sin(time * PI * 2.0 * 0.575 + 0.7)).pow(8.0)
            (kick * 0.86 + body * 0.14).toFloat()
        } else {
            -1f
        }
        return FloatArray(3) { index ->
            val lane = if (power >= 0f) power else state.audioPower[index]
            1f + lane.coerceIn(0f, 1f) * 0.1f
        }
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val handle = IntArray(1)
        GLES30.glGenTextures(1, handle, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, handle[0])
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR_MIPMAP_LINEAR,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        return handle[0]
    }

    private fun bindTexture(unit: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
    }

    override fun close() {
        rotationTarget?.close()
        kawaseHalfTarget?.close()
        kawaseQuarterTarget?.close()
        kawaseEighthTarget?.close()
        lyricsBlurTarget?.close()
        ordinaryBlurTarget?.close()
        materialTarget?.close()
        quad.close()
        mesh.close()
        rotationProgram.close()
        blurProgram.close()
        fullscreenMaterialProgram.close()
        pinchMaterialProgram.close()
        val textures = if (currentArtwork == previousArtwork) {
            intArrayOf(currentArtwork)
        } else {
            intArrayOf(currentArtwork, previousArtwork)
        }
        GLES30.glDeleteTextures(textures.size, textures, 0)
    }

    private companion object {
        const val BLUR_DOWNSAMPLE = 4f
        const val KAWASE_SIGMA_PER_OFFSET = 16f
        const val LYRICS_BLUR_SIGMA = 42.5f
        const val ORDINARY_BLUR_SIGMA = 80f
        const val ARTWORK_TRANSITION_SECONDS = 0.5
        const val LYRICS_TRANSITION_SECONDS = 0.25
        const val MATERIAL_ORDINARY = 0
        const val MATERIAL_LYRICS = 1
        const val MATERIAL_COMPOSITE = 2

        fun lerp(from: Float, to: Float, amount: Float): Float =
            from + (to - from) * amount

        fun uiKitEaseInOut(progress: Float): Float {
            if (progress <= 0f || progress >= 1f) return progress
            var lower = 0f
            var upper = 1f
            var parameter = progress
            repeat(12) {
                parameter = (lower + upper) * 0.5f
                val inverse = 1f - parameter
                val x =
                    3f * inverse * inverse * parameter * 0.42f +
                            3f * inverse * parameter * parameter * 0.58f +
                            parameter * parameter * parameter
                if (x < progress) lower = parameter else upper = parameter
            }
            return parameter * parameter * (3f - 2f * parameter)
        }

        fun createGrayArtwork(): Bitmap =
            createBitmap(1, 1).apply {
                eraseColor(Color.GRAY)
            }
    }
}
