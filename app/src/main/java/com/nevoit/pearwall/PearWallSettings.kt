package com.nevoit.pearwall

import android.content.Context

class PearWallSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var noArtworkBehavior: Int
        get() = prefs.getInt("no_artwork", KEEP_LAST)
        set(value) = prefs.edit().putInt("no_artwork", value).apply()
    var renderScale: Float
        get() = prefs.getFloat("render_scale", .25f)
        set(value) = prefs.edit().putFloat("render_scale", value).apply()
    var frameRate: Int
        get() = prefs.getInt("frame_rate", 30)
        set(value) = prefs.edit().putInt("frame_rate", value).apply()
    var randomizeOnScreenOn: Boolean
        get() = prefs.getBoolean("randomize", false)
        set(value) = prefs.edit().putBoolean("randomize", value).apply()
    var portraitPreset: Int
        get() = prefs.getInt("portrait_preset", 2)
        set(value) = prefs.edit().putInt("portrait_preset", value).apply()
    var landscapePreset: Int
        get() = prefs.getInt("landscape_preset", 4)
        set(value) = prefs.edit().putInt("landscape_preset", value).apply()
    var customArtworkUri: String?
        get() = prefs.getString("custom_artwork_uri", null)
        set(value) = prefs.edit().putString("custom_artwork_uri", value).apply()

    companion object {
        const val FILE = "pear_wall_settings"
        const val CUSTOM_IMAGE = 0
        const val KEEP_LAST = 1
    }
}
