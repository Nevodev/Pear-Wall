package com.nevoit.pearwall

import android.content.Context
import androidx.core.content.edit

class PearWallSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var noArtworkBehavior: Int
        get() = prefs.getInt("no_artwork", KEEP_LAST)
        set(value) = prefs.edit { putInt("no_artwork", value) }
    var renderScale: Float
        get() = prefs.getFloat("render_scale", .25f)
        set(value) = prefs.edit { putFloat("render_scale", value) }
    var frameRate: Int
        get() = prefs.getInt("frame_rate", 30)
        set(value) = prefs.edit { putInt("frame_rate", value) }
    var randomizeOnScreenOn: Boolean
        get() = prefs.getBoolean("randomize", true)
        set(value) = prefs.edit { putBoolean("randomize", value) }
    var portraitPreset: Int
        get() = prefs.getInt("portrait_preset", 3)
        set(value) = prefs.edit { putInt("portrait_preset", value) }
    var landscapePreset: Int
        get() = prefs.getInt("landscape_preset", 3)
        set(value) = prefs.edit { putInt("landscape_preset", value) }
    var scrimAlpha: Float
        get() = prefs.getFloat("scrim_alpha", DEFAULT_SCRIM_ALPHA)
        set(value) = prefs.edit { putFloat("scrim_alpha", value) }
    var blurMultiplier: Float
        get() = prefs.getFloat("blur_multiplier", DEFAULT_BLUR_MULTIPLIER)
        set(value) = prefs.edit { putFloat("blur_multiplier", value) }
    var flowSpeed: Int
        get() = prefs.getInt("flow_speed", STANDARD_FLOW_SPEED)
        set(value) = prefs.edit { putInt("flow_speed", value) }
    var audioVisualizationEnabled: Boolean
        get() = prefs.getBoolean("audio_visualization", false)
        set(value) = prefs.edit { putBoolean("audio_visualization", value) }
    var customArtworkUri: String?
        get() = prefs.getString("custom_artwork_uri", null)
        set(value) = prefs.edit { putString("custom_artwork_uri", value) }
    var pauseUsesNoArtworkBehavior: Boolean
        get() = prefs.getBoolean("pause_no_artwork", false)
        set(value) = prefs.edit { putBoolean("pause_no_artwork", value) }
    var pauseFlowEnabled: Boolean
        get() = prefs.getBoolean("pause_flow", true)
        set(value) = prefs.edit { putBoolean("pause_flow", value) }

    companion object {
        const val FILE = "pear_wall_settings"
        const val CUSTOM_IMAGE = 0
        const val KEEP_LAST = 1
        const val DEFAULT_SCRIM_ALPHA = 0.4f
        const val DEFAULT_BLUR_MULTIPLIER = 1f
        const val STANDARD_FLOW_SPEED = 0
        const val FAST_FLOW_SPEED = 1
    }
}
