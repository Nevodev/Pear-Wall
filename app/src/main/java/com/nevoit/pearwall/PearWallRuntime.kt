package com.nevoit.pearwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.nevoit.pearwall.media.ArtworkCache
import com.nevoit.pearwall.pearmesh.PearMeshState
import java.util.concurrent.CopyOnWriteArraySet

object PearWallRuntime {
    private val states = CopyOnWriteArraySet<PearMeshState>()
    private var lastPublishedArtwork: Bitmap? = null

    fun createState(context: Context): PearMeshState {
        val settings = PearWallSettings(context)
        return PearMeshState(
            portraitPresetIndex = settings.portraitPreset,
            landscapePresetIndex = settings.landscapePreset,
            renderScale = settings.renderScale,
            targetFrameRate = settings.frameRate,
        ).also { state ->
            states += state
            initialArtwork(context, settings)?.let(state::setArtwork)
        }
    }

    fun release(state: PearMeshState) {
        states -= state
    }

    @Synchronized
    fun updateArtwork(context: Context, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val duplicate = lastPublishedArtwork?.let { previous ->
            !previous.isRecycled && runCatching { previous.sameAs(bitmap) }.getOrDefault(false)
        } ?: false
        if (duplicate) return

        lastPublishedArtwork = bitmap
        ArtworkCache(context).save(bitmap)
        states.forEach { it.setArtwork(bitmap) }
    }

    fun showNoArtworkFallback(context: Context) {
        val settings = PearWallSettings(context)
        if (settings.noArtworkBehavior != PearWallSettings.CUSTOM_IMAGE) return
        settings.customArtworkUri?.let { value ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)
            }.getOrNull()?.let { bitmap -> states.forEach { it.setArtwork(bitmap) } }
        }
    }

    fun applySettings(context: Context) {
        val settings = PearWallSettings(context)
        states.forEach {
            it.setRenderScale(settings.renderScale)
            it.setTargetFrameRate(settings.frameRate)
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
