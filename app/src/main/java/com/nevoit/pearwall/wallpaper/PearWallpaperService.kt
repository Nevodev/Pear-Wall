package com.nevoit.pearwall.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Display
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.nevoit.pearwall.PearWallRuntime
import com.nevoit.pearwall.pearmesh.PearMeshState
import com.nevoit.pearwall.pearmesh.gl.EglRenderThread
import java.util.concurrent.atomic.AtomicInteger

class PearWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PearEngine()

    private inner class PearEngine : Engine() {
        private val engineId = nextEngineId.incrementAndGet()
        private val state: PearMeshState = PearWallRuntime.createState(applicationContext)
        private var renderer: EglRenderThread? = null
        private var visible = false
        private var dozing = false
        private var dreaming = false
        private var renderingEnabled = false
        private var lastDiagnosticState: String? = null
        private val displayManager = getSystemService(DisplayManager::class.java)
        private val powerManager = getSystemService(PowerManager::class.java)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                updateDeviceState("displayChanged")
            }
        }
        private val deviceStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> dreaming = false
                    Intent.ACTION_DREAMING_STARTED -> dreaming = true
                    Intent.ACTION_DREAMING_STOPPED -> dreaming = false
                }
                updateDeviceState("broadcast:${intent.action}")
            }
        }
        private val activeStateProbe = object : Runnable {
            override fun run() {
                updateDeviceState("activeProbe")
                if (renderingEnabled) mainHandler.postDelayed(this, ACTIVE_PROBE_INTERVAL_MS)
            }
        }

        init {
            state.setArtworkChangedListener {
                mainHandler.post {
                    if (renderingEnabled) renderer?.restartRenderSession()
                }
            }
            displayManager.registerDisplayListener(displayListener, mainHandler)
            ContextCompat.registerReceiver(
                applicationContext,
                deviceStateReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_DREAMING_STARTED)
                    addAction(Intent.ACTION_DREAMING_STOPPED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            updateDeviceState("engineInit")
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            visible = isVisible
            updateDeviceState("surfaceCreated")
            startRenderer(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            logAod("surfaceChanged format=$format size=${width}x$height")
            renderer?.resize(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            logAod("surfaceDestroyed")
            mainHandler.removeCallbacks(activeStateProbe)
            renderer?.stopAndJoin()
            renderer = null
            renderingEnabled = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (this.visible == visible) return
            this.visible = visible
            logAod("visibilityChanged visible=$visible")
            updateDeviceState("visibilityChanged")
        }

        override fun onDestroy() {
            logAod("engineDestroyed")
            mainHandler.removeCallbacks(activeStateProbe)
            displayManager.unregisterDisplayListener(displayListener)
            runCatching { applicationContext.unregisterReceiver(deviceStateReceiver) }
            renderer?.stopAndJoin()
            state.setArtworkChangedListener(null)
            PearWallRuntime.release(state)
            super.onDestroy()
        }

        private fun updateDeviceState(trigger: String) {
            val displayState = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.state
            val interactive = powerManager.isInteractive
            dozing = displayState != Display.STATE_ON || !interactive || dreaming
            val shouldRender = visible && !dozing
            val diagnosticState =
                "display=${displayStateName(displayState)} interactive=$interactive " +
                    "dreaming=$dreaming visible=$visible render=$shouldRender"
            if (trigger != "activeProbe" || diagnosticState != lastDiagnosticState) {
                logAod("$trigger $diagnosticState")
            }
            lastDiagnosticState = diagnosticState
            updateRenderingEnabled(shouldRender)
        }

        private fun randomizeAfterRenderingPaused() {
            PearWallRuntime.randomizePresets(applicationContext)
        }

        private fun updateRenderingEnabled(enabled: Boolean) {
            if (renderingEnabled == enabled) return
            renderingEnabled = enabled
            renderer?.setRenderingEnabled(enabled)
            mainHandler.removeCallbacks(activeStateProbe)
            if (enabled) mainHandler.postDelayed(activeStateProbe, ACTIVE_PROBE_INTERVAL_MS)
        }

        private fun startRenderer(holder: SurfaceHolder) {
            if (!holder.surface.isValid) {
                Log.w(TAG, "engine=$engineId startRenderer ignored: invalid surface")
                return
            }
            renderer?.stopAndJoin()
            val frame = holder.surfaceFrame
            logAod("startRenderer size=${frame.width()}x${frame.height()} enabled=$renderingEnabled")
            renderer = EglRenderThread(
                context = applicationContext,
                surface = holder.surface,
                initialWidth = frame.width(),
                initialHeight = frame.height(),
                stateProvider = state::snapshot,
                onRenderingPaused = {
                    mainHandler.post { randomizeAfterRenderingPaused() }
                },
                maxContinuousRenderMillis = MAX_CONTINUOUS_RENDER_MILLIS,
                initiallyRenderingEnabled = renderingEnabled,
            ).also(EglRenderThread::start)
            mainHandler.removeCallbacks(activeStateProbe)
            if (renderingEnabled) mainHandler.postDelayed(activeStateProbe, ACTIVE_PROBE_INTERVAL_MS)
        }

        private fun logAod(message: String) {
            Log.i(TAG, "engine=$engineId $message")
        }
    }

    private companion object {
        const val TAG = "PearWallAod"
        const val ACTIVE_PROBE_INTERVAL_MS = 1_000L
        const val MAX_CONTINUOUS_RENDER_MILLIS = 20_000L
        val nextEngineId = AtomicInteger(0)

        fun displayStateName(state: Int?): String = when (state) {
            Display.STATE_OFF -> "OFF"
            Display.STATE_ON -> "ON"
            Display.STATE_DOZE -> "DOZE"
            Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
            Display.STATE_VR -> "VR"
            null -> "UNKNOWN"
            else -> state.toString()
        }
    }
}
