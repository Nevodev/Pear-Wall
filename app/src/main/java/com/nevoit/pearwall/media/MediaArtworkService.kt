package com.nevoit.pearwall.media

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.nevoit.pearwall.PearWallRuntime
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri

class MediaArtworkService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null
    private var controllers = emptyList<MediaController>()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val handler = Handler(Looper.getMainLooper())
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        replaceControllers(sessions.orEmpty())
    }

    override fun onListenerConnected() {
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
        mediaSessionManager?.addOnActiveSessionsChangedListener(
            sessionListener,
            component(this),
        )
        refreshMediaSessions()
        publishActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshMediaSessions()
        if (currentController() == null) publishNotificationArtwork(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshMediaSessions()
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT) {
            // Some players keep a paused MediaController briefly after their
            // notification is swiped away. The removed transport notification is
            // the reliable signal that the player is no longer available.
            PearWallRuntime.setPlaybackPlaying(applicationContext, false)
            PearWallRuntime.showNoArtworkFallback(applicationContext)
        }
    }

    override fun onListenerDisconnected() {
        handler.removeCallbacksAndMessages(null)
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        unregisterControllerCallbacks()
        controllers = emptyList()
        PearWallRuntime.setMediaSessionActive(false)
        mediaSessionManager = null
        PearWallRuntime.setPlaybackPlaying(applicationContext, false)
        super.onListenerDisconnected()
    }

    private fun publishActiveNotifications(retry: Boolean = true) {
        if (currentController() != null) return
        runCatching {
            activeNotifications.orEmpty()
                .filter { it.notification.category == Notification.CATEGORY_TRANSPORT }
                .maxByOrNull(StatusBarNotification::getPostTime)
                ?.let(::publishNotificationArtwork)
        }.onFailure { error ->
            Log.w(TAG, "Unable to read active notifications", error)
            if (retry) handler.postDelayed({ publishActiveNotifications(retry = false) }, 500)
        }
    }

    private fun refreshMediaSessions() {
        val sessions = runCatching {
            mediaSessionManager?.getActiveSessions(component(this)).orEmpty()
        }.onFailure { error ->
            Log.w(TAG, "Unable to read active media sessions", error)
        }.getOrDefault(emptyList())
        replaceControllers(sessions)
    }

    private fun replaceControllers(sessions: List<MediaController>) {
        unregisterControllerCallbacks()
        controllers = sessions
        PearWallRuntime.setMediaSessionActive(
            controllers.any { it.metadata != null || it.playbackState != null },
        )
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    syncPlaybackState()
                    publishCurrentController()
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    syncPlaybackState()
                    publishCurrentController()
                }
            }
            controllerCallbacks[controller] = callback
            controller.registerCallback(callback)
        }
        syncPlaybackState()
        publishCurrentController()
    }

    private fun unregisterControllerCallbacks() {
        controllerCallbacks.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        controllerCallbacks.clear()
    }

    private fun currentController(): MediaController? = controllers.firstOrNull {
        isActivePlaybackState(it.playbackState?.state)
    } ?: controllers.firstOrNull { it.metadata != null }

    private fun syncPlaybackState() {
        val controller = currentController()
        val playing = isActivePlaybackState(controller?.playbackState?.state)
        PearWallRuntime.setPlaybackPlaying(applicationContext, playing)
    }

    private fun publishCurrentController() {
        val controller = currentController() ?: return
        if (!isActivePlaybackState(controller.playbackState?.state)) return
        controller.metadata?.let(::publishMetadata)
    }

    private fun isActivePlaybackState(state: Int?): Boolean =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    private fun publishMetadata(metadata: MediaMetadata?) {
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null) {
            publish(bitmap)
            return
        }
        val uri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        uri?.let(::loadUri)?.let(::publish)
    }

    private fun publishNotificationArtwork(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (notification.category != Notification.CATEGORY_TRANSPORT) return
        val extras = notification.extras
        val bitmap = notification.getLargeIcon()?.toBitmap()
            ?: @Suppress("DEPRECATION") notification.largeIcon
            ?: extras.getParcelableCompat("android.picture")
            ?: extras.getParcelableCompat("android.largeIcon")
        bitmap?.let(::publish)
    }

    private fun loadUri(value: String): Bitmap? = runCatching {
        contentResolver.openInputStream(value.toUri())?.use(BitmapFactory::decodeStream)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun android.os.Bundle.getParcelableCompat(key: String): Bitmap? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, Bitmap::class.java)
        else getParcelable(key)

    private fun publish(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        PearWallRuntime.updateArtwork(applicationContext, bitmap)
    }

    private fun Icon.toBitmap(): Bitmap? = runCatching {
        val drawable = loadDrawable(this@MediaArtworkService) ?: return null
        createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
        ).also { bitmap ->
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }.getOrNull()

    companion object {
        private const val TAG = "PearWall-MediaService"

        fun component(context: android.content.Context) =
            ComponentName(context, MediaArtworkService::class.java)
    }
}
