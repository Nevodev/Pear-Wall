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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.nevoit.pearwall.PearWallRuntime
import com.nevoit.pearwall.PearWallSettings

class MediaArtworkService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null
    private var controllers = emptyList<MediaController>()
    private val mediaNotificationPackages = mutableMapOf<String, String>()
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
        syncActiveMediaNotifications()
        refreshMediaSessions()
        publishActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isEligibleMediaNotification(sbn)) {
            mediaNotificationPackages[sbn.key] = sbn.packageName
            publishMediaNotificationState()
        }
        refreshMediaSessions()
        if (currentController() == null) publishNotificationArtwork(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val wasTracked = mediaNotificationPackages.remove(sbn.key) != null
        if (wasTracked) publishMediaNotificationState()
        refreshMediaSessions()
        if (wasTracked && mediaNotificationPackages.isEmpty()) {
            // A controller may remain after its notification is removed. Notification
            // presence, rather than controller lifetime, defines the no-artwork state.
            PearWallRuntime.setPlaybackPlaying(applicationContext, false)
            PearWallRuntime.showNoArtworkFallback(applicationContext)
        }
    }

    override fun onListenerDisconnected() {
        handler.removeCallbacksAndMessages(null)
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        unregisterControllerCallbacks()
        controllers = emptyList()
        mediaNotificationPackages.clear()
        // Disconnection means the notification state is unknown, not that the media
        // notification disappeared. Keep the last persisted value for cold start.
        mediaSessionManager = null
        PearWallRuntime.setPlaybackPlaying(applicationContext, false)
        super.onListenerDisconnected()
    }

    private fun syncActiveMediaNotifications() {
        val notifications = runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
        mediaNotificationPackages.clear()
        notifications
            .filter(::isEligibleMediaNotification)
            .forEach { mediaNotificationPackages[it.key] = it.packageName }
        publishMediaNotificationState()
    }

    private fun isEligibleMediaNotification(sbn: StatusBarNotification): Boolean =
        sbn.notification.category == Notification.CATEGORY_TRANSPORT &&
                !isBlockedPackage(sbn.packageName)

    private fun isBlockedPackage(packageName: String): Boolean =
        PearWallSettings(this).blockVideoPlayers && packageName in VIDEO_PLAYER_PACKAGE_BLACKLIST

    private fun publishMediaNotificationState() {
        PearWallRuntime.setMediaSessionActive(
            applicationContext,
            mediaNotificationPackages.isNotEmpty(),
        )
    }

    private fun publishActiveNotifications(retry: Boolean = true) {
        if (currentController() != null) return
        runCatching {
            activeNotifications.orEmpty()
                .filter(::isEligibleMediaNotification)
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

    private fun currentController(): MediaController? {
        // A MediaSession can outlive its media notification (for example, when a
        // video is played inline). Without this guard it would look like playback
        // to PearWall even though there is no media source to track.
        val packagesWithMediaNotifications = mediaNotificationPackages.values
        if (packagesWithMediaNotifications.isEmpty()) return null
        val eligibleControllers = controllers.filter {
            it.packageName in packagesWithMediaNotifications
        }
        return eligibleControllers.firstOrNull {
            isActivePlaybackState(it.playbackState?.state)
        } ?: eligibleControllers.firstOrNull { it.metadata != null }
    }

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
        if (!isEligibleMediaNotification(sbn)) return
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
    private fun Bundle.getParcelableCompat(key: String): Bitmap? =
        if (Build.VERSION.SDK_INT >= 33) getParcelable(key, Bitmap::class.java)
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
        private val VIDEO_PLAYER_PACKAGE_BLACKLIST = setOf(
            "com.huawei.himovie",
            "com.qiyi.video.lite",
            "com.qiyi.video",
            "com.sohu.sohuvideo",
            "com.youku.phone",
            "com.huawei.hwvplayer.youku",
            "com.ss.android.article.video",
            "com.tencent.qqlive",
            "tv.danmaku.bili",
            "com.baidu.haokan",
            "com.phoenix.read",
            "com.smile.gifmaker",
            "com.kuaishou.nebula",
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.live",
            "com.ss.android.ugc.aweme.lite",
            "com.android.VideoPlayer",
            "com.cctv.yangshipin.app.androidp",
            "com.baidu.video",
            "com.letv.android.client",
            "com.example.pptv",
            "com.hunantv.imgo.activity",
            "com.pplive.androidphone",
            "com.funshion.video.mobile",
            "com.cmcc.cmvideo",
            "com.baidu.netdisk",
            "com.iqiyi.ivrcinema.ardp",
            "com.iqiyi.ivrcinema.cb",
            "tv.pps.mobile",
            "cn.cntv",
            "com.sohu.tv",
            "com.example.piliplus",
            "com.miui.video",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "com.amazon.avod.thirdpartyclient",
            "com.netflix.mediaclient",
            "com.vimeo.android.videoapp",
            "com.streaminfo.huluguide",
            "com.tubitv",
            "com.cbs.ca",
            "com.upst.hayu",
            "com.plexapp.android",
            "com.netshort.abroad",
            "com.wbd.stream",
            "com.apple.atve.androidtv.appletv",
            "com.disney.disneyplus",
            "com.google.android.videos",
            "org.jellyfin.mobile",
            "com.crunchyroll.crunchyroid",
            "com.ted.android",
            "com.vidio.android",
            "com.coloros.video",
            "com.bilibili.app.in",
            "com.guozhigq.pilipala",
            "com.skylineui.bili"
        )

        fun component(context: android.content.Context) =
            ComponentName(context, MediaArtworkService::class.java)

        fun requestRefresh(context: android.content.Context) {
            requestRebind(component(context))
        }
    }
}
