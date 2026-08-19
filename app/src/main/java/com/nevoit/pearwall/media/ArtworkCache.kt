package com.nevoit.pearwall.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import java.io.File

class ArtworkCache(
    context: Context,
    fileName: String = "last_artwork.webp",
) {
    private val file = File(context.filesDir, fileName)

    fun load(): Bitmap? = synchronized(lock) {
        runCatching {
            file.takeIf(File::isFile)?.let { decodeSampled(it) }
        }.onFailure { error ->
            Log.w(TAG, "Unable to load cached artwork", error)
        }.getOrNull()
    }

    private fun decodeSampled(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_DIMENSION
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            source.path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
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
        const val MAX_DIMENSION = 2048
        val lock = Any()
    }
}
