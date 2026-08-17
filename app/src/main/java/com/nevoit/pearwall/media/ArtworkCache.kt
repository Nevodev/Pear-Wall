package com.nevoit.pearwall.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import java.io.File

class ArtworkCache(context: Context) {
    private val file = File(context.filesDir, "last_artwork.webp")

    fun load(): Bitmap? = synchronized(lock) {
        runCatching {
            file.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path) }
        }.onFailure { error ->
            Log.w(TAG, "Unable to load cached artwork", error)
        }.getOrNull()
    }

    fun save(bitmap: Bitmap) = synchronized(lock) {
        val temp = File(file.parentFile, "last_artwork.tmp")
        runCatching {
            temp.outputStream().use { output ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                check(bitmap.compress(format, 90, output)) {
                    "Unable to encode artwork"
                }
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to cache artwork", error)
        }
        temp.delete()
    }

    private companion object {
        const val TAG = "PearWall-ArtworkCache"
        val lock = Any()
    }
}
